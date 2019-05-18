package com.github.alex1304.ultimategdbot.gdplugin.leaderboard;

import java.util.Objects;

import com.github.alex1304.jdash.entity.GDUser;

public class LeaderboardEntry implements Comparable<LeaderboardEntry> {
	private final String emoji;
	private final int value;
	private final GDUser gdUser;
	private final String discordUser;
	
	public LeaderboardEntry(String emoji, int value, GDUser gdUser, String discordUser) {
		this.emoji = Objects.requireNonNull(emoji);
		this.value = value;
		this.gdUser = Objects.requireNonNull(gdUser);
		this.discordUser = Objects.requireNonNull(discordUser);
	}
	
	public String getEmoji() {
		return emoji;
	}

	public int getValue() {
		return value;
	}

	public GDUser getGdUser() {
		return gdUser;
	}

	public String getDiscordUser() {
		return discordUser;
	}

	@Override
	public int compareTo(LeaderboardEntry o) {
		return value == o.value ? gdUser.getName().compareToIgnoreCase(o.gdUser.getName()) : o.value - value;
	}

	@Override
	public String toString() {
		return "LeaderboardEntry{" + gdUser.getName() + ": " + value + " " + emoji + "}";
	}
}
