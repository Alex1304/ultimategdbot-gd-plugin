package com.github.alex1304.ultimategdbot.gdplugin.database;

import java.util.Optional;

import org.immutables.value.Value;

import com.github.alex1304.ultimategdbot.api.Bot;
import com.github.alex1304.ultimategdbot.api.guildconfig.GuildConfigData;
import com.github.alex1304.ultimategdbot.api.guildconfig.GuildConfigurator;

import discord4j.rest.util.Snowflake;

@Value.Immutable
public interface GDEventConfigData extends GuildConfigData<GDEventConfigData> {
	
	Optional<Snowflake> channelAwardedLevels();
	
	Optional<Snowflake> channelTimelyLevels();
	
	Optional<Snowflake> channelGdModerators();
	
	Optional<Snowflake> channelChangelog();
	
	Optional<Snowflake> roleAwardedLevels();
	
	Optional<Snowflake> roleTimelyLevels();
	
	Optional<Snowflake> roleGdModerators();
	
	@Override
	default GuildConfigurator<GDEventConfigData> configurator(Bot bot) {
		return GuildConfigurator.builder("Geometry Dash Notifications", this, null)
				.build();
	}
}
