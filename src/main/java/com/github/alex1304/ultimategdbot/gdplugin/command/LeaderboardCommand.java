package com.github.alex1304.ultimategdbot.gdplugin.command;

import static com.github.alex1304.ultimategdbot.api.util.Markdown.code;
import static java.util.function.Predicate.not;
import static java.util.stream.Collectors.toCollection;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static reactor.function.TupleUtils.function;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;

import com.github.alex1304.jdash.entity.GDUser;
import com.github.alex1304.jdash.exception.GDClientException;
import com.github.alex1304.ultimategdbot.api.Translator;
import com.github.alex1304.ultimategdbot.api.command.CommandFailedException;
import com.github.alex1304.ultimategdbot.api.command.Context;
import com.github.alex1304.ultimategdbot.api.command.PermissionLevel;
import com.github.alex1304.ultimategdbot.api.command.Scope;
import com.github.alex1304.ultimategdbot.api.command.annotated.CommandAction;
import com.github.alex1304.ultimategdbot.api.command.annotated.CommandDescriptor;
import com.github.alex1304.ultimategdbot.api.command.annotated.CommandDoc;
import com.github.alex1304.ultimategdbot.api.command.annotated.CommandPermission;
import com.github.alex1304.ultimategdbot.api.command.menu.InteractiveMenuService;
import com.github.alex1304.ultimategdbot.api.command.menu.PageNumberOutOfRangeException;
import com.github.alex1304.ultimategdbot.api.command.menu.UnexpectedReplyException;
import com.github.alex1304.ultimategdbot.api.database.DatabaseService;
import com.github.alex1304.ultimategdbot.api.emoji.EmojiService;
import com.github.alex1304.ultimategdbot.api.util.DurationUtils;
import com.github.alex1304.ultimategdbot.api.util.MessageSpecTemplate;
import com.github.alex1304.ultimategdbot.gdplugin.GDService;
import com.github.alex1304.ultimategdbot.gdplugin.database.GDLeaderboardBanDao;
import com.github.alex1304.ultimategdbot.gdplugin.database.GDLeaderboardBanData;
import com.github.alex1304.ultimategdbot.gdplugin.database.GDLeaderboardDao;
import com.github.alex1304.ultimategdbot.gdplugin.database.GDLeaderboardData;
import com.github.alex1304.ultimategdbot.gdplugin.database.GDLinkedUserDao;
import com.github.alex1304.ultimategdbot.gdplugin.database.GDLinkedUserData;
import com.github.alex1304.ultimategdbot.gdplugin.database.ImmutableGDLeaderboardBanData;
import com.github.alex1304.ultimategdbot.gdplugin.database.ImmutableGDLeaderboardData;
import com.github.alex1304.ultimategdbot.gdplugin.util.GDFormatter;
import com.github.alex1304.ultimategdbot.gdplugin.util.GDUsers;

import discord4j.common.util.Snowflake;
import discord4j.core.object.entity.Guild;
import discord4j.core.object.entity.User;
import discord4j.core.spec.EmbedCreateSpec;
import reactor.core.publisher.EmitterProcessor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.core.publisher.Mono;
import reactor.util.Logger;
import reactor.util.Loggers;
import reactor.util.annotation.Nullable;

@CommandDescriptor(
		aliases = { "leaderboard", "leaderboards" },
		shortDescription = "Builds and displays a server-wide Geometry Dash leaderboard.",
		scope = Scope.GUILD_ONLY
)
public class LeaderboardCommand {

	private static final Logger LOGGER = Loggers.getLogger(LeaderboardCommand.class);
	private static final int ENTRIES_PER_PAGE = 20;
	
	private volatile boolean isLocked;
	private final GDService gdService;
	
	public LeaderboardCommand(GDService gdService) {
		this.gdService = gdService;
	}

