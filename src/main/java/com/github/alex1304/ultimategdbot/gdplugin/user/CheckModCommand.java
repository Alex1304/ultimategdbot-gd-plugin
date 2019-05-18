package com.github.alex1304.ultimategdbot.gdplugin.user;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import com.github.alex1304.jdash.entity.GDUser;
import com.github.alex1304.jdash.entity.Role;
import com.github.alex1304.ultimategdbot.api.Command;
import com.github.alex1304.ultimategdbot.api.CommandFailedException;
import com.github.alex1304.ultimategdbot.api.Context;
import com.github.alex1304.ultimategdbot.api.Plugin;
import com.github.alex1304.ultimategdbot.api.utils.ArgUtils;
import com.github.alex1304.ultimategdbot.gdplugin.GDPlugin;
import com.github.alex1304.ultimategdbot.gdplugin.database.GDLinkedUsers;
import com.github.alex1304.ultimategdbot.gdplugin.database.GDModList;
import com.github.alex1304.ultimategdbot.gdplugin.gdevent.UserDemotedFromElderEvent;
import com.github.alex1304.ultimategdbot.gdplugin.gdevent.UserDemotedFromModEvent;
import com.github.alex1304.ultimategdbot.gdplugin.gdevent.UserPromotedToElderEvent;
import com.github.alex1304.ultimategdbot.gdplugin.gdevent.UserPromotedToModEvent;
import com.github.alex1304.ultimategdbot.gdplugin.util.GDUtils;

import reactor.core.publisher.Mono;

public class CheckModCommand implements Command {
	
	private final GDPlugin plugin;

	public CheckModCommand(GDPlugin plugin) {
		this.plugin = Objects.requireNonNull(plugin);
	}

	@Override
	public Mono<Void> execute(Context ctx) {
		if (ctx.getArgs().size() == 1) {
			final var authorId = ctx.getEvent().getMessage().getAuthor().get().getId().asLong();
			return ctx.getBot().getDatabase().findByID(GDLinkedUsers.class, authorId)
					.filter(GDLinkedUsers::getIsLinkActivated)
					.switchIfEmpty(Mono.error(new CommandFailedException("No user specified. If you want to check your own mod status, "
							+ "link your Geometry Dash account using `" + ctx.getPrefixUsed() + "account` and retry this command. Otherwise, you "
									+ "need to specify a user like so: `" + ctx.getPrefixUsed() + "checkmod <gd_username>`.")))
					.flatMap(linkedUser -> showModStatus(ctx, plugin.getGdClient().getUserByAccountId(linkedUser.getGdAccountId())));
		}
		var input = ArgUtils.concatArgs(ctx, 1);
		return showModStatus(ctx, GDUtils.stringToUser(ctx.getBot(), plugin.getGdClient(), input));
	}
	
	public Mono<Void> showModStatus(Context ctx, Mono<GDUser> userMono) {
		return userMono.flatMap(user -> Mono.zip(ctx.getBot().getEmoji("success"), ctx.getBot().getEmoji("failed"), ctx.getBot().getEmoji("mod"))
				.flatMap(emojis -> ctx.reply("Checking in-game mod status for user **" + user.getName() + "**...\n||"
						+ (user.getRole() == Role.USER
						? emojis.getT2() + " Failed. Nothing found."
						: emojis.getT1() + " Success! Access granted: " + user.getRole()) + "||"))
				.then(ctx.getBot().getDatabase().findByID(GDModList.class, user.getAccountId()))
				.map(Optional::of)
				.defaultIfEmpty(Optional.empty())
				.doOnNext(gdModOptional -> gdModOptional.ifPresentOrElse(gdMod -> {
					if (user.getRole() == Role.USER) {
						plugin.getGdEventDispatcher().dispatch(gdMod.getIsElder() ? new UserDemotedFromElderEvent(user) : new UserDemotedFromModEvent(user));
						ctx.getBot().getDatabase().delete(gdMod).subscribe();
					} else {
						if (user.getRole() == Role.MODERATOR && gdMod.getIsElder()) {
							plugin.getGdEventDispatcher().dispatch(new UserDemotedFromElderEvent(user));
							gdMod.setIsElder(false);
						} else if (user.getRole() == Role.ELDER_MODERATOR && !gdMod.getIsElder()) {
							plugin.getGdEventDispatcher().dispatch(new UserPromotedToElderEvent(user));
							gdMod.setIsElder(true);
						}
						gdMod.setName(user.getName());
						ctx.getBot().getDatabase().save(gdMod).subscribe();
					}
				}, () -> {
					if (user.getRole() == Role.USER) {
						return;
					}
					var isElder = user.getRole() == Role.ELDER_MODERATOR;
					plugin.getGdEventDispatcher().dispatch(isElder ? new UserPromotedToElderEvent(user) : new UserPromotedToModEvent(user));
					var gdMod = new GDModList();
					gdMod.setAccountId(user.getAccountId());
					gdMod.setName(user.getName());
					gdMod.setIsElder(isElder);
					ctx.getBot().getDatabase().save(gdMod).subscribe();
				}))
				.then());
	}
	
	@Override
	public Set<String> getAliases() {
		return Set.of("checkmod");
	}

	@Override
	public String getDescription() {
		return "Checks for the presence of the Moderator badge on someone's profile.";
	}

	@Override
	public String getSyntax() {
		return "<gd_username>";
	}

	@Override
	public String getLongDescription() {
		return "This command displays the mod status as if you pressed the 'REQ' button in-game, but note that it doesn't actually push this button for you.\n"
				+ "It just checks for the presence of the 'M' badge, nothing else.";
	}

	@Override
	public Plugin getPlugin() {
		return plugin;
	}
}
