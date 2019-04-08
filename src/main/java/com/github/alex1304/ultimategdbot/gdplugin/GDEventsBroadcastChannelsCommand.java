package com.github.alex1304.ultimategdbot.gdplugin;

import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

import com.github.alex1304.ultimategdbot.api.Command;
import com.github.alex1304.ultimategdbot.api.CommandFailedException;
import com.github.alex1304.ultimategdbot.api.Context;
import com.github.alex1304.ultimategdbot.api.PermissionLevel;
import com.github.alex1304.ultimategdbot.api.utils.ArgUtils;

import discord4j.core.object.entity.Channel.Type;
import discord4j.core.object.util.Snowflake;
import reactor.core.publisher.Mono;

public class GDEventsBroadcastChannelsCommand implements Command {
	
	private final ChannelLoader channelLoader;

	public GDEventsBroadcastChannelsCommand(ChannelLoader channelLoader) {
		this.channelLoader = Objects.requireNonNull(channelLoader);
	}

	@Override
	public Mono<Void> execute(Context ctx) {
		var errorMessage = "You need to provide one of those arguments: `preload`, `unload` or `purge_unused`.\n"
				+ "Read `" + ctx.getPrefixUsed() + "help gdevents broadcast_channels` for documentation.";
		ArgUtils.requireMinimumArgCount(ctx, 2, errorMessage);
		switch (ctx.getArgs().get(1).toLowerCase()) {
			case "preload":
				return ctx.reply("Processing...")
						.flatMap(wait -> GDUtils.preloadBroadcastChannels(ctx.getBot(), channelLoader)
								.flatMap(count -> ctx.reply("Sucessfully preloaded " + count + " channels!"))
								.then(wait.delete()));
			case "unload":
				channelLoader.clearCache();
				return ctx.reply("Broadcast channels have been unloaded.").then();
			case "purge_unused":
				if (channelLoader.getInvalidChannelSnowflakes().isEmpty()) {
					return Mono.error(new CommandFailedException("You need to preload channels first."));
				}
				return ctx.reply("Processing...")
						.flatMap(wait -> GDUtils.getExistingSubscribedGuilds(ctx.getBot(), "").collectList()
								.flatMap(subscribedList -> ctx.getBot().getDatabase().performTransaction(session -> {
									var invalidIds = channelLoader.getInvalidChannelSnowflakes().stream()
											.map(Snowflake::asLong)
											.collect(Collectors.toSet());
									var updatedCount = 0;
									for (var subscribedGuild : subscribedList) {
										var updated = false;
										if (invalidIds.contains(subscribedGuild.getChannelAwardedLevelsId())) {
											subscribedGuild.setChannelAwardedLevelsId(0);
											updated = true;
										}
										if (invalidIds.contains(subscribedGuild.getChannelGdModeratorsId())) {
											subscribedGuild.setChannelGdModeratorsId(0);
											updated = true;
										}
										if (invalidIds.contains(subscribedGuild.getChannelTimelyLevelsId())) {
											subscribedGuild.setChannelTimelyLevelsId(0);
											updated = true;
										}
										if (invalidIds.contains(subscribedGuild.getChannelChangelogId())) {
											subscribedGuild.setChannelChangelogId(0);
											updated = true;
										}
										if (updated) {
											session.saveOrUpdate(subscribedGuild);
											updatedCount++;
										}
									}
									return updatedCount;
								}))
								.flatMap(updatedCount -> ctx.reply("Successfully cleaned up invalid broadcast channels for "
										+ updatedCount + " guilds!"))
								.then(wait.delete()));	
			default:
				return Mono.error(new CommandFailedException(errorMessage));
		}
	}

	@Override
	public Set<String> getAliases() {
		return Set.of("broadcast_channels");
	}

	@Override
	public Set<Command> getSubcommands() {
		return Set.of();
	}

	@Override
	public String getDescription() {
		return "Perform actions on GD events broadcast channels.";
	}

	@Override
	public String getLongDescription() {
		return "In order to improve performances when broadcasting GD events to servers, the bot first need to "
				+ "preload all channels that are configured to receive the announcements. This is usually done "
				+ "on bot startup, but it can also be done manually via this command with the `preload` argument.\n"
				+ "As opposed to preloading channels, you can also unload all channels using the `unload` argument.\n"
				+ "Finally, you can also use `purge_unused` in order to remove from database all channels that are "
				+ "invalid or deleted. This can decrease the processing time for future invocations of the preload "
				+ "process.";
	}

	@Override
	public String getSyntax() {
		return "preload|unload|purge_unused";
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
		return GDUtils.DEFAULT_GD_ERROR_ACTIONS;
	}

}
