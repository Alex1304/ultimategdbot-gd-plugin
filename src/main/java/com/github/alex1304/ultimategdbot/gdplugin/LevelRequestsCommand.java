package com.github.alex1304.ultimategdbot.gdplugin;

import java.util.Objects;
import java.util.Set;

import com.github.alex1304.ultimategdbot.api.Command;
import com.github.alex1304.ultimategdbot.api.Context;
import com.github.alex1304.ultimategdbot.api.Plugin;

import reactor.core.publisher.Mono;

public class LevelRequestsCommand implements Command {
	
	private final GDPlugin plugin;
	
	public LevelRequestsCommand(GDPlugin plugin) {
		this.plugin = Objects.requireNonNull(plugin);
	}

	@Override
	public Mono<Void> execute(Context ctx) {
		return ctx.reply("**__Get other players to play your level and give feedback with the Level Request feature!__**")
				.then();
	}

	@Override
	public Set<String> getAliases() {
		return Set.of("levelrequests", "lvlreqs");
	}

	@Override
	public String getDescription() {
		return "";
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
	public Plugin getPlugin() {
		return plugin;
	}
}
