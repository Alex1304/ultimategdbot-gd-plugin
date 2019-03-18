package com.github.alex1304.ultimategdbot.gdplugin;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;

import com.github.alex1304.ultimategdbot.api.Command;
import com.github.alex1304.ultimategdbot.api.CommandFailedException;
import com.github.alex1304.ultimategdbot.api.Context;
import com.github.alex1304.ultimategdbot.api.InvalidSyntaxException;
import com.github.alex1304.ultimategdbot.api.PermissionLevel;
import com.github.alex1304.ultimategdbot.api.utils.BotUtils;

import discord4j.core.object.entity.Channel;
import discord4j.core.object.entity.Message;
import discord4j.core.object.entity.Channel.Type;
import reactor.core.publisher.Mono;

public class GDEventsBroadcastResultsCommand implements Command {
	
	private final Map<Long, List<Message>> broadcastedLevels;

	public GDEventsBroadcastResultsCommand(Map<Long, List<Message>> broadcastedLevels) {
		this.broadcastedLevels = Objects.requireNonNull(broadcastedLevels);
	}

	@Override
	public Mono<Void> execute(Context ctx) {
		if (ctx.getArgs().size() == 1) {
			return Mono.error(new InvalidSyntaxException(this));
		}
		switch (ctx.getArgs().get(1)) {
			case "view":
				var sb = new StringBuilder("__**GD events broadcast results:**__\n"
						).append( "Data below is collected in order to have the ability to edit previous announcement messages"
						).append( " in case an **Awarded Level Updated** event is dispatched.\n"
						).append( "You can clear this at anytime in order to free resources and avoid memory leaks.\n\n");
				if (broadcastedLevels.isEmpty()) {
					sb.append("*(No data)*");
				}
				broadcastedLevels.forEach((k, v) -> sb.append("LevelID **")
						.append(k).append("** => **").append(v.size()).append("** messages sent\n"));
				return BotUtils.sendMultipleSimpleMessagesToOneChannel(ctx.getEvent().getMessage().getChannel()
						.cast(Channel.class), BotUtils.chunkMessage(sb.toString()))
						.then();
			case "clear":
				broadcastedLevels.clear();
				return ctx.getBot().getEmoji("success").flatMap(emoji -> ctx.reply(emoji + " Broadcast results cleared!")).then();
			case "remove":
				var fail = new CommandFailedException("Please give a valid levelID present in the broadcast results");
				if (ctx.getArgs().size() == 2) {
					return Mono.error(fail);
				}
				try {
					var key = Long.parseLong(ctx.getArgs().get(2));
					if (!broadcastedLevels.containsKey(key)) {
						return Mono.error(new CommandFailedException("Entry not found."));
					}
					broadcastedLevels.remove(key);
					return ctx.getBot().getEmoji("success").flatMap(emoji -> ctx.reply(emoji + " Data for " + key + " has been removed."))
							.then();
				} catch (NumberFormatException e) {
					return Mono.error(fail);
				}
			default:
				return Mono.error(new InvalidSyntaxException(this));
		}
	}

	@Override
	public Set<String> getAliases() {
		return Set.of("broadcast_results");
	}

	@Override
	public Set<Command> getSubcommands() {
		return Set.of();
	}

	@Override
	public String getDescription() {
		return "View or clear the GD events broadcast results.";
	}

	@Override
	public String getSyntax() {
		return "view|clear|remove <id>";
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
