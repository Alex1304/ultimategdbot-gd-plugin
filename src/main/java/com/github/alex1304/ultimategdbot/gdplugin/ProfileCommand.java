package com.github.alex1304.ultimategdbot.gdplugin;

import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;

import com.github.alex1304.jdash.client.AuthenticatedGDClient;
import com.github.alex1304.jdash.entity.GDUser;
import com.github.alex1304.jdash.graphics.SpriteFactory;
import com.github.alex1304.jdash.util.GDUserIconSet;
import com.github.alex1304.ultimategdbot.api.Command;
import com.github.alex1304.ultimategdbot.api.CommandFailedException;
import com.github.alex1304.ultimategdbot.api.Context;
import com.github.alex1304.ultimategdbot.api.PermissionLevel;

import discord4j.core.object.entity.Channel.Type;
import reactor.core.publisher.Mono;

public class ProfileCommand implements Command {
	
	private final AuthenticatedGDClient gdClient;
	private final SpriteFactory spriteFactory;
	private final Map<GDUserIconSet, String[]> iconsCache;

	public ProfileCommand(AuthenticatedGDClient gdClient, SpriteFactory spriteFactory, Map<GDUserIconSet, String[]> iconsCache) {
		this.gdClient = Objects.requireNonNull(gdClient);
		this.spriteFactory = Objects.requireNonNull(spriteFactory);
		this.iconsCache = Objects.requireNonNull(iconsCache);
	}

	@Override
	public Mono<Void> execute(Context ctx) {
		if (ctx.getArgs().size() == 1) {
			final var authorId = ctx.getEvent().getMessage().getAuthor().get().getId().asLong();
			return ctx.getBot().getDatabase().findByID(GDLinkedUsers.class, authorId)
					.switchIfEmpty(Mono.error(new CommandFailedException("No user specified. If you want to show your own profile, "
							+ "link your Geometry Dash account using `" + ctx.getPrefixUsed() + "account` and retry this command. Otherwise, you "
									+ "need to specify a user like so: `" + ctx.getPrefixUsed() + "profile <gd_username>`.")))
					.flatMap(linkedUser -> showProfile(ctx, gdClient.getUserByAccountId(linkedUser.getGdAccountId())));
		}
		var input = String.join(" ", ctx.getArgs().subList(1, ctx.getArgs().size()));
		return showProfile(ctx, gdClient.searchUser(input));
	}
	
	public Mono<Void> showProfile(Context ctx, Mono<GDUser> userMono) {
		return userMono.flatMap(user -> GDUtils.makeIconSet(ctx.getBot(), user, spriteFactory, iconsCache)
				.flatMap(urls -> GDUtils.userProfileView(ctx.getBot(), ctx.getEvent().getMessage().getAuthor(), user,
								"User profile", "https://i.imgur.com/ppg4HqJ.png", urls[0], urls[1])
						.flatMap(view -> ctx.reply(view))))
				.then();
	}

	@Override
	public Set<String> getAliases() {
		return Set.of("profile");
	}

	@Override
	public Set<Command> getSubcommands() {
		return Set.of();
	}

	@Override
	public String getDescription() {
		return "Fetches a user's GD profile and displays information on it.";
	}

	@Override
	public String getSyntax() {
		return "<username_or_playerID>";
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
