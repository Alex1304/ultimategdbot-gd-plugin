package com.github.alex1304.ultimategdbot.gdplugin.command;

import com.github.alex1304.ultimategdbot.api.command.Context;
import com.github.alex1304.ultimategdbot.api.command.PermissionLevel;
import com.github.alex1304.ultimategdbot.api.command.annotated.CommandAction;
import com.github.alex1304.ultimategdbot.api.command.annotated.CommandSpec;

import reactor.core.publisher.Mono;

@CommandSpec(
		aliases = "cleanuneligibleservers",
		shortDescription = "Resets configuration for servers that are not eligible to the new rates announcement feature.",
		permLevel = PermissionLevel.BOT_OWNER
)
public class CleanUneligibleServersCommand {
	
	@CommandAction
	public Mono<Void> run(Context ctx, int filter) {
		return ctx.getBot().getMainDiscordClient().getGuilds()
				.filter(g -> g.getMemberCount().orElse(0) > filter)
				.count()
				.flatMap(count -> ctx.reply("There are **" + count + "** servers that have more than " + filter + " members."))
				.then();
	}
}
