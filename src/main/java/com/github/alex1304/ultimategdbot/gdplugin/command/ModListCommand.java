package com.github.alex1304.ultimategdbot.gdplugin.command;

import static reactor.function.TupleUtils.function;

import com.github.alex1304.ultimategdbot.api.command.Context;
import com.github.alex1304.ultimategdbot.api.command.annotated.CommandAction;
import com.github.alex1304.ultimategdbot.api.command.annotated.CommandDescriptor;
import com.github.alex1304.ultimategdbot.api.service.Root;
import com.github.alex1304.ultimategdbot.gdplugin.GDService;
import com.github.alex1304.ultimategdbot.gdplugin.database.GDModDao;

import reactor.core.publisher.Mono;

@CommandDescriptor(
		aliases = "modlist",
		shortDescription = "tr:GDStrings/modlist_desc"
)
public final class ModListCommand {

	@Root
	private GDService gd;
	
	@CommandAction
	public Mono<Void> run(Context ctx) {
		return Mono.zip(gd.bot().emoji().get("mod"), gd.bot().emoji().get("elder_mod"), 
						gd.bot().database().withExtension(GDModDao.class, GDModDao::getAll))
				.flatMap(function((modEmoji, elderModEmoji, modList) -> {
					var sb = new StringBuilder("**__" + ctx.translate("GDStrings", "mod_list") + "__\n**");
					sb.append(ctx.translate("GDStrings", "modlist_intro", ctx.prefixUsed()) + "\n\n");
					for (var gdMod : modList) {
						sb.append(gdMod.isElder() ? elderModEmoji : modEmoji);
						sb.append(" **").append(gdMod.name()).append("**\n");
					}
					return gd.bot().interactiveMenu()
							.createPaginated(sb.toString(), 1000)
							.open(ctx);
				})).then();
	}
}
