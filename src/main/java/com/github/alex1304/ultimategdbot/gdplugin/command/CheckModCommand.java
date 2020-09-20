package com.github.alex1304.ultimategdbot.gdplugin.command;

import com.github.alex1304.jdash.entity.GDUser;
import com.github.alex1304.jdash.entity.Role;
import com.github.alex1304.ultimategdbot.api.command.CommandFailedException;
import com.github.alex1304.ultimategdbot.api.command.Context;
import com.github.alex1304.ultimategdbot.api.command.annotated.CommandAction;
import com.github.alex1304.ultimategdbot.api.command.annotated.CommandDescriptor;
import com.github.alex1304.ultimategdbot.api.command.annotated.CommandDoc;
import com.github.alex1304.ultimategdbot.api.service.Root;
import com.github.alex1304.ultimategdbot.gdplugin.GDService;
import com.github.alex1304.ultimategdbot.gdplugin.database.GDLinkedUserDao;
import com.github.alex1304.ultimategdbot.gdplugin.database.GDLinkedUserData;

import reactor.core.publisher.Mono;
import reactor.util.annotation.Nullable;

@CommandDescriptor(
		aliases = "checkmod",
		shortDescription = "tr:GDStrings/checkmod_desc"
)
public final class CheckModCommand {
	
	@Root
	private GDService gd;

	@CommandAction
	@CommandDoc("tr:GDStrings/checkmod_run")
	public Mono<Void> run(Context ctx, @Nullable GDUser gdUser) {
		return Mono.justOrEmpty(gdUser)
				.switchIfEmpty(gd.bot().database()
						.withExtension(GDLinkedUserDao.class, dao -> dao.getByDiscordUserId(ctx.author().getId().asLong()))
						.flatMap(Mono::justOrEmpty)
						.filter(GDLinkedUserData::isLinkActivated)
						.switchIfEmpty(Mono.error(new CommandFailedException(
								ctx.translate("GDStrings", "error_checkmod_user_not_specified", ctx.prefixUsed(), "checkmod"))))
						.map(GDLinkedUserData::gdUserId)
						.flatMap(gd.client()::getUserByAccountId))
				.flatMap(user -> Mono.zip(
								gd.bot().emoji().get("success"),
								gd.bot().emoji().get("failed"),
								gd.bot().emoji().get("mod"))
						.flatMap(emojis -> ctx.reply(ctx.translate("GDStrings", "checking_mod", user.getName()) + "\n||"
								+ (user.getRole() == Role.USER
								? emojis.getT2() + ' ' + ctx.translate("GDStrings", "checkmod_failed")
								: emojis.getT1() + ' ' + ctx.translate("GDStrings", "checkmod_success", user.getRole().toString()))+ "||")))
				.then();
	}
}
