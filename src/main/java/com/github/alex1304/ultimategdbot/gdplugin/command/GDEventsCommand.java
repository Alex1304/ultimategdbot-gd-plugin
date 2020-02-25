package com.github.alex1304.ultimategdbot.gdplugin.command;

import static com.github.alex1304.ultimategdbot.api.util.Markdown.code;

import com.github.alex1304.jdashevents.event.AwardedLevelAddedEvent;
import com.github.alex1304.jdashevents.event.AwardedLevelRemovedEvent;
import com.github.alex1304.jdashevents.event.AwardedLevelUpdatedEvent;
import com.github.alex1304.jdashevents.event.GDEvent;
import com.github.alex1304.jdashevents.event.TimelyLevelChangedEvent;
import com.github.alex1304.ultimategdbot.api.command.CommandFailedException;
import com.github.alex1304.ultimategdbot.api.command.Context;
import com.github.alex1304.ultimategdbot.api.command.PermissionLevel;
import com.github.alex1304.ultimategdbot.api.command.annotated.CommandAction;
import com.github.alex1304.ultimategdbot.api.command.annotated.CommandDescriptor;
import com.github.alex1304.ultimategdbot.api.command.annotated.CommandDoc;
import com.github.alex1304.ultimategdbot.api.command.annotated.CommandPermission;
import com.github.alex1304.ultimategdbot.gdplugin.GDServiceMediator;

import reactor.core.publisher.Mono;
import reactor.util.annotation.Nullable;

@CommandDescriptor(
		aliases = "gdevents",
		shortDescription = "Allows the bot owner to manage the GD event broadcasting system."
)
@CommandPermission(level = PermissionLevel.BOT_OWNER)
public class GDEventsCommand {

	private final GDServiceMediator gdServiceMediator;
	
	public GDEventsCommand(GDServiceMediator gdServiceMediator) {
		this.gdServiceMediator = gdServiceMediator;
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
			case "weekly_demon_changed":
				eventToDispatch = gdServiceMediator.getGdClient().getWeeklyDemon().map(TimelyLevelChangedEvent::new);
				break;
			default:
				if (levelId == null) {
					return Mono.error(new CommandFailedException("Please specify a level ID."));
				}
				switch (eventName) {
					case "awarded_level_added":
						eventToDispatch = gdServiceMediator.getGdClient().getLevelById(levelId).map(AwardedLevelAddedEvent::new);
						break;
					case "awarded_level_removed":
						eventToDispatch = gdServiceMediator.getGdClient().getLevelById(levelId).map(AwardedLevelRemovedEvent::new);
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
