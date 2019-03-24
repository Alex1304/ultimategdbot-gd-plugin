package com.github.alex1304.ultimategdbot.gdplugin.gdevents;

import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.github.alex1304.jdashevents.event.AwardedLevelUpdatedEvent;
import com.github.alex1304.ultimategdbot.api.Bot;
import com.github.alex1304.ultimategdbot.gdplugin.GDUtils;

import discord4j.core.object.entity.Message;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

public class AwardedLevelUpdatedEventProcessor extends TypeSafeGDEventProcessor<AwardedLevelUpdatedEvent> {

	private final Bot bot;
	private final Map<Long, List<Message>> broadcastedLevels;
	
	public AwardedLevelUpdatedEventProcessor(Bot bot, Map<Long, List<Message>> broadcastedMessages) {
		super(AwardedLevelUpdatedEvent.class);
		this.bot = Objects.requireNonNull(bot);
		this.broadcastedLevels = Objects.requireNonNull(broadcastedMessages);
	}

	@Override
	public Mono<Void> process0(AwardedLevelUpdatedEvent t) {
		var messageList = broadcastedLevels.getOrDefault(t.getNewLevel().getId(), List.of());
		var logText = "**Awarded Level Updated** for level " + GDUtils.levelToString(t.getNewLevel());
		return Mono.zip(bot.getEmoji("info"), bot.getEmoji("success"))
				.flatMap(emojis -> bot.log(emojis.getT1() + " GD event fired: " + logText)
						.onErrorResume(e -> Mono.empty())
						.then(Flux.fromIterable(messageList)
								.parallel().runOn(Schedulers.parallel())
								.flatMap(message -> GDUtils.shortLevelView(bot, t.getNewLevel(), message.getEmbeds().get(0).getAuthor().get().getName(),
												message.getEmbeds().get(0).getAuthor().get().getIconUrl())
										.flatMap(embed -> message.edit(mes -> mes.setEmbed(embed))))
								.collectSortedList(Comparator.comparing(Message::getId))
								.elapsed()
								.flatMap(tupleOfTimeAndMessageList -> {
									var time = Duration.ofMillis(tupleOfTimeAndMessageList.getT1());
									var formattedTime = (time.toDaysPart() > 0 ? time.toDaysPart() + "d " : "")
											+ (time.toHoursPart() > 0 ? time.toHoursPart() + "h " : "")
											+ (time.toMinutesPart() > 0 ? time.toMinutesPart() + "min " : "")
											+ (time.toSecondsPart() > 0 ? time.toSecondsPart() + "s " : "")
											+ (time.toMillisPart() > 0 ? time.toMillisPart() + "ms " : "");
									var oldList = broadcastedLevels.put(t.getNewLevel().getId(), tupleOfTimeAndMessageList.getT2());
									if (oldList == null) {
										return bot.log(emojis.getT1() + " Skipping " + logText + ": list of messages to edit is no longer available.");
									}
									return bot.log(emojis.getT2() + " Successfully processed event: " + logText + "\n"
											+ "Successfully edited **" + messageList.size() + "/" + oldList.size() + "** messages!\n"
											+ "**Execution time: " + formattedTime + "**").onErrorResume(e -> Mono.empty());
								}))).then();
	}

	@Override
	String logText0(AwardedLevelUpdatedEvent event) {
		return "**Awarded Level Updated** for level " + GDUtils.levelToString(event.getNewLevel());
	}
}
