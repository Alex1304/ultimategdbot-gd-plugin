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
		shortDescription = "tr:cmddoc_gd_level/short_description"
)
public class LevelCommand {

	private final GDService gdService;
	
	public LevelCommand(GDService gdService) {
		this.gdService = gdService;
	}
	
	@CommandAction
	@CommandDoc("tr:cmddoc_gd_level/run")
	public Mono<Void> run(Context ctx, String query) {
		if (!query.matches("[a-zA-Z0-9 _-]+")) {
			return Mono.error(new CommandFailedException(ctx.translate("cmdtext_gd_level", "error_invalid_characters")));
		}
		return GDLevels.searchAndSend(ctx, ctx.translate("cmdtext_gd_level", "search_results", query),
				() -> gdService.getGdClient().searchLevels(query, LevelSearchFilters.create(), 0));
	}
}
