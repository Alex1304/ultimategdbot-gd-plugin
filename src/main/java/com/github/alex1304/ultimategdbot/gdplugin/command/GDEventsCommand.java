package com.github.alex1304.ultimategdbot.gdplugin.command;

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
import com.github.alex1304.ultimategdbot.api.Translator;
import com.github.alex1304.ultimategdbot.api.command.CommandFailedException;
import com.github.alex1304.ultimategdbot.api.command.Context;
import com.github.alex1304.ultimategdbot.api.command.PermissionLevel;
import com.github.alex1304.ultimategdbot.api.command.annotated.CommandAction;
import com.github.alex1304.ultimategdbot.api.command.annotated.CommandDescriptor;
import com.github.alex1304.ultimategdbot.api.command.annotated.CommandDoc;
import com.github.alex1304.ultimategdbot.api.command.annotated.CommandPermission;
import com.github.alex1304.ultimategdbot.api.command.annotated.FlagDoc;
import com.github.alex1304.ultimategdbot.api.command.annotated.FlagInfo;
import com.github.alex1304.ultimategdbot.api.command.menu.InteractiveMenu;
import com.github.alex1304.ultimategdbot.api.command.menu.PageNumberOutOfRangeException;
import com.github.alex1304.ultimategdbot.api.service.Root;
import com.github.alex1304.ultimategdbot.api.util.Markdown;
import com.github.alex1304.ultimategdbot.api.util.MessageSpecTemplate;
import com.github.alex1304.ultimategdbot.gdplugin.GDService;
import com.github.alex1304.ultimategdbot.gdplugin.level.GDLevelService;

import reactor.core.publisher.EmitterProcessor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.core.publisher.Mono;
import reactor.util.annotation.Nullable;

@CommandDescriptor(
		aliases = "gdevents",
		shortDescription = "tr:GDStrings/gdevents_desc"
)
@CommandPermission(level = PermissionLevel.BOT_OWNER)
public final class GDEventsCommand {

	@Root
	private GDService gd;
	
	@CommandAction("dispatch")
	@CommandDoc("tr:GDStrings/gdevents_run_dispatch")
	public Mono<Void> runDispatch(Context ctx, String eventName, @Nullable Long levelId) {
		Mono<GDEvent> eventToDispatch;
		switch (eventName) {
			case "daily_level_changed":
				eventToDispatch = gd.client().getDailyLevel().map(TimelyLevelChangedEvent::new);
				break;
			case "weekly_demon_changed":
				eventToDispatch = gd.client().getWeeklyDemon().map(TimelyLevelChangedEvent::new);
				break;
			default:
				if (levelId == null) {
					return Mono.error(new CommandFailedException(ctx.translate("GDStrings", "error_id_not_specified")));
				}
				switch (eventName) {
					case "awarded_level_added":
						eventToDispatch = gd.client().getLevelById(levelId).map(AwardedLevelAddedEvent::new);
						break;
					case "awarded_level_removed":
						eventToDispatch = gd.client().getLevelById(levelId).map(AwardedLevelRemovedEvent::new);
						break;
					case "awarded_level_updated":
						eventToDispatch = gd.client().getLevelById(levelId)
								.map(level -> new AwardedLevelUpdatedEvent(level, level));
						break;
					default:
						return Mono.error(new CommandFailedException(ctx.translate("GDStrings", "error_id_not_specified", ctx.prefixUsed())));
				}
		}
		
		return eventToDispatch.doOnNext(gd.event().dispatcher()::dispatch)
				.then(gd.bot().emoji().get("success").flatMap(emoji -> ctx.reply(emoji + ' '
						+ ctx.translate("GDStrings", "dispatch_success"))))
				.then();
	}
	
	@CommandAction("loop")
	@CommandDoc("tr:GDStrings/gdevents_run_loop")
	public Mono<Void> runLoop(Context ctx, String action) {
		switch (action) {
			case "start":
				return Mono.fromRunnable(gd.event().loop()::start)
						.then(ctx.reply(ctx.translate("GDStrings", "event_loop_started")))
						.then();
			case "stop":
				return Mono.fromRunnable(gd.event().loop()::stop)
						.then(ctx.reply(ctx.translate("GDStrings", "event_loop_stopped")))
						.then();
			default:
				return Mono.error(new CommandFailedException(
						ctx.translate("GDStrings", "error_unknown_action", ctx.prefixUsed())));
		}
	}
	
	@CommandAction("dispatch_all_awarded_resuming_from")
	@CommandDoc("tr:GDStrings/gdevents_run_dispatch_all_awarded_resuming_from")
	@FlagDoc(
			@FlagInfo(name = "max-page", valueFormat = "number", description = "tr:GDStrings/gdevents_flag_max_page")
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
			return Mono.error(new CommandFailedException(ctx.translate("GDStrings", "error_invalid_max_page")));
		}
		
		var processor = EmitterProcessor.<GDLevel>create(false);
		var sink = processor.sink(FluxSink.OverflowStrategy.BUFFER);
		
		Mono.zip(
				processor.then(),
				Flux.range(0, maxPage)
						.concatMap(n -> gd.client().browseAwardedLevels(LevelSearchFilters.create(), n)
								.flatMapMany(Flux::fromIterable)
								.doOnNext(level -> {
									if (level.getId() == levelId) {
										sink.complete();
									} else {
										sink.next(level);
									}
								}))
						.doOnComplete(() -> sink.error(new CommandFailedException(
								ctx.translate("GDStrings", "error_max_page_reached", maxPage))))
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
						menu = gd.bot().interactiveMenu()
								.create(paginateEvents(ctx, 0, 0, events).getContent())
								.closeAfterReaction(false)
								.addReactionItem("cross", interaction -> Mono.fromRunnable(interaction::closeMenu));
					} else {
						menu = gd.bot().interactiveMenu()
								.createPaginated((tr, page) -> paginateEvents(tr, page, lastPage, events));
					}
					return menu.deleteMenuOnClose(true)
							.addReactionItem("success", interaction -> {
								events.forEach(gd.event().dispatcher()::dispatch);
								return gd.bot().emoji().get("success")
										.flatMap(success -> ctx.reply(success + ' '
												+ ctx.translate("GDStrings", "dispatch_success_multi", events.size())))
										.then(Mono.fromRunnable(interaction::closeMenu));
							})
							.open(ctx);
				})
				.then();
	}
	
	private static MessageSpecTemplate paginateEvents(Translator tr, int page, int lastPage, List<AwardedLevelAddedEvent> events) {
		PageNumberOutOfRangeException.check(page, 0, lastPage);
		return new MessageSpecTemplate(tr.translate("GDStrings", "dispatch_list") + "\n\n"
				+ tr.translate("CommonStrings", "pagination_page_count", page + 1, lastPage + 1) + '\n'
				+ events.stream()
						.skip(page * 10)
						.limit(10)
						.map(event -> Markdown.quote(GDLevelService.toString(event.getAddedLevel())))
						.collect(joining("\n"))
				+ "\n\n" + tr.translate("GDStrings", "dispatch_confirm"));
	}
}
