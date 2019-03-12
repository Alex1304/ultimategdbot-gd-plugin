package com.github.alex1304.ultimategdbot.gdplugin;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.BiConsumer;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;

import com.github.alex1304.jdash.client.AuthenticatedGDClient;
import com.github.alex1304.jdash.entity.GDUser;
import com.github.alex1304.ultimategdbot.api.Command;
import com.github.alex1304.ultimategdbot.api.CommandFailedException;
import com.github.alex1304.ultimategdbot.api.Context;
import com.github.alex1304.ultimategdbot.api.InvalidSyntaxException;
import com.github.alex1304.ultimategdbot.api.PermissionLevel;
import com.github.alex1304.ultimategdbot.api.utils.BotUtils;
import com.github.alex1304.ultimategdbot.api.utils.reply.ReplyMenuBuilder;

import discord4j.core.object.entity.Channel.Type;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.function.Tuples;

public class LeaderboardCommand implements Command {
	
	private final AuthenticatedGDClient gdClient;
	
	public LeaderboardCommand(AuthenticatedGDClient gdClient) {
		this.gdClient = Objects.requireNonNull(gdClient);
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
			return Mono.zip(ctx.getEffectivePrefix(), starEmoji, diamondEmoji, userCoinEmoji, secretCoinEmoji, demonEmoji, cpEmoji)
					.flatMap(tuple -> ctx.reply("**Compare your stats with other players in this server by "
							+ "showing a server-wide Geometry Dash leaderboard!**\n"
							+ "__To get started, select which type of leaderboard you want to show:__\n"
							+ "To view " + tuple.getT2() + " Stars leaderboard, run `" + tuple.getT1() + "leaderboard stars`\n"
							+ "To view " + tuple.getT3() + " Diamonds leaderboard, run `" + tuple.getT1() + "leaderboard diamonds`\n"
							+ "To view " + tuple.getT4() + " User Coins leaderboard, run `" + tuple.getT1() + "leaderboard ucoins`\n"
							+ "To view " + tuple.getT5() + " Secret Coins leaderboard, run `" + tuple.getT1() + "leaderboard scoins`\n"
							+ "To view " + tuple.getT6() + " Demons leaderboard, run `" + tuple.getT1() + "leaderboard demons`\n"
							+ "To view " + tuple.getT7() + " Creator Points leaderboard, run `" + tuple.getT1() + "leaderboard cp`\n"))
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
					Command.invoke(this, ctx);
					return Mono.empty();
				});
			}
			if (page > 0) {
				rb.addItem("prev", "To go to previous page, type `prev`", ctx0 -> {
					ctx.setVar("page", page - 1);
					Command.invoke(this, ctx);
					return Mono.empty();
				});
			}
			if (maxPage > 0) {
				rb.addItem("page", "To go to a specific page, type `page <number>`, e.g `page 3`", ctx0 -> {
					if (ctx0.getArgs().size() == 1) {
						Command.invoke(this, ctx);
						return Mono.error(new CommandFailedException("Please specify a page number"));
					}
					try {
						var requestedPage = Integer.parseInt(ctx0.getArgs().get(1)) - 1;
						if (requestedPage < 0 || requestedPage > maxPage) {
							Command.invoke(this, ctx);
							return Mono.error(new CommandFailedException("Page number out of range"));
						}
						ctx.setVar("page", requestedPage);
						Command.invoke(this, ctx);
						return Mono.empty();
					} catch (NumberFormatException e) {
						Command.invoke(this, ctx);
						return Mono.error(new CommandFailedException("Please specify a valid page number"));
					}
				});
			}
			rb.addItem("finduser", "To jump to the page where a specific user is, type `finduser <GD_username>`", ctx0 -> {
				if (ctx0.getArgs().size() == 1) {
					Command.invoke(this, ctx);
					return Mono.error(new CommandFailedException("Please specify a user"));
				}
				final var names = entryList.stream().map(entry -> entry.getGdUser().getName()).map(String::toLowerCase).collect(Collectors.toList());
				final var rank = names.indexOf(ctx0.getArgs().get(1).toLowerCase());
				if (rank == -1) {
					Command.invoke(this, ctx);
					return Mono.error(new CommandFailedException("This user wasn't found on this leaderboard."));
				}
				final var jumpTo = rank / elementsPerPage;
				ctx.setVar("page", jumpTo);
				ctx.setVar("highlighted", ctx0.getArgs().get(1));
				Command.invoke(this, ctx);
				return Mono.empty();
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
						.collectList(), guild.getMembers().collectList())
						.map(tuple -> {
							// Filter out from database results (T2) users that aren't in the guild. Guild member list is stored in T3
							var ids = new TreeSet<Long>(tuple.getT2().stream().map(GDLinkedUsers::getDiscordUserId).collect(Collectors.toSet()));
							ids.retainAll(tuple.getT3().stream().map(member -> member.getId().asLong()).collect(Collectors.toSet()));
							tuple.getT2().removeIf(linkedUser -> !ids.contains(linkedUser.getDiscordUserId()));
							tuple.getT3().removeIf(member -> !ids.contains(member.getId().asLong()));
							var discordTags = tuple.getT3().stream().collect(Collectors.groupingBy(m -> m.getId().asLong(),
									Collectors.mapping(m -> BotUtils.formatDiscordUsername(m), Collectors.joining())));
							return Tuples.of(tuple.getT1(), tuple.getT2(), discordTags);
						})
						.flatMapMany(tuple -> Flux.fromIterable(tuple.getT2())
								.parallel().runOn(Schedulers.parallel())
								.flatMap(linkedUser -> gdClient.getUserByAccountId(linkedUser.getGdAccountId())
										.onErrorResume(e -> Mono.empty()) // Just skip if unable to fetch user
										.map(gdUser -> new LeaderboardEntry(tuple.getT1(), stat.applyAsInt(gdUser),
												gdUser, tuple.getT3().get(linkedUser.getDiscordUserId())))))
						.distinct(LeaderboardEntry::getGdUser)
						.collectSortedList(Comparator.naturalOrder())
						.flatMap(list -> {
							ctx.setVar("leaderboard", list);
							ctx.setVar("page", 0);
							Command.invoke(this, ctx);
							return Mono.<Void>empty();
						})
						.doOnSuccessOrError((__, e) -> message.delete().subscribe())));
				
