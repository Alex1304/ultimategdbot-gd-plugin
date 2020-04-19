package com.github.alex1304.ultimategdbot.gdplugin.database;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

import org.immutables.value.Value;

@Value.Immutable
public interface GDLevelRequestSubmissionData {
	
	long id();
	
	long levelId();
	
	Optional<String> youtubeLink();
	
	long messageId();
	
	long messageChannelId();
	
	long guildId();
	
	long submitterId();
	
	Timestamp submissionTimestamp();
	
	boolean isReviewed();
	
	List<GDLevelRequestReviewData> reviews();
}
