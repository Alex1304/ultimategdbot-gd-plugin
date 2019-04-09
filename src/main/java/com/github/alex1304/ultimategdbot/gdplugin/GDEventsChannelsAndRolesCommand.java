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

public class GDEventsChannelsAndRolesCommand implements Command {
	
	private final BroadcastPreloader preloader;

	public GDEventsChannelsAndRolesCommand(BroadcastPreloader preloader) {
		this.preloader = Objects.requireNonNull(preloader);
	}

	@Override
	public Mono<Void> execute(Context ctx) {
		var errorMessage = "You need to provide one of those arguments: `preload`, `unload` or `purge_unused`.\n"
				+ "Read `" + ctx.getPrefixUsed() + "help gdevents channels_and_roles` for documentation.";
		ArgUtils.requireMinimumArgCount(ctx, 2, errorMessage);
		switch (ctx.getArgs().get(1).toLowerCase()) {
			case "preload":
				return ctx.reply("Processing...")
						.flatMap(wait -> GDUtils.preloadBroadcastChannelsAndRoles(ctx.getBot(), preloader)
								.flatMap(count -> ctx.reply("Sucessfully preloaded **" + count.getT1() + "** channels and **" + count.getT2() + "** roles!"))
								.then(wait.delete()));
			case "unload":
				preloader.unload();
				return ctx.reply("Broadcast channels and roles have been unloaded.").then();
			case "purge_unused":
				if (preloader.getInvalidChannelSnowflakes().isEmpty() && preloader.getInvalidRoleSnowflakes().isEmpty()) {
					return Mono.error(new CommandFailedException("Nothing to clean. Maybe try preloading again first?"));
				}
				return ctx.reply("Processing...")
						.flatMap(wait -> GDUtils.getExistingSubscribedGuilds(ctx.getBot(), "").collectList()
								.flatMap(subscribedList -> ctx.getBot().getDatabase().performTransaction(session -> {
									var invalidChannels = preloader.getInvalidChannelSnowflakes().stream()
											.map(Snowflake::asLong)
											.collect(Collectors.toSet());
									var invalidRoles = preloader.getInvalidRoleSnowflakes().stream()
											.map(Snowflake::asLong)
											.collect(Collectors.toSet());
									var updatedCount = 0;
									for (var subscribedGuild : subscribedList) {
										var updated = false;
										if (invalidChannels.contains(subscribedGuild.getChannelAwardedLevelsId())) {
											subscribedGuild.setChannelAwardedLevelsId(0);
											updated = true;
										}
										if (invalidChannels.contains(subscribedGuild.getChannelGdModeratorsId())) {
											subscribedGuild.setChannelGdModeratorsId(0);
											updated = true;
										}
										if (invalidChannels.contains(subscribedGuild.getChannelTimelyLevelsId())) {
											subscribedGuild.setChannelTimelyLevelsId(0);
											updated = true;
										}
										if (invalidChannels.contains(subscribedGuild.getChannelChangelogId())) {
											subscribedGuild.setChannelChangelogId(0);
											updated = true;
										}
										if (invalidRoles.contains(subscribedGuild.getRoleAwardedLevelsId())) {
											subscribedGuild.setRoleAwardedLevelsId(0);
											updated = true;
										}
										if (invalidRoles.contains(subscribedGuild.getRoleGdModeratorsId())) {
											subscribedGuild.setRoleGdModeratorsId(0);
											updated = true;
										}
										if (invalidRoles.contains(subscribedGuild.getRoleTimelyLevelsId())) {
											subscribedGuild.setRoleTimelyLevelsId(0);
											updated = true;
										}
										if (updated) {
											session.saveOrUpdate(subscribedGuild);
											updatedCount++;
										}
									}
									return updatedCount;
								}))
								.flatMap(updatedCount -> ctx.reply("Successfully cleaned up invalid channels and roles for "
										+ updatedCount + " guilds!"))
								.then(wait.delete()));	
			default:
				return Mono.error(new CommandFailedException(errorMessage));
		}
	}

	@Override
	public Set<String> getAliases() {
		return Set.of("channels_and_roles");
	}

	@Override
	public Set<Command> getSubcommands() {
		return Set.of();
	}

	@Override
	public String getDescription() {
		return "Manage channels and roles configured to receive GD event announcements.";
	}

	@Override
	public String getLongDescription() {
		return "In order to improve performances when broadcasting GD events to servers, the bot first need to "
				+ "preload all channels that are configured to receive the announcements, as well as the roles to "
				+ "tag, if configured. This is usually done on bot startup, but it can also be done manually via "
				+ "this command with the `preload` argument.\n"
				+ "As opposed to preloading, you can also unload everything using the `unload` argument.\n"
				+ "Finally, you can also use `purge_unused` in order to remove from database all channels/roles that are "
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
