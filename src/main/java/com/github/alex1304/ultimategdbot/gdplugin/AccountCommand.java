package com.github.alex1304.ultimategdbot.gdplugin;

import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;

import com.github.alex1304.jdash.client.AuthenticatedGDClient;
import com.github.alex1304.ultimategdbot.api.Command;
import com.github.alex1304.ultimategdbot.api.Context;
import com.github.alex1304.ultimategdbot.api.PermissionLevel;
import com.github.alex1304.ultimategdbot.api.utils.Utils;

import discord4j.core.object.entity.Channel.Type;
import reactor.core.publisher.Mono;

public class AccountCommand implements Command {
	
	private final AuthenticatedGDClient gdClient;
	private final AccountLinkCommand linkSubcmd;
	
	public AccountCommand(AuthenticatedGDClient gdClient) {
		this.gdClient = Objects.requireNonNull(gdClient);
		this.linkSubcmd = new AccountLinkCommand(gdClient);
	}

	@Override
	public Mono<Void> execute(Context ctx) {
		final var authorId = ctx.getEvent().getMessage().getAuthor().get().getId().asLong();
		final var alias = Utils.joinAliases(getAliases());
		final var subalias = Utils.joinAliases(linkSubcmd.getAliases());
		return ctx.getEffectivePrefix().flatMap(prefix -> ctx.getBot().getDatabase().findByID(GDLinkedUsers.class, authorId)
				.filter(GDLinkedUsers::getIsLinkActivated)
				.flatMap(linkedUser -> gdClient.getUserByAccountId(linkedUser.getGdAccountId()))
				.map(user -> "You are currently linked to the Geometry Dash account **" + user.getName() + "**!")
				.defaultIfEmpty("You are not yet linked to any Geometry Dash account!")
				.flatMap(message -> ctx.reply("You can link your Discord account with your Geometry Dash account "
						+ "to get access to cool stuff in UltimateGDBot. You can for example use the `profile` "
						+ "command without arguments to display your own info, let others easily access your "
						+ "profile by mentionning you, or appear in server-wide Geometry Dash leaderboards.\n\n"
						+ message + "\n" + "To start linking your account, use `" + prefix + alias + " " + subalias
						+ " <your_gd_username>`")))
				.then();
	}

	@Override
	public Set<String> getAliases() {
		return Set.of("account");
	}

	@Override
	public Set<Command> getSubcommands() {
		return Set.of(linkSubcmd);
	}

	@Override
	public String getDescription() {
		return "Allows you to manage your connection with your Geometry Dash account.";
	}

	@Override
	public String getSyntax() {
		return "";
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
