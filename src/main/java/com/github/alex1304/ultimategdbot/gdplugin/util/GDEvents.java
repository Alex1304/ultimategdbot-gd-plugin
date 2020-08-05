package com.github.alex1304.ultimategdbot.gdplugin.util;

import java.util.function.Consumer;

import discord4j.core.spec.MessageCreateSpec;
import discord4j.core.spec.MessageEditSpec;
import discord4j.discordjson.json.MessageEditRequest;
import discord4j.rest.util.MultipartRequest;

public final class GDEvents {
	
	private GDEvents() {
	}
	
	public static MultipartRequest specToRequest(Consumer<MessageCreateSpec> specConsumer) {
		var spec = new MessageCreateSpec();
		specConsumer.accept(spec);
		return spec.asRequest();
	}
	
	public static MessageEditRequest editSpecToRequest(Consumer<MessageEditSpec> specConsumer) {
		var spec = new MessageEditSpec();
		specConsumer.accept(spec);
		return spec.asRequest();
	}
}
