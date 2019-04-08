package com.github.alex1304.ultimategdbot.gdplugin;

import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import discord4j.core.DiscordClient;
import discord4j.core.object.entity.MessageChannel;
import discord4j.core.object.util.Snowflake;
import reactor.core.publisher.Mono;
import reactor.function.TupleUtils;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

public class ChannelLoader {
	
	private final Map<Snowflake, MessageChannel> cache;
	private final Set<Snowflake> invalidCache;
	private final DiscordClient client;
	
	public ChannelLoader(DiscordClient client) {
		this.cache = new HashMap<>();
		this.invalidCache = new HashSet<>();
		this.client = client;
	}
	
	public Mono<MessageChannel> load(Snowflake channelId) {
		if (invalidCache.contains(channelId)) {
			return Mono.empty();
		}
		return Mono.just(channelId)
				.filter(cache::containsKey)
				.map(cache::get)
				.switchIfEmpty(Mono.just(channelId)
						.delayElement(Duration.ofMillis(200))
						.flatMap(snowflake -> client.getChannelById(snowflake)
								.timeout(Duration.ofSeconds(10))
								.doOnError(e -> invalidCache.add(snowflake))
								.onErrorResume(e -> Mono.empty())
								.ofType(MessageChannel.class)
								.map(channel -> Tuples.of(snowflake, channel)))
						.doOnNext(TupleUtils.consumer(cache::put))
						.map(Tuple2::getT2));
	}
	
	public Set<Snowflake> getInvalidChannelSnowflakes() {
		return Collections.unmodifiableSet(invalidCache);
	}
	
	public void clearCache() {
		cache.clear();
		invalidCache.clear();
	}
}
