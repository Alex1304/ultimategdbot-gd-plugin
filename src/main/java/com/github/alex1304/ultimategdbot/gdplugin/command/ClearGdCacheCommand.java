package com.github.alex1304.ultimategdbot.gdplugin.command;

import com.github.alex1304.ultimategdbot.api.command.Context;
import com.github.alex1304.ultimategdbot.api.command.PermissionLevel;
import com.github.alex1304.ultimategdbot.api.command.annotated.CommandAction;
import com.github.alex1304.ultimategdbot.api.command.annotated.CommandDescriptor;
import com.github.alex1304.ultimategdbot.api.command.annotated.CommandPermission;
import com.github.alex1304.ultimategdbot.gdplugin.GDServiceMediator;

import reactor.core.publisher.Mono;

@CommandDescriptor(
		aliases = "cleargdcache",
		shortDescription = "Clears the cache of the HTTP client used to make requests to Geometry Dash servers"
)
@CommandPermission(level = PermissionLevel.BOT_ADMIN)
public class ClearGdCacheCommand {
	
	private final GDServiceMediator gdServiceMediator;
	
	public ClearGdCacheCommand(GDServiceMediator gdServiceMediator) {
		this.gdServiceMediator = gdServiceMediator;
	}

	@CommandAction
	public Mono<Void> run(Context ctx) {
		return Mono.fromRunnable(gdServiceMediator.getGdClient()::clearCache)
				.then(ctx.reply("GD client cache has been cleared."))
				.then();
	}
}
