package com.github.alex1304.ultimategdbot.gdplugin.command;

import com.github.alex1304.jdash.util.LevelSearchFilters;
import com.github.alex1304.ultimategdbot.api.command.CommandFailedException;
import com.github.alex1304.ultimategdbot.api.command.Context;
import com.github.alex1304.ultimategdbot.api.command.annotated.CommandAction;
import com.github.alex1304.ultimategdbot.api.command.annotated.CommandDoc;
import com.github.alex1304.ultimategdbot.api.command.annotated.CommandDescriptor;
import com.github.alex1304.ultimategdbot.gdplugin.GDService;
import com.github.alex1304.ultimategdbot.gdplugin.util.GDLevels;

import reactor.core.publisher.Mono;

@CommandDescriptor(
		aliases = "level",
		shortDescription = "tr:GDStrings/level_desc"
)
public class LevelCommand {
	
	@CommandAction
	@CommandDoc("tr:GDStrings/level_run")
	public Mono<Void> run(Context ctx, String query) {
		if (!query.matches("[a-zA-Z0-9 _-]+")) {
			return Mono.error(new CommandFailedException(ctx.translate("GDStrings", "error_invalid_characters")));
		}
		return GDLevels.searchAndSend(ctx, ctx.translate("GDStrings", "search_results", query),
				() -> ctx.bot().service(GDService.class).getGdClient().searchLevels(query, LevelSearchFilters.create(), 0));
	}
}
