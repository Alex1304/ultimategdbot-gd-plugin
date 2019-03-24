package com.github.alex1304.ultimategdbot.gdplugin;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;

import com.github.alex1304.jdash.client.AuthenticatedGDClient;
import com.github.alex1304.jdashevents.GDEventDispatcher;
import com.github.alex1304.jdashevents.GDEventScannerLoop;
import com.github.alex1304.ultimategdbot.api.Command;
import com.github.alex1304.ultimategdbot.api.Context;
import com.github.alex1304.ultimategdbot.api.InvalidSyntaxException;
import com.github.alex1304.ultimategdbot.api.PermissionLevel;

import discord4j.core.object.entity.Channel.Type;
import discord4j.core.object.entity.Message;
import reactor.core.publisher.Mono;

public class GDEventsCommand implements Command {
	
	private final AuthenticatedGDClient gdClient;
	private final GDEventDispatcher gdEventDispatcher;
	private final GDEventScannerLoop scannerLoop;
	private final Map<Long, List<Message>> broadcastedLevels;

	public GDEventsCommand(AuthenticatedGDClient gdClient, GDEventDispatcher gdEventDispatcher, GDEventScannerLoop scannerLoop,
			Map<Long, List<Message>> broadcastedLevels) {
		this.gdClient = Objects.requireNonNull(gdClient);
		this.gdEventDispatcher = Objects.requireNonNull(gdEventDispatcher);
		this.scannerLoop = Objects.requireNonNull(scannerLoop);
		this.broadcastedLevels = Objects.requireNonNull(broadcastedLevels);
	}

	@Override
	public Mono<Void> execute(Context ctx) {
		return Mono.error(new InvalidSyntaxException(this));
	}

	@Override
	public Set<String> getAliases() {
		return Set.of("gdevents");
	}

	@Override
	public Set<Command> getSubcommands() {
		return Set.of(new GDEventsDispatchCommand(gdClient, gdEventDispatcher), new GDEventsScannerLoopCommand(scannerLoop),
				new GDEventsBroadcastResultsCommand(broadcastedLevels));
	}

	@Override
	public String getDescription() {
		return "Allows the bot owner to manage the GD event dispatcher.";
	}

	@Override
	public String getLongDescription() {
		return "This is particularly useful when the bot fails to detect some events, this allows you to dispatch them manually.\n"
				+ "You can also view the results of previous broadcasts, and start/stop the event scanner loop.";
	}

	@Override
	public String getSyntax() {
		return "";
	}

	@Override
	public PermissionLevel getPermissionLevel() {
		return PermissionLevel.BOT_OWNER;
	}

	@Override
	public EnumSet<Type> getChannelTypesAllowed() {
		return EnumSet.of(Type.GUILD_TEXT, Type.DM);
	}

	@Override
	public Map<Class<? extends Throwable>, BiConsumer<Throwable, Context>> getErrorActions() {
		return Map.of();
	}

}
