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

import discord4j.core.object.entity.Channel.Type;
import reactor.core.publisher.Mono;

public class LeaderboardCommand implements Command {
	
	private final AuthenticatedGDClient gdClient;
	
	public LeaderboardCommand(AuthenticatedGDClient gdClient) {
		this.gdClient = Objects.requireNonNull(gdClient);
	}

	@Override
	public Mono<Void> execute(Context ctx) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Set<String> getAliases() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Set<Command> getSubcommands() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String getDescription() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String getSyntax() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public PermissionLevel getPermissionLevel() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public EnumSet<Type> getChannelTypesAllowed() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Map<Class<? extends Throwable>, BiConsumer<Throwable, Context>> getErrorActions() {
		// TODO Auto-generated method stub
		return null;
	}

}
