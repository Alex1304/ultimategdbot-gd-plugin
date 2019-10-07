package com.github.alex1304.ultimategdbot.gdplugin.database;

import java.sql.Timestamp;
import java.util.Objects;
import java.util.Set;

public class GDLevelRequestSubmissions {
	
	private long id;
	private long levelId;
	private String youtubeLink;
	private long messageId;
	private long messageChannelId;
	private long guildId;
	private long submitterId;
	private Timestamp submissionTimestamp;
	private boolean isReviewed;
	private Set<GDLevelRequestReviews> reviews;
	
	public long getId() {
		return id;
	}
	
	public void setId(long id) {
		this.id = id;
	}
	
	public long getLevelId() {
		return levelId;
	}
	
	public void setLevelId(long levelId) {
		this.levelId = levelId;
	}
	
	public String getYoutubeLink() {
		return youtubeLink;
	}
	
	public void setYoutubeLink(String youtubeLink) {
		this.youtubeLink = youtubeLink;
	}
	
	public long getMessageId() {
		return messageId;
	}
	
	public void setMessageId(long messageId) {
		this.messageId = messageId;
	}
	
	public long getMessageChannelId() {
		return messageChannelId;
	}
	
	public void setMessageChannelId(Long messageChannelId) {
		this.messageChannelId = Objects.requireNonNullElse(messageChannelId, 0L);
	}
	
	public long getGuildId() {
		return guildId;
	}

	public void setGuildId(long guildId) {
		this.guildId = guildId;
	}

	public long getSubmitterId() {
		return submitterId;
	}
	public void setSubmitterId(long submitterId) {
		this.submitterId = submitterId;
	}
	
	public Timestamp getSubmissionTimestamp() {
		return submissionTimestamp;
	}
	
	public void setSubmissionTimestamp(Timestamp submissionTimestamp) {
		this.submissionTimestamp = submissionTimestamp;
	}

	public boolean getIsReviewed() {
		return isReviewed;
	}

	public void setIsReviewed(boolean isReviewed) {
		this.isReviewed = isReviewed;
	}

	public Set<GDLevelRequestReviews> getReviews() {
		return reviews;
	}

	public void setReviews(Set<GDLevelRequestReviews> reviews) {
		this.reviews = reviews;
	}
}
