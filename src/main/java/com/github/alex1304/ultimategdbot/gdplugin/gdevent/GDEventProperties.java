package com.github.alex1304.ultimategdbot.gdplugin.gdevent;

import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;

import com.github.alex1304.jdashevents.event.GDEvent;
import com.github.alex1304.ultimategdbot.api.Translator;
import com.github.alex1304.ultimategdbot.api.util.MessageSpecTemplate;
import com.github.alex1304.ultimategdbot.gdplugin.database.GDEventConfigData;

import discord4j.common.util.Snowflake;
import discord4j.core.object.entity.Message;
import reactor.core.publisher.Mono;
import reactor.function.Function3;

public class GDEventProperties<E extends GDEvent> {
	
	private final BiFunction<Translator, E, String> logText;
	private final String databaseField;
	private final Function<GDEventConfigData, Optional<Snowflake>> channelId;
	private final Function<GDEventConfigData, Optional<Snowflake>> roleId;
	private final Function<E, Optional<Long>> levelIdGetter;
	private final Function<E, Mono<Long>> recipientAccountId;
	private final Function3<E, GDEventConfigData, Message, Mono<MessageSpecTemplate>> messageTemplateFactory;
	private final Function<E, String> congratMessage;
	private final GDEventBroadcastStrategy broadcastStrategy;

	GDEventProperties(BiFunction<Translator, E, String> logText, String databaseField, Function<GDEventConfigData, Optional<Snowflake>> channelId,
			Function<GDEventConfigData, Optional<Snowflake>> roleId, Function<E, Optional<Long>> levelIdGetter,
			Function<E, Mono<Long>> recipientAccountId,
			Function3<E, GDEventConfigData, Message, Mono<MessageSpecTemplate>> messageTemplateFactory,
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
	public String logText(Translator tr, GDEvent event) {
		return logText.apply(tr, (E) event);
	}
	
	public String databaseField() {
		return databaseField;
	}

	@SuppressWarnings("unchecked")
	public Mono<Long> recipientAccountId(GDEvent event) {
		return recipientAccountId.apply((E) event);
	}
	
	@SuppressWarnings("unchecked")
	public Mono<MessageSpecTemplate> createMessageTemplate(GDEvent event, GDEventConfigData gsg, Message old) {
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
	
	public Snowflake channelId(GDEventConfigData gsg) {
		return channelId.apply(gsg).orElseThrow();
	}
	
	public Snowflake roleId(GDEventConfigData gsg) {
		return roleId.apply(gsg).orElseThrow();
	}

	public GDEventBroadcastStrategy broadcastStrategy() {
		return broadcastStrategy;
	}
}
