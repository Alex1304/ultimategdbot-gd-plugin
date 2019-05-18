package com.github.alex1304.ultimategdbot.gdplugin.gdevent.processor;

import java.util.Objects;

import com.github.alex1304.jdashevents.event.GDEvent;

import reactor.core.publisher.Mono;

abstract class TypeSafeGDEventProcessor<E extends GDEvent> implements GDEventProcessor {
	
	private final Class<E> clazz;
	
	TypeSafeGDEventProcessor(Class<E> clazz) {
		this.clazz = Objects.requireNonNull(clazz);
	}

	@SuppressWarnings("unchecked")
	@Override
	public final Mono<Void> process(GDEvent event) {
		if (!clazz.isInstance(event)) {
			return Mono.empty();
		}
		return process0((E) event);
	}

	@SuppressWarnings("unchecked")
	@Override
	public final String logText(GDEvent event) {
		if (!clazz.isInstance(event)) {
			return "";
		}
		return logText0((E) event);
	}
	
	abstract Mono<Void> process0(E event);
	abstract String logText0(E event);
}