	@CommandAction
	@CommandDoc("Displays a server-wide Geometry Dash leaderboard of the given type. All members of the current server that have a Geometry "
			+ "Dash account linked may be shown in the leaderboards provided by this command.\n"
			+ "`stat_name` can be one of: `stars`, `demons`, `diamonds`, `ucoins`, `scoins`, `cp` to show respectively stars, demons, diamonds, "
			+ "user coins, secret coins and creator points leaderboards. Leaderboards are refreshed once in a while, up to 4 times per day.")
	public Mono<Void> run(Context ctx, @Nullable String statName) {
		if (isLocked) {
			return Mono.error(new CommandFailedException("Leaderboards are temporarily locked because "
					+ "they are currently being refreshed. Retry later."));
		}
		var starEmoji = ctx.bot().service(EmojiService.class).emoji("star");
		var diamondEmoji = ctx.bot().service(EmojiService.class).emoji("diamond");
		var userCoinEmoji = ctx.bot().service(EmojiService.class).emoji("user_coin");
		var secretCoinEmoji = ctx.bot().service(EmojiService.class).emoji("secret_coin");
		var demonEmoji = ctx.bot().service(EmojiService.class).emoji("demon");
		var cpEmoji = ctx.bot().service(EmojiService.class).emoji("creator_points");
		if (statName == null) {
			return Mono.zip(starEmoji, diamondEmoji, userCoinEmoji, secretCoinEmoji, demonEmoji, cpEmoji)
					.flatMap(tuple -> ctx.reply("**Compare your stats with other players in this server by "
							+ "showing a server-wide Geometry Dash leaderboard!**\n"
							+ "__To get started, select which type of leaderboard you want to show:__\n"
							+ "To view " + tuple.getT1() + " Stars leaderboard, run `" + ctx.prefixUsed() + "leaderboard stars`\n"
							+ "To view " + tuple.getT2() + " Diamonds leaderboard, run `" + ctx.prefixUsed() + "leaderboard diamonds`\n"
							+ "To view " + tuple.getT3() + " User Coins leaderboard, run `" + ctx.prefixUsed() + "leaderboard ucoins`\n"
							+ "To view " + tuple.getT4() + " Secret Coins leaderboard, run `" + ctx.prefixUsed() + "leaderboard scoins`\n"
							+ "To view " + tuple.getT5() + " Demons leaderboard, run `" + ctx.prefixUsed() + "leaderboard demons`\n"
							+ "To view " + tuple.getT6() + " Creator Points leaderboard, run `" + ctx.prefixUsed() + "leaderboard cp`\n"))
					.then();
		}
		ToIntFunction<GDLeaderboardData> stat;
		Mono<String> emojiMono;
		boolean noBanList;
		switch (statName.toLowerCase()) {
			case "stars":
				stat = GDLeaderboardData::stars;
				emojiMono = starEmoji;
				noBanList = false;
				break;
			case "diamonds":
				stat = GDLeaderboardData::diamonds;
				emojiMono = diamondEmoji;
				noBanList = false;
				break;
			case "ucoins":
				stat = GDLeaderboardData::userCoins;
				emojiMono = userCoinEmoji;
				noBanList = false;
				break;
			case "scoins":
				stat = GDLeaderboardData::secretCoins;
				emojiMono = secretCoinEmoji;
				noBanList = false;
				break;
			case "demons":
				stat = GDLeaderboardData::demons;
				emojiMono = demonEmoji;
				noBanList = false;
				break;
			case "cp":
				stat = GDLeaderboardData::creatorPoints;
				emojiMono = cpEmoji;
				noBanList = true;
				break;
			default:
				return Mono.error(new CommandFailedException("Unknown leaderboard type, expected " + code("stars") + ", "
						+ code("diamonds") + ", " + code("ucoins") + ", " + code("scoins") + ", " + code("demons") + " or "
						+ code("cp") + "."));
		}
		var now = Instant.now();
		var lastRefreshed = new AtomicReference<Instant>(now);
		var emojiRef = new AtomicReference<String>();
		return ctx.event().getGuild()
				.flatMap(guild -> guild.getMembers()
						.collect(toMap(m -> m.getId().asLong(), User::getTag, (a, b) -> a))
						.filter(not(Map::isEmpty))
						.flatMap(members -> ctx.bot().service(DatabaseService.class)
								.withExtension(GDLinkedUserDao.class, dao -> dao.getAllIn(List.copyOf(members.keySet())))
								.filter(not(List::isEmpty))
								.flatMap(linkedUsers -> Mono.zip(
												emojiMono.doOnNext(emojiRef::set),
												ctx.bot().service(DatabaseService.class)
														.withExtension(GDLeaderboardDao.class, dao -> dao.getAllIn(gdAccIds(linkedUsers))),
												ctx.bot().service(DatabaseService.class)
														.withExtension(GDLeaderboardBanDao.class, dao -> dao.getAllIn(gdAccIds(linkedUsers)))
														.flatMapMany(Flux::fromIterable)
														.map(GDLeaderboardBanData::accountId)
														.collect(Collectors.toUnmodifiableSet()))
										.map(function((emoji, userStats, bans) -> userStats.stream()
												.peek(u -> lastRefreshed.compareAndSet(now, userStats.get(0).lastRefreshed()))
												.filter(u -> noBanList || !bans.contains(u.accountId()))
												.flatMap(u -> linkedUsers.stream()
														.filter(l -> l.gdUserId() == u.accountId())
														.map(GDLinkedUserData::discordUserId)
														.map(Snowflake::asLong)
														.map(members::get)
														.map(tag -> new LeaderboardEntry(stat.applyAsInt(u), u, tag)))
												.collect(toCollection(() -> new TreeSet<LeaderboardEntry>()))))))
						.map(List::copyOf)
						.defaultIfEmpty(List.of())
						.flatMap(list -> {
							if (list.size() <= ENTRIES_PER_PAGE) {
								return ctx.reply(m -> m.setEmbed(leaderboardView(ctx.prefixUsed(), guild, list, 0,
										lastRefreshed.get(), null, emojiRef.get()))).then();
							}
							var highlighted = new AtomicReference<String>();
							BiFunction<Translator, Integer, MessageSpecTemplate> paginator = (tr, page) -> {
								PageNumberOutOfRangeException.check(page, 0, (list.size() - 1) / ENTRIES_PER_PAGE);
								return new MessageSpecTemplate(leaderboardView(ctx.prefixUsed(), guild, list, page,
										lastRefreshed.get(), highlighted.get(), emojiRef.get()));
							};
							return ctx.bot().service(InteractiveMenuService.class).createPaginated(paginator)
									.addMessageItem("finduser", interaction -> Mono.just(interaction.getArgs().getAllAfter(1))
											.filter(userName -> !userName.isEmpty())
											.switchIfEmpty(Mono.error(new UnexpectedReplyException("Please specify a GD username.")))
											.flatMap(userName -> GDUsers.stringToUser(ctx.bot(), gdService.getGdClient(), userName))
											.onErrorMap(GDClientException.class, e -> new UnexpectedReplyException("Unable to fetch info from that user in Geometry Dash."))
											.flatMap(gdUser -> {
												final var ids = list.stream().map(entry -> entry.getStats().accountId()).collect(Collectors.toList());
												final var rank = ids.indexOf(gdUser.getAccountId());
												if (rank == -1) {
													return Mono.error(new UnexpectedReplyException("This user wasn't found on this leaderboard."));
												}
												final var jumpTo = rank / ENTRIES_PER_PAGE;
												interaction.set("currentPage", jumpTo);
												highlighted.set(gdUser.getName());
												return interaction.getMenuMessage()
														.edit(paginator.apply(interaction.getTranslator(), jumpTo).toMessageEditSpec())
														.then();
											}))
									.open(ctx);
						})).then();
	}
	
