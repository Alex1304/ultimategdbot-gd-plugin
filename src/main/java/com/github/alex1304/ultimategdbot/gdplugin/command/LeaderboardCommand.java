package com.github.alex1304.ultimategdbot.gdplugin.command;

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
import com.github.alex1304.ultimategdbot.api.command.menu.PageNumberOutOfRangeException;
import com.github.alex1304.ultimategdbot.api.command.menu.UnexpectedReplyException;
import com.github.alex1304.ultimategdbot.api.service.Root;
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

import discord4j.common.util.Snowflake;
import discord4j.core.object.entity.Guild;
import discord4j.core.object.entity.User;
import discord4j.core.spec.EmbedCreateSpec;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.Logger;
import reactor.util.Loggers;
import reactor.util.annotation.Nullable;

@CommandDescriptor(
		aliases = { "leaderboard", "leaderboards" },
		shortDescription = "tr:GDStrings/leaderboard_desc",
		scope = Scope.GUILD_ONLY
)
public final class LeaderboardCommand {

	private static final Logger LOGGER = Loggers.getLogger(LeaderboardCommand.class);
	private static final int ENTRIES_PER_PAGE = 20;
	
	private volatile boolean isLocked;

	@Root
	private GDService gd;
	
	@CommandAction
	@CommandDoc("tr:GDStrings/leaderboard_run")
	public Mono<Void> run(Context ctx, @Nullable String statName) {
		if (isLocked) {
			return Mono.error(new CommandFailedException(ctx.translate("GDStrings", "error_lb_locked")));
		}
		var emojiService = gd.bot().emoji();
		var starEmoji = emojiService.get("star");
		var diamondEmoji = emojiService.get("diamond");
		var userCoinEmoji = emojiService.get("user_coin");
		var secretCoinEmoji = emojiService.get("secret_coin");
		var demonEmoji = emojiService.get("demon");
		var cpEmoji = emojiService.get("creator_points");
		if (statName == null) {
			return Mono.zip(starEmoji, diamondEmoji, userCoinEmoji, secretCoinEmoji, demonEmoji, cpEmoji)
					.flatMap(tuple -> ctx.reply("**" + ctx.translate("GDStrings", "lb_intro") + "**\n"
							+ "__" + ctx.translate("GDStrings", "select_lb") + "__\n"
							+ ctx.translate("GDStrings", "select_lb_item", tuple.getT1() + " Stars", ctx.prefixUsed(), "stars") + '\n'
							+ ctx.translate("GDStrings", "select_lb_item", tuple.getT2() + " Diamonds", ctx.prefixUsed(), "diamonds") + '\n'
							+ ctx.translate("GDStrings", "select_lb_item", tuple.getT3() + " User Coins", ctx.prefixUsed(), "ucoins") + '\n'
							+ ctx.translate("GDStrings", "select_lb_item", tuple.getT4() + " Secret Coins", ctx.prefixUsed(), "scoins") + '\n'
							+ ctx.translate("GDStrings", "select_lb_item", tuple.getT5() + " Demons", ctx.prefixUsed(), "demons") + '\n'
							+ ctx.translate("GDStrings", "select_lb_item", tuple.getT6() + " Creator Points", ctx.prefixUsed(), "cp")))
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
				return Mono.error(new CommandFailedException(ctx.translate("GDStrings", "error_unknown_lb_type")));
		}
		var now = Instant.now();
		var lastRefreshed = new AtomicReference<Instant>(now);
		var emojiRef = new AtomicReference<String>();
		return ctx.event().getGuild()
				.flatMap(guild -> gd.bot().gateway().requestMembers(guild.getId())
						.collect(toMap(m -> m.getId().asLong(), User::getTag, (a, b) -> a))
						.filter(not(Map::isEmpty))
						.flatMap(members -> gd.bot().database()
								.withExtension(GDLinkedUserDao.class, dao -> dao.getAllIn(List.copyOf(members.keySet())))
								.filter(not(List::isEmpty))
								.flatMap(linkedUsers -> Mono.zip(
												emojiMono.doOnNext(emojiRef::set),
												gd.bot().database()
														.withExtension(GDLeaderboardDao.class, dao -> dao.getAllIn(gdAccIds(linkedUsers))),
												gd.bot().database()
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
								return ctx.reply(m -> m.setEmbed(leaderboardView(ctx, ctx.prefixUsed(), guild, list, 0,
										lastRefreshed.get(), null, emojiRef.get()))).then();
							}
							var highlighted = new AtomicReference<String>();
							BiFunction<Translator, Integer, MessageSpecTemplate> paginator = (tr, page) -> {
								PageNumberOutOfRangeException.check(page, 0, (list.size() - 1) / ENTRIES_PER_PAGE);
								return new MessageSpecTemplate(leaderboardView(ctx, ctx.prefixUsed(), guild, list, page,
										lastRefreshed.get(), highlighted.get(), emojiRef.get()));
							};
							return gd.bot().interactiveMenu().createPaginated(paginator)
									.addMessageItem("finduser", interaction -> Mono.just(interaction.getArgs().getAllAfter(1))
											.filter(userName -> !userName.isEmpty())
											.switchIfEmpty(Mono.error(new UnexpectedReplyException(
													ctx.translate("GDStrings", "error_username_not_specified"))))
											.flatMap(userName -> gd.user().stringToUser(ctx, userName))
											.onErrorMap(GDClientException.class, e -> new UnexpectedReplyException(
													ctx.translate("GDStrings", "error_user_fetch")))
											.flatMap(gdUser -> {
												final var ids = list.stream().map(entry -> entry.getStats().accountId()).collect(Collectors.toList());
												final var rank = ids.indexOf(gdUser.getAccountId());
												if (rank == -1) {
													return Mono.error(new UnexpectedReplyException(
															ctx.translate("GDStrings", "error_user_not_on_lb")));
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
	@CommandDoc("tr:GDStrings/leaderboard_run_refresh")
	@CommandPermission(level = PermissionLevel.BOT_ADMIN)
	public Mono<Void> runRefresh(Context ctx) {
		if (isLocked) {
			return Mono.error(new CommandFailedException(ctx.translate("GDStrings", "error_refresh_in_progress")));
		}
		isLocked = true;
		LOGGER.debug("Locked leaderboards");
		var now = Instant.now();
		return gd.bot().database()
				.withExtension(GDLeaderboardDao.class, GDLeaderboardDao::getLastRefreshed)
				.flatMap(Mono::justOrEmpty)
				.map(Timestamp::toInstant)
				.defaultIfEmpty(Instant.MIN)
				.map(lastRefreshed -> Duration.ofHours(6).minus(Duration.between(lastRefreshed, Instant.now())))
				.flatMap(cooldown -> !cooldown.isNegative()
						? Mono.error(new CommandFailedException(
								ctx.translate("GDStrings", "error_already_refreshed",
										DurationUtils.format(cooldown.withNanos(0)))))
						: Mono.empty())
				.then(gd.bot().database().withExtension(GDLinkedUserDao.class, GDLinkedUserDao::getAll))
				.flatMapMany(Flux::fromIterable)
				.distinct(GDLinkedUserData::gdUserId)
				.collectList()
				.flatMap(list -> gd.bot().emoji().get("info")
						.flatMap(info -> gd.bot().logging().log(info + ' '
								+ gd.bot().localization().translate("GDStrings", "refresh_log", ctx.author().getTag())))
						.then(ctx.reply(ctx.translate("GDStrings", "refreshing")))
						.flatMapMany(message -> {
							var processed = new AtomicInteger();
							var done = new AtomicBoolean();
							Flux.interval(Duration.ofSeconds(2))
									.takeLast(1)
									.flatMap(__ -> message
											.edit(spec -> spec.setContent(
													ctx.translate("GDStrings", "refreshing_progress", processed.get(), list.size())))
											.onErrorResume(e -> Mono.empty()))
									.repeat(() -> !done.get())
									.then(message.delete().onErrorResume(e -> Mono.empty()))
									.subscribe();
							return Flux.fromIterable(list)
									.flatMap(linkedUser -> gd.client().getUserByAccountId(linkedUser.gdUserId())
											.onErrorResume(e -> Mono.fromRunnable(() -> LOGGER.warn("Failed to refresh user "
													+ linkedUser.gdUserId(), e))), gd.getLeaderboardRefreshParallelism())
									.index()
									.map(function((i, gdUser) -> {
										processed.incrementAndGet();
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
				.flatMap(stats -> ctx.reply(ctx.translate("GDStrings", "saving_to_db"))
						.onErrorResume(e -> Mono.empty())
						.flatMap(message -> gd.bot().database()
								.useExtension(GDLeaderboardDao.class, dao -> dao.cleanInsertAll(stats))
								.then(message.delete())
								.then(gd.bot().emoji().get("success")
										.flatMap(success -> ctx.reply(success + ' ' + ctx.translate("GDStrings", "refresh_success")))
										.then())))
				.doFinally(signal -> {
					isLocked = false;
					LOGGER.debug("Unlocked leaderboards");
				});
	}
	
	@CommandAction("ban")
	@CommandDoc("tr:GDStrings/leaderboard_run_ban")
	@CommandPermission(level = PermissionLevel.BOT_ADMIN)
	public Mono<Void> runBan(Context ctx, GDUser gdUser) {
		return gd.bot().database()
				.withExtension(GDLeaderboardBanDao.class, dao -> dao.get(gdUser.getAccountId()))
				.flatMap(Mono::justOrEmpty)
				.flatMap(__ -> Mono.error(new CommandFailedException(ctx.translate("GDStrings", "error_user_already_banned"))))
				.then(gd.bot().database()
						.useExtension(GDLeaderboardBanDao.class, dao -> dao.insert(ImmutableGDLeaderboardBanData.builder()
								.accountId(gdUser.getAccountId())
								.bannedBy(ctx.author().getId())
								.build())))
				.then(ctx.reply(ctx.translate("GDStrings", "ban_success", gdUser.getName())))
				.and(gd.bot().emoji().get("info")
						.flatMap(info -> gd.bot().logging().log(info + ' ' + gd.bot().localization()
								.translate("GDStrings", "ban_log", gdUser.getName(), ctx.author().getTag()))));
	}
	
	@CommandAction("unban")
	@CommandDoc("tr:GDStrings/leaderboard_run_unban")
	@CommandPermission(level = PermissionLevel.BOT_ADMIN)
	public Mono<Void> runUnban(Context ctx, GDUser gdUser) {
		return gd.bot().database()
				.withExtension(GDLeaderboardBanDao.class, dao -> dao.get(gdUser.getAccountId()))
				.flatMap(Mono::justOrEmpty)
				.switchIfEmpty(Mono.error(new CommandFailedException(ctx.translate("GDStrings", "error_user_already_unbanned"))))
				.flatMap(banData -> gd.bot().database()
						.useExtension(GDLeaderboardBanDao.class, dao -> dao.delete(banData.accountId())))
				.then(ctx.reply(ctx.translate("GDStrings", "unban_success", gdUser.getName())))
				.and(gd.bot().emoji().get("info")
						.flatMap(info -> gd.bot().logging().log(info + ' ' + gd.bot().localization()
								.translate("GDStrings", "unban_log", gdUser.getName(), ctx.author().getTag()))));
	}

	private static Consumer<EmbedCreateSpec> leaderboardView(Translator tr, String prefix, Guild guild,
			List<LeaderboardEntry> entryList, int page, Instant lastRefreshed, String highlighted, String emoji) {
		final var size = entryList.size();
		final var maxPage = (size - 1) / ENTRIES_PER_PAGE;
		final var offset = page * ENTRIES_PER_PAGE;
		final var subList = entryList.subList(offset, Math.min(offset + ENTRIES_PER_PAGE, size));
		final var refreshed = Duration.between(lastRefreshed, Instant.now()).withNanos(0);
		return embed -> {
			embed.setTitle(tr.translate("GDStrings", "lb_title", guild.getName()));
			if (size == 0 || subList.isEmpty()) {
				embed.setDescription(tr.translate("GDStrings", "lb_no_entries"));
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
			embed.setDescription("**" + tr.translate("GDStrings", "lb_total_players", size, emoji) + "**\n\n" + sb.toString());
			embed.addField(tr.translate("GDStrings", "lb_last_refreshed", DurationUtils.format(refreshed)),
					tr.translate("GDStrings", "lb_account_notice", prefix), false);
			if (maxPage > 0) {
				embed.addField(tr.translate("CommonStrings", "pagination_page_counter", page + 1, maxPage + 1),
						tr.translate("CommonStrings", "pagination_go_to") + '\n'
						+ tr.translate("GDStrings", "lb_jump_to_user"), false);
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
