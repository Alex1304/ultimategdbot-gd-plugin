package com.github.alex1304.ultimategdbot.gdplugin.command;

import static reactor.function.TupleUtils.function;

import com.github.alex1304.ultimategdbot.api.command.Context;
import com.github.alex1304.ultimategdbot.api.command.annotated.CommandAction;
import com.github.alex1304.ultimategdbot.api.command.annotated.CommandDescriptor;
import com.github.alex1304.ultimategdbot.api.command.menu.InteractiveMenuService;
import com.github.alex1304.ultimategdbot.api.database.DatabaseService;
import com.github.alex1304.ultimategdbot.api.emoji.EmojiService;
import com.github.alex1304.ultimategdbot.gdplugin.database.GDModDao;

import reactor.core.publisher.Mono;

@CommandDescriptor(
		aliases = "modlist",
		shortDescription = "tr:GDStrings/modlist_desc"
)
public class ModListCommand {

	@CommandAction
	public Mono<Void> run(Context ctx) {
		return Mono.zip(ctx.bot().service(EmojiService.class).emoji("mod"), ctx.bot().service(EmojiService.class).emoji("elder_mod"), 
						ctx.bot().service(DatabaseService.class).withExtension(GDModDao.class, GDModDao::getAll))
				.flatMap(function((modEmoji, elderModEmoji, modList) -> {
					var sb = new StringBuilder("**__" + ctx.translate("GDStrings", "mod_list") + "__\n**");
					sb.append(ctx.translate("GDStrings", "modlist_intro", ctx.prefixUsed()) + "\n\n");
					for (var gdMod : modList) {
						sb.append(gdMod.isElder() ? elderModEmoji : modEmoji);
						sb.append(" **").append(gdMod.name()).append("**\n");
					}
					return ctx.bot().service(InteractiveMenuService.class)
							.createPaginated(sb.toString(), 800)
							.open(ctx);
				})).then();
	}
}
