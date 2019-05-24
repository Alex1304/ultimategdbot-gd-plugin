package com.github.alex1304.ultimategdbot.gdplugin.gdevent;

import java.util.Objects;
import java.util.Set;

import com.github.alex1304.ultimategdbot.api.Command;
import com.github.alex1304.ultimategdbot.api.Context;
import com.github.alex1304.ultimategdbot.api.InvalidSyntaxException;
import com.github.alex1304.ultimategdbot.api.PermissionLevel;
import com.github.alex1304.ultimategdbot.api.Plugin;
import com.github.alex1304.ultimategdbot.api.utils.ArgUtils;
import com.github.alex1304.ultimategdbot.gdplugin.GDPlugin;

import reactor.core.publisher.Mono;

public class GDEventsScannerLoopCommand implements Command {
	
	private final GDPlugin plugin;

	public GDEventsScannerLoopCommand(GDPlugin plugin) {
		this.plugin = Objects.requireNonNull(plugin);
	}

	@Override
	public Mono<Void> execute(Context ctx) {
		ArgUtils.requireMinimumArgCount(ctx, 2);
		switch (ctx.getArgs().get(1)) {
			case "start":
				return Mono.fromRunnable(plugin.getScannerLoop()::start)
						.then(ctx.reply("GD event scanner loop has been started."))
						.then();
			case "stop":
				return Mono.fromRunnable(plugin.getScannerLoop()::stop)
						.then(ctx.reply("GD event scanner loop has been stopped."))
						.then();
			default:
				return Mono.error(new InvalidSyntaxException(this));
		}
	}

	@Override
	public Set<String> getAliases() {
		return Set.of("scanner_loop");
	}

	@Override
	public String getDescription() {
		return "Starts or stops the GD event scanner loop.";
	}

	@Override
	public String getLongDescription() {
		return "If stopped, GD events will no longer be dispatched automatically when they happen in game.";
	}

	@Override
	public String getSyntax() {
		return "start|stop";
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