	@CommandAction("refresh")
	@CommandDoc("Refreshes the leaderboard (bot admin only). Leaderboard refresh is an heavy process that consists of "
			+ "loading profiles of all users that have linked their account to the bot. This command "
			+ "may be run once in 6 hours.")
	@CommandPermission(level = PermissionLevel.BOT_ADMIN)
	public Mono<Void> runRefresh(Context ctx) {
		if (isLocked) {
			return Mono.error(new CommandFailedException("Refresh is already in progress."));
		}
		isLocked = true;
		LOGGER.debug("Locked leaderboards");
		var now = Instant.now();
		return ctx.bot().service(DatabaseService.class).withExtension(GDLeaderboardDao.class, GDLeaderboardDao::getLastRefreshed)
				.flatMap(Mono::justOrEmpty)
				.map(Timestamp::toInstant)
				.defaultIfEmpty(Instant.MIN)
				.map(lastRefreshed -> Duration.ofHours(6).minus(Duration.between(lastRefreshed, Instant.now())))
				.flatMap(cooldown -> !cooldown.isNegative()
						? Mono.error(new CommandFailedException("The leaderboard has already been refreshed less than 6 hours ago. "
								+ "Try again in " + DurationUtils.format(cooldown.withNanos(0))))
						: Mono.empty())
				.then(ctx.bot().service(DatabaseService.class).withExtension(GDLinkedUserDao.class, GDLinkedUserDao::getAll))
				.flatMapMany(Flux::fromIterable)
				.distinct(GDLinkedUserData::gdUserId)
				.collectList()
				.flatMap(list -> ctx.bot().service(EmojiService.class).emoji("info")
						.flatMap(info -> ctx.bot().log(info + " Leaderboard refresh triggered by **" + ctx.author().getTag() + "**"))
						.then(ctx.reply("Refreshing leaderboards..."))
						.flatMapMany(message -> {
							var processor = EmitterProcessor.<Long>create();
							var sink = processor.sink(FluxSink.OverflowStrategy.BUFFER);
							var done = new AtomicBoolean();
							processor.take(Duration.ofSeconds(2))
									.takeLast(1)
									.flatMap(i -> message
											.edit(spec -> spec.setContent("Refreshing leaderboards... "
													+ "(" + i + "/" + list.size() + " users processed)"))
											.onErrorResume(e -> Mono.empty()))
									.repeat(() -> !done.get())
									.then(message.delete().onErrorResume(e -> Mono.empty()))
									.subscribe();
							return Flux.fromIterable(list)
									.flatMap(linkedUser -> gdService.getGdClient().getUserByAccountId(linkedUser.gdUserId())
											.onErrorResume(e -> Mono.fromRunnable(() -> LOGGER.warn("Failed to refresh user "
													+ linkedUser.gdUserId(), e))), gdService.getLeaderboardRefreshParallelism())
									.index()
									.map(function((i, gdUser) -> {
										sink.next(i);
										return ImmutableGDLeaderboardData.builder()
												.accountId(gdUser.getAccountId())
												.name(gdUser.getName())
												.lastRefreshed(now)
												.stars(gdUser.getStars())
												.diamonds(gdUser.getDiamonds())
												.userCoins(gdUser.getUserCoins())
												.secretCoins(gdUser.getSecretCoins())
												.demons(gdUser.getDemons())
												.creatorPoints(gdUser.getCreatorPoints())
												.build();
									}))
									.doFinally(__ -> done.set(true));
						})
						.collectList())
				.flatMap(stats -> ctx.reply("Saving new player stats to database...")
						.onErrorResume(e -> Mono.empty())
						.flatMap(message -> ctx.bot().service(DatabaseService.class)
								.useExtension(GDLeaderboardDao.class, dao -> dao.cleanInsertAll(stats))
								.then(message.delete())
								.then(ctx.bot().service(EmojiService.class).emoji("success")
										.flatMap(success -> ctx.reply(success + " Leaderboard refreshed!"))
										.then())))
				.doFinally(signal -> {
					isLocked = false;
					LOGGER.debug("Unlocked leaderboards");
				});
	}
	
