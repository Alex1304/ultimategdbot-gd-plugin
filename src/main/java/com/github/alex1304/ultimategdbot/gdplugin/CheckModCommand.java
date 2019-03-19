package com.github.alex1304.ultimategdbot.gdplugin;

import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;

import com.github.alex1304.jdash.client.AuthenticatedGDClient;
import com.github.alex1304.jdash.entity.GDUser;
import com.github.alex1304.jdash.entity.Role;
import com.github.alex1304.jdashevents.GDEventDispatcher;
import com.github.alex1304.ultimategdbot.api.Command;
import com.github.alex1304.ultimategdbot.api.CommandFailedException;
import com.github.alex1304.ultimategdbot.api.Context;
import com.github.alex1304.ultimategdbot.api.PermissionLevel;
import com.github.alex1304.ultimategdbot.gdplugin.gdevents.UserDemotedFromElderEvent;
import com.github.alex1304.ultimategdbot.gdplugin.gdevents.UserDemotedFromModEvent;
import com.github.alex1304.ultimategdbot.gdplugin.gdevents.UserPromotedToElderEvent;
import com.github.alex1304.ultimategdbot.gdplugin.gdevents.UserPromotedToModEvent;

import discord4j.core.object.entity.Channel.Type;
import reactor.core.publisher.Mono;

public class CheckModCommand implements Command {
	
	private final AuthenticatedGDClient gdClient;
	private final GDEventDispatcher gdEventDispatcher;

	public CheckModCommand(AuthenticatedGDClient gdClient, GDEventDispatcher gdEventDispatcher) {
		this.gdClient = Objects.requireNonNull(gdClient);
		this.gdEventDispatcher = Objects.requireNonNull(gdEventDispatcher);
	}

	@Override
	public Mono<Void> execute(Context ctx) {
		if (ctx.getArgs().size() == 1) {
			final var authorId = ctx.getEvent().getMessage().getAuthor().get().getId().asLong();
			return ctx.getBot().getDatabase().findByID(GDLinkedUsers.class, authorId)
					.switchIfEmpty(Mono.error(new CommandFailedException("No user specified. If you want to check your own mod status, "
							+ "link your Geometry Dash account using `" + ctx.getPrefixUsed() + "account` and retry this command. Otherwise, you "
									+ "need to specify a user like so: `" + ctx.getPrefixUsed() + "checkmod <gd_username>`.")))
					.flatMap(linkedUser -> showModStatus(ctx, gdClient.getUserByAccountId(linkedUser.getGdAccountId())));
		}
		var input = String.join(" ", ctx.getArgs().subList(1, ctx.getArgs().size()));
		return showModStatus(ctx, gdClient.searchUser(input));
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
						gdEventDispatcher.dispatch(gdMod.getIsElder() ? new UserDemotedFromElderEvent(user) : new UserDemotedFromModEvent(user));
						ctx.getBot().getDatabase().delete(gdMod).subscribe();
					} else {
						if (user.getRole() == Role.MODERATOR && gdMod.getIsElder()) {
							gdEventDispatcher.dispatch(new UserDemotedFromElderEvent(user));
							gdMod.setIsElder(false);
						} else if (user.getRole() == Role.ELDER_MODERATOR && !gdMod.getIsElder()) {
							gdEventDispatcher.dispatch(new UserPromotedToElderEvent(user));
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
					gdEventDispatcher.dispatch(isElder ? new UserPromotedToElderEvent(user) : new UserPromotedToModEvent(user));
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
	public Set<Command> getSubcommands() {
		return Set.of();
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
	public PermissionLevel getPermissionLevel() {
		return PermissionLevel.PUBLIC;
	}

	@Override
	public EnumSet<Type> getChannelTypesAllowed() {
		return EnumSet.of(Type.GUILD_TEXT, Type.DM);
	}

	@Override
	public Map<Class<? extends Throwable>, BiConsumer<Throwable, Context>> getErrorActions() {
		return GDUtils.DEFAULT_GD_ERROR_ACTIONS;
	}
}
