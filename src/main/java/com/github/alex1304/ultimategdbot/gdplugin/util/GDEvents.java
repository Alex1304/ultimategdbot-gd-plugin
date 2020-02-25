package com.github.alex1304.ultimategdbot.gdplugin.util;

import java.util.HashSet;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import com.github.alex1304.ultimategdbot.api.Bot;
import com.github.alex1304.ultimategdbot.gdplugin.database.GDSubscribedGuilds;

import discord4j.core.object.entity.Guild;
import discord4j.core.object.util.Snowflake;
import discord4j.core.spec.MessageCreateSpec;
import discord4j.rest.util.MultipartRequest;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class GDEvents {
	
	private GDEvents() {
	}

	public static Flux<GDSubscribedGuilds> getExistingSubscribedGuilds(Bot bot, String hql) {
		return Mono.zip(bot.getGateway().getGuilds().collectList(),
				bot.getDatabase().query(GDSubscribedGuilds.class, "from GDSubscribedGuilds " + hql).collectList())
						.flatMapMany(tuple -> {
							var subSet = new HashSet<>(tuple.getT2());
							var guildIds = tuple.getT1().stream().map(Guild::getId).map(Snowflake::asLong).collect(Collectors.toSet());
							subSet.removeIf(sub -> !guildIds.contains(sub.getGuildId()));
							return Flux.fromIterable(subSet);
						});
	}
	
	public static MultipartRequest specToRequest(Consumer<MessageCreateSpec> specConsumer) {
		var spec = new MessageCreateSpec();
		specConsumer.accept(spec);
		return spec.asRequest();
	}
}