	@CommandAction("ban")
	@CommandDoc("Bans a player from server leaderboards (bot admin only). Players that are banned from leaderboards won't be displayed in the results of "
			+ "the `leaderboard` command in any server, regardless of whether they have an account linked. Bans are by GD account and "
			+ "not by Discord account, so linking with a different Discord account does not allow ban evasion.")
	@CommandPermission(level = PermissionLevel.BOT_ADMIN)
	public Mono<Void> runBan(Context ctx, GDUser gdUser) {
		return ctx.bot().service(DatabaseService.class)
				.withExtension(GDLeaderboardBanDao.class, dao -> dao.get(gdUser.getAccountId()))
				.flatMap(Mono::justOrEmpty)
				.flatMap(__ -> Mono.error(new CommandFailedException("This user is already banned.")))
				.then(ctx.bot().service(DatabaseService.class)
						.useExtension(GDLeaderboardBanDao.class, dao -> dao.insert(ImmutableGDLeaderboardBanData.builder()
								.accountId(gdUser.getAccountId())
								.bannedBy(ctx.author().getId())
								.build())))
				.then(ctx.reply("**" + gdUser.getName() + "** is now banned from leaderboards!"))
				.and(ctx.bot().service(EmojiService.class).emoji("info")
						.flatMap(info -> ctx.bot().log(info + " Leaderboard ban added: **" + gdUser.getName()
								+ "**, by **" + ctx.author().getTag() + "**")));
	}
	
