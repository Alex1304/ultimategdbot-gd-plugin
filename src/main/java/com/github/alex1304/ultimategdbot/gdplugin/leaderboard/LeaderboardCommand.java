package com.github.alex1304.ultimategdbot.gdplugin.leaderboard;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;

import com.github.alex1304.ultimategdbot.api.Command;
import com.github.alex1304.ultimategdbot.api.CommandFailedException;
import com.github.alex1304.ultimategdbot.api.Context;
import com.github.alex1304.ultimategdbot.api.InvalidSyntaxException;
import com.github.alex1304.ultimategdbot.api.Plugin;
import com.github.alex1304.ultimategdbot.api.utils.ArgUtils;
import com.github.alex1304.ultimategdbot.api.utils.BotUtils;
import com.github.alex1304.ultimategdbot.api.utils.reply.ReplyMenuBuilder;
import com.github.alex1304.ultimategdbot.gdplugin.GDPlugin;
import com.github.alex1304.ultimategdbot.gdplugin.database.GDLeaderboardBans;
import com.github.alex1304.ultimategdbot.gdplugin.database.GDLinkedUsers;
import com.github.alex1304.ultimategdbot.gdplugin.database.GDUserStats;
import com.github.alex1304.ultimategdbot.gdplugin.util.GDUtils;

import discord4j.core.object.entity.Channel.Type;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.function.TupleUtils;
import reactor.util.function.Tuples;

public class LeaderboardCommand implements Command {
	
	private final GDPlugin plugin;
	private final AtomicBoolean isLocked;

	public LeaderboardCommand(GDPlugin plugin) {
		this.plugin = Objects.requireNonNull(plugin);
		this.isLocked = new AtomicBoolean();
	}

