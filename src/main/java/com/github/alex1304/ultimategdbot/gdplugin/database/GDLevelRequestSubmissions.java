package com.github.alex1304.ultimategdbot.gdplugin.database;

import java.sql.Timestamp;

public class GDLevelRequestSubmissions {
	
	private long id;
	private long levelId;
	private String youtubeLink;
	private long messageId;
	private long submitterId;
	private Timestamp submissionTimestamp;
	
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
}
