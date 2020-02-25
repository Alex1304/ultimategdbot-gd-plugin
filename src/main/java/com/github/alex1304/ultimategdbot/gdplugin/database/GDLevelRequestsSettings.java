package com.github.alex1304.ultimategdbot.gdplugin.database;

import com.github.alex1304.ultimategdbot.api.database.GuildSettings;

public class GDLevelRequestsSettings implements GuildSettings {
	
	private Long guildId;
	private Long submissionQueueChannelId;
	private Long reviewedLevelsChannelId;
	private Long reviewerRoleId;
	private Boolean isOpen;
	private Integer maxQueuedSubmissionsPerPerson;
	private int maxReviewsRequired;

	@Override
	public Long getGuildId() {
		return guildId;
	}

	@Override
	public void setGuildId(Long guildId) {
		this.guildId = guildId;
	}

	public Long getSubmissionQueueChannelId() {
		return submissionQueueChannelId;
	}

	public void setSubmissionQueueChannelId(Long submissionQueueChannelId) {
		this.submissionQueueChannelId = submissionQueueChannelId;
	}

	public Long getReviewedLevelsChannelId() {
		return reviewedLevelsChannelId;
	}

	public void setReviewedLevelsChannelId(Long reviewedLevelsChannelId) {
		this.reviewedLevelsChannelId = reviewedLevelsChannelId;
	}

	public Long getReviewerRoleId() {
		return reviewerRoleId;
	}

	public void setReviewerRoleId(Long reviewerRoleId) {
		this.reviewerRoleId = reviewerRoleId;
	}

	public Boolean getIsOpen() {
		return isOpen;
	}

	public void setIsOpen(Boolean isOpen) {
		this.isOpen = isOpen;
	}

	public Integer getMaxQueuedSubmissionsPerPerson() {
		return maxQueuedSubmissionsPerPerson;
	}

	public void setMaxQueuedSubmissionsPerPerson(Integer maxQueuedSubmissionsPerPerson) {
		this.maxQueuedSubmissionsPerPerson = maxQueuedSubmissionsPerPerson;
	}

	public Integer getMaxReviewsRequired() {
		return maxReviewsRequired;
	}

	public void setMaxReviewsRequired(Integer maxReviewsRequired) {
		this.maxReviewsRequired = maxReviewsRequired;
	}
}
