package com.github.alex1304.ultimategdbot.gdplugin;

import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;

import com.github.alex1304.jdash.client.AuthenticatedGDClient;
import com.github.alex1304.jdash.entity.GDLevel;
import com.github.alex1304.jdash.exception.MissingAccessException;
import com.github.alex1304.jdash.util.LevelSearchFilters;
import com.github.alex1304.ultimategdbot.api.Command;
import com.github.alex1304.ultimategdbot.api.CommandFailedException;
import com.github.alex1304.ultimategdbot.api.Context;
import com.github.alex1304.ultimategdbot.api.InvalidSyntaxException;
import com.github.alex1304.ultimategdbot.api.PermissionLevel;

import discord4j.core.object.entity.Channel.Type;
import discord4j.core.object.entity.Message;
import reactor.core.publisher.Mono;

public class FeaturedInfoCommand implements Command {
	
	private final AuthenticatedGDClient gdClient;
	
	public FeaturedInfoCommand(AuthenticatedGDClient gdClient) {
		this.gdClient = Objects.requireNonNull(gdClient);
	}

	@Override
	public Mono<Void> execute(Context ctx) {
		if (ctx.getArgs().size() == 1) {
			return Mono.error(new InvalidSyntaxException(this));
		}
		var searchedLevelId = ctx.getVar("id", Long.class);
		if (searchedLevelId == null) {
			System.out.println("what");
			var input = String.join(" ", ctx.getArgs().subList(1, ctx.getArgs().size()));
			return gdClient.searchLevels(input, LevelSearchFilters.create(), 0)
					.map(paginator -> paginator.asList().get(0))
					.filter(level -> level.getFeaturedScore() != 0)
					.switchIfEmpty(Mono.error(new CommandFailedException("This level is not featured.")))
					.doOnNext(level -> {
						ctx.setVar("min", 0);
						ctx.setVar("max", 10000);
						ctx.setVar("current", 500);
						ctx.setVar("id", level.getId());
						ctx.setVar("score", level.getFeaturedScore());
						ctx.setVar("linear", false);
						ctx.getBot().getCommandKernel().invokeCommand(this, ctx).subscribe();
					}).then();
		}
		var minPageVisited = ctx.getVar("min", Integer.class);
		var maxPageVisited = ctx.getVar("max", Integer.class);
		var currentPage = ctx.getVar("current", Integer.class);
		var targetScore = ctx.getVar("score", Integer.class);
		var isLinear = ctx.getVar("linear", Boolean.class);
		
		return gdClient.browseFeaturedLevels(currentPage)
				.flatMap(paginator -> {
					System.out.println(paginator);
					System.out.println("Found:min=" + minPageVisited + ", max=" + maxPageVisited + ", current=" + currentPage + ", targetScore=" + targetScore + ", linear=" + isLinear);
					var first = paginator.asList().get(0);
					var i = 1;
					for (var level : paginator.asList()) {
						if (level.getId() == searchedLevelId) {
							return sendResult(ctx, level, currentPage, i);
						}
						i++;
					}
					if (first.getFeaturedScore() < targetScore) {
						ctx.setVar("max", currentPage - 1);
						ctx.setVar("current", ((currentPage - 1) + minPageVisited) / 2);
						ctx.getBot().getCommandKernel().invokeCommand(this, ctx).subscribe();
						System.out.println("left");
						return Mono.empty();
					} else if (first.getFeaturedScore() > targetScore) {
						ctx.setVar("min", currentPage);
						ctx.setVar("current", (maxPageVisited + currentPage) / 2 + ((maxPageVisited - currentPage) / 2 == 0 ? 1 : 0));
						ctx.getBot().getCommandKernel().invokeCommand(this, ctx).subscribe();
						System.out.println("right");
						return Mono.empty();
					} else {
						if (!isLinear) {
							if (currentPage.intValue() == maxPageVisited.intValue()) {
								ctx.setVar("linear", true);
								ctx.setVar("max", currentPage - 1);
								ctx.setVar("current", ((currentPage - 1) + minPageVisited) / 2);
								ctx.getBot().getCommandKernel().invokeCommand(this, ctx).subscribe();
								System.out.println("linear switch");
								return Mono.empty();
							} else {
								ctx.setVar("current", currentPage + 1);
								ctx.getBot().getCommandKernel().invokeCommand(this, ctx).subscribe();
								System.out.println("linear right");
								return Mono.empty();
							}
						} else {
							if (currentPage.intValue() != minPageVisited.intValue()) {
								ctx.setVar("current", currentPage - 1);
								ctx.getBot().getCommandKernel().invokeCommand(this, ctx).subscribe();
								System.out.println("linear left");
								return Mono.empty();
							}
						}
					}
					return Mono.error(new CommandFailedException("This level couldn't be found in the Featured section."));
				})
				.onErrorResume(MissingAccessException.class, e -> {
					System.out.println("NotFound:min=" + minPageVisited + ", max=" + maxPageVisited + ", current=" + currentPage + ", score=" + targetScore + ", linear=" + isLinear);
					ctx.setVar("max", currentPage - 1);
					ctx.setVar("current", ((currentPage - 1) + minPageVisited) / 2);
					ctx.getBot().getCommandKernel().invokeCommand(this, ctx).subscribe();
					return Mono.empty();
				})
				.then();
	}
	
	private Mono<Message> sendResult(Context ctx, GDLevel level, int page, int position) { 
		return ctx.reply(GDUtils.levelToString(level) + " is currently placed in page **" + (page + 1)
				+ "** of the Featured section at position " + position);
	}

	@Override
	public Set<String> getAliases() {
		return Set.of("featuredinfo");
	}

	@Override
	public Set<Command> getSubcommands() {
		return Set.of();
	}

	@Override
	public String getDescription() {
		return "Finds the exact position of a level in the Featured section.";
	}

	@Override
	public String getSyntax() {
		return "<level_name_or_ID>";
	}

	@Override
	public PermissionLevel getPermissionLevel() {
		return PermissionLevel.PUBLIC;
	}

	@Override
	public EnumSet<Type> getChannelTypesAllowed() {
		return EnumSet.of(Type.GUILD_TEXT, Type.DM);
	}

	@Override
	public Map<Class<? extends Throwable>, BiConsumer<Throwable, Context>> getErrorActions() {
		return GDUtils.DEFAULT_GD_ERROR_ACTIONS;
	}

}
