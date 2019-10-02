package com.github.alex1304.ultimategdbot.gdplugin.leaderboard;

import java.util.Objects;

import com.github.alex1304.ultimategdbot.gdplugin.database.GDUserStats;

public class LeaderboardEntry implements Comparable<LeaderboardEntry> {
	private final int value;
	private final GDUserStats stats;
	private final String discordUser;
	
	public LeaderboardEntry(int value, GDUserStats stats, String discordUser) {
		this.value = value;
		this.stats = Objects.requireNonNull(stats);
		this.discordUser = Objects.requireNonNull(discordUser);
	}

	public int getValue() {
		return value;
	}

	public GDUserStats getStats() {
		return stats;
	}

	public String getDiscordUser() {
		return discordUser;
	}

	@Override
	public int compareTo(LeaderboardEntry o) {
		return value == o.value ? stats.getName().compareToIgnoreCase(o.stats.getName()) : o.value - value;
	}

	@Override
	public String toString() {
		return "LeaderboardEntry{" + stats.getName() + ": " + value + "}";
	}
}
