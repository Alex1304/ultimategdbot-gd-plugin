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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.IntFunction;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;

import com.github.alex1304.jdash.entity.GDUser;
import com.github.alex1304.jdash.exception.GDClientException;
import com.github.alex1304.ultimategdbot.api.command.CommandFailedException;
import com.github.alex1304.ultimategdbot.api.command.Context;
import com.github.alex1304.ultimategdbot.api.command.PermissionLevel;
import com.github.alex1304.ultimategdbot.api.command.Scope;
import com.github.alex1304.ultimategdbot.api.command.annotated.CommandAction;
import com.github.alex1304.ultimategdbot.api.command.annotated.CommandDescriptor;
import com.github.alex1304.ultimategdbot.api.command.annotated.CommandDoc;
import com.github.alex1304.ultimategdbot.api.command.annotated.CommandPermission;
import com.github.alex1304.ultimategdbot.api.util.BotUtils;
import com.github.alex1304.ultimategdbot.api.util.MessageSpecTemplate;
import com.github.alex1304.ultimategdbot.api.util.menu.InteractiveMenu;
import com.github.alex1304.ultimategdbot.api.util.menu.PageNumberOutOfRangeException;
import com.github.alex1304.ultimategdbot.api.util.menu.UnexpectedReplyException;
import com.github.alex1304.ultimategdbot.gdplugin.GDService;
import com.github.alex1304.ultimategdbot.gdplugin.database.GDLeaderboardBanData;
import com.github.alex1304.ultimategdbot.gdplugin.database.GDLinkedUserData;
import com.github.alex1304.ultimategdbot.gdplugin.database.GDLeaderboardData;
import com.github.alex1304.ultimategdbot.gdplugin.util.GDFormatter;
import com.github.alex1304.ultimategdbot.gdplugin.util.GDUsers;

