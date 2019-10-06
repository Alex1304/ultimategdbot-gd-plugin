package com.github.alex1304.ultimategdbot.gdplugin.command;

import com.github.alex1304.jdash.entity.GDUser;
import com.github.alex1304.ultimategdbot.api.command.Context;
import com.github.alex1304.ultimategdbot.api.command.annotated.CommandAction;
import com.github.alex1304.ultimategdbot.api.command.annotated.CommandDoc;
import com.github.alex1304.ultimategdbot.api.command.annotated.CommandSpec;
import com.github.alex1304.ultimategdbot.gdplugin.GDServiceMediator;
import com.github.alex1304.ultimategdbot.gdplugin.util.GDLevels;

import reactor.core.publisher.Mono;

@CommandSpec(
		aliases = "levelsby",
		shortDescription = "Browse levels from a specific player in Geometry Dash."
)
public class LevelsbyCommand {

	private final GDServiceMediator gdServiceMediator;
	
	public LevelsbyCommand(GDServiceMediator gdServiceMediator) {
		this.gdServiceMediator = gdServiceMediator;
	}
	
	@CommandAction
	@CommandDoc("Browse levels from a specific player in Geometry Dash. You can specify "
			+ "the user either by their name or their player ID. If several results are "
			+ "found, an interactive menu will open allowing you to navigate through "
			+ "results and select the result you want.")
	public Mono<Void> execute(Context ctx, GDUser user) {
		return GDLevels.searchAndSend(ctx, user.getName() + "'s levels",
				() -> gdServiceMediator.getGdClient().getLevelsByUser(user, 0));
	}
}
