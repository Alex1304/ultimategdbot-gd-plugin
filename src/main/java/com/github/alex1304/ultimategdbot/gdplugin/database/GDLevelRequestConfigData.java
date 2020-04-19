package com.github.alex1304.ultimategdbot.gdplugin.database;

import java.util.Optional;

import org.immutables.value.Value;

import com.github.alex1304.ultimategdbot.api.Bot;
import com.github.alex1304.ultimategdbot.api.guildconfig.GuildConfigData;
import com.github.alex1304.ultimategdbot.api.guildconfig.GuildConfigurator;

import discord4j.rest.util.Snowflake;

@Value.Immutable
public interface GDLevelRequestConfigData extends GuildConfigData<GDLevelRequestConfigData> {
	
	Optional<Snowflake> channelSubmissionQueue();

	Optional<Snowflake> channelArchivedSubmissions();

	Optional<Snowflake> roleReviewer();
	
	boolean isOpen();
	
	int maxQueuedSubmissionPerUser();
	
	int minReviewsRequired();
	
	@Override
	default GuildConfigurator<GDLevelRequestConfigData> configurator(Bot bot) {
		return GuildConfigurator.builder("Geometry Dash Level Requests", this, null)
				.build();
	}
}