	@Override
	public Mono<Void> execute(Context ctx) {
		if (isLocked.get()) {
			return Mono.error(new CommandFailedException("Leaderboards are temporarily locked because "
					+ "they are currently being refreshed. Retry later."));
		}
		var starEmoji = ctx.getBot().getEmoji("star");
		var diamondEmoji = ctx.getBot().getEmoji("diamond");
		var userCoinEmoji = ctx.getBot().getEmoji("user_coin");
		var secretCoinEmoji = ctx.getBot().getEmoji("secret_coin");
		var demonEmoji = ctx.getBot().getEmoji("demon");
		var cpEmoji = ctx.getBot().getEmoji("creator_points");
		if (ctx.getArgs().size() == 1) {
			return Mono.zip(starEmoji, diamondEmoji, userCoinEmoji, secretCoinEmoji, demonEmoji, cpEmoji)
					.flatMap(tuple -> ctx.reply("**Compare your stats with other players in this server by "
							+ "showing a server-wide Geometry Dash leaderboard!**\n"
							+ "__To get started, select which type of leaderboard you want to show:__\n"
							+ "To view " + tuple.getT1() + " Stars leaderboard, run `" + ctx.getPrefixUsed() + "leaderboard stars`\n"
							+ "To view " + tuple.getT2() + " Diamonds leaderboard, run `" + ctx.getPrefixUsed() + "leaderboard diamonds`\n"
							+ "To view " + tuple.getT3() + " User Coins leaderboard, run `" + ctx.getPrefixUsed() + "leaderboard ucoins`\n"
							+ "To view " + tuple.getT4() + " Secret Coins leaderboard, run `" + ctx.getPrefixUsed() + "leaderboard scoins`\n"
							+ "To view " + tuple.getT5() + " Demons leaderboard, run `" + ctx.getPrefixUsed() + "leaderboard demons`\n"
							+ "To view " + tuple.getT6() + " Creator Points leaderboard, run `" + ctx.getPrefixUsed() + "leaderboard cp`\n"))
					.then();
		}
		@SuppressWarnings("unchecked")
		var entryList = (List<LeaderboardEntry>) ctx.getVar("leaderboard", List.class);
		var page = ctx.getVarOrDefault("page", 0);
		if (entryList != null) {
			final var elementsPerPage = 20;
			final var size = entryList.size();
			final var maxPage = (size - 1) / elementsPerPage;
			final var offset = page * elementsPerPage;
			final var subList = entryList.subList(offset, Math.min(offset + elementsPerPage, size));
			var rb = new ReplyMenuBuilder(ctx, true, false);
			if (page < maxPage) {
				rb.addItem("next", "To go to next page, type `next`", ctx0 -> {
					ctx.setVar("page", page + 1);
					ctx.getBot().getCommandKernel().invokeCommand(this, ctx).subscribe();
					return Mono.empty();
				});
			}
			if (page > 0) {
				rb.addItem("prev", "To go to previous page, type `prev`", ctx0 -> {
					ctx.setVar("page", page - 1);
					ctx.getBot().getCommandKernel().invokeCommand(this, ctx).subscribe();
					return Mono.empty();
				});
			}
			if (maxPage > 0) {
				rb.addItem("page", "To go to a specific page, type `page <number>`, e.g `page 3`", ctx0 -> {
					ArgUtils.requireMinimumArgCount(ctx0, 2, "Please specify a page number");
					var requestedPage = ArgUtils.getArgAsInt(ctx0, 1) - 1;
					if (requestedPage < 0 || requestedPage > maxPage) {
						return Mono.error(new CommandFailedException("Page number out of range"));
					}
					ctx.setVar("page", requestedPage);
					ctx.getBot().getCommandKernel().invokeCommand(this, ctx).subscribe();
					return Mono.empty();
				});
			}
			rb.addItem("finduser", "To jump to the page where a specific user is, type `finduser <GD_username>`", ctx0 -> {
				ArgUtils.requireMinimumArgCount(ctx0, 2, "Please specify a user");
				return GDUtils.stringToUser(ctx.getBot(), plugin.getGdClient(), ArgUtils.concatArgs(ctx0, 1))
						.flatMap(gdUser -> {
							final var ids = entryList.stream().map(entry -> entry.getStats().getAccountId()).collect(Collectors.toList());
							final var rank = ids.indexOf(gdUser.getAccountId());
							if (rank == -1) {
								return Mono.error(new CommandFailedException("This user wasn't found on this leaderboard."));
							}
							final var jumpTo = rank / elementsPerPage;
							ctx.setVar("page", jumpTo);
							ctx.setVar("highlighted", gdUser.getName());
							ctx.getBot().getCommandKernel().invokeCommand(this, ctx).subscribe();
							return Mono.empty();
						});
			});
			rb.setHeader("Page " + (page + 1) + "/" + (size / elementsPerPage + 1));
			return GDUtils.leaderboardView(ctx, subList, page, elementsPerPage, size).flatMap(embed -> rb.build(null, embed)).then();
		}
		ToIntFunction<GDUserStats> stat;
		Mono<String> emojiMono;
		boolean noBanList;
		switch (ctx.getArgs().get(1).toLowerCase()) {
			case "stars":
				stat = GDUserStats::getStars;
				emojiMono = starEmoji;
				noBanList = false;
				break;
			case "diamonds":
				stat = GDUserStats::getDiamonds;
				emojiMono = diamondEmoji;
				noBanList = false;
				break;
			case "ucoins":
				stat = GDUserStats::getUserCoins;
				emojiMono = userCoinEmoji;
				noBanList = false;
				break;
			case "scoins":
				stat = GDUserStats::getSecretCoins;
				emojiMono = secretCoinEmoji;
				noBanList = false;
				break;
			case "demons":
				stat = GDUserStats::getDemons;
				emojiMono = demonEmoji;
				noBanList = false;
				break;
			case "cp":
				stat = GDUserStats::getCreatorPoints;
				emojiMono = cpEmoji;
				noBanList = true;
				break;
			default:
				return Mono.error(new InvalidSyntaxException(this));
		}
		var lastRefreshed = new AtomicReference<Instant>();
		return ctx.getEvent().getGuild()
				.flatMap(guild -> Mono.zip(emojiMono, ctx.getBot().getDatabase()
						.query(GDUserStats.class, "from GDUserStats u order by u.lastRefreshed desc").collectList(), ctx.getBot().getDatabase()
						.query(GDLinkedUsers.class, "from GDLinkedUsers l where l.isLinkActivated = 1").collectList(), guild.getMembers().collectList(),
						ctx.getBot().getDatabase().query(GDLeaderboardBans.class, "from GDLeaderboardBans").collectList())
						.map(TupleUtils.function((emoji, userStats, linkedUsers, guildMembers, leaderboardBans) -> {
							lastRefreshed.set(userStats.stream()
									.map(GDUserStats::getLastRefreshed)
									.map(Timestamp::toInstant)
									.findFirst()
									.orElse(Instant.now()));
							var bannedAccountIds = noBanList ? Set.of() : leaderboardBans.stream()
									.map(GDLeaderboardBans::getAccountId)
									.collect(Collectors.toSet());
							var ids = linkedUsers.stream()
									.filter(linkedUser -> !bannedAccountIds.contains(linkedUser.getGdAccountId()))
									.map(GDLinkedUsers::getDiscordUserId)
									.collect(Collectors.toSet());
							ids.retainAll(guildMembers.stream().map(member -> member.getId().asLong()).collect(Collectors.toSet()));
							linkedUsers.removeIf(linkedUser -> !ids.contains(linkedUser.getDiscordUserId()));
							userStats.removeIf(ustat -> linkedUsers.stream().map(GDLinkedUsers::getGdAccountId).noneMatch(id -> id == ustat.getAccountId()));
							guildMembers.removeIf(member -> !ids.contains(member.getId().asLong()));
							var discordTags = guildMembers.stream().collect(Collectors.groupingBy(m -> m.getId().asLong(),
									Collectors.mapping(m -> BotUtils.formatDiscordUsername(m), Collectors.joining())));
							return Tuples.of(emoji, linkedUsers, userStats, discordTags);
						}))
						.flatMapMany(TupleUtils.function((emoji, linkedUsers, userStats, discordTags) -> Flux.fromIterable(linkedUsers)
								.flatMap(linkedUser -> Mono.justOrEmpty(userStats.stream().filter(u -> u.getAccountId() == linkedUser.getGdAccountId()).findAny())
										.map(userStat -> new LeaderboardEntry(emoji, stat.applyAsInt(userStat),
												userStat, discordTags.get(linkedUser.getDiscordUserId()))))))
						.distinct(LeaderboardEntry::getStats)
						.collectSortedList(Comparator.naturalOrder())
						.flatMap(list -> {
							ctx.setVar("leaderboard", list);
							ctx.setVar("page", 0);
							ctx.setVar("lastRefreshed", lastRefreshed.get());
							return ctx.getBot().getCommandKernel().invokeCommand(this, ctx);
						}));
	}

	@Override
	public Set<String> getAliases() {
		return Set.of("leaderboard", "leaderboards");
	}

	@Override
	public Set<Command> getSubcommands() {
		return Set.of(new LeaderboardBanCommand(plugin), new LeaderboardUnbanCommand(plugin),
				new LeaderboardBanListCommand(plugin), new LeaderboardRefreshCommand(plugin, isLocked));
	}

	@Override
	public String getDescription() {
		return "Builds and displays a server-wide Geometry Dash leaderboard.";
	}

	@Override
	public String getLongDescription() {
		return "All members of the current server that has a Geometry Dash account linked may be shown in the leaderboards provided by this command."
				+ "You can choose which leaderboard to show (stars, demons, creator points, etc), and there is an internal ban system to remove cheaters"
				+ "from them.\n"
				+ "This command might take a while to execute, because it needs to fetch the profile of every single user that is on the server and "
				+ "that has an account linked.\n";
	}

	@Override
	public String getSyntax() {
		return "[<stat_name>]";
	}

	@Override
	public EnumSet<Type> getChannelTypesAllowed() {
		return EnumSet.of(Type.GUILD_TEXT);
	}

	@Override
	public Plugin getPlugin() {
		return plugin;
	}
}
