package com.github.alex1304.ultimategdbot.gdplugin.command;

import com.github.alex1304.ultimategdbot.api.command.Context;
import com.github.alex1304.ultimategdbot.api.command.PermissionLevel;
import com.github.alex1304.ultimategdbot.api.command.annotated.CommandAction;
import com.github.alex1304.ultimategdbot.api.command.annotated.CommandDescriptor;
import com.github.alex1304.ultimategdbot.api.command.annotated.CommandPermission;
import com.github.alex1304.ultimategdbot.gdplugin.GDService;

import reactor.core.publisher.Mono;

@CommandDescriptor(
		aliases = "cleargdcache",
		shortDescription = "tr:GDStrings/cleargdcache_desc"
)
@CommandPermission(level = PermissionLevel.BOT_ADMIN)
public class ClearGdCacheCommand {

	@CommandAction
	public Mono<Void> run(Context ctx) {
		return Mono.fromRunnable(ctx.bot().service(GDService.class).getGdClient()::clearCache)
				.then(ctx.reply(ctx.translate("GDStrings", "cache_clear_success")))
				.then();
	}
}
