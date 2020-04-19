package com.github.alex1304.ultimategdbot.gdplugin.database;

import org.immutables.value.Value;

@Value.Immutable
public interface GDModData {

	long accountId();
	
	String name();
	
	boolean isElder();
}
