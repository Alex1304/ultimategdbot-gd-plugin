package com.github.alex1304.ultimategdbot.gdplugin.gdevents;

import java.util.Objects;
import java.util.Optional;

import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import com.github.alex1304.jdashevents.event.GDEvent;

import reactor.core.publisher.Flux;

public class GDEventSubscriber implements Subscriber<GDEvent> {
	
	private Optional<Subscription> subscription;
	private final Flux<GDEventProcessor> processors;
	
	public GDEventSubscriber(Flux<GDEventProcessor> processors) {
		this.subscription = Optional.empty();
		this.processors = Objects.requireNonNull(processors);
	}

	@Override
	public void onSubscribe(Subscription s) {
		this.subscription = Optional.of(s);
		s.request(1);
	}

	@Override
	public void onNext(GDEvent t) {
		processors.flatMap(processor -> processor.process(t))
				.doFinally(__ -> requestNext())
				.subscribe();
	}

	@Override
	public void onError(Throwable t) {
	}

	@Override
	public void onComplete() {
	}
	
	public void requestNext() {
		subscription.ifPresent(s -> s.request(1));
	}
}
