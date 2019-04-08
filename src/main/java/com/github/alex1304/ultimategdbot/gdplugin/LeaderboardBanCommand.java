package com.github.alex1304.ultimategdbot.gdplugin;

import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;

import com.github.alex1304.jdash.client.AuthenticatedGDClient;
import com.github.alex1304.ultimategdbot.api.Command;
import com.github.alex1304.ultimategdbot.api.CommandFailedException;
import com.github.alex1304.ultimategdbot.api.Context;
import com.github.alex1304.ultimategdbot.api.PermissionLevel;
import com.github.alex1304.ultimategdbot.api.utils.ArgUtils;

import discord4j.core.object.entity.Channel.Type;
import discord4j.core.object.entity.User;
import discord4j.core.object.util.Snowflake;
import reactor.core.publisher.Mono;

public class LeaderboardBanCommand implements Command {
	
	private final AuthenticatedGDClient gdClient;
	
	public LeaderboardBanCommand(AuthenticatedGDClient gdClient) {
		this.gdClient = Objects.requireNonNull(gdClient);
	}

	@Override
	public Mono<Void> execute(Context ctx) {
		ArgUtils.requireMinimumArgCount(ctx, 2);
		return GDUtils.stringToUser(ctx.getBot(), gdClient, ArgUtils.concatArgs(ctx, 1))
				.flatMap(gdUser -> ctx.getBot().getDatabase().findByID(GDLeaderboardBans.class, gdUser.getAccountId())
						.flatMap(__ -> Mono.error(new CommandFailedException("This user is already banned.")))
						.then(Mono.just(new GDLeaderboardBans())
								.doOnNext(newBan -> newBan.setAccountId(gdUser.getAccountId()))
								.doOnNext(newBan -> newBan.setBannedBy(ctx.getEvent().getMessage().getAuthor()
										.map(User::getId)
										.map(Snowflake::asLong)
										.orElse(0L)))
								.flatMap(ctx.getBot().getDatabase()::save))
						.then(ctx.reply("**" + gdUser.getName() + "** is now banned from leaderboards!")))
				.then();
	}

	@Override
	public Set<String> getAliases() {
		return Set.of("ban");
	}

	@Override
	public Set<Command> getSubcommands() {
		return Set.of();
	}

	@Override
	public String getDescription() {
		return "Bans a player from server leaderboards.";
	}

	@Override
	public String getLongDescription() {
		return "Players that are banned from leaderboards won't be displayed in the results of the `leaderboard` command in any server, "
				+ "regardless of whether they have an account linked. Bans are per GD account and not per Discord account, so linking "
				+ "with a different Discord account does not allow ban evasion. This command allows you to add someone to the ban list.";
	}

	@Override
	public String getSyntax() {
		return "<gd_name>";
	}

	@Override
	public PermissionLevel getPermissionLevel() {
		return PermissionLevel.BOT_ADMIN;
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
