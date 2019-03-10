package com.github.alex1304.ultimategdbot.gdplugin;

import java.util.Objects;

import com.github.alex1304.jdash.entity.GDUser;

import discord4j.core.object.entity.User;

public class LeaderboardEntry implements Comparable<LeaderboardEntry> {
	private final String emoji;
	private final int value;
	private final GDUser gdUser;
	private final User discordUser;
	
	public LeaderboardEntry(String emoji, int value, GDUser gdUser, User discordUser) {
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

	public User getDiscordUser() {
		return discordUser;
	}

	@Override
	public int compareTo(LeaderboardEntry o) {
		return value == o.value ? gdUser.getName().compareToIgnoreCase(o.gdUser.getName()) : value - o.value;
	}
}