	@CommandAction("unban")
	@CommandDoc("Unbans a player from server leaderboards (bot admin only). Players that are banned from leaderboards won't be displayed in the results of "
			+ "the `leaderboard` command in any server, regardless of whether they have an account linked. Bans are by GD account and "
			+ "not by Discord account, so linking with a different Discord account does not allow ban evasion.")
	@CommandPermission(level = PermissionLevel.BOT_ADMIN)
	public Mono<Void> runUnban(Context ctx, GDUser gdUser) {
		return ctx.bot().service(DatabaseService.class)
				.withExtension(GDLeaderboardBanDao.class, dao -> dao.get(gdUser.getAccountId()))
				.flatMap(Mono::justOrEmpty)
				.switchIfEmpty(Mono.error(new CommandFailedException("This user is already unbanned.")))
				.flatMap(banData -> ctx.bot().service(DatabaseService.class).useExtension(GDLeaderboardBanDao.class, dao -> dao.delete(banData.accountId())))
				.then(ctx.reply("**" + gdUser.getName() + "** has been unbanned from leaderboards!"))
				.and(ctx.bot().service(EmojiService.class).emoji("info")
						.flatMap(info -> ctx.bot().log(info + " Leaderboard ban removed: **" + gdUser.getName()
								+ "**, by **" + ctx.author().getTag() + "**")));
	}

	private static Consumer<EmbedCreateSpec> leaderboardView(String prefix, Guild guild,
			List<LeaderboardEntry> entryList, int page, Instant lastRefreshed, String highlighted, String emoji) {
		final var size = entryList.size();
		final var maxPage = (size - 1) / ENTRIES_PER_PAGE;
		final var offset = page * ENTRIES_PER_PAGE;
		final var subList = entryList.subList(offset, Math.min(offset + ENTRIES_PER_PAGE, size));
		final var refreshed = Duration.between(lastRefreshed, Instant.now()).withNanos(0);
		return embed -> {
			embed.setTitle("Geometry Dash leaderboard for server __" + guild.getName() + "__");
			if (size == 0 || subList.isEmpty()) {
				embed.setDescription("No entries.");
				return;
			}
			var sb = new StringBuilder();
			var rankWidth = (int) Math.log10(size) + 1;
			var statWidth = (int) Math.log10(subList.get(0).getValue()) + 1;
			final var maxRowLength = 100;
			for (var i = 1 ; i <= subList.size() ; i++) {
				var entry = subList.get(i - 1);
				var isHighlighted = entry.getStats().name().equalsIgnoreCase(highlighted);
				var rank = page * ENTRIES_PER_PAGE + i;
				if (isHighlighted) {
					sb.append("**");
				}
				var row = String.format("%s | %s %s | %s (%s)",
						String.format("`#%" + rankWidth + "d`", rank).replaceAll(" ", " ‌‌"),
						emoji,
						GDFormatter.formatCode(entry.getValue(), statWidth),
						entry.getStats().name(),
						entry.getDiscordUser());
				if (row.length() > maxRowLength) {
					row = row.substring(0, maxRowLength - 3) + "...";
				}
				sb.append(row).append("\n");
				if (isHighlighted) {
					sb.append("**");
				}
			}
			embed.setDescription("**Total players: " + size + ", " + emoji + " leaderboard**\n\n" + sb.toString());
			embed.addField("Last refreshed: " + DurationUtils.format(refreshed) + " ago", "Note that members of this server must have "
					+ "linked their Geometry Dash account with `" + prefix + "account` in order to be displayed on this "
					+ "leaderboard. If you have just freshly linked your account, you will need to wait for next leaderboard refresh "
					+ "in order to be displayed.", false);
			if (maxPage > 0) {
				embed.addField("Page " + (page + 1) + "/" + (maxPage + 1),
						"To go to a specific page, type `page <number>`, e.g `page 3`\n"
						+ "To jump to the page where a specific user is, type `finduser <GD_username>`", false);
			}
		};
	}
	
	private static List<Long> gdAccIds(List<GDLinkedUserData> l) {
		return l.stream().map(GDLinkedUserData::gdUserId).collect(toList());
	}
	
	private static class LeaderboardEntry implements Comparable<LeaderboardEntry> {
		private final int value;
		private final GDLeaderboardData stats;
		private final String discordUser;
		
		public LeaderboardEntry(int value, GDLeaderboardData stats, String discordUser) {
			this.value = value;
			this.stats = Objects.requireNonNull(stats);
			this.discordUser = Objects.requireNonNull(discordUser);
		}

		public int getValue() {
			return value;
		}

		public GDLeaderboardData getStats() {
			return stats;
		}

		public String getDiscordUser() {
			return discordUser;
		}

		@Override
		public int compareTo(LeaderboardEntry o) {
			return value == o.value ? stats.name().compareToIgnoreCase(o.stats.name()) : o.value - value;
		}

		@Override
		public String toString() {
			return "LeaderboardEntry{" + stats.name() + ": " + value + "}";
		}
	}
}
