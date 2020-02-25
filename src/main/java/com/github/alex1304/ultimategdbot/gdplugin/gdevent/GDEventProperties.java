package com.github.alex1304.ultimategdbot.gdplugin.gdevent;

import java.util.Optional;
import java.util.function.Function;

import com.github.alex1304.jdashevents.event.GDEvent;
import com.github.alex1304.ultimategdbot.api.util.MessageSpecTemplate;
import com.github.alex1304.ultimategdbot.gdplugin.database.GDSubscribedGuilds;

import discord4j.core.object.entity.Message;
import discord4j.core.object.util.Snowflake;
import reactor.core.publisher.Mono;
import reactor.function.Function3;

public class GDEventProperties<E extends GDEvent> {
	
	private final Function<E, String> logText;
	private final String databaseField;
	private final Function<GDSubscribedGuilds, Long> channelId;
	private final Function<GDSubscribedGuilds, Long> roleId;
	private final Function<E, Optional<Long>> levelIdGetter;
	private final Function<E, Mono<Long>> recipientAccountId;
	private final Function3<E, GDSubscribedGuilds, Message, Mono<MessageSpecTemplate>> messageTemplateFactory;
	private final Function<E, String> congratMessage;
	private final GDEventBroadcastStrategy broadcastStrategy;

	GDEventProperties(Function<E, String> logText, String databaseField, Function<GDSubscribedGuilds, Long> channelId,
			Function<GDSubscribedGuilds, Long> roleId, Function<E, Optional<Long>> levelIdGetter,
			Function<E, Mono<Long>> recipientAccountId,
			Function3<E, GDSubscribedGuilds, Message, Mono<MessageSpecTemplate>> messageTemplateFactory,
			Function<E, String> congratMessage, GDEventBroadcastStrategy broadcastStrategy) {
		this.logText = logText;
		this.databaseField = databaseField;
		this.channelId = channelId;
		this.roleId = roleId;
		this.levelIdGetter = levelIdGetter;
		this.recipientAccountId = recipientAccountId;
		this.messageTemplateFactory = messageTemplateFactory;
		this.congratMessage = congratMessage;
		this.broadcastStrategy = broadcastStrategy;
	}
	
	@SuppressWarnings("unchecked")
	public String logText(GDEvent event) {
		return logText.apply((E) event);
	}
	
	public String databaseField() {
		return databaseField;
	}

	@SuppressWarnings("unchecked")
	public Mono<Long> recipientAccountId(GDEvent event) {
		return recipientAccountId.apply((E) event);
	}
	
	@SuppressWarnings("unchecked")
	public Mono<MessageSpecTemplate> createMessageTemplate(GDEvent event, GDSubscribedGuilds gsg, Message old) {
		return messageTemplateFactory.apply((E) event, gsg, old);
	}

	@SuppressWarnings("unchecked")
	public String congratMessage(GDEvent event) {
		return congratMessage.apply((E) event);
	}
	
	@SuppressWarnings("unchecked")
	public Optional<Long> levelId(GDEvent event) {
		return levelIdGetter.apply((E) event);
	}
	
	public Snowflake channelId(GDSubscribedGuilds gsg) {
		return Snowflake.of(channelId.apply(gsg));
	}
	
	public Snowflake roleId(GDSubscribedGuilds gsg) {
		return Snowflake.of(roleId.apply(gsg));
	}

	public GDEventBroadcastStrategy broadcastStrategy() {
		return broadcastStrategy;
	}
}
