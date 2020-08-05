package com.github.alex1304.ultimategdbot.gdplugin.command;

import com.github.alex1304.jdash.entity.GDUser;
import com.github.alex1304.ultimategdbot.api.command.Context;
import com.github.alex1304.ultimategdbot.api.command.annotated.CommandAction;
import com.github.alex1304.ultimategdbot.api.command.annotated.CommandDescriptor;
import com.github.alex1304.ultimategdbot.api.command.annotated.CommandDoc;
import com.github.alex1304.ultimategdbot.api.service.Root;
import com.github.alex1304.ultimategdbot.gdplugin.GDService;

import reactor.core.publisher.Mono;

@CommandDescriptor(
		aliases = "levelsby",
		shortDescription = "tr:GDStrings/levelsby_desc"
)
public final class LevelsbyCommand {

	@Root
	private GDService gd;
	
	@CommandAction
	@CommandDoc("tr:GDStrings/levelsby_run")
	public Mono<Void> run(Context ctx, GDUser user) {
		return gd.level().searchAndSend(ctx, ctx.translate("GDStrings", "player_levels", user.getName()),
				() -> gd.client().getLevelsByUser(user, 0));
	}
}
