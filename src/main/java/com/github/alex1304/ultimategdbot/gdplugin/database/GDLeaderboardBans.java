package com.github.alex1304.ultimategdbot.gdplugin.database;

import static java.util.Objects.requireNonNullElse;

public class GDLeaderboardBans {
	
	private long accountId;
	private long bannedBy;
	
	public long getAccountId() {
		return accountId;
	}
	
	public void setAccountId(Long accountId) {
		this.accountId = requireNonNullElse(accountId, 0L);
	}
	
	public long getBannedBy() {
		return bannedBy;
	}
	
	public void setBannedBy(Long bannedBy) {
		this.bannedBy = requireNonNullElse(bannedBy, 0L);
	}
}