import discord4j.core.object.entity.Guild;
import discord4j.core.object.entity.User;
import discord4j.core.spec.EmbedCreateSpec;
import discord4j.rest.util.Snowflake;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
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
		var starEmoji = ctx.bot().emoji("star");
		var diamondEmoji = ctx.bot().emoji("diamond");
		var userCoinEmoji = ctx.bot().emoji("user_coin");
		var secretCoinEmoji = ctx.bot().emoji("secret_coin");
		var demonEmoji = ctx.bot().emoji("demon");
		var cpEmoji = ctx.bot().emoji("creator_points");
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
				stat = GDLeaderboardData::getStars;
				emojiMono = starEmoji;
				noBanList = false;
				break;
			case "diamonds":
				stat = GDLeaderboardData::getDiamonds;
				emojiMono = diamondEmoji;
				noBanList = false;
				break;
			case "ucoins":
				stat = GDLeaderboardData::getUserCoins;
				emojiMono = userCoinEmoji;
				noBanList = false;
				break;
			case "scoins":
				stat = GDLeaderboardData::getSecretCoins;
				emojiMono = secretCoinEmoji;
				noBanList = false;
				break;
			case "demons":
				stat = GDLeaderboardData::getDemons;
				emojiMono = demonEmoji;
				noBanList = false;
				break;
			case "cp":
				stat = GDLeaderboardData::getCreatorPoints;
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
						.flatMap(members -> ctx.bot().database()
								.query(GDLinkedUserData.class, "from GDLinkedUsers l where l.isLinkActivated = 1 and l.discordUserId in ?0", members.keySet())
								.collectList()
								.filter(not(List::isEmpty))
								.flatMap(linkedUsers -> Mono.zip(
												emojiMono.doOnNext(emojiRef::set),
												ctx.bot().database()
														.query(GDLeaderboardData.class, "from GDUserStats u where u.accountId in ?0 order by u.lastRefreshed desc", gdAccIds(linkedUsers))
														.collectList(),
												ctx.bot().database()
														.query(GDLeaderboardBanData.class, "from GDLeaderboardBans b where b.accountId in ?0", gdAccIds(linkedUsers))
														.map(GDLeaderboardBanData::getAccountId)
														.collect(Collectors.toUnmodifiableSet()))
										.map(function((emoji, userStats, bans) -> userStats.stream()
												.peek(u -> lastRefreshed.compareAndSet(now, userStats.get(0).getLastRefreshed().toInstant()))
												.filter(u -> noBanList || !bans.contains(u.getAccountId()))
												.flatMap(u -> linkedUsers.stream()
														.filter(l -> l.getGdAccountId() == u.getAccountId())
														.map(GDLinkedUserData::getDiscordUserId)
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
							var pageNum = new AtomicInteger();
							IntFunction<MessageSpecTemplate> paginator = page -> {
								PageNumberOutOfRangeException.check(page, 0, (list.size() - 1) / ENTRIES_PER_PAGE);
								return new MessageSpecTemplate(leaderboardView(ctx.prefixUsed(), guild, list, page,
										lastRefreshed.get(), highlighted.get(), emojiRef.get()));
							};
							return InteractiveMenu.createPaginated(pageNum, ctx.bot().config().getPaginationControls(), paginator)
									.addMessageItem("finduser", interaction -> Mono.just(interaction.getArgs().getAllAfter(1))
											.filter(userName -> !userName.isEmpty())
											.switchIfEmpty(Mono.error(new UnexpectedReplyException("Please specify a GD username.")))
											.flatMap(userName -> GDUsers.stringToUser(ctx.bot(), gdService.getGdClient(), userName))
											.onErrorMap(GDClientException.class, e -> new UnexpectedReplyException("Unable to fetch info from that user in Geometry Dash."))
											.flatMap(gdUser -> {
												final var ids = list.stream().map(entry -> entry.getStats().getAccountId()).collect(Collectors.toList());
												final var rank = ids.indexOf(gdUser.getAccountId());
												if (rank == -1) {
													return Mono.error(new UnexpectedReplyException("This user wasn't found on this leaderboard."));
												}
												final var jumpTo = rank / ENTRIES_PER_PAGE;
												pageNum.set(jumpTo);
												highlighted.set(gdUser.getName());
												return interaction.getMenuMessage().edit(paginator.apply(jumpTo).toMessageEditSpec())
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
		var cooldown = new AtomicReference<Duration>();
		var disposableProgress = new AtomicReference<Disposable>();
		var isSaving = new AtomicBoolean();
		var loaded = new AtomicLong();
		var total = new AtomicLong();
		var now = Timestamp.from(Instant.now());
		var progressRefreshRate = Duration.ofSeconds(2);
		var progress = ctx.bot().emoji("info")
				.flatMap(info -> ctx.bot().log(info + " Leaderboard refresh triggered by **" + ctx.author().getTag() + "**"))
				.then(ctx.reply("Refreshing leaderboards..."))
				.flatMapMany(message -> Flux.interval(progressRefreshRate, progressRefreshRate)
						.map(tick -> isSaving.get() ? "Saving new player stats in database..." : "Refreshing leaderboards..."
								+ (total.get() == 0 ? "" : " (" + loaded.get() + "/" + total.get() + " users processed)"))
						.flatMap(text -> message.edit(spec -> spec.setContent(text)))
						.doFinally(signal -> message.delete()
								.then(ctx.bot().emoji("success"))
								.flatMap(success -> ctx.bot().log(success + " Leaderboard refresh finished with success!")
										.then(ctx.reply(success + " Leaderboards refreshed!")))
								.subscribe()));
		
		return ctx.bot().database().query(GDLeaderboardData.class, "from GDUserStats s order by s.lastRefreshed desc")
				.next()
				.map(GDLeaderboardData::getLastRefreshed)
				.map(Timestamp::toInstant)
				.defaultIfEmpty(Instant.MIN)
				.map(lastRefreshed -> Duration.ofHours(6).minus(Duration.between(lastRefreshed, Instant.now())))
				.doOnNext(cooldown::set)
				.filter(Duration::isNegative)
				.switchIfEmpty(Mono.error(() -> new CommandFailedException("The leaderboard has already been refreshed less than 6 hours ago. "
						+ "Try again in " + BotUtils.formatDuration(cooldown.get().withNanos(0)))))
				.thenMany(ctx.bot().database().query(GDLinkedUserData.class, "from GDLinkedUsers where isLinkActivated = 1"))
				.distinct(GDLinkedUserData::getGdAccountId)
				.buffer()
				.doOnNext(buf -> total.set(buf.size()))
				.doOnNext(buf -> disposableProgress.set(progress.subscribe()))
				.doOnNext(buf -> gdService.getGdClient().clearCache())
				.flatMap(Flux::fromIterable)
				.flatMap(linkedUser -> gdService.getGdClient().getUserByAccountId(linkedUser.getGdAccountId())
						.onErrorResume(e -> Mono.fromRunnable(() -> LOGGER.warn("Failed to refresh user "
								+ linkedUser.getGdAccountId(), e))), gdService.getLeaderboardRefreshParallelism())
				.map(gdUser -> {
					loaded.incrementAndGet();
					var s = new GDLeaderboardData();
					s.setAccountId(gdUser.getAccountId());
					s.setName(gdUser.getName());
					s.setLastRefreshed(now);
					s.setStars(gdUser.getStars());
					s.setDiamonds(gdUser.getDiamonds());
					s.setUserCoins(gdUser.getUserCoins());
					s.setSecretCoins(gdUser.getSecretCoins());
					s.setDemons(gdUser.getDemons());
					s.setCreatorPoints(gdUser.getCreatorPoints());
					return s;
				})
				.collectList()
				.doOnNext(stats -> isSaving.set(true))
				.flatMap(stats -> ctx.bot().database().performEmptyTransaction(session -> stats.forEach(session::saveOrUpdate)))
				.doFinally(signal -> {
					isLocked = false;
					LOGGER.debug("Unlocked leaderboards");
					var d = disposableProgress.get();
					if (d != null) {
						d.dispose();
					}
				});
	}
	
	@CommandAction("ban")
	@CommandDoc("Bans a player from server leaderboards (bot admin only). Players that are banned from leaderboards won't be displayed in the results of "
			+ "the `leaderboard` command in any server, regardless of whether they have an account linked. Bans are by GD account and "
			+ "not by Discord account, so linking with a different Discord account does not allow ban evasion.")
	@CommandPermission(level = PermissionLevel.BOT_ADMIN)
	public Mono<Void> runBan(Context ctx, GDUser gdUser) {
		return ctx.bot().database().findByID(GDLeaderboardBanData.class, gdUser.getAccountId())
				.flatMap(__ -> Mono.error(new CommandFailedException("This user is already banned.")))
				.then(Mono.just(new GDLeaderboardBanData())
						.doOnNext(newBan -> newBan.setAccountId(gdUser.getAccountId()))
						.doOnNext(newBan -> newBan.setBannedBy(ctx.event().getMessage().getAuthor()
								.map(User::getId)
								.map(Snowflake::asLong)
								.orElse(0L)))
						.flatMap(ctx.bot().database()::save))
				.then(ctx.reply("**" + gdUser.getName() + "** is now banned from leaderboards!"))
				.and(ctx.bot().emoji("info")
						.flatMap(info -> ctx.bot().log(info + " Leaderboard ban added: **" + gdUser.getName()
								+ "**, by **" + ctx.event()
								.getMessage()
								.getAuthor()
								.map(User::getTag)
								.orElse("Unknown User#0000") + "**")));
	}
	
	@CommandAction("unban")
	@CommandDoc("Unbans a player from server leaderboards (bot admin only). Players that are banned from leaderboards won't be displayed in the results of "
			+ "the `leaderboard` command in any server, regardless of whether they have an account linked. Bans are by GD account and "
			+ "not by Discord account, so linking with a different Discord account does not allow ban evasion.")
	@CommandPermission(level = PermissionLevel.BOT_ADMIN)
	public Mono<Void> runUnban(Context ctx, GDUser gdUser) {
		return ctx.bot().database().findByID(GDLeaderboardBanData.class, gdUser.getAccountId())
						.switchIfEmpty(Mono.error(new CommandFailedException("This user is already unbanned.")))
						.flatMap(ctx.bot().database()::delete)
						.then(ctx.reply("**" + gdUser.getName() + "** has been unbanned from leaderboards!"))
						.and(ctx.bot().emoji("info")
								.flatMap(info -> ctx.bot().log(info + " Leaderboard ban removed: **" + gdUser.getName()
										+ "**, by **" + ctx.event()
										.getMessage()
										.getAuthor()
										.map(User::getTag)
										.orElse("Unknown User#0000") + "**")));
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
				var isHighlighted = entry.getStats().getName().equalsIgnoreCase(highlighted);
				var rank = page * ENTRIES_PER_PAGE + i;
				if (isHighlighted) {
					sb.append("**");
				}
				var row = String.format("%s | %s %s | %s (%s)",
						String.format("`#%" + rankWidth + "d`", rank).replaceAll(" ", " ‌‌"),
						emoji,
						GDFormatter.formatCode(entry.getValue(), statWidth),
						entry.getStats().getName(),
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
			embed.addField("Last refreshed: " + BotUtils.formatDuration(refreshed) + " ago", "Note that members of this server must have "
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
		return l.stream().map(GDLinkedUserData::getGdAccountId).collect(toList());
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
			return value == o.value ? stats.getName().compareToIgnoreCase(o.stats.getName()) : o.value - value;
		}

		@Override
		public String toString() {
			return "LeaderboardEntry{" + stats.getName() + ": " + value + "}";
		}
	}
}
