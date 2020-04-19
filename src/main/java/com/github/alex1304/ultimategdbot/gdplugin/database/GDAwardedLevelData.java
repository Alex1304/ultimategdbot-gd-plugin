package com.github.alex1304.ultimategdbot.gdplugin.database;

import java.sql.Timestamp;

import org.immutables.value.Value;

@Value.Immutable
public interface GDAwardedLevelData {
	
	long levelId();
	
	Timestamp insertDate();
	
	int downloads();
	
	int likes();
}
