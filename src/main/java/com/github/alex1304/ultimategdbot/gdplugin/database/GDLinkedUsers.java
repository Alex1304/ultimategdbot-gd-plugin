package com.github.alex1304.ultimategdbot.gdplugin.database;

import static java.util.Objects.requireNonNullElse;

public class GDLinkedUsers {
	private long discordUserId;
	private long gdAccountId;
	private boolean isLinkActivated;
	private String confirmationToken;

	public long getDiscordUserId() {
		return discordUserId;
	}

	public void setDiscordUserId(Long discordUserId) {
		this.discordUserId = requireNonNullElse(discordUserId, 0L);
	}

	public long getGdAccountId() {
		return gdAccountId;
	}

	public void setGdAccountId(Long gdAccountId) {
		this.gdAccountId = requireNonNullElse(gdAccountId, 0L);
	}

	public boolean getIsLinkActivated() {
		return isLinkActivated;
	}

	public void setIsLinkActivated(Boolean isLinkActivated) {
		this.isLinkActivated = requireNonNullElse(isLinkActivated, false);
	}

	public String getConfirmationToken() {
		return confirmationToken;
	}

	public void setConfirmationToken(String confirmationToken) {
		this.confirmationToken = confirmationToken;
	}
}
