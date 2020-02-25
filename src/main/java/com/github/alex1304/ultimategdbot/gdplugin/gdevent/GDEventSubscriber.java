package com.github.alex1304.ultimategdbot.gdplugin.gdevent;

import org.reactivestreams.Subscription;

import com.github.alex1304.jdashevents.event.GDEvent;
import com.github.alex1304.ultimategdbot.api.Bot;
import com.github.alex1304.ultimategdbot.gdplugin.GDServiceMediator;

import reactor.core.publisher.BaseSubscriber;
import reactor.core.scheduler.Scheduler;

public class GDEventSubscriber extends BaseSubscriber<GDEvent> {
	
	private volatile Subscription subscription;
	private final GDEventProcessor processor;
	private final Scheduler scheduler;
	
	public GDEventSubscriber(Bot bot, GDServiceMediator gdServiceMediator) {
		this.processor = new GDEventProcessor(bot, gdServiceMediator);
		this.scheduler = gdServiceMediator.getGdEventScheduler();
	}

	@Override
	public void hookOnSubscribe(Subscription s) {
		this.subscription = s;
		s.request(1);
	}

	@Override
	public void hookOnNext(GDEvent t) {
		processor.process(t)
				.subscribeOn(scheduler)
				.doFinally(__ -> subscription.request(1))
				.subscribe();
	}
}
