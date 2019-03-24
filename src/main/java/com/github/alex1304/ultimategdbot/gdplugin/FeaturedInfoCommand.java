package com.github.alex1304.ultimategdbot.gdplugin;

import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;

import com.github.alex1304.jdash.client.AuthenticatedGDClient;
import com.github.alex1304.jdash.entity.GDLevel;
import com.github.alex1304.jdash.exception.MissingAccessException;
import com.github.alex1304.jdash.util.LevelSearchFilters;
import com.github.alex1304.ultimategdbot.api.Command;
import com.github.alex1304.ultimategdbot.api.CommandFailedException;
import com.github.alex1304.ultimategdbot.api.Context;
import com.github.alex1304.ultimategdbot.api.PermissionLevel;
import com.github.alex1304.ultimategdbot.api.utils.ArgUtils;

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
		ArgUtils.requireMinimumArgCount(ctx, 2);
		var searchedLevelId = ctx.getVar("id", Long.class);
		if (searchedLevelId == null) {
			var input = String.join(" ", ctx.getArgs().subList(1, ctx.getArgs().size()));
			return ctx.reply("Searching, please wait...")
					.flatMap(waitMessage -> gdClient.searchLevels(input, LevelSearchFilters.create(), 0)
							.map(paginator -> paginator.asList().get(0))
							.filter(level -> level.getFeaturedScore() != 0)
							.switchIfEmpty(Mono.just(waitMessage).flatMap(message -> {
								ctx.setVar("wait", waitMessage);
								return cleanError(ctx, "This level is not featured.");
							}))
							.flatMap(level -> {
								ctx.setVar("min", 0);
								ctx.setVar("max", 10000);
								ctx.setVar("current", 500);
								ctx.setVar("id", level.getId());
								ctx.setVar("score", level.getFeaturedScore());
								ctx.setVar("linear", false);
								ctx.setVar("wait", waitMessage);
								return ctx.getBot().getCommandKernel().invokeCommand(this, ctx);
							})).then();
		}
		var minPageVisited = ctx.getVar("min", Integer.class);
		var maxPageVisited = ctx.getVar("max", Integer.class);
		var currentPage = ctx.getVar("current", Integer.class);
		var targetScore = ctx.getVar("score", Integer.class);
		var isLinear = ctx.getVar("linear", Boolean.class);
		
		return gdClient.browseFeaturedLevels(currentPage)
				.flatMap(paginator -> {
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
						return ctx.getBot().getCommandKernel().invokeCommand(this, ctx);
					} else if (first.getFeaturedScore() > targetScore) {
						ctx.setVar("min", currentPage);
						ctx.setVar("current", (maxPageVisited + currentPage) / 2 + ((maxPageVisited - currentPage) / 2 == 0 ? 1 : 0));
						return ctx.getBot().getCommandKernel().invokeCommand(this, ctx);
					} else {
						if (!isLinear) {
							if (currentPage.intValue() == maxPageVisited.intValue()) {
								ctx.setVar("linear", true);
								ctx.setVar("max", currentPage - 1);
								ctx.setVar("current", ((currentPage - 1) + minPageVisited) / 2);
								return ctx.getBot().getCommandKernel().invokeCommand(this, ctx);
							} else {
								ctx.setVar("current", currentPage + 1);
								return ctx.getBot().getCommandKernel().invokeCommand(this, ctx);
							}
						} else {
							if (currentPage.intValue() != minPageVisited.intValue()) {
								ctx.setVar("current", currentPage - 1);
								return ctx.getBot().getCommandKernel().invokeCommand(this, ctx);
							}
						}
					}
					return cleanError(ctx, "This level couldn't be found in the Featured section.");
				})
				.onErrorResume(MissingAccessException.class, e -> {
					ctx.setVar("max", currentPage - 1);
					ctx.setVar("current", ((currentPage - 1) + minPageVisited) / 2);
					return ctx.getBot().getCommandKernel().invokeCommand(this, ctx);
				})
				.then();
	}
	
	private Mono<Message> sendResult(Context ctx, GDLevel level, int page, int position) {
		var deleteWait = Optional.ofNullable(ctx.getVar("wait", Message.class))
				.map(Message::delete).orElse(Mono.empty());
		return deleteWait.onErrorResume(e -> Mono.empty())
				.then(ctx.getEvent().getMessage().getAuthor().map(author -> ctx.reply(author.getMention() + ", "
						+ GDUtils.levelToString(level) + " is currently placed in page **" + (page + 1)
						+ "** of the Featured section at position " + position)).orElse(Mono.empty()));
	}
	
	private <X> Mono<X> cleanError(Context ctx, String text) {
		var deleteWait = Optional.ofNullable(ctx.getVar("wait", Message.class))
				.map(Message::delete).orElse(Mono.empty());
		return deleteWait.onErrorResume(e -> Mono.empty())
				.then(Mono.error(new CommandFailedException(text)));
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
