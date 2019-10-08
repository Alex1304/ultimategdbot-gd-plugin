package com.github.alex1304.ultimategdbot.gdplugin.database;

import static java.util.Objects.requireNonNullElse;

public class GDModList {

	private long accountId;
	private String name;
	private boolean isElder;
	
	public long getAccountId() {
		return accountId;
	}
	
	public void setAccountId(Long accountId) {
		this.accountId = requireNonNullElse(accountId, 0L);
	}
	
	public String getName() {
		return name;
	}
	
	public void setName(String name) {
		this.name = name;
	}
	
	public boolean getIsElder() {
		return isElder;
	}
	
	public void setIsElder(Boolean isElder) {
		this.isElder = requireNonNullElse(isElder, false);
	}

}
