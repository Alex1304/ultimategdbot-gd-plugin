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
import com.github.alex1304.ultimategdbot.gdplugin.database.GDModDao;
import com.github.alex1304.ultimategdbot.gdplugin.database.ImmutableGDModData;
import com.github.alex1304.ultimategdbot.gdplugin.gdevent.UserDemotedFromElderEvent;
import com.github.alex1304.ultimategdbot.gdplugin.gdevent.UserDemotedFromModEvent;
import com.github.alex1304.ultimategdbot.gdplugin.gdevent.UserPromotedToElderEvent;
import com.github.alex1304.ultimategdbot.gdplugin.gdevent.UserPromotedToModEvent;

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
								ctx.translate("GDStrings", "error_user_not_specified", ctx.prefixUsed(), "checkmod"))))
						.map(GDLinkedUserData::gdUserId)
						.flatMap(gd.client()::getUserByAccountId))
				.flatMap(user -> Mono.zip(
								gd.bot().emoji().get("success"),
								gd.bot().emoji().get("failed"),
								gd.bot().emoji().get("mod"))
						.flatMap(emojis -> ctx.reply(ctx.translate("GDStrings", "checking_mod", user.getName()) + "\n||"
								+ (user.getRole() == Role.USER
								? emojis.getT2() + ' ' + ctx.translate("GDStrings", "checkmod_failed")
								: emojis.getT1() + ' ' + ctx.translate("GDStrings", "checkmod_success", user.getRole().toString()))+ "||"))
						.then(gd.user().makeIconSet(ctx, user).onErrorResume(e -> Mono.empty()))
						.then(gd.bot().database().withExtension(GDModDao.class, dao -> dao.get(user.getAccountId())))
						.flatMap(Mono::justOrEmpty)
						.switchIfEmpty(Mono.defer(() -> {
							if (user.getRole() == Role.USER) {
								return Mono.empty();
							}
							var isElder = user.getRole() == Role.ELDER_MODERATOR;
							gd.event().dispatcher().dispatch(isElder ? new UserPromotedToElderEvent(user)
									: new UserPromotedToModEvent(user));
							var gdMod = ImmutableGDModData.builder()
									.accountId(user.getAccountId())
									.name(user.getName())
									.isElder(isElder)
									.build();
							return gd.bot().database()
									.useExtension(GDModDao.class, dao -> dao.insert(gdMod))
									.then(Mono.empty());
						}))
						.flatMap(gdMod -> {
							if (user.getRole() == Role.USER) {
								gd.event().dispatcher().dispatch(gdMod.isElder() 
										? new UserDemotedFromElderEvent(user) : new UserDemotedFromModEvent(user));
								return gd.bot().database()
										.useExtension(GDModDao.class, dao -> dao.delete(gdMod.accountId()));
							} else {
								var updatedGdMod = ImmutableGDModData.builder().from(gdMod);
								if (user.getRole() == Role.MODERATOR && gdMod.isElder()) {
									gd.event().dispatcher().dispatch(new UserDemotedFromElderEvent(user));
									updatedGdMod.isElder(false);
								} else if (user.getRole() == Role.ELDER_MODERATOR && !gdMod.isElder()) {
									gd.event().dispatcher().dispatch(new UserPromotedToElderEvent(user));
									updatedGdMod.isElder(true);
								}
								updatedGdMod.name(user.getName());
								return gd.bot().database()
										.useExtension(GDModDao.class, dao -> dao.update(updatedGdMod.build()));
							}
						}))
				.then();
	}
}
