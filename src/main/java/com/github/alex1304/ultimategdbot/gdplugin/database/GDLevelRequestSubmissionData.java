package com.github.alex1304.ultimategdbot.gdplugin.database;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.immutables.value.Value;

import discord4j.rest.util.Snowflake;

@Value.Immutable
public interface GDLevelRequestSubmissionData {
	
	long submissionId();
	
	long levelId();
	
	Optional<String> youtubeLink();
	
	Optional<Snowflake> messageId();
	
	Optional<Snowflake> messageChannelId();
	
	Snowflake guildId();
	
	Snowflake submitterId();
	
	Instant submissionTimestamp();
	
	boolean isReviewed();
	
	List<GDLevelRequestReviewData> reviews();
}
