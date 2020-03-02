package com.github.alex1304.ultimategdbot.gdplugin.database;

import static java.util.Objects.requireNonNullElse;

import com.github.alex1304.ultimategdbot.api.database.GuildSettings;

public class GDLevelRequestsSettings implements GuildSettings {
	
	private Long guildId;
	private Long submissionQueueChannelId;
	private Long reviewedLevelsChannelId;
	private Long reviewerRoleId;
	private Boolean isOpen;
	private Integer maxQueuedSubmissionsPerPerson;
	private Integer maxReviewsRequired;

	@Override
	public Long getGuildId() {
		return guildId;
	}

	@Override
	public void setGuildId(Long guildId) {
		this.guildId = guildId;
	}

	public Long getSubmissionQueueChannelId() {
		return requireNonNullElse(submissionQueueChannelId, 0L);
	}

	public void setSubmissionQueueChannelId(Long submissionQueueChannelId) {
		this.submissionQueueChannelId = submissionQueueChannelId;
	}

	public Long getReviewedLevelsChannelId() {
		return requireNonNullElse(reviewedLevelsChannelId, 0L);
	}

	public void setReviewedLevelsChannelId(Long reviewedLevelsChannelId) {
		this.reviewedLevelsChannelId = reviewedLevelsChannelId;
	}

	public Long getReviewerRoleId() {
		return requireNonNullElse(reviewerRoleId, 0L);
	}

	public void setReviewerRoleId(Long reviewerRoleId) {
		this.reviewerRoleId = reviewerRoleId;
	}

	public Boolean getIsOpen() {
		return requireNonNullElse(isOpen, false);
	}

	public void setIsOpen(Boolean isOpen) {
		this.isOpen = isOpen;
	}

	public Integer getMaxQueuedSubmissionsPerPerson() {
		return requireNonNullElse(maxQueuedSubmissionsPerPerson, 0);
	}

	public void setMaxQueuedSubmissionsPerPerson(Integer maxQueuedSubmissionsPerPerson) {
		this.maxQueuedSubmissionsPerPerson = maxQueuedSubmissionsPerPerson;
	}

	public Integer getMaxReviewsRequired() {
		return requireNonNullElse(maxReviewsRequired, 0);
	}

	public void setMaxReviewsRequired(Integer maxReviewsRequired) {
		this.maxReviewsRequired = maxReviewsRequired;
	}
}
