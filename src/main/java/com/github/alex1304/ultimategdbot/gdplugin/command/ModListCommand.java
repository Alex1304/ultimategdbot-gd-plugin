package com.github.alex1304.ultimategdbot.gdplugin.command;

import com.github.alex1304.ultimategdbot.api.command.Context;
import com.github.alex1304.ultimategdbot.api.command.annotated.CommandAction;
import com.github.alex1304.ultimategdbot.api.command.annotated.CommandDescriptor;
import com.github.alex1304.ultimategdbot.api.util.BotUtils;
import com.github.alex1304.ultimategdbot.gdplugin.database.GDModDao;

import reactor.core.publisher.Mono;
import reactor.function.TupleUtils;

@CommandDescriptor(
		aliases = "modlist",
		shortDescription = "Displays the full list of last known Geometry Dash moderators."
)
public class ModListCommand {

	@CommandAction
	public Mono<Void> run(Context ctx) {
		return Mono.zip(ctx.bot().emoji("mod"), ctx.bot().emoji("elder_mod"), 
						ctx.bot().database().withExtension(GDModDao.class, GDModDao::getAll))
				.flatMap(TupleUtils.function((modEmoji, elderModEmoji, modList) -> {
					var sb = new StringBuilder("**__Geometry Dash Moderator List:__\n**");
					sb.append("This list is automatically updated when the `")
						.append(ctx.prefixUsed())
						.append("checkmod` command is used against newly promoted/demoted players.\n\n");
					for (var gdMod : modList) {
						sb.append(gdMod.isElder() ? elderModEmoji : modEmoji);
						sb.append(" **").append(gdMod.name()).append("**\n");
					}
					return BotUtils.sendPaginatedMessage(ctx, sb.toString());
				})).then();
	}
}
