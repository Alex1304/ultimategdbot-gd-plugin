package com.github.alex1304.ultimategdbot.gdplugin.database;

import java.util.List;
import java.util.Optional;

import org.jdbi.v3.sqlobject.customizer.BindList;
import org.jdbi.v3.sqlobject.customizer.BindPojo;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

public interface GDLeaderboardBanDao {

	String TABLE = "gd_leaderboard_ban";
	
	@SqlQuery("SELECT * FROM " + TABLE + " WHERE account_id IN (<accountIds>)")
	List<GDLeaderboardBanData> getAllIn(@BindList List<Long> accountIds);
	
	@SqlQuery("SELECT * FROM " + TABLE + " WHERE account_id = ?")
	Optional<GDLeaderboardBanData> get(long accountId);
	
	@SqlUpdate("INSERT INTO " + TABLE + " VALUES (:accountId, :bannedBy)")
	void insert(@BindPojo GDLeaderboardBanData data);

	@SqlUpdate("DELETE FROM " + TABLE + " WHERE account_id = ?")
	void delete(long accountId);
	
}
