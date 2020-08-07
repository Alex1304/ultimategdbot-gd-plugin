package com.github.alex1304.ultimategdbot.gdplugin.gdevent;

import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;

import com.github.alex1304.jdashevents.event.GDEvent;
import com.github.alex1304.ultimategdbot.api.Translator;
import com.github.alex1304.ultimategdbot.api.util.MessageSpecTemplate;

import discord4j.core.object.entity.Message;
import discord4j.rest.entity.RestChannel;
import reactor.core.publisher.Mono;

class GDEventProperties<E extends GDEvent> {
	
	private final BiFunction<Translator, E, String> logText;
	private final String databaseField;
	private final Function<E, RestChannel> channel;
	private final Function<E, Optional<Long>> levelIdGetter;
	private final Function<E, Mono<Long>> recipientAccountId;
	private final BiFunction<E, Message, Mono<MessageSpecTemplate>> messageTemplateFactory;
	private final Function<E, String> congratMessage;
	private final boolean isUpdate;

	GDEventProperties(BiFunction<Translator, E, String> logText, String databaseField,
			Function<E, RestChannel> channel, Function<E, Optional<Long>> levelIdGetter,
			Function<E, Mono<Long>> recipientAccountId,
			BiFunction<E, Message, Mono<MessageSpecTemplate>> messageTemplateFactory,
			Function<E, String> congratMessage, boolean isUpdate) {
		this.logText = logText;
		this.databaseField = databaseField;
		this.channel = channel;
		this.levelIdGetter = levelIdGetter;
		this.recipientAccountId = recipientAccountId;
		this.messageTemplateFactory = messageTemplateFactory;
		this.congratMessage = congratMessage;
		this.isUpdate = isUpdate;
	}
	
	@SuppressWarnings("unchecked")
	String logText(Translator tr, GDEvent event) {
		return logText.apply(tr, (E) event);
	}
	
	String databaseField() {
		return databaseField;
	}

	@SuppressWarnings("unchecked")
	Mono<Long> recipientAccountId(GDEvent event) {
		return recipientAccountId.apply((E) event);
	}
	
	@SuppressWarnings("unchecked")
	Mono<MessageSpecTemplate> createMessageTemplate(GDEvent event, Message old) {
		return messageTemplateFactory.apply((E) event, old);
	}

	@SuppressWarnings("unchecked")
	String congratMessage(GDEvent event) {
		return congratMessage.apply((E) event);
	}
	
	@SuppressWarnings("unchecked")
	Optional<Long> levelId(GDEvent event) {
		return levelIdGetter.apply((E) event);
	}
	
	@SuppressWarnings("unchecked")
	RestChannel channel(GDEvent event) {
		return channel.apply((E) event);
	}

	boolean isUpdate() {
		return isUpdate;
	}
}
