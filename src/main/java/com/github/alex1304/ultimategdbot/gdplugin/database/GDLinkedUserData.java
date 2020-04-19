package com.github.alex1304.ultimategdbot.gdplugin.database;

import java.util.Optional;

import org.immutables.value.Value;

import discord4j.rest.util.Snowflake;

@Value.Immutable
public interface GDLinkedUserData {
	
	Snowflake discordUserId();
	
	Optional<Long> gdAccountId();
	
	boolean isLinkActivated();
	
	Optional<String> confirmationToken();
}
