package com.github.alex1304.ultimategdbot.gdplugin.gdevent;

import java.util.Objects;
import java.util.Set;

import com.github.alex1304.ultimategdbot.api.Command;
import com.github.alex1304.ultimategdbot.api.CommandFailedException;
import com.github.alex1304.ultimategdbot.api.Context;
import com.github.alex1304.ultimategdbot.api.InvalidSyntaxException;
import com.github.alex1304.ultimategdbot.api.PermissionLevel;
import com.github.alex1304.ultimategdbot.api.Plugin;
import com.github.alex1304.ultimategdbot.api.utils.ArgUtils;
import com.github.alex1304.ultimategdbot.api.utils.BotUtils;
import com.github.alex1304.ultimategdbot.gdplugin.GDPlugin;

import discord4j.core.object.entity.Channel;
import reactor.core.publisher.Mono;

public class GDEventsBroadcastResultsCommand implements Command {
	
	private final GDPlugin plugin;

	public GDEventsBroadcastResultsCommand(GDPlugin plugin) {
		this.plugin = Objects.requireNonNull(plugin);
	}

	@Override
	public Mono<Void> execute(Context ctx) {
		ArgUtils.requireMinimumArgCount(ctx, 2);
		switch (ctx.getArgs().get(1)) {
			case "view":
				var sb = new StringBuilder("__**GD events broadcast results:**__\n")
						.append( "Data below is collected in order to have the ability to edit previous announcement messages")
						.append( " in case an **Awarded Level Updated** event is dispatched.\n")
						.append( "Only the last 10 results are saved here. Older ones automatically get deleted in order to ")
						.append("save resources and avoid memory leaks.\n\n");
				if (plugin.getBroadcastedLevels().isEmpty()) {
					sb.append("There is nothing here yet!");
				}
				plugin.getBroadcastedLevels().forEach((k, v) -> sb.append("LevelID **")
						.append(k).append("** => **").append(v.size()).append("** messages sent\n"));
				return BotUtils.sendMultipleSimpleMessagesToOneChannel(ctx.getEvent().getMessage().getChannel()
						.cast(Channel.class), BotUtils.chunkMessage(sb.toString()))
						.then();
			case "clear":
				plugin.getBroadcastedLevels().clear();
				return ctx.getBot().getEmoji("success").flatMap(emoji -> ctx.reply(emoji + " Broadcast results cleared!")).then();
			case "remove":
				var fail = new CommandFailedException("Please give a valid levelID present in the broadcast results");
				if (ctx.getArgs().size() == 2) {
					return Mono.error(fail);
				}
				try {
					var key = Long.parseLong(ctx.getArgs().get(2));
					if (!plugin.getBroadcastedLevels().containsKey(key)) {
						return Mono.error(new CommandFailedException("Entry not found."));
					}
					plugin.getBroadcastedLevels().remove(key);
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
	public String getDescription() {
		return "View or clear the GD events broadcast results.";
	}

	@Override
	public String getLongDescription() {
		return "";
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
	public Plugin getPlugin() {
		return plugin;
	}
}
