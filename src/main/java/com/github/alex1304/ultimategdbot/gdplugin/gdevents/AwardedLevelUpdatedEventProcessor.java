package com.github.alex1304.ultimategdbot.gdplugin.gdevents;

import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import com.github.alex1304.jdashevents.event.AwardedLevelUpdatedEvent;
import com.github.alex1304.ultimategdbot.api.utils.BotUtils;
import com.github.alex1304.ultimategdbot.gdplugin.GDPlugin;
import com.github.alex1304.ultimategdbot.gdplugin.GDUtils;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

public class AwardedLevelUpdatedEventProcessor extends TypeSafeGDEventProcessor<AwardedLevelUpdatedEvent> {

	private final GDPlugin plugin;
	
	public AwardedLevelUpdatedEventProcessor(GDPlugin plugin) {
		super(AwardedLevelUpdatedEvent.class);
		this.plugin = Objects.requireNonNull(plugin);
	}

	@Override
	public Mono<Void> process0(AwardedLevelUpdatedEvent t) {
		var messageList = plugin.getBroadcastedLevels().getOrDefault(t.getNewLevel().getId(), List.of());
		var logText = "**Awarded Level Updated** for level " + GDUtils.levelToString(t.getNewLevel());
		return Mono.zip(plugin.getBot().getEmoji("info"), plugin.getBot().getEmoji("success"))
				.flatMap(emojis -> plugin.getBot().log(emojis.getT1() + " GD event fired: " + logText)
						.onErrorResume(e -> Mono.empty())
						.then(Flux.fromIterable(messageList)
								.filter(message -> message.getEmbeds().size() > 0)
								.parallel(2).runOn(Schedulers.parallel())
								.flatMap(message -> GDUtils.shortLevelView(plugin.getBot(), t.getNewLevel(), message.getEmbeds().get(0).getAuthor().get().getName(),
												message.getEmbeds().get(0).getAuthor().get().getIconUrl())
										.flatMap(embed -> message.edit(mes -> mes.setEmbed(embed)).onErrorResume(e -> Mono.empty())))
								.collectSortedList(Comparator.comparing(m -> m.getId().asLong()), 1000)
								.elapsed()
								.flatMap(tupleOfTimeAndMessageList -> {
									var time = Duration.ofMillis(tupleOfTimeAndMessageList.getT1());
									var formattedTime = BotUtils.formatTimeMillis(time);
									var oldList = plugin.getBroadcastedLevels().put(t.getNewLevel().getId(), tupleOfTimeAndMessageList.getT2());
									if (oldList == null) {
										return plugin.getBot().log(emojis.getT1() + " Skipping " + logText + ": list of messages to edit is no longer available.");
									}
									return plugin.getBot().log(emojis.getT2() + " Successfully processed event: " + logText + "\n"
											+ "Successfully edited **" + messageList.size() + "/" + oldList.size() + "** messages!\n"
											+ "**Execution time: " + formattedTime + "**\n"
											+ "**Average speed: " + ((int) ((messageList.size() / (double) time.toMillis()) * 1000)) + " messages/s**")
													.onErrorResume(e -> Mono.empty());
								}))).then();
	}

	@Override
	String logText0(AwardedLevelUpdatedEvent event) {
		return "**Awarded Level Updated** for level " + GDUtils.levelToString(event.getNewLevel());
	}
}
