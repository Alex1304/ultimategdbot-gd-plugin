package com.github.alex1304.ultimategdbot.gdplugin.database;

import java.sql.Timestamp;

import org.immutables.value.Value;

@Value.Immutable
public interface GDLevelRequestReviewData {
	
	long id();
	
	long reviewerId();
	
	Timestamp reviewTimestamp();
	
	String reviewContent();
	
	long submissionId();
}
