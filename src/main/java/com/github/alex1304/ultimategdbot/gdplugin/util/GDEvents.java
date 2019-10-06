package com.github.alex1304.ultimategdbot.gdplugin.util;

import java.util.HashSet;
import java.util.stream.Collectors;

import com.github.alex1304.ultimategdbot.api.Bot;
import com.github.alex1304.ultimategdbot.gdplugin.database.GDSubscribedGuilds;

import discord4j.core.object.entity.Guild;
import discord4j.core.object.util.Snowflake;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.function.TupleUtils;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

public class GDEvents {
	
	private GDEvents() {
	}

	public static Flux<GDSubscribedGuilds> getExistingSubscribedGuilds(Bot bot, String hql) {
		return Mono.zip(bot.getMainDiscordClient().getGuilds().collectList(),
				bot.getDatabase().query(GDSubscribedGuilds.class, "from GDSubscribedGuilds " + hql).collectList())
						.flatMapMany(tuple -> {
							var subSet = new HashSet<>(tuple.getT2());
							var guildIds = tuple.getT1().stream().map(Guild::getId).map(Snowflake::asLong).collect(Collectors.toSet());
							subSet.removeIf(sub -> !guildIds.contains(sub.getGuildId()));
							return Flux.fromIterable(subSet);
						});
	}

	public static Mono<Tuple2<Long, Long>> preloadBroadcastChannelsAndRoles(Bot bot, BroadcastPreloader preloader) {
		return Flux.concat(getExistingSubscribedGuilds(bot, "where channelAwardedLevelsId > 0")
						.map(GDSubscribedGuilds::getChannelAwardedLevelsId), 
				getExistingSubscribedGuilds(bot, "where channelTimelyLevelsId > 0")
						.map(GDSubscribedGuilds::getChannelTimelyLevelsId), 
				getExistingSubscribedGuilds(bot, "where channelGdModeratorsId > 0")
						.map(GDSubscribedGuilds::getChannelGdModeratorsId), 
				getExistingSubscribedGuilds(bot, "where channelChangelogId > 0")
						.map(GDSubscribedGuilds::getChannelChangelogId))
				.distinct()
				.map(Snowflake::of)
				.concatMap(preloader::preloadChannel)
				.count()
				.zipWith(Flux.concat(getExistingSubscribedGuilds(bot, "where roleAwardedLevelsId > 0")
						.map(subscribedGuild -> Tuples.of(Snowflake.of(subscribedGuild.getGuildId()), Snowflake.of(subscribedGuild.getRoleAwardedLevelsId()))), 
				getExistingSubscribedGuilds(bot, "where roleTimelyLevelsId > 0")
						.map(subscribedGuild -> Tuples.of(Snowflake.of(subscribedGuild.getGuildId()), Snowflake.of(subscribedGuild.getRoleTimelyLevelsId()))), 
				getExistingSubscribedGuilds(bot, "where roleGdModeratorsId > 0")
						.map(subscribedGuild -> Tuples.of(Snowflake.of(subscribedGuild.getGuildId()), Snowflake.of(subscribedGuild.getRoleGdModeratorsId()))))
				.distinct()
				.concatMap(TupleUtils.function(preloader::preloadRole))
				.count());
	}
}
