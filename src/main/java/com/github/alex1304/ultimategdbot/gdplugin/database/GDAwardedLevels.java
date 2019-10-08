package com.github.alex1304.ultimategdbot.gdplugin.database;

import static java.util.Objects.requireNonNullElse;

import java.sql.Timestamp;

public class GDAwardedLevels {
	
	private long levelId;
	private Timestamp insertDate;
	private int downloads;
	private int likes;
	
	public long getLevelId() {
		return levelId;
	}
	
	public void setLevelId(Long levelId) {
		this.levelId = requireNonNullElse(levelId, 0L);
	}
	
	public Timestamp getInsertDate() {
		return insertDate;
	}
	
	public void setInsertDate(Timestamp insertDate) {
		this.insertDate = insertDate;
	}
	
	public int getDownloads() {
		return downloads;
	}
	
	public void setDownloads(Integer downloads) {
		this.downloads = requireNonNullElse(downloads, 0);
	}
	
	public int getLikes() {
		return likes;
	}
	
	public void setLikes(Integer likes) {
		this.likes = requireNonNullElse(likes, 0);
	}
}
