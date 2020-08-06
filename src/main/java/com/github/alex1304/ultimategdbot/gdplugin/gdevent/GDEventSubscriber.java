package com.github.alex1304.ultimategdbot.gdplugin.gdevent;

import org.reactivestreams.Subscription;

import com.github.alex1304.jdashevents.event.GDEvent;

import reactor.core.publisher.BaseSubscriber;
import reactor.core.scheduler.Scheduler;

class GDEventSubscriber extends BaseSubscriber<GDEvent> {
	
	private volatile Subscription subscription;
	private final GDEventService gdEventService;
	private final Scheduler scheduler;
	
	GDEventSubscriber(GDEventService gdEventService, Scheduler scheduler) {
		this.gdEventService = gdEventService;
		this.scheduler = scheduler;
	}

	@Override
	public void hookOnSubscribe(Subscription s) {
		this.subscription = s;
		s.request(1);
	}

	@Override
	public void hookOnNext(GDEvent t) {
		gdEventService.process(t)
				.subscribeOn(scheduler)
				.doFinally(__ -> subscription.request(1))
				.subscribe();
	}
}
