package com.github.alex1304.ultimategdbot.gdplugin.gdevent.processor;

import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.alex1304.jdashevents.event.GDEvent;
import com.github.alex1304.ultimategdbot.api.utils.BotUtils;
import com.github.alex1304.ultimategdbot.gdplugin.GDPlugin;
import com.github.alex1304.ultimategdbot.gdplugin.database.GDLinkedUsers;
import com.github.alex1304.ultimategdbot.gdplugin.database.GDSubscribedGuilds;
import com.github.alex1304.ultimategdbot.gdplugin.util.GDUtils;

import discord4j.core.object.entity.Message;
import discord4j.core.object.entity.MessageChannel;
import discord4j.core.object.entity.Role;
import discord4j.core.object.entity.User;
import discord4j.core.object.util.Snowflake;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.function.TupleUtils;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

abstract class AbstractGDEventProcessor<E extends GDEvent> extends TypeSafeGDEventProcessor<E> {

	private static final Logger LOGGER = LoggerFactory.getLogger(AbstractGDEventProcessor.class);
	static final Random RANDOM_GENERATOR = new Random();
	final GDPlugin plugin;
	
	public AbstractGDEventProcessor(Class<E> clazz, GDPlugin plugin) {
		super(clazz);
		this.plugin = Objects.requireNonNull(plugin);
	}

	@Override
	public Mono<Void> process0(E t) {
		var logText = logText(t);
		var timeStart = new AtomicLong();
		LOGGER.info("Processing Geometry Dash event: {}", logText);
		return Mono.zip(plugin.getBot().getEmoji("info"), plugin.getBot().getEmoji("success"))
				.flatMap(emojis -> plugin.getBot().log(emojis.getT1() + " GD event fired: " + logText)
						.onErrorResume(e -> Mono.empty())
						.log("gdevent.before_dbload", Level.FINE)
						.and(congrat(t).mergeWith(GDUtils.getExistingSubscribedGuilds(plugin.getBot(), "where " + databaseField() + " > 0")
										.flatMap(this::findChannel)
										.flatMap(this::findRole))
								.log("gdevent.after_dbload", Level.FINE)
								.buffer()
								.doOnNext(buffer -> timeStart.set(System.nanoTime()))
								.flatMap(Flux::fromIterable)
								.parallel()
								.runOn(plugin.getGdEventScheduler())
								.flatMap(TupleUtils.function((channel, roleToTag) -> sendOne(t, channel, roleToTag)))
								.log("gdevent.message_sent", Level.FINE)
								.collectSortedList(Comparator.comparing(Message::getId), 2000)
								.flatMap(messageList -> {
									var time = System.nanoTime() - timeStart.get();
									var formattedTime = BotUtils.formatTimeMillis(Duration.ofNanos(time));
									var broadcastSpeed = ((int) ((messageList.size() / (double) time) * 1_000_000_000));
									onBroadcastSuccess(t, messageList);
									LOGGER.info("Finished processing Geometry Dash event: {} in {} ({} messages/s)", logText, formattedTime, broadcastSpeed);
									return plugin.getBot().log(emojis.getT2() + " Successfully processed event: " + logText + "\n"
											+ "Successfully notified **" + messageList.size() + "** guilds!\n"
											+ "**Execution time: " + formattedTime + "**\n"
											+ "**Average broadcast speed: " + broadcastSpeed + " messages/s**")
													.onErrorResume(e -> Mono.empty());
								})));
	}
	
	abstract String databaseField();
	abstract long entityFieldChannel(GDSubscribedGuilds subscribedGuild);
	abstract long entityFieldRole(GDSubscribedGuilds subscribedGuild);
	abstract Mono<Message> sendOne(E event, MessageChannel channel, Optional<Role> roleToTag);
	abstract void onBroadcastSuccess(E event, List<Message> broadcastResult);
	abstract Mono<Long> accountIdGetter(E event);
	
	private Mono<Tuple2<GDSubscribedGuilds, MessageChannel>> findChannel(GDSubscribedGuilds subscribedGuild) {
		return plugin.getPreloader().preloadChannel(Snowflake.of(entityFieldChannel(subscribedGuild)))
				.map(channel -> Tuples.of(subscribedGuild, channel))
				.log("gdevent.channel_found", Level.FINE)
				.onErrorResume(e -> Mono.empty());
	}
	
	private Mono<Tuple2<MessageChannel, Optional<Role>>> findRole(Tuple2<GDSubscribedGuilds, MessageChannel> tuple) {
		var subscribedGuild = tuple.getT1();
		var channel = tuple.getT2();
		return plugin.getPreloader().preloadRole(Snowflake.of(subscribedGuild.getGuildId()), Snowflake.of(entityFieldRole(subscribedGuild)))
				.map(Optional::of)
				.onErrorReturn(Optional.empty())
				.defaultIfEmpty(Optional.empty())
				.log("gdevent.role_found", Level.FINE)
				.map(role -> Tuples.of(channel, role));
	}
	
	Flux<Tuple2<MessageChannel, Optional<Role>>> congrat(E event) {
		return accountIdGetter(event)
				.flatMapMany(accountId -> plugin.getBot().getDatabase().query(GDLinkedUsers.class, "from GDLinkedUsers where gdAccountId = ?0", accountId))
				.flatMap(linkedUser -> plugin.getBot().getMainDiscordClient().getUserById(Snowflake.of(linkedUser.getDiscordUserId())))
				.flatMap(User::getPrivateChannel)
				.onErrorResume(e -> Mono.empty())
				.log("gdevent.congrat", Level.FINE)
				.map(channel -> Tuples.of(channel, Optional.empty()));
	}
}
