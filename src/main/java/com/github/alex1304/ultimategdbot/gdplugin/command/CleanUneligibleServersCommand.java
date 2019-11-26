package com.github.alex1304.ultimategdbot.gdplugin.command;

import static com.github.alex1304.ultimategdbot.gdplugin.util.DatabaseUtils.in;

import com.github.alex1304.ultimategdbot.api.command.Context;
import com.github.alex1304.ultimategdbot.api.command.PermissionLevel;
import com.github.alex1304.ultimategdbot.api.command.annotated.CommandAction;
import com.github.alex1304.ultimategdbot.api.command.annotated.CommandSpec;

import discord4j.core.object.entity.Guild;
import discord4j.core.object.util.Snowflake;
import reactor.core.publisher.Mono;

@CommandSpec(
		aliases = "cleanuneligibleservers",
		shortDescription = "Resets configuration for servers that are not eligible to the new rates announcement feature.",
		permLevel = PermissionLevel.BOT_OWNER
)
public class CleanUneligibleServersCommand {
	
	@CommandAction
	public Mono<Void> run(Context ctx) {
		return ctx.getBot().getMainDiscordClient().getGuilds()
				.filter(g -> g.getMemberCount().orElse(0) > 200)
				.map(Guild::getId)
				.map(Snowflake::asLong)
				.collectList()
				.flatMap(list -> ctx.getBot().getDatabase().performTransaction(session -> session
						.createQuery("update GDSubscribedGuilds s "
								+ "set s.channelAwardedLevelsId = 0, "
								+ "s.channelGdModeratorsId = 0, "
								+ "s.channelTimelyLevelsId = 0, "
								+ "s.roleAwardedLevelsId = 0, "
								+ "s.roleGdModeratorsId = 0, "
								+ "s.roleTimelyLevelsId = 0 "
								+ "where s.guildId not " + in(list))
						.executeUpdate()))
				.flatMap(count -> ctx.reply("Reset configuration of new rates announcement feature for " + count + " uneligible guilds!"))
				.then();
	}
}
