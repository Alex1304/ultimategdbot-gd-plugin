package com.github.alex1304.ultimategdbot.gdplugin.command;

import static com.github.alex1304.ultimategdbot.api.util.Markdown.code;
import static java.util.stream.Collectors.joining;

import java.util.Collections;
import java.util.List;

import com.github.alex1304.jdash.entity.GDLevel;
import com.github.alex1304.jdash.util.LevelSearchFilters;
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
import com.github.alex1304.ultimategdbot.api.command.annotated.FlagDoc;
import com.github.alex1304.ultimategdbot.api.command.annotated.FlagInfo;
import com.github.alex1304.ultimategdbot.api.util.Markdown;
import com.github.alex1304.ultimategdbot.api.util.MessageSpecTemplate;
import com.github.alex1304.ultimategdbot.api.util.menu.InteractiveMenu;
import com.github.alex1304.ultimategdbot.api.util.menu.PageNumberOutOfRangeException;
import com.github.alex1304.ultimategdbot.gdplugin.GDService;
import com.github.alex1304.ultimategdbot.gdplugin.util.GDLevels;

import reactor.core.publisher.EmitterProcessor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.core.publisher.Mono;
import reactor.util.annotation.Nullable;

@CommandDescriptor(
		aliases = "gdevents",
		shortDescription = "Allows the bot owner to manage the GD event broadcasting system."
)
@CommandPermission(level = PermissionLevel.BOT_OWNER)
public class GDEventsCommand {

	private final GDService gdService;
	
	public GDEventsCommand(GDService gdService) {
		this.gdService = gdService;
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
				eventToDispatch = gdService.getGdClient().getDailyLevel().map(TimelyLevelChangedEvent::new);
				break;
			case "weekly_demon_changed":
				eventToDispatch = gdService.getGdClient().getWeeklyDemon().map(TimelyLevelChangedEvent::new);
				break;
			default:
				if (levelId == null) {
					return Mono.error(new CommandFailedException("Please specify a level ID."));
				}
				switch (eventName) {
					case "awarded_level_added":
						eventToDispatch = gdService.getGdClient().getLevelById(levelId).map(AwardedLevelAddedEvent::new);
						break;
					case "awarded_level_removed":
						eventToDispatch = gdService.getGdClient().getLevelById(levelId).map(AwardedLevelRemovedEvent::new);
						break;
					case "awarded_level_updated":
						eventToDispatch = gdService.getGdClient().getLevelById(levelId)
								.map(level -> new AwardedLevelUpdatedEvent(level, level));
						break;
					default:
						return Mono.error(new CommandFailedException("Unknown event. See " + code(ctx.prefixUsed()
								+ "help gdevents dispatch") + " to see the existing events."));
				}
		}
		
		return eventToDispatch.doOnNext(gdService.getGdEventDispatcher()::dispatch)
				.then(ctx.bot().emoji("success").flatMap(emoji -> ctx.reply(emoji + " Event has been dispatched.")))
				.then();
	}
	
	@CommandAction("scanner_loop")
	@CommandDoc("Starts or stops the GD event scanner loop. If stopped, GD events will no longer be dispatched "
			+ "automatically when they happen in game. The possible `action`s are `start` and `stop`, respectively.")
	public Mono<Void> runScannerLoop(Context ctx, String action) {
		switch (action) {
			case "start":
				return Mono.fromRunnable(gdService.getGdEventscannerLoop()::start)
						.then(ctx.reply("GD event scanner loop has been started."))
						.then();
			case "stop":
				return Mono.fromRunnable(gdService.getGdEventscannerLoop()::stop)
						.then(ctx.reply("GD event scanner loop has been stopped."))
						.then();
			default:
				return Mono.error(new CommandFailedException("Unknown action. See " + code(ctx.prefixUsed()
						+ "help gdevents scanner_loop") + " to see the different actions possible"));
		}
	}
	
	@CommandAction("dispatch_all_awarded_resuming_from")
	@CommandDoc("Dispatches new awarded events for the given level plus all levels that have been rated after it.")
	@FlagDoc(
			@FlagInfo(name = "max-page", description = "The maximum page where to search the level in the awarded section. "
					+ "Default is 10.")
	)
	public Mono<Void> runDispatchAllAwardedResumingFrom(Context ctx, long levelId) {
		var maxPage = ctx.flags().get("max-page").map(v -> {
			try {
				return Integer.parseInt(v);
			} catch (NumberFormatException e) {
				return null;
			}
		}).orElse(10);
		if (maxPage < 1) {
			return Mono.error(new CommandFailedException("Invalid `max-page`"));
		}
		
		var processor = EmitterProcessor.<GDLevel>create(false);
		var sink = processor.sink(FluxSink.OverflowStrategy.BUFFER);
		
		Mono.zip(
				processor.then(),
				Flux.range(0, maxPage)
						.concatMap(n -> gdService.getGdClient().browseAwardedLevels(LevelSearchFilters.create(), n)
								.flatMapMany(Flux::fromIterable)
								.doOnNext(level -> {
									if (level.getId() == levelId) {
										sink.complete();
									} else {
										sink.next(level);
									}
								}))
						.doOnComplete(() -> sink.error(new CommandFailedException("Reached max-page (" + maxPage + ") without finding the level.")))
						.doOnError(sink::error)
						.then())
				.onErrorResume(e -> Mono.empty())
				.subscribe();
		
		return processor.map(AwardedLevelAddedEvent::new)
				.collectList()
				.doOnNext(Collections::reverse)
				.flatMap(events -> {
					var lastPage = (events.size() - 1) / 10;
					InteractiveMenu menu;
					if (lastPage == 0) {
						menu = InteractiveMenu.create(paginateEvents(0, 0, events).getContent())
								.closeAfterReaction(false)
								.addReactionItem("cross", interaction -> Mono.fromRunnable(interaction::closeMenu));
					} else {
						menu = InteractiveMenu.createPaginated(ctx.bot().config().getPaginationControls(),
								page -> paginateEvents(page, lastPage, events));
					}
					return menu.deleteMenuOnClose(true)
							.addReactionItem("success", interaction -> {
								events.forEach(gdService.getGdEventDispatcher()::dispatch);
								return ctx.bot().emoji("success")
										.flatMap(success -> ctx.reply(success + " Dispatched " + events.size() + " events."))
										.then(Mono.fromRunnable(interaction::closeMenu));
							})
							.open(ctx);
				})
				.then();
	}
	
	private static MessageSpecTemplate paginateEvents(int page, int lastPage, List<AwardedLevelAddedEvent> events) {
		PageNumberOutOfRangeException.check(page, 0, lastPage);
		return new MessageSpecTemplate("AwardedLevelAddedEvents are going to be dispatched for the following levels:\n\n"
				+ "Page " + (page + 1) + " of " + (lastPage + 1) + "\n"
				+ events.stream()
						.skip(page * 10)
						.limit(10)
						.map(event -> Markdown.quote(GDLevels.toString(event.getAddedLevel())))
						.collect(joining("\n"))
				+ "\n\nReact below to confirm.");
	}
}
