package com.github.alex1304.ultimategdbot.gdplugin;

import java.time.Duration;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;

import com.github.alex1304.jdash.entity.GDUser;
import com.github.alex1304.ultimategdbot.api.Command;
import com.github.alex1304.ultimategdbot.api.CommandFailedException;
import com.github.alex1304.ultimategdbot.api.Context;
import com.github.alex1304.ultimategdbot.api.InvalidSyntaxException;
import com.github.alex1304.ultimategdbot.api.Plugin;
import com.github.alex1304.ultimategdbot.api.utils.ArgUtils;
import com.github.alex1304.ultimategdbot.api.utils.BotUtils;
import com.github.alex1304.ultimategdbot.api.utils.reply.ReplyMenuBuilder;

import discord4j.core.object.entity.Channel.Type;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.function.TupleUtils;
import reactor.util.function.Tuples;

public class LeaderboardCommand implements Command {
	
	private final GDPlugin plugin;

	public LeaderboardCommand(GDPlugin plugin) {
		this.plugin = Objects.requireNonNull(plugin);
	}

	@Override
	public Mono<Void> execute(Context ctx) {
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
			final var maxPage = size / elementsPerPage;
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
							final var ids = entryList.stream().map(entry -> entry.getGdUser().getId()).collect(Collectors.toList());
							final var rank = ids.indexOf(gdUser.getId());
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
		ToIntFunction<GDUser> stat;
		Mono<String> emojiMono;
		switch (ctx.getArgs().get(1).toLowerCase()) {
			case "stars":
				stat = GDUser::getStars;
				emojiMono = starEmoji;
				break;
			case "diamonds":
				stat = GDUser::getDiamonds;
				emojiMono = diamondEmoji;
				break;
			case "ucoins":
				stat = GDUser::getUserCoins;
				emojiMono = userCoinEmoji;
				break;
			case "scoins":
				stat = GDUser::getSecretCoins;
				emojiMono = secretCoinEmoji;
				break;
			case "demons":
				stat = GDUser::getDemons;
				emojiMono = demonEmoji;
				break;
			case "cp":
				stat = GDUser::getCreatorPoints;
				emojiMono = cpEmoji;
				break;
			default:
				return Mono.error(new InvalidSyntaxException(this));
		}
		return ctx.getEvent().getGuild().flatMap(guild -> ctx.reply("Building leaderboard, this might take a while...")
				.flatMap(message -> Mono.zip(emojiMono, ctx.getBot().getDatabase()
						.query(GDLinkedUsers.class, "from GDLinkedUsers where isLinkActivated = 1")
						.collectList(), guild.getMembers().collectList(), ctx.getBot().getDatabase()
						.query(GDLeaderboardBans.class, "from GDLeaderboardBans").collectList())
						.map(TupleUtils.function((emoji, linkedUsers, guildMembers, leaderboardBans) -> {
							// Filter out from database results (T2) users that aren't in the guild or that are banned.
							// Guild member list is stored in T3 and ban list in T4
							var bannedAccountIds = leaderboardBans.stream()
									.map(GDLeaderboardBans::getAccountId)
									.collect(Collectors.toSet());
							var ids = linkedUsers.stream()
									.filter(linkedUser -> !bannedAccountIds.contains(linkedUser.getGdAccountId()))
									.map(GDLinkedUsers::getDiscordUserId)
									.collect(Collectors.toSet());
							ids.retainAll(guildMembers.stream().map(member -> member.getId().asLong()).collect(Collectors.toSet()));
							linkedUsers.removeIf(linkedUser -> !ids.contains(linkedUser.getDiscordUserId()));
							guildMembers.removeIf(member -> !ids.contains(member.getId().asLong()));
							var discordTags = guildMembers.stream().collect(Collectors.groupingBy(m -> m.getId().asLong(),
									Collectors.mapping(m -> BotUtils.formatDiscordUsername(m), Collectors.joining())));
							return Tuples.of(emoji, linkedUsers, discordTags);
						}))
						.flatMapMany(TupleUtils.function((emoji, linkedUsers, discordTags) -> Flux.fromIterable(linkedUsers)
								.flatMap(linkedUser -> plugin.getGdClient().getUserByAccountId(linkedUser.getGdAccountId())
										.subscribeOn(Schedulers.elastic())
										.onErrorResume(e -> Mono.empty()) // Just skip if unable to fetch user
										.map(gdUser -> new LeaderboardEntry(emoji, stat.applyAsInt(gdUser),
												gdUser, discordTags.get(linkedUser.getDiscordUserId()))))))
						.distinct(LeaderboardEntry::getGdUser)
						.collectSortedList(Comparator.naturalOrder())
						.flatMap(list -> {
							ctx.setVar("leaderboard", list);
							ctx.setVar("page", 0);
							return ctx.getBot().getCommandKernel().invokeCommand(this, ctx);
						})
						.timeout(Duration.ofMinutes(2), Mono.error(new CommandFailedException("The leaderboard took too long to build. Try again.")))
						.doOnSuccessOrError((__, e) -> message.delete().subscribe())));
	}

	@Override
	public Set<String> getAliases() {
		return Set.of("leaderboard", "leaderboards");
	}

	@Override
	public Set<Command> getSubcommands() {
		return Set.of(new LeaderboardBanCommand(plugin), new LeaderboardUnbanCommand(plugin),
				new LeaderboardBanListCommand(plugin));
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
