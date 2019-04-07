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
import com.github.alex1304.ultimategdbot.api.PermissionLevel;
import com.github.alex1304.ultimategdbot.api.utils.ArgUtils;
import com.github.alex1304.ultimategdbot.api.utils.reply.ReplyMenuBuilder;

import discord4j.core.object.entity.Channel.Type;
import reactor.core.publisher.Mono;

public class LevelCommand implements Command {
	
	private final AuthenticatedGDClient gdClient;
	private final boolean byUser;

	public LevelCommand(AuthenticatedGDClient gdClient, boolean byUser) {
		this.gdClient = Objects.requireNonNull(gdClient);
		this.byUser = byUser;
	}

	@Override
	public Mono<Void> execute(Context ctx) {
		ArgUtils.requireMinimumArgCount(ctx, 2);
		var input = ArgUtils.concatArgs(ctx, 1);
		if (!input.matches("[a-zA-Z0-9 _-]+")) {
			return Mono.error(new CommandFailedException("Your query contains invalid characters."));
		}
		@SuppressWarnings("unchecked")
		var paginatorMono = (Mono<GDPaginator<GDLevel>>) ctx.getVar("paginator", Mono.class);
		if (paginatorMono == null) {
			if (byUser) {
				ctx.setVar("paginator", GDUtils.stringToUser(ctx.getBot(), gdClient, input)
						.flatMap(user -> {
							ctx.setVar("creatorName", user.getName());
							return gdClient.getLevelsByUser(user, 0);
						}));
			} else {
				ctx.setVar("paginator", gdClient.searchLevels(input, LevelSearchFilters.create(), 0));
			}
			return ctx.getBot().getCommandKernel().invokeCommand(this, ctx).onErrorResume(e -> Mono.empty());
		}
		return paginatorMono.flatMap(paginator -> {
			if (!paginator.hasPreviousPage() && paginator.getPageSize() == 1) {
				ctx.setVar("canGoBack", false);
				return showLevelDetail(ctx, paginator.asList().get(0));
			}
			var rb = new ReplyMenuBuilder(ctx, true, false);
			rb.addItem("select", "To view details on a specific level, type `select <result_number>`, e.g `select 2`", ctx0 -> {
				if (ctx0.getArgs().size() == 1 || !ctx0.getArgs().get(1).matches("[0-9]+")) {
					return Mono.error(new CommandFailedException("Invalid input"));
				}
				var selected = Integer.parseInt(ctx0.getArgs().get(1)) - 1;
				if (selected < 0 || selected >= paginator.getPageSize()) {
					return Mono.error(new CommandFailedException("Number out of range"));
				}
				ctx.setVar("canGoBack", true);
				return showLevelDetail(ctx, paginator.asList().get(selected));
			});
			GDUtils.addPaginatorItems(rb, this, ctx, paginator);
			var creatorName = ctx.getVarOrDefault("creatorName", "(unknown)");
			return GDUtils.levelPaginatorView(ctx, paginator, byUser ? creatorName + "'s levels" : "Search results for \"" + input + "\"")
					.flatMap(embed -> rb.build(null, embed))
					.then();
		}).then();
	}
	
	private Mono<Void> showLevelDetail(Context ctx, GDLevel level) {
		var view = GDUtils.levelView(ctx, level, "Search result", "https://i.imgur.com/a9B6LyS.png");
		if (ctx.getVar("canGoBack", Boolean.class)) {
			var rb = new ReplyMenuBuilder(ctx, true, false);
			rb.addItem("back", "To go back to search results, type `back`", ctx0 -> {
				ctx.getBot().getCommandKernel().invokeCommand(this, ctx).subscribe();
				return Mono.empty();
			});
			return view.flatMap(embed -> rb.build(null, embed)).then();
		}
		return view.flatMap(embed -> ctx.reply(mcs -> mcs.setEmbed(embed))).then();
	}

	@Override
	public Set<String> getAliases() {
		return Set.of(byUser ? "levelsby" : "level");
	}

	@Override
	public Set<Command> getSubcommands() {
		return Set.of();
	}

	@Override
	public String getDescription() {
		return byUser ? "Browse levels from a specific player in Geometry Dash." : "Searches for online levels in Geometry Dash.";
	}

	@Override
	public String getLongDescription() {
		return (byUser ? "You can specify the user either by their name or their player ID. " : "You can specify the level either by its name or its ID. ")
				+ "If several results are found, an interactive menu will open allowing you to navigate through results and select the result you want.";
	}

	@Override
	public String getSyntax() {
		return byUser ? "<username_or_playerID>" : "<name_or_ID>";
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
