package com.github.alex1304.ultimategdbot.gdplugin;

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
	public void setGuildId(long guildId) {
		this.guildId = guildId;
	}

	public long getSubmissionQueueChannelId() {
		return submissionQueueChannelId;
	}

	public void setSubmissionQueueChannelId(long submissionQueueChannelId) {
		this.submissionQueueChannelId = submissionQueueChannelId;
	}

	public long getReviewedLevelsChannelId() {
		return reviewedLevelsChannelId;
	}

	public void setReviewedLevelsChannelId(long reviewedLevelsChannelId) {
		this.reviewedLevelsChannelId = reviewedLevelsChannelId;
	}

	public long getReviewerRoleId() {
		return reviewerRoleId;
	}

	public void setReviewerRoleId(long reviewerRoleId) {
		this.reviewerRoleId = reviewerRoleId;
	}

	public boolean getIsOpen() {
		return isOpen;
	}

	public void setIsOpen(boolean isOpen) {
		this.isOpen = isOpen;
	}

	public int getMaxQueuedSubmissionsPerPerson() {
		return maxQueuedSubmissionsPerPerson;
	}

	public void setMaxQueuedSubmissionsPerPerson(int maxQueuedSubmissionsPerPerson) {
		this.maxQueuedSubmissionsPerPerson = maxQueuedSubmissionsPerPerson;
	}

	public int getMaxReviewsRequired() {
		return maxReviewsRequired;
	}

	public void setMaxReviewsRequired(int maxReviewsRequired) {
		this.maxReviewsRequired = maxReviewsRequired;
	}
}
