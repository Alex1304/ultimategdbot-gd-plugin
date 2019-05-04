package com.github.alex1304.ultimategdbot.gdplugin;

import java.util.Objects;
import java.util.Set;

import com.github.alex1304.ultimategdbot.api.Command;
import com.github.alex1304.ultimategdbot.api.Context;
import com.github.alex1304.ultimategdbot.api.PermissionLevel;
import com.github.alex1304.ultimategdbot.api.Plugin;

import reactor.core.publisher.Mono;

public class ClearCacheCommand implements Command {
	
	private final GDPlugin plugin;

	public ClearCacheCommand(GDPlugin plugin) {
		this.plugin = Objects.requireNonNull(plugin);
	}

	@Override
	public Mono<Void> execute(Context ctx) {
		plugin.getGdClient().clearCache();
		return ctx.reply("GD client cache has been cleared.").then();
	}

	@Override
	public Set<String> getAliases() {
		return Set.of("cleargdcache");
	}

	@Override
	public String getDescription() {
		return "Clears the cache of the HTTP client used to make requests to Geometry Dash servers";
	}

	@Override
	public String getLongDescription() {
		return "";
	}

	@Override
	public String getSyntax() {
		return "";
	}

	@Override
	public PermissionLevel getPermissionLevel() {
		return PermissionLevel.BOT_ADMIN;
	}

	@Override
	public Plugin getPlugin() {
		return plugin;
	}
}
