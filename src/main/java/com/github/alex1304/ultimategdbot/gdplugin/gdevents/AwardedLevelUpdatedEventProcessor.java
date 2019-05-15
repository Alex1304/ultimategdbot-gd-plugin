package com.github.alex1304.ultimategdbot.gdplugin.gdevents;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.alex1304.jdashevents.event.AwardedLevelUpdatedEvent;
import com.github.alex1304.ultimategdbot.api.utils.BotUtils;
import com.github.alex1304.ultimategdbot.gdplugin.GDPlugin;
import com.github.alex1304.ultimategdbot.gdplugin.GDUtils;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class AwardedLevelUpdatedEventProcessor extends TypeSafeGDEventProcessor<AwardedLevelUpdatedEvent> {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(AwardedLevelUpdatedEventProcessor.class);

	private final GDPlugin plugin;
	
	public AwardedLevelUpdatedEventProcessor(GDPlugin plugin) {
		super(AwardedLevelUpdatedEvent.class);
		this.plugin = Objects.requireNonNull(plugin);
	}

	@Override
	public Mono<Void> process0(AwardedLevelUpdatedEvent t) {
		var timeStart = new AtomicLong();
		var messageList = plugin.getBroadcastedLevels().getOrDefault(t.getNewLevel().getId(), List.of());
		var logText = "**Awarded Level Updated** for level " + GDUtils.levelToString(t.getNewLevel());
		LOGGER.info("Processing Geometry Dash event: {}", logText);
		return Mono.zip(plugin.getBot().getEmoji("info"), plugin.getBot().getEmoji("success"))
				.flatMap(emojis -> plugin.getBot().log(emojis.getT1() + " GD event fired: " + logText)
						.onErrorResume(e -> Mono.empty())
						.then(Mono.fromRunnable(() -> timeStart.set(System.nanoTime())))
						.then(Flux.fromIterable(messageList)
								.filter(message -> message.getEmbeds().size() > 0)
								.publishOn(plugin.getGdEventScheduler())
								.flatMap(message -> GDUtils.shortLevelView(plugin.getBot(), t.getNewLevel(), message.getEmbeds().get(0).getAuthor().get().getName(),
												message.getEmbeds().get(0).getAuthor().get().getIconUrl())
										.flatMap(embed -> message.edit(mes -> mes.setEmbed(embed)).onErrorResume(e -> Mono.empty())))
								.collectList()
								.flatMap(newMessageList -> {
									var time = System.nanoTime() - timeStart.get();
									var formattedTime = BotUtils.formatTimeMillis(Duration.ofNanos(time));
									var oldList = plugin.getBroadcastedLevels().put(t.getNewLevel().getId(), newMessageList);
									var broadcastSpeed = ((int) ((newMessageList.size() / (double) time) * 1_000_000_000));
									if (oldList == null) {
										LOGGER.info("Skipping Geometry Dash event {}: list of messages to edit is no longer available.", logText);
										return plugin.getBot().log(emojis.getT1() + " Skipping " + logText + ": list of messages to edit is no longer available.");
									}
									LOGGER.info("Finished processing Geometry Dash event: {} in {} ({} messages/s)", logText, formattedTime, broadcastSpeed);
									return plugin.getBot().log(emojis.getT2() + " Successfully processed event: " + logText + "\n"
											+ "Successfully edited **" + newMessageList.size() + "/" + oldList.size() + "** messages!\n"
											+ "**Execution time: " + formattedTime + "**\n"
											+ "**Average speed: " + broadcastSpeed + " messages/s**")
													.onErrorResume(e -> Mono.empty());
								}))).then();
	}

	@Override
	String logText0(AwardedLevelUpdatedEvent event) {
		return "**Awarded Level Updated** for level " + GDUtils.levelToString(event.getNewLevel());
	}
}
