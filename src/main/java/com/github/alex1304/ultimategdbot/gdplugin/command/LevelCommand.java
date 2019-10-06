package com.github.alex1304.ultimategdbot.gdplugin.command;

import com.github.alex1304.jdash.util.LevelSearchFilters;
import com.github.alex1304.ultimategdbot.api.command.CommandFailedException;
import com.github.alex1304.ultimategdbot.api.command.Context;
import com.github.alex1304.ultimategdbot.api.command.annotated.CommandAction;
import com.github.alex1304.ultimategdbot.api.command.annotated.CommandDoc;
import com.github.alex1304.ultimategdbot.api.command.annotated.CommandSpec;
import com.github.alex1304.ultimategdbot.gdplugin.GDServiceMediator;
import com.github.alex1304.ultimategdbot.gdplugin.util.GDLevels;

import reactor.core.publisher.Mono;

@CommandSpec(
		aliases = "level",
		shortDescription = "Searches for online levels in Geometry Dash."
)
public class LevelCommand {

	private final GDServiceMediator gdServiceMediator;
	
	public LevelCommand(GDServiceMediator gdServiceMediator) {
		this.gdServiceMediator = gdServiceMediator;
	}
	
	@CommandAction
	@CommandDoc("Searches for online levels in Geometry Dash. You can specify the level either by "
			+ "its name or its ID. If several results are found, an interactive menu will open "
			+ "allowing you to navigate through results and select the result you want.")
	public Mono<Void> execute(Context ctx, String query) {
		if (!query.matches("[a-zA-Z0-9 _-]+")) {
			return Mono.error(new CommandFailedException("Your query contains invalid characters."));
		}
		return GDLevels.searchAndSend(ctx, "Search results for `" + query + "`",
				() -> gdServiceMediator.getGdClient().searchLevels(query, LevelSearchFilters.create(), 0));
	}
}
