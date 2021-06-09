package com.github.alex1304.ultimategdbot.gdplugin.database;

import org.jdbi.v3.core.transaction.TransactionIsolationLevel;
import org.jdbi.v3.sqlobject.customizer.BindList;
import org.jdbi.v3.sqlobject.customizer.BindPojo;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;
import org.jdbi.v3.sqlobject.transaction.Transaction;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

public interface GDLeaderboardDao {

	String TABLE = "gd_leaderboard";

    @SqlQuery("SELECT * FROM " + TABLE)
    List<GDLeaderboardData> getAll();
	
	@SqlQuery("SELECT * FROM " + TABLE + " WHERE account_id IN (<gdAccountIds>) ORDER BY last_refreshed DESC")
	List<GDLeaderboardData> getAllIn(@BindList List<Long> gdAccountIds);
	
	@SqlQuery("SELECT last_refreshed FROM " + TABLE + " ORDER BY last_refreshed DESC LIMIT 1")
	Optional<Timestamp> getLastRefreshed();
	
	@SqlUpdate("INSERT INTO " + TABLE + " VALUES (:accountId, :name, :stars, :diamonds, :userCoins, "
			+ ":secretCoins, :demons, :creatorPoints, :lastRefreshed)")
	void insert(@BindPojo GDLeaderboardData data);
	
	@SqlUpdate("DELETE FROM " + TABLE + " WHERE account_id = ?")
	void delete(long accountId);
	
	@Transaction(TransactionIsolationLevel.SERIALIZABLE)
	default void cleanInsert(GDLeaderboardData data) {
		delete(data.accountId());
		insert(data);
	}
}
