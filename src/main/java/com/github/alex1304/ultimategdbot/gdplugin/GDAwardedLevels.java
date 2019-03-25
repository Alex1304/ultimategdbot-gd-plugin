package com.github.alex1304.ultimategdbot.gdplugin;

import java.sql.Timestamp;

public class GDAwardedLevels {
	
	private long levelId;
	private Timestamp insertDate;
	private int downloads;
	private int likes;
	
	public long getLevelId() {
		return levelId;
	}
	
	public void setLevelId(long levelId) {
		this.levelId = levelId;
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
	
	public void setDownloads(int downloads) {
		this.downloads = downloads;
	}
	
	public int getLikes() {
		return likes;
	}
	
	public void setLikes(int likes) {
		this.likes = likes;
	}
}
