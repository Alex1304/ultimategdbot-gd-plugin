package com.github.alex1304.ultimategdbot.gdplugin.database;

import static java.util.Objects.requireNonNullElse;

import java.sql.Timestamp;

public class GDLevelRequestReviews {
	
	private long id;
	private long reviewerId;
	private Timestamp reviewTimestamp;
	private String reviewContent;
	private GDLevelRequestSubmissions submission;
	
	public long getId() {
		return id;
	}
	
	public void setId(Long id) {
		this.id = requireNonNullElse(id, 0L);
	}
	
	public long getReviewerId() {
		return reviewerId;
	}
	
	public void setReviewerId(Long reviewerId) {
		this.reviewerId = requireNonNullElse(reviewerId, 0L);
	}
	
	public Timestamp getReviewTimestamp() {
		return reviewTimestamp;
	}
	
	public void setReviewTimestamp(Timestamp reviewTimestamp) {
		this.reviewTimestamp = reviewTimestamp;
	}
	
	public String getReviewContent() {
		return reviewContent;
	}
	
	public void setReviewContent(String reviewContent) {
		this.reviewContent = reviewContent;
	}
	
	public GDLevelRequestSubmissions getSubmission() {
		return submission;
	}
	
	public void setSubmission(GDLevelRequestSubmissions submission) {
		this.submission = submission;
	}
}
