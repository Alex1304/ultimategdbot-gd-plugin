package com.github.alex1304.ultimategdbot.gdplugin;

public class GDLeaderboardBans {
	
	private long accountId;
	private long bannedBy;
	
	public long getAccountId() {
		return accountId;
	}
	
	public void setAccountId(long accountId) {
		this.accountId = accountId;
	}
	
	public long getBannedBy() {
		return bannedBy;
	}
	
	public void setBannedBy(long bannedBy) {
		this.bannedBy = bannedBy;
	}
}
