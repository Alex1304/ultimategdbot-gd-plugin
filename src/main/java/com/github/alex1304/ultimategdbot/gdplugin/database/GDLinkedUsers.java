package com.github.alex1304.ultimategdbot.gdplugin.database;

public class GDLinkedUsers {
	private long discordUserId;
	private long gdAccountId;
	private boolean isLinkActivated;
	private String confirmationToken;

	public long getDiscordUserId() {
		return discordUserId;
	}

	public void setDiscordUserId(long discordUserId) {
		this.discordUserId = discordUserId;
	}

	public long getGdAccountId() {
		return gdAccountId;
	}

	public void setGdAccountId(long gdAccountId) {
		this.gdAccountId = gdAccountId;
	}

	public boolean getIsLinkActivated() {
		return isLinkActivated;
	}

	public void setIsLinkActivated(boolean isLinkActivated) {
		this.isLinkActivated = isLinkActivated;
	}

	public String getConfirmationToken() {
		return confirmationToken;
	}

	public void setConfirmationToken(String confirmationToken) {
		this.confirmationToken = confirmationToken;
	}
}
