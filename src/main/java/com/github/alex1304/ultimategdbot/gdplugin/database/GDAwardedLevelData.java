package com.github.alex1304.ultimategdbot.gdplugin.database;

import java.time.Instant;

import org.immutables.value.Value;

@Value.Immutable
public interface GDAwardedLevelData {
	
	long levelId();
	
	Instant insertDate();
	
	int downloads();
	
	int likes();
}
