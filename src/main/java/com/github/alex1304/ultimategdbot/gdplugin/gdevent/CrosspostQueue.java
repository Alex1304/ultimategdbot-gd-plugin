package com.github.alex1304.ultimategdbot.gdplugin.gdevent;

import java.time.Duration;

import com.github.alex1304.jdashevents.event.GDEvent;
import com.github.alex1304.ultimategdbot.api.service.BotService;

import discord4j.core.object.entity.Message;
import reactor.core.publisher.Mono;
import reactor.core.publisher.MonoProcessor;
import reactor.util.Logger;
import reactor.util.Loggers;

class CrosspostQueue {
	
	private static final Logger LOGGER = Loggers.getLogger(CrosspostQueue.class);
	
	private final BotService bot;

	CrosspostQueue(BotService bot) {
		this.bot = bot;
		
	}
	
	void submit(Message message, GDEvent event, GDEventProperties<?> eventProps) {
		var tr = bot.localization();
		var crosspostCompletion = MonoProcessor.create();
		var warnDelayed = Mono.first(crosspostCompletion, Mono.delay(Duration.ofSeconds(10))
				.flatMap(__ -> message.getChannel())
				.flatMap(channel -> channel.createMessage(":warning: " + tr
						.translate("GDStrings", "gdevproc_crosspost_delayed"))));
		var doCrosspost = message.publish().then(Mono.fromRunnable(crosspostCompletion::onComplete));
		var logText = eventProps.logText(tr, event);
		Mono.when(doCrosspost, warnDelayed).subscribe(null,
				t -> log("failed", tr.translate("GDStrings", "gdevproc_crosspost_failed", logText), logText, t),
				() -> log("success", tr.translate("GDStrings", "gdevproc_crosspost_success", logText), logText, null));
	}
	
	void log(String emoji, String message, String eventString, Throwable t) {
		var throwableSummary = t == null ? "" : "\n`" + t + "`";
		bot.emoji().get(emoji)
				.flatMap(em -> bot.logging().log(em + ' ' + message + throwableSummary))
				.and(Mono.fromRunnable(() -> {
					if (t != null) {
						LOGGER.error("Unable to crosspost message for event " + eventString, t);
					} else {
						LOGGER.info("Successfully crossposted message for event {}", eventString);
					}
				}))
				.subscribe();
	}
}
