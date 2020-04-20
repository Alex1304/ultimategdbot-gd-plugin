package com.github.alex1304.ultimategdbot.gdplugin.database;

import org.jdbi.v3.sqlobject.customizer.BindPojo;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

public interface GDLevelRequestReviewDao {

	String TABLE = "gd_level_request_review";
	
	@SqlUpdate("INSERT INTO " + TABLE + " VALUES (NULL, :reviewerId, "
			+ ":reviewTimestamp, :reviewContent, :submissionId)")
	void insert(@BindPojo GDLevelRequestReviewData data);
	
	@SqlUpdate("DELETE FROM " + TABLE + " WHERE review_id = ?")
	void delete(long id);
}
