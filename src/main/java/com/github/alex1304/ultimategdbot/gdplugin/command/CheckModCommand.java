package com.github.alex1304.ultimategdbot.gdplugin.command;

import com.github.alex1304.jdash.entity.GDUser;
import com.github.alex1304.jdash.entity.Role;
import com.github.alex1304.ultimategdbot.api.command.CommandFailedException;
import com.github.alex1304.ultimategdbot.api.command.Context;
import com.github.alex1304.ultimategdbot.api.command.annotated.CommandAction;
import com.github.alex1304.ultimategdbot.api.command.annotated.CommandDoc;
import com.github.alex1304.ultimategdbot.api.command.annotated.CommandSpec;
import com.github.alex1304.ultimategdbot.gdplugin.GDServiceMediator;
import com.github.alex1304.ultimategdbot.gdplugin.database.GDLinkedUsers;
import com.github.alex1304.ultimategdbot.gdplugin.database.GDModList;
import com.github.alex1304.ultimategdbot.gdplugin.gdevent.UserDemotedFromElderEvent;
import com.github.alex1304.ultimategdbot.gdplugin.gdevent.UserDemotedFromModEvent;
import com.github.alex1304.ultimategdbot.gdplugin.gdevent.UserPromotedToElderEvent;
import com.github.alex1304.ultimategdbot.gdplugin.gdevent.UserPromotedToModEvent;
import com.github.alex1304.ultimategdbot.gdplugin.util.GDUsers;

import reactor.core.publisher.Mono;
import reactor.util.annotation.Nullable;

@CommandSpec(
		aliases = "checkmod",
		shortDescription = "Checks for the presence of the Moderator badge on someone's profile."
)
public class CheckModCommand {

	private final GDServiceMediator gdServiceMediator;
	
	public CheckModCommand(GDServiceMediator gdServiceMediator) {
		this.gdServiceMediator = gdServiceMediator;
	}

	@CommandAction
	@CommandDoc("Checks for the presence of the Moderator badge on someone's profile. This command "
			+ "displays the mod status as if you pressed the 'REQ' button in-game, but note that it "
			+ "doesn't actually push this button for you. It just checks for the presence of the "
			+ "'M' badge on the profile, nothing else.")
	public Mono<Void> run(Context ctx, @Nullable GDUser gdUser) {
		return Mono.justOrEmpty(gdUser)
				.switchIfEmpty(ctx.getBot().getDatabase().findByID(GDLinkedUsers.class, ctx.getAuthor().getId().asLong())
						.filter(GDLinkedUsers::getIsLinkActivated)
								.switchIfEmpty(Mono.error(new CommandFailedException("No user specified. If you want to "
										+ "check your own mod status, link your Geometry Dash account using `" 
										+ ctx.getPrefixUsed() + "account` and retry this command. Otherwise, you "
										+ "need to specify a user like so: `" + ctx.getPrefixUsed() + "checkmod <gd_user>`.")))
						.map(GDLinkedUsers::getGdAccountId)
						.flatMap(gdServiceMediator.getGdClient()::getUserByAccountId))
				.flatMap(user -> Mono.zip(ctx.getBot().getEmoji("success"), ctx.getBot().getEmoji("failed"), ctx.getBot().getEmoji("mod"))
						.flatMap(emojis -> ctx.reply("Checking in-game mod status for user **" + user.getName() + "**...\n||"
								+ (user.getRole() == Role.USER
								? emojis.getT2() + " Failed. Nothing found."
								: emojis.getT1() + " Success! Access granted: " + user.getRole()) + "||"))
						.then(GDUsers.makeIconSet(ctx.getBot(), user, gdServiceMediator.getSpriteFactory(), gdServiceMediator.getIconsCache())
								.onErrorResume(e -> Mono.empty()))
						.then(ctx.getBot().getDatabase().findByID(GDModList.class, user.getAccountId()))
						.switchIfEmpty(Mono.defer(() -> {
							if (user.getRole() == Role.USER) {
								return Mono.empty();
							}
							var isElder = user.getRole() == Role.ELDER_MODERATOR;
							gdServiceMediator.getGdEventDispatcher().dispatch(isElder ? new UserPromotedToElderEvent(user)
									: new UserPromotedToModEvent(user));
							var gdMod = new GDModList();
							gdMod.setAccountId(user.getAccountId());
							gdMod.setName(user.getName());
							gdMod.setIsElder(isElder);
							return ctx.getBot().getDatabase().save(gdMod).then(Mono.empty());
						}))
						.flatMap(gdMod -> {
							if (user.getRole() == Role.USER) {
								gdServiceMediator.getGdEventDispatcher().dispatch(gdMod.getIsElder() 
										? new UserDemotedFromElderEvent(user) : new UserDemotedFromModEvent(user));
								return ctx.getBot().getDatabase().delete(gdMod);
							} else {
								if (user.getRole() == Role.MODERATOR && gdMod.getIsElder()) {
									gdServiceMediator.getGdEventDispatcher().dispatch(new UserDemotedFromElderEvent(user));
									gdMod.setIsElder(false);
								} else if (user.getRole() == Role.ELDER_MODERATOR && !gdMod.getIsElder()) {
									gdServiceMediator.getGdEventDispatcher().dispatch(new UserPromotedToElderEvent(user));
									gdMod.setIsElder(true);
								}
								gdMod.setName(user.getName());
								return ctx.getBot().getDatabase().save(gdMod);
							}
						}))
				.then();
	}
}
