package com.github.alex1304.ultimategdbot.gdplugin.database;

import java.time.Instant;

import org.immutables.value.Value;

import discord4j.rest.util.Snowflake;

@Value.Immutable
public interface GDLevelRequestReviewData {
	
	long reviewId();
	
	Snowflake reviewerId();
	
	Instant reviewTimestamp();
	
	String reviewContent();
	
	long submissionId();
}
