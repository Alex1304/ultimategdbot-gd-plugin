package com.github.alex1304.ultimategdbot.gdplugin;

import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;

import com.github.alex1304.ultimategdbot.api.Command;
import com.github.alex1304.ultimategdbot.api.Context;
import com.github.alex1304.ultimategdbot.api.PermissionLevel;
import com.github.alex1304.ultimategdbot.gdplugin.gdevents.GDEventSubscriber;

import discord4j.core.object.entity.Channel.Type;
import reactor.core.publisher.Mono;

public class GDEventsReleaseNextCommand implements Command {
	
	private final GDEventSubscriber subscriber;

	public GDEventsReleaseNextCommand(GDEventSubscriber subscriber) {
		this.subscriber = Objects.requireNonNull(subscriber);
	}

	@Override
	public Mono<Void> execute(Context ctx) {
		subscriber.requestNext();
		return ctx.reply("Next GD event will be processed immediately upon receipt.").then();
	}

	@Override
	public Set<String> getAliases() {
		return Set.of("release_next");
	}

	@Override
	public Set<Command> getSubcommands() {
		return Set.of();
	}

	@Override
	public String getDescription() {
		return "Forces the next GD event in queue to be broadcast even if one is already in progress.";
	}

	@Override
	public String getLongDescription() {
		return "By default, the bot will wait for an event to finish broadcasting before broadcasting the next one. "
				+ "This command allows to force the bot to broadcast next event even if the previous one hasn't finished. "
				+ "May be useful if an event is stuck and never finishes, case which the bot would be waiting indefinitely and "
				+ "basically locking down the event queue.";
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
