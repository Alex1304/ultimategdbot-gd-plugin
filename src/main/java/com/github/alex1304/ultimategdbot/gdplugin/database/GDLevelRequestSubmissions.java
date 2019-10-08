package com.github.alex1304.ultimategdbot.gdplugin.database;

import static java.util.Objects.requireNonNullElse;

import java.sql.Timestamp;
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
	
	public void setId(Long id) {
		this.id = requireNonNullElse(id, 0L);
	}
	
	public long getLevelId() {
		return levelId;
	}
	
	public void setLevelId(Long levelId) {
		this.levelId = requireNonNullElse(levelId, 0L);
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
	
	public void setMessageId(Long messageId) {
		this.messageId = requireNonNullElse(messageId, 0L);
	}
	
	public long getMessageChannelId() {
		return messageChannelId;
	}
	
	public void setMessageChannelId(Long messageChannelId) {
		this.messageChannelId = requireNonNullElse(messageChannelId, 0L);
	}
	
	public long getGuildId() {
		return guildId;
	}

	public void setGuildId(Long guildId) {
		this.guildId = requireNonNullElse(guildId, 0L);
	}

	public long getSubmitterId() {
		return submitterId;
	}
	public void setSubmitterId(Long submitterId) {
		this.submitterId = requireNonNullElse(submitterId, 0L);
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

	public void setIsReviewed(Boolean isReviewed) {
		this.isReviewed = requireNonNullElse(isReviewed, false);
	}

	public Set<GDLevelRequestReviews> getReviews() {
		return reviews;
	}

	public void setReviews(Set<GDLevelRequestReviews> reviews) {
		this.reviews = reviews;
	}
}