//		return ctx.getEvent().getGuild().flatMap(guild -> ctx.reply("Building leaderboard, this might take a while...")
//				.flatMap(message -> emojiMono.flatMap(emoji -> ctx.getBot().getDatabase()
//						.query(GDLinkedUsers.class, "from GDLinkedUsers where isLinkActivated = 1")
//								.parallel().runOn(Schedulers.parallel())
//								.flatMap(linkedUser -> guild.getMembers()
//										.filter(m -> m.getId().asLong() == linkedUser.getDiscordUserId())
//										.map(m -> Tuples.of(linkedUser, m)))
//								.buffer()
//								.flatMap(list -> Flux.fromIterable(list).flatMap(tuple -> gdClient.getUserByAccountId(tuple.getT1().getGdAccountId())
//										.onErrorContinue((__, __0) -> {})
//										.map(gdUser -> Tuples.of(tuple.getT2(), gdUser))))
//								.map(tuple -> new LeaderboardEntry(emoji, stat.applyAsInt(tuple.getT2()), tuple.getT2(), tuple.getT1()))
//								.doOnNext(System.out::println)
//								.sequential()
//								.distinct(LeaderboardEntry::getGdUser)
//								.collectSortedList(Comparator.naturalOrder())
//								.flatMap(list -> {
//									ctx.setVar("leaderboard", list);
//									ctx.setVar("page", 0);
//									Command.invoke(this, ctx);
//									return Mono.<Void>empty();
//								})).doOnSuccessOrError((__, e) -> message.delete().subscribe())));

	}

	@Override
	public Set<String> getAliases() {
		return Set.of("leaderboard", "leaderboards");
	}

	@Override
	public Set<Command> getSubcommands() {
		return Set.of();
	}

	@Override
	public String getDescription() {
		return "Builds and displays a server-wide Geometry Dash leaderboard based on a player stat (stars, demons, creator points, etc)";
	}

	@Override
	public String getSyntax() {
		return "";
	}

	@Override
	public PermissionLevel getPermissionLevel() {
		return PermissionLevel.PUBLIC;
	}

	@Override
	public EnumSet<Type> getChannelTypesAllowed() {
		return EnumSet.of(Type.GUILD_TEXT);
	}

	@Override
	public Map<Class<? extends Throwable>, BiConsumer<Throwable, Context>> getErrorActions() {
		return GDUtils.DEFAULT_GD_ERROR_ACTIONS;
	}

}
