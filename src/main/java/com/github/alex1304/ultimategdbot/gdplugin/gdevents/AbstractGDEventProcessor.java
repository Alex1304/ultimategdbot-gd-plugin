package com.github.alex1304.ultimategdbot.gdplugin.gdevents;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;

import com.github.alex1304.jdash.client.AuthenticatedGDClient;
import com.github.alex1304.jdashevents.event.GDEvent;
import com.github.alex1304.ultimategdbot.api.Bot;
import com.github.alex1304.ultimategdbot.api.utils.BotUtils;
import com.github.alex1304.ultimategdbot.gdplugin.GDLinkedUsers;
import com.github.alex1304.ultimategdbot.gdplugin.GDSubscribedGuilds;
import com.github.alex1304.ultimategdbot.gdplugin.GDUtils;

import discord4j.core.object.entity.Message;
import discord4j.core.object.entity.MessageChannel;
import discord4j.core.object.entity.Role;
import discord4j.core.object.entity.User;
import discord4j.core.object.util.Snowflake;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

abstract class AbstractGDEventProcessor<E extends GDEvent> extends TypeSafeGDEventProcessor<E> {

	static final Random RANDOM_GENERATOR = new Random();
	
	final Bot bot;
	final Map<Long, List<Message>> broadcastedLevels;
	final AuthenticatedGDClient gdClient;
	final int broadcastMessageIntervalMillis;
	
	public AbstractGDEventProcessor(Class<E> clazz, Bot bot, int broadcastMessageIntervalMillis, Map<Long, List<Message>> broadcastedMessages,
			AuthenticatedGDClient gdClient) {
		super(clazz);
		this.bot = Objects.requireNonNull(bot);
		this.broadcastMessageIntervalMillis = broadcastMessageIntervalMillis;
		this.broadcastedLevels = Objects.requireNonNull(broadcastedMessages);
		this.gdClient = Objects.requireNonNull(gdClient);
	}

	@Override
	public Mono<Void> process0(E t) {
		return Mono.zip(bot.getEmoji("info"), bot.getEmoji("success"))
				.flatMap(emojis -> bot.log(emojis.getT1() + " GD event fired: " + logText(t))
						.onErrorResume(e -> Mono.empty())
						.then(congrat(t).concatWith(GDUtils.getExistingSubscribedGuilds(bot, "where " + databaseField() + " > 0")
										.delayElements(Duration.ofMillis(broadcastMessageIntervalMillis))
										.flatMap(this::findChannel)
										.flatMap(this::findRole))
								.flatMap(tuple -> sendOne(t, tuple.getT1(), tuple.getT2()))
								.collectList()
								.elapsed()
								.flatMap(tupleOfTimeAndMessageList -> {
									var time = Duration.ofMillis(tupleOfTimeAndMessageList.getT1());
									var formattedTime = BotUtils.formatTimeMillis(time);
									var messageList = tupleOfTimeAndMessageList.getT2();
									onBroadcastSuccess(t, messageList);
									return bot.log(emojis.getT2() + " Successfully processed event: " + logText(t) + "\n"
											+ "Successfully notified **" + messageList.size() + "** guilds!\n"
											+ "**Execution time: " + formattedTime + "**").onErrorResume(e -> Mono.empty());
								}))).then();
	}
	
	abstract String databaseField();
	abstract long entityFieldChannel(GDSubscribedGuilds subscribedGuild);
	abstract long entityFieldRole(GDSubscribedGuilds subscribedGuild);
	abstract Mono<Message> sendOne(E event, MessageChannel channel, Optional<Role> roleToTag);
	abstract void onBroadcastSuccess(E event, List<Message> broadcastResult);
	abstract Mono<Long> accountIdGetter(E event);
	
	private Mono<Tuple2<GDSubscribedGuilds, MessageChannel>> findChannel(GDSubscribedGuilds subscribedGuild) {
		return bot.getDiscordClients().next()
				.flatMap(client -> client.getChannelById(Snowflake.of(entityFieldChannel(subscribedGuild)))
				.ofType(MessageChannel.class))
				.map(channel -> Tuples.of(subscribedGuild, channel))
				.onErrorResume(e -> Mono.empty());
	}
	
	private Mono<Tuple2<MessageChannel, Optional<Role>>> findRole(Tuple2<GDSubscribedGuilds, MessageChannel> tuple) {
		var subscribedGuild = tuple.getT1();
		var channel = tuple.getT2();
		return bot.getDiscordClients().flatMap(client -> client.getRoleById(Snowflake.of(subscribedGuild.getGuildId()),
						Snowflake.of(entityFieldRole(subscribedGuild))))
				.next()
				.map(Optional::of)
				.onErrorReturn(Optional.empty())
				.defaultIfEmpty(Optional.empty())
				.map(role -> Tuples.of(channel, role));
	}
	

	private Flux<Tuple2<MessageChannel, Optional<Role>>> congrat(E event) {
		return accountIdGetter(event).flux()
				.flatMap(accountId -> bot.getDatabase().query(GDLinkedUsers.class, "from GDLinkedUsers where gdAccountId = ?0", accountId))
				.flatMap(linkedUser -> bot.getDiscordClients().flatMap(client -> client.getUserById(Snowflake.of(linkedUser.getDiscordUserId()))))
				.flatMap(User::getPrivateChannel)
				.map(channel -> Tuples.of(channel, Optional.empty()));
	}
}
