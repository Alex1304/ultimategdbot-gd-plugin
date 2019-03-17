package com.github.alex1304.ultimategdbot.gdplugin.gdevents;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import com.github.alex1304.jdashevents.event.AwardedLevelUpdatedEvent;
import com.github.alex1304.ultimategdbot.api.Bot;

import discord4j.core.object.entity.Message;

public class AwardedLevelUpdatedEventSubscriber implements Subscriber<AwardedLevelUpdatedEvent> {

	private final Bot bot;
	private final Map<Long, List<Message>> broadcastedLevels;
	private Optional<Subscription> subscription;
	
	public AwardedLevelUpdatedEventSubscriber(Bot bot, Map<Long, List<Message>> broadcastedMessages) {
		this.bot = Objects.requireNonNull(bot);
		this.broadcastedLevels = Objects.requireNonNull(broadcastedMessages);
		this.subscription = Optional.empty();
	}

	@Override
	public void onSubscribe(Subscription s) {
		this.subscription = Optional.of(s);
		s.request(1);
	}

	@Override
	public void onNext(AwardedLevelUpdatedEvent t) {
		var messageList = broadcastedLevels.get(t.getNewLevel().getId());
		if (messageList == null) {
			return;
		}
//		Mono.zip(bot.getEmoji("info"), bot.getEmoji("success"))
//		.flatMap(emojis -> bot.log(emojis.getT1() + " GD event fired: " + logText(t))
//				.onErrorResume(e -> Mono.empty())
//				.then(Flux.fromIterable(messageList)
//						.parallel().runOn(Schedulers.parallel())
//						.flatMap(message -> GDUtils.shortLevelView(bot, t.getNewLevel()).flatMap(embed -> message.edit(mes -> mes.setEmbed(embed))))
//						.collectSortedList(Comparator.comparing(Message::getId))
//						.elapsed()
//						.doOnSuccess(__-> {
//							var time = Duration.ofMillis(tupleOfTimeAndMessageList.getT1());
//							var formattedTime = (time.toDaysPart() > 0 ? time.toDaysPart() + "d " : "")
//									+ (time.toHoursPart() > 0 ? time.toHoursPart() + "h " : "")
//									+ (time.toMinutesPart() > 0 ? time.toMinutesPart() + "min " : "")
//									+ (time.toSecondsPart() > 0 ? time.toSecondsPart() + "s " : "")
//									+ (time.toMillisPart() > 0 ? time.toMillisPart() + "ms " : "");
//							return bot.log(emojis.getT2() + " Successfully processed event: " + logText(t) + "\n"
//									+ "**Execution time: " + formattedTime + "**").onErrorResume(e -> Mono.empty());
//						})))
//		.doAfterTerminate(() -> subscription.get().request(1))
//		.subscribe();
	}

	@Override
	public void onError(Throwable t) {
	}

	@Override
	public void onComplete() {
	}
}
