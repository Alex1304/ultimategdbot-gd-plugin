package com.github.alex1304.ultimategdbot.gdplugin;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import discord4j.core.DiscordClient;
import discord4j.core.object.entity.MessageChannel;
import discord4j.core.object.entity.Role;
import discord4j.core.object.util.Snowflake;
import reactor.core.publisher.Mono;
import reactor.function.TupleUtils;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

public class BroadcastPreloader {
	
	private final Map<Snowflake, MessageChannel> preloadedChannels;
	private final Set<Snowflake> invalidChannelSnowflakes;
	private final Map<Snowflake, Role> preloadedRoles;
	private final Set<Snowflake> invalidRoleSnowflakes;
	private final DiscordClient client;
	
	public BroadcastPreloader(DiscordClient client) {
		this.preloadedChannels = new HashMap<>();
		this.invalidChannelSnowflakes = new HashSet<>();
		this.preloadedRoles = new HashMap<>();
		this.invalidRoleSnowflakes = new HashSet<>();
		this.client = client;
	}
	
	public Mono<MessageChannel> preloadChannel(Snowflake channelId) {
		if (invalidChannelSnowflakes.contains(channelId)) {
			return Mono.empty();
		}
		return Mono.just(channelId)
				.filter(preloadedChannels::containsKey)
				.map(preloadedChannels::get)
				.switchIfEmpty(Mono.just(channelId)
						.flatMap(snowflake -> client.getChannelById(snowflake)
								.switchIfEmpty(Mono.error(new RuntimeException("Empty")))
								.doOnError(e -> invalidChannelSnowflakes.add(snowflake))
								.onErrorResume(e -> Mono.empty())
								.ofType(MessageChannel.class)
								.map(channel -> Tuples.of(snowflake, channel)))
						.doOnNext(TupleUtils.consumer(preloadedChannels::put))
						.map(Tuple2::getT2));
	}
	
	public Set<Snowflake> getInvalidChannelSnowflakes() {
		return Collections.unmodifiableSet(invalidChannelSnowflakes);
	}
	
	public Mono<Role> preloadRole(Snowflake guildId, Snowflake roleId) {
		if (invalidRoleSnowflakes.contains(roleId)) {
			return Mono.empty();
		}
		return Mono.just(roleId)
				.filter(preloadedRoles::containsKey)
				.map(preloadedRoles::get)
				.switchIfEmpty(Mono.just(roleId)
						.flatMap(snowflake -> client.getRoleById(guildId, snowflake)
								.switchIfEmpty(Mono.error(new RuntimeException("Empty")))
								.doOnError(e -> invalidRoleSnowflakes.add(snowflake))
								.onErrorResume(e -> Mono.empty())
								.map(role -> Tuples.of(snowflake, role)))
						.doOnNext(TupleUtils.consumer(preloadedRoles::put))
						.map(Tuple2::getT2));
	}
	
	public Set<Snowflake> getInvalidRoleSnowflakes() {
		return Collections.unmodifiableSet(invalidRoleSnowflakes);
	}
	
	public void unload() {
		preloadedChannels.clear();
		invalidChannelSnowflakes.clear();
		preloadedRoles.clear();
		invalidRoleSnowflakes.clear();
	}
}
