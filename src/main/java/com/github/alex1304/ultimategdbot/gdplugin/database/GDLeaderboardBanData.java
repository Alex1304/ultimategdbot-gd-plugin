package com.github.alex1304.ultimategdbot.gdplugin.database;

import org.immutables.value.Value;

import discord4j.rest.util.Snowflake;

@Value.Immutable
public interface GDLeaderboardBanData {
	
	long accountId();
	
	Snowflake bannedBy();
}
