package com.github.alex1304.ultimategdbot.gdplugin.gdevents;

import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;

import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import com.github.alex1304.jdashevents.event.GDEvent;
import com.github.alex1304.ultimategdbot.api.Bot;
import com.github.alex1304.ultimategdbot.gdplugin.GDSubscribedGuilds;

import discord4j.core.object.entity.Message;
import discord4j.core.object.entity.MessageChannel;
import discord4j.core.object.entity.Role;
import discord4j.core.object.util.Snowflake;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuple3;
import reactor.util.function.Tuples;

abstract class GDEventSubscriber<E extends GDEvent> implements Subscriber<E> {

	static final Random RANDOM_GENERATOR = new Random();
	
	final Bot bot;
	private final Map<Long, List<Message>> broadcastedLevels;
	private Optional<Subscription> subscription;
	
	public GDEventSubscriber(Bot bot, Map<Long, List<Message>> broadcastedMessages) {
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
	public void onNext(E t) {
		Mono.zip(bot.getEmoji("info"), bot.getEmoji("success"))
				.flatMap(emojis -> bot.log(emojis.getT1() + " GD event fired: " + logText(t))
						.onErrorResume(e -> Mono.empty())
						.then(bot.getDatabase().query(GDSubscribedGuilds.class, "from GDSubscribedGuilds where " + databaseField() + " > 0")
								.parallel().runOn(Schedulers.parallel())
								.flatMap(this::findChannel)
								.flatMap(this::findRole)
								.flatMap(tuple -> sendOne(t, tuple.getT2(), tuple.getT3()))
								.collectSortedList(Comparator.comparing(Message::getId))
								.elapsed()
								.flatMap(tupleOfTimeAndMessageList -> {
									var time = Duration.ofMillis(tupleOfTimeAndMessageList.getT1());
									var formattedTime = (time.toDaysPart() > 0 ? time.toDaysPart() + "d " : "")
											+ (time.toHoursPart() > 0 ? time.toHoursPart() + "h " : "")
											+ (time.toMinutesPart() > 0 ? time.toMinutesPart() + "min " : "")
											+ (time.toSecondsPart() > 0 ? time.toSecondsPart() + "s " : "")
											+ (time.toMillisPart() > 0 ? time.toMillisPart() + "ms " : "");
									var messageList = tupleOfTimeAndMessageList.getT2();
									broadcastedLevels.put(getBroadcastKey(t), messageList);
									return bot.log(emojis.getT2() + " Successfully processed event: " + logText(t) + "\n"
											+ "Successfully notified " + messageList.size() + " guilds!\n"
											+ "**Execution time: " + formattedTime + "**").onErrorResume(e -> Mono.empty());
								})))
				.doAfterTerminate(() -> subscription.get().request(1))
				.subscribe();
	}
	
	abstract String logText(E event);
	abstract String databaseField();
	abstract Mono<Message> sendOne(E event, MessageChannel channel, Optional<Role> roleToTag);
	abstract long getBroadcastKey(E event);
	
	private Mono<Tuple2<GDSubscribedGuilds, MessageChannel>> findChannel(GDSubscribedGuilds subscribedGuild) {
		return bot.getDiscordClients().flatMap(client -> client.getChannelById(Snowflake.of(subscribedGuild.getChannelAwardedLevelsId()))
				.ofType(MessageChannel.class))
				.next()
				.map(channel -> Tuples.of(subscribedGuild, channel)).onErrorResume(e -> Mono.empty());
	}
	
	private Mono<Tuple3<GDSubscribedGuilds, MessageChannel, Optional<Role>>> findRole(Tuple2<GDSubscribedGuilds, MessageChannel> tuple) {
		var subscribedGuild = tuple.getT1();
		var channel = tuple.getT2();
		return bot.getDiscordClients().flatMap(client -> client.getRoleById(Snowflake.of(subscribedGuild.getGuildId()),
						Snowflake.of(subscribedGuild.getRoleAwardedLevelsId())))
				.next()
				.map(Optional::of)
				.onErrorReturn(Optional.empty())
				.defaultIfEmpty(Optional.empty())
				.map(role -> Tuples.of(subscribedGuild, channel, role));
	}
	

	@Override
	public void onError(Throwable t) {
	}

	@Override
	public void onComplete() {
	}
}
