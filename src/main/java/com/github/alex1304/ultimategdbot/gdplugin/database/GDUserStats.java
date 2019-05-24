package com.github.alex1304.ultimategdbot.gdplugin.database;

import java.sql.Timestamp;

public class GDUserStats {
	
	private long accountId;
	private String name;
	private int stars;
	private int diamonds;
	private int userCoins;
	private int secretCoins;
	private int demons;
	private int creatorPoints;
	private Timestamp lastRefreshed;

	public long getAccountId() {
		return accountId;
	}

	public void setAccountId(long accountId) {
		this.accountId = accountId;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public int getStars() {
		return stars;
	}

	public void setStars(int stars) {
		this.stars = stars;
	}

	public int getDiamonds() {
		return diamonds;
	}

	public void setDiamonds(int diamonds) {
		this.diamonds = diamonds;
	}

	public int getUserCoins() {
		return userCoins;
	}

	public void setUserCoins(int userCoins) {
		this.userCoins = userCoins;
	}

	public int getSecretCoins() {
		return secretCoins;
	}

	public void setSecretCoins(int secretCoins) {
		this.secretCoins = secretCoins;
	}

	public int getDemons() {
		return demons;
	}

	public void setDemons(int demons) {
		this.demons = demons;
	}

	public int getCreatorPoints() {
		return creatorPoints;
	}

	public void setCreatorPoints(int creatorPoints) {
		this.creatorPoints = creatorPoints;
	}

	public Timestamp getLastRefreshed() {
		return lastRefreshed;
	}

	public void setLastRefreshed(Timestamp lastRefreshed) {
		this.lastRefreshed = lastRefreshed;
	}
}
