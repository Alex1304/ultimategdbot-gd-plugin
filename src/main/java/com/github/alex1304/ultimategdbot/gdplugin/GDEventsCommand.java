package com.github.alex1304.ultimategdbot.gdplugin;

import java.util.Objects;
import java.util.Set;

import com.github.alex1304.ultimategdbot.api.Command;
import com.github.alex1304.ultimategdbot.api.Context;
import com.github.alex1304.ultimategdbot.api.InvalidSyntaxException;
import com.github.alex1304.ultimategdbot.api.PermissionLevel;
import com.github.alex1304.ultimategdbot.api.Plugin;

import reactor.core.publisher.Mono;

public class GDEventsCommand implements Command {
	
	private final GDPlugin plugin;

	public GDEventsCommand(GDPlugin plugin) {
		this.plugin = Objects.requireNonNull(plugin);
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
		return Set.of(new GDEventsDispatchCommand(plugin), new GDEventsScannerLoopCommand(plugin),
				new GDEventsBroadcastResultsCommand(plugin),
				new GDEventsChannelsAndRolesCommand(plugin));
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
	public Plugin getPlugin() {
		return plugin;
	}
}
