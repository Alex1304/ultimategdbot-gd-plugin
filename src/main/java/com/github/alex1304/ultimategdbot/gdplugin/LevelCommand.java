package com.github.alex1304.ultimategdbot.gdplugin;

import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;

import com.github.alex1304.jdash.client.AuthenticatedGDClient;
import com.github.alex1304.jdash.entity.GDLevel;
import com.github.alex1304.jdash.util.GDPaginator;
import com.github.alex1304.jdash.util.LevelSearchFilters;
import com.github.alex1304.ultimategdbot.api.Command;
import com.github.alex1304.ultimategdbot.api.CommandFailedException;
import com.github.alex1304.ultimategdbot.api.Context;
import com.github.alex1304.ultimategdbot.api.InvalidSyntaxException;
import com.github.alex1304.ultimategdbot.api.PermissionLevel;
import com.github.alex1304.ultimategdbot.api.utils.reply.ReplyMenuBuilder;

import discord4j.core.object.entity.Channel.Type;
import reactor.core.publisher.Mono;

public class LevelCommand implements Command {
	
	private final AuthenticatedGDClient gdClient;

	public LevelCommand(AuthenticatedGDClient gdClient) {
		this.gdClient = Objects.requireNonNull(gdClient);
	}

	@Override
	public Mono<Void> execute(Context ctx) {
		if (ctx.getArgs().size() < 2) {
			return Mono.error(new InvalidSyntaxException(this));
		}
		var input = String.join(" ", ctx.getArgs().subList(1, ctx.getArgs().size()));
		@SuppressWarnings("unchecked")
		var paginatorMono = (Mono<GDPaginator<GDLevel>>) ctx.getVar("paginator", Mono.class);
		if (paginatorMono == null) {
			ctx.setVar("paginator", gdClient.searchLevels(input, LevelSearchFilters.create(), 0));
			Command.invoke(this, ctx);
			return Mono.empty();
		}
		return paginatorMono.flatMap(paginator -> {
			var rb = new ReplyMenuBuilder(ctx, true, false);
			if (paginator.hasNextPage()) {
				rb.addItem("next", "To go to next page, type `next`", ctx0 -> {
					ctx.setVar("paginator", paginator.goToNextPage());
					Command.invoke(this, ctx);
					return Mono.empty();
				});
			}
			if (paginator.hasPreviousPage()) {
				rb.addItem("prev", "To go to previous page, type `prev`", ctx0 -> {
					ctx.setVar("paginator", paginator.hasPreviousPage());
					Command.invoke(this, ctx);
					return Mono.empty();
				});
			}
			if (paginator.getTotalNumberOfPages() > 1) {
				rb.addItem("page", "To go to a specific page, type `page <number>`, e.g `page 3`", ctx0 -> {
					if (ctx0.getArgs().size() == 1) {
						Command.invoke(this, ctx);
						return Mono.error(new CommandFailedException("Please specify a page number"));
					}
					try {
						var page = Integer.parseInt(ctx0.getArgs().get(1)) - 1;
						if (page < 0 || page >= paginator.getTotalNumberOfPages()) {
							Command.invoke(this, ctx);
							return Mono.error(new CommandFailedException("Page number out of range"));
						}
						ctx.setVar("paginator", paginator.goTo(page));
						Command.invoke(this, ctx);
						return Mono.empty();
					} catch (NumberFormatException e) {
						Command.invoke(this, ctx);
						return Mono.error(new CommandFailedException("Please specify a valid page number"));
					}
				});
			}
			rb.setHeader("Page " + (paginator.getPageNumber() + 1));
			return rb.build(null, GDUtils.levelPaginatorView(ctx, paginator)).then();
		}).then();
	}

	@Override
	public Set<String> getAliases() {
		return Set.of("level");
	}

	@Override
	public Set<Command> getSubcommands() {
		return Set.of();
	}

	@Override
	public String getDescription() {
		return "Searches for online levels in Geometry Dash.";
	}

	@Override
	public String getSyntax() {
		return "<name_or_ID>";
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
