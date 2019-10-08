package com.github.alex1304.ultimategdbot.gdplugin.database;

import static java.util.Objects.requireNonNullElse;

import com.github.alex1304.ultimategdbot.api.database.GuildSettings;

public class GDLevelRequestsSettings implements GuildSettings {
	
	private long guildId;
	private long submissionQueueChannelId;
	private long reviewedLevelsChannelId;
	private long reviewerRoleId;
	private boolean isOpen;
	private int maxQueuedSubmissionsPerPerson;
	private int maxReviewsRequired;

	@Override
	public long getGuildId() {
		return guildId;
	}

	@Override
	public void setGuildId(Long guildId) {
		this.guildId = requireNonNullElse(guildId, 0L);
	}

	public long getSubmissionQueueChannelId() {
		return submissionQueueChannelId;
	}

	public void setSubmissionQueueChannelId(Long submissionQueueChannelId) {
		this.submissionQueueChannelId = requireNonNullElse(submissionQueueChannelId, 0L);
	}

	public long getReviewedLevelsChannelId() {
		return reviewedLevelsChannelId;
	}

	public void setReviewedLevelsChannelId(Long reviewedLevelsChannelId) {
		this.reviewedLevelsChannelId = requireNonNullElse(reviewedLevelsChannelId, 0L);
	}

	public long getReviewerRoleId() {
		return reviewerRoleId;
	}

	public void setReviewerRoleId(Long reviewerRoleId) {
		this.reviewerRoleId = requireNonNullElse(reviewerRoleId, 0L);
	}

	public boolean getIsOpen() {
		return isOpen;
	}

	public void setIsOpen(Boolean isOpen) {
		this.isOpen = requireNonNullElse(isOpen, false);
	}

	public int getMaxQueuedSubmissionsPerPerson() {
		return maxQueuedSubmissionsPerPerson;
	}

	public void setMaxQueuedSubmissionsPerPerson(Integer maxQueuedSubmissionsPerPerson) {
		this.maxQueuedSubmissionsPerPerson = requireNonNullElse(maxQueuedSubmissionsPerPerson, 0);
	}

	public int getMaxReviewsRequired() {
		return maxReviewsRequired;
	}

	public void setMaxReviewsRequired(Integer maxReviewsRequired) {
		this.maxReviewsRequired = requireNonNullElse(maxReviewsRequired, 0);
	}
}
