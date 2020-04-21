package com.github.alex1304.ultimategdbot.gdplugin.database;

import java.util.Optional;

import org.jdbi.v3.core.transaction.TransactionIsolationLevel;
import org.jdbi.v3.sqlobject.customizer.BindPojo;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;
import org.jdbi.v3.sqlobject.transaction.Transaction;

public interface GDAwardedLevelDao {

	String TABLE = "gd_awarded_level";
	
	@SqlUpdate("INSERT INTO " + TABLE + " VALUES (:levelId, :insertDate, :downloads, :likes)")
	void insert(@BindPojo GDAwardedLevelData data);
	
	@SqlUpdate("UPDATE " + TABLE + " SET "
			+ "insert_date = :insertDate, "
			+ "downloads = :downloads, "
			+ "likes = :likes "
			+ "WHERE level_id = :levelId")
	void update(@BindPojo GDAwardedLevelData data);
	
	@SqlQuery("SELECT * FROM " + TABLE + " WHERE level_id = ?")
	Optional<GDAwardedLevelData> get(long levelId);
	
	@Transaction(TransactionIsolationLevel.SERIALIZABLE)
	default void insertOrUpdate(GDAwardedLevelData data) {
		get(data.levelId()).ifPresentOrElse(__ -> update(data), () -> insert(data));
	}
}
