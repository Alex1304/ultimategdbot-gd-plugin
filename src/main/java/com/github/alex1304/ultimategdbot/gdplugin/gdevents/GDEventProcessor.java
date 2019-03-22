package com.github.alex1304.ultimategdbot.gdplugin.gdevents;

import com.github.alex1304.jdashevents.event.GDEvent;

import reactor.core.publisher.Mono;

public interface GDEventProcessor {
	
	Mono<Void> process(GDEvent event);
	
	String logText(GDEvent event);
}
