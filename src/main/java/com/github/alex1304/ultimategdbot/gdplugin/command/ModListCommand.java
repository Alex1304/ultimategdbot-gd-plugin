package com.github.alex1304.ultimategdbot.gdplugin.command;

import com.github.alex1304.ultimategdbot.api.command.Context;
import com.github.alex1304.ultimategdbot.api.command.annotated.CommandAction;
import com.github.alex1304.ultimategdbot.api.command.annotated.CommandSpec;
import com.github.alex1304.ultimategdbot.api.utils.BotUtils;
import com.github.alex1304.ultimategdbot.gdplugin.database.GDModList;

import reactor.core.publisher.Mono;
import reactor.function.TupleUtils;

@CommandSpec(
		aliases = "modlist",
		shortDescription = "Displays the full list of last known Geometry Dash moderators."
)
public class ModListCommand {

	@CommandAction
	public Mono<Void> run(Context ctx) {
		return Mono.zip(ctx.getBot().getEmoji("mod"), ctx.getBot().getEmoji("elder_mod"), 
						ctx.getBot().getDatabase().query(GDModList.class, "from GDModList order by isElder desc, name").collectList())
				.flatMap(TupleUtils.function((modEmoji, elderModEmoji, modList) -> {
					var sb = new StringBuilder("**__Geometry Dash Moderator List:__\n**");
					sb.append("This list is automatically updated when the `")
						.append(ctx.getPrefixUsed())
						.append("checkmod` command is used against newly promoted/demoted players.\n\n");
					for (var gdMod : modList) {
						sb.append(gdMod.getIsElder() ? elderModEmoji : modEmoji);
						sb.append(" **").append(gdMod.getName()).append("**\n");
					}
					return BotUtils.sendPaginatedMessage(ctx, sb.toString());
				})).then();
	}
}
