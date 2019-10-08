package com.github.alex1304.ultimategdbot.gdplugin.command;

import static com.github.alex1304.ultimategdbot.api.utils.BotUtils.sendPaginatedMessage;
import static com.github.alex1304.ultimategdbot.api.utils.Markdown.code;
import static java.util.stream.Collectors.toSet;

import com.github.alex1304.jdashevents.event.AwardedLevelAddedEvent;
import com.github.alex1304.jdashevents.event.AwardedLevelRemovedEvent;
import com.github.alex1304.jdashevents.event.AwardedLevelUpdatedEvent;
import com.github.alex1304.jdashevents.event.GDEvent;
import com.github.alex1304.jdashevents.event.TimelyLevelChangedEvent;
import com.github.alex1304.ultimategdbot.api.command.CommandFailedException;
import com.github.alex1304.ultimategdbot.api.command.Context;
import com.github.alex1304.ultimategdbot.api.command.PermissionLevel;
import com.github.alex1304.ultimategdbot.api.command.annotated.CommandAction;
import com.github.alex1304.ultimategdbot.api.command.annotated.CommandDoc;
import com.github.alex1304.ultimategdbot.api.command.annotated.CommandSpec;
import com.github.alex1304.ultimategdbot.gdplugin.GDServiceMediator;
import com.github.alex1304.ultimategdbot.gdplugin.gdevent.LateAwardedLevelAddedEvent;
import com.github.alex1304.ultimategdbot.gdplugin.gdevent.LateAwardedLevelRemovedEvent;
import com.github.alex1304.ultimategdbot.gdplugin.gdevent.LateTimelyLevelChangedEvent;
import com.github.alex1304.ultimategdbot.gdplugin.util.GDEvents;

import discord4j.core.object.util.Snowflake;
import reactor.core.publisher.Mono;
import reactor.util.annotation.Nullable;

@CommandSpec(
		aliases = "gdevents",
		shortDescription = "Allows the bot owner to manage the GD event broadcasting system.",
		permLevel = PermissionLevel.BOT_OWNER
)
public class GDEventsCommand {

	private final GDServiceMediator gdServiceMediator;
	
	public GDEventsCommand(GDServiceMediator gdServiceMediator) {
		this.gdServiceMediator = gdServiceMediator;
	}
	
	@CommandAction("dispatch_results")
	@CommandDoc("View or clear the GD events dispatch results.\n`action` can be one of:\n"
			+ "- `view`: Check the list of levels that were successfully dispatched\n"
			+ "- `clear`: Clears the dispatch result list.\n"
			+ "- `remove <level_id>`: Removes one entry from the dispatch result list")
	public Mono<Void> runDispatchResults(Context ctx, String action, @Nullable Long levelId) {
		switch (action) {
			case "view":
				var sb = new StringBuilder("__**GD events dispatch results:**__\n")
						.append( "Data below is collected in order to have the ability to edit previous announcement messages")
						.append( " in case an **Awarded Level Updated** event is dispatched.\n")
						.append( "Only the last 10 results are saved here. Older ones automatically get deleted in order to ")
						.append("save resources and avoid memory leaks.\n\n");
				if (gdServiceMediator.getDispatchedLevels().isEmpty()) {
					sb.append("There is nothing here yet!");
				}
				gdServiceMediator.getDispatchedLevels().forEach((k, v) -> sb.append("LevelID **")
						.append(k).append("** => **").append(v.size()).append("** messages sent\n"));
				return sendPaginatedMessage(ctx, sb.toString()).then();
			case "clear":
				gdServiceMediator.getDispatchedLevels().clear();
				return ctx.getBot().getEmoji("success").flatMap(emoji -> ctx.reply(emoji + " Dispatch results cleared!")).then();
			case "remove":
				if (levelId == null) {
					return Mono.error(new CommandFailedException("Please specify a level ID."));
				}
				gdServiceMediator.getDispatchedLevels().remove(levelId);
				return ctx.getBot().getEmoji("success").flatMap(emoji -> ctx.reply(emoji + " Data for " + levelId + " has been removed."))
						.then();
			default:
				return Mono.error(new CommandFailedException("Unknown action. See " + code(ctx.getPrefixUsed()
						+ "help gdevents dispatch_results") + " to see the different actions possible"));
		}
	}
	
