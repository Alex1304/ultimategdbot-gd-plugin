package com.github.alex1304.ultimategdbot.gdplugin.database;

import java.sql.Timestamp;

import org.immutables.value.Value;

@Value.Immutable
public interface GDLeaderboardData {
	
	long accountId();
	
	String name();
	
	int stars();
	
	int diamonds();
	
	int userCoins();
	
	int secretCoins();
	
	int demons();
	
	int creatorPoints();
	
	Timestamp lastRefreshed();
}
