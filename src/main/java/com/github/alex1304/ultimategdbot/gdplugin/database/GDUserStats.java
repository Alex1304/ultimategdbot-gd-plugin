package com.github.alex1304.ultimategdbot.gdplugin.database;

import static java.util.Objects.requireNonNullElse;

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

	public void setAccountId(Long accountId) {
		this.accountId = requireNonNullElse(accountId, 0L);
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

	public void setStars(Integer stars) {
		this.stars = requireNonNullElse(stars, 0);
	}

	public int getDiamonds() {
		return diamonds;
	}

	public void setDiamonds(Integer diamonds) {
		this.diamonds = requireNonNullElse(diamonds, 0);
	}

	public int getUserCoins() {
		return userCoins;
	}

	public void setUserCoins(Integer userCoins) {
		this.userCoins = requireNonNullElse(userCoins, 0);
	}

	public int getSecretCoins() {
		return secretCoins;
	}

	public void setSecretCoins(Integer secretCoins) {
		this.secretCoins = requireNonNullElse(secretCoins, 0);
	}

	public int getDemons() {
		return demons;
	}

	public void setDemons(Integer demons) {
		this.demons = requireNonNullElse(demons, 0);
	}

	public int getCreatorPoints() {
		return creatorPoints;
	}

	public void setCreatorPoints(Integer creatorPoints) {
		this.creatorPoints = requireNonNullElse(creatorPoints, 0);
	}

	public Timestamp getLastRefreshed() {
		return lastRefreshed;
	}

	public void setLastRefreshed(Timestamp lastRefreshed) {
		this.lastRefreshed = lastRefreshed;
	}
}