	@CommandAction("channels_and_roles")
	@CommandDoc("Performance optimization tool for channel/role preloading. In order to improve performances when "
			+ "broadcasting GD events to servers, the bot first need to "
			+ "preload all channels that are configured to receive the announcements, as well as the roles to "
			+ "tag, if configured. This is usually done on bot startup, but it can also be done manually via "
			+ "this command with the `preload` argument.\n"
			+ "As opposed to preloading, you can also unload everything using the `unload` argument.\n"
			+ "Finally, you can also use `purge_unused` in order to remove from database all channels/roles that are "
			+ "invalid or deleted. This can decrease the processing time for future invocations of the preload "
			+ "process.")
	public Mono<Void> runChannelsAndRoles(Context ctx, String action) {
		var preloader = gdServiceMediator.getBroadcastPreloader();
		switch (action) {
			case "preload":
				return ctx.reply("Processing...")
						.flatMap(wait -> GDEvents.preloadBroadcastChannelsAndRoles(ctx.getBot(), preloader)
								.flatMap(count -> ctx.reply("Sucessfully preloaded **" + count.getT1() + "** channels and **" + count.getT2() + "** roles!"))
								.then(wait.delete()));
			case "unload":
				return Mono.fromRunnable(preloader::unload)
						.then(ctx.reply("Broadcast channels and roles have been unloaded."))
						.then();
			case "purge_unused":
				if (preloader.getInvalidChannelSnowflakes().isEmpty() && preloader.getInvalidRoleSnowflakes().isEmpty()) {
					return Mono.error(new CommandFailedException("Nothing to clean. Maybe try preloading again first?"));
				}
				return ctx.reply("Processing...")
						.flatMap(wait -> GDEvents.getExistingSubscribedGuilds(ctx.getBot(), "").collectList()
								.flatMap(subscribedList -> ctx.getBot().getDatabase().performTransaction(session -> {
									var invalidChannels = preloader.getInvalidChannelSnowflakes().stream()
											.map(Snowflake::asLong)
											.filter(x -> x > 0)
											.collect(toSet());
									var invalidRoles = preloader.getInvalidRoleSnowflakes().stream()
											.map(Snowflake::asLong)
											.collect(toSet());
									var updatedCount = 0;
									for (var subscribedGuild : subscribedList) {
										var updated = false;
										if (invalidChannels.contains(subscribedGuild.getChannelAwardedLevelsId())) {
											subscribedGuild.setChannelAwardedLevelsId(null);
											updated = true;
										}
										if (invalidChannels.contains(subscribedGuild.getChannelGdModeratorsId())) {
											subscribedGuild.setChannelGdModeratorsId(null);
											updated = true;
										}
										if (invalidChannels.contains(subscribedGuild.getChannelTimelyLevelsId())) {
											subscribedGuild.setChannelTimelyLevelsId(null);
											updated = true;
										}
										if (invalidChannels.contains(subscribedGuild.getChannelChangelogId())) {
											subscribedGuild.setChannelChangelogId(null);
											updated = true;
										}
										if (invalidRoles.contains(subscribedGuild.getRoleAwardedLevelsId())) {
											subscribedGuild.setRoleAwardedLevelsId(null);
											updated = true;
										}
										if (invalidRoles.contains(subscribedGuild.getRoleGdModeratorsId())) {
											subscribedGuild.setRoleGdModeratorsId(null);
											updated = true;
										}
										if (invalidRoles.contains(subscribedGuild.getRoleTimelyLevelsId())) {
											subscribedGuild.setRoleTimelyLevelsId(null);
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
				return Mono.error(new CommandFailedException("You need to provide one of those arguments: `preload`, `unload` or `purge_unused`.\n"
						+ "Read `" + ctx.getPrefixUsed() + "help gdevents channels_and_roles` for documentation."));
		}
	}
	
	@CommandAction("dispatch")
	@CommandDoc("Manually dispatches a new GD event.\n`event_name` can be one of:\n"
				+ "- `daily_level_changed`: dispatches the current Daily level\n"
				+ "- `late_daily_level_changed`: dispatches the current Daily level, without tagging subscriber roles\n"
				+ "- `weekly_demon_changed`: dispatches the current Weekly demon\n"
				+ "- `late_weekly_demon_changed`: dispatches the current Weekly demon, without tagging subscriber roles\n"
				+ "- `awarded_level_added <level_id>`: dispatches the level with the specified ID as a newly awarded level\n"
				+ "- `late_awarded_level_added <level_id>`: dispatches the level with the specified ID as a newly awarded "
				+ "level, without tagging subscriber roles\n"
				+ "- `awarded_level_removed <level_id>`: dispatches the level with the specified ID as a level that got unrated\n"
				+ "- `late_awarded_level_removed <level_id>`: dispatches the level with the specified ID as a level that got "
				+ "unrated, without tagging subscriber roles\n"
				+ "- `awarded_level_updated <level_id>`: dispatches the level with the specified ID as a level that got its "
				+ "rating changed. Only works for levels that were previously dispatched as new rates.\n")
	public Mono<Void> runDispatch(Context ctx, String eventName, @Nullable Long levelId) {
		Mono<GDEvent> eventToDispatch;
		switch (eventName) {
			case "daily_level_changed":
				eventToDispatch = gdServiceMediator.getGdClient().getDailyLevel().map(TimelyLevelChangedEvent::new);
				break;
			case "late_daily_level_changed":
				eventToDispatch = gdServiceMediator.getGdClient().getDailyLevel().map(LateTimelyLevelChangedEvent::new);
				break;
			case "weekly_demon_changed":
				eventToDispatch = gdServiceMediator.getGdClient().getWeeklyDemon().map(TimelyLevelChangedEvent::new);
				break;
			case "late_weekly_demon_changed":
				eventToDispatch = gdServiceMediator.getGdClient().getWeeklyDemon().map(LateTimelyLevelChangedEvent::new);
				break;
			default:
				if (levelId == null) {
					return Mono.error(new CommandFailedException("Please specify a level ID."));
				}
				switch (eventName) {
					case "awarded_level_added":
						eventToDispatch = gdServiceMediator.getGdClient().getLevelById(levelId).map(AwardedLevelAddedEvent::new);
						break;
					case "late_awarded_level_added":
						eventToDispatch = gdServiceMediator.getGdClient().getLevelById(levelId).map(LateAwardedLevelAddedEvent::new);
						break;
					case "awarded_level_removed":
						eventToDispatch = gdServiceMediator.getGdClient().getLevelById(levelId).map(AwardedLevelRemovedEvent::new);
						break;
					case "late_awarded_level_removed":
						eventToDispatch = gdServiceMediator.getGdClient().getLevelById(levelId).map(LateAwardedLevelRemovedEvent::new);
						break;
					case "awarded_level_updated":
						eventToDispatch = gdServiceMediator.getGdClient().getLevelById(levelId)
								.map(level -> new AwardedLevelUpdatedEvent(level, level));
						break;
					default:
						return Mono.error(new CommandFailedException("Unknown event. See " + code(ctx.getPrefixUsed()
								+ "help gdevents dispatch") + " to see the existing events."));
				}
		}
		
		return eventToDispatch.doOnNext(gdServiceMediator.getGdEventDispatcher()::dispatch)
				.then(ctx.getBot().getEmoji("success").flatMap(emoji -> ctx.reply(emoji + " Event has been dispatched.")))
				.then();
	}
	
	@CommandAction("scanner_loop")
	@CommandDoc("Starts or stops the GD event scanner loop. If stopped, GD events will no longer be dispatched "
			+ "automatically when they happen in game. The possible `action`s are `start` and `stop`, respectively.")
	public Mono<Void> runScannerLoop(Context ctx, String action) {
		switch (action) {
			case "start":
				return Mono.fromRunnable(gdServiceMediator.getGdEventscannerLoop()::start)
						.then(ctx.reply("GD event scanner loop has been started."))
						.then();
			case "stop":
				return Mono.fromRunnable(gdServiceMediator.getGdEventscannerLoop()::stop)
						.then(ctx.reply("GD event scanner loop has been stopped."))
						.then();
			default:
				return Mono.error(new CommandFailedException("Unknown action. See " + code(ctx.getPrefixUsed()
						+ "help gdevents scanner_loop") + " to see the different actions possible"));
		}
	}
}
