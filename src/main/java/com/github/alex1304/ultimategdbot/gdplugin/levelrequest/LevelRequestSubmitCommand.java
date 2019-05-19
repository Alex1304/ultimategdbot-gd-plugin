package com.github.alex1304.ultimategdbot.gdplugin.levelrequest;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import com.github.alex1304.ultimategdbot.api.Command;
import com.github.alex1304.ultimategdbot.api.Context;
import com.github.alex1304.ultimategdbot.api.Plugin;
import com.github.alex1304.ultimategdbot.gdplugin.GDPlugin;
import com.github.alex1304.ultimategdbot.gdplugin.util.LevelRequestUtils;

import discord4j.core.object.entity.Channel.Type;
import reactor.core.publisher.Mono;

public class LevelRequestSubmitCommand implements Command {
	
	private final GDPlugin plugin;
	
	public LevelRequestSubmitCommand(GDPlugin plugin) {
		this.plugin = Objects.requireNonNull(plugin);
	}

	@Override
	public Mono<Void> execute(Context ctx) {
		var isOpening = new AtomicBoolean();
		return LevelRequestUtils.getLevelRequestsSettings(ctx)
				.doOnNext(lvlReqSettings -> isOpening.set(!lvlReqSettings.getIsOpen()))
				.doOnNext(lvlReqSettings -> lvlReqSettings.setIsOpen(isOpening.get()))
				.flatMap(ctx.getBot().getDatabase()::save)
				.then(Mono.defer(() -> ctx.reply("Level requests are now " + (isOpening.get() ? "opened" : "closed") + "!")))
				.then();
	}

	@Override
	public Set<String> getAliases() {
		return Set.of("toggle");
	}

	@Override
	public String getDescription() {
		return "Enable or disable level requests for this server.";
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
	public EnumSet<Type> getChannelTypesAllowed() {
		return EnumSet.of(Type.GUILD_TEXT);
	}

	@Override
	public Plugin getPlugin() {
		return plugin;
	}
}
