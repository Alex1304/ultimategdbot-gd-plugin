package com.github.alex1304.ultimategdbot.gdplugin;

import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;

import com.github.alex1304.jdash.client.AuthenticatedGDClient;
import com.github.alex1304.jdash.graphics.SpriteFactory;
import com.github.alex1304.jdash.util.GDUserIconSet;
import com.github.alex1304.ultimategdbot.api.Command;
import com.github.alex1304.ultimategdbot.api.Context;
import com.github.alex1304.ultimategdbot.api.InvalidSyntaxException;
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
		if (ctx.getArgs().size() < 2) {
			return Mono.error(new InvalidSyntaxException(this));
		}
		var input = String.join(" ", ctx.getArgs().subList(1, ctx.getArgs().size()));
		return gdClient.searchUser(input)
				.flatMap(user -> GDUtils.makeIconSet(ctx, user, spriteFactory, iconsCache)
						.flatMap(urls -> ctx.reply(GDUtils.userProfileView(ctx, user, urls[0], urls[1]))))
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
