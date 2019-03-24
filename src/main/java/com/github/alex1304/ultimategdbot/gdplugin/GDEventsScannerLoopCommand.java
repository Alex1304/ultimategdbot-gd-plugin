package com.github.alex1304.ultimategdbot.gdplugin;

import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;

import com.github.alex1304.jdashevents.GDEventScannerLoop;
import com.github.alex1304.ultimategdbot.api.Command;
import com.github.alex1304.ultimategdbot.api.Context;
import com.github.alex1304.ultimategdbot.api.InvalidSyntaxException;
import com.github.alex1304.ultimategdbot.api.PermissionLevel;
import com.github.alex1304.ultimategdbot.api.utils.ArgUtils;

import discord4j.core.object.entity.Channel.Type;
import reactor.core.publisher.Mono;

public class GDEventsScannerLoopCommand implements Command {
	
	private final GDEventScannerLoop scannerLoop;

	public GDEventsScannerLoopCommand(GDEventScannerLoop scannerLoop) {
		this.scannerLoop = Objects.requireNonNull(scannerLoop);
	}

	@Override
	public Mono<Void> execute(Context ctx) {
		ArgUtils.requireMinimumArgCount(ctx, 2);
		switch (ctx.getArgs().get(1)) {
			case "start":
				scannerLoop.start();
				return ctx.reply("GD event scanner loop has been started.").then();
			case "stop":
				scannerLoop.stop();
				return ctx.reply("GD event scanner loop has been stopped.").then();
			default:
				return Mono.error(new InvalidSyntaxException(this));
		}
	}

	@Override
	public Set<String> getAliases() {
		return Set.of("scanner_loop");
	}

	@Override
	public Set<Command> getSubcommands() {
		return Set.of();
	}

	@Override
	public String getDescription() {
		return "Starts or stops the GD event scanner loop. If stopped, GD events won't be dispatched automatically when they happen in game.";
	}

	@Override
	public String getLongDescription() {
		return "";
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
	public EnumSet<Type> getChannelTypesAllowed() {
		return EnumSet.of(Type.GUILD_TEXT, Type.DM);
	}

	@Override
	public Map<Class<? extends Throwable>, BiConsumer<Throwable, Context>> getErrorActions() {
		return Map.of();
	}

}
