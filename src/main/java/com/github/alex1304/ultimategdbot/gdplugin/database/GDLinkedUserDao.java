package com.github.alex1304.ultimategdbot.gdplugin.database;

import java.util.List;
import java.util.Optional;

import org.jdbi.v3.core.transaction.TransactionIsolationLevel;
import org.jdbi.v3.sqlobject.customizer.BindList;
import org.jdbi.v3.sqlobject.customizer.BindPojo;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;
import org.jdbi.v3.sqlobject.transaction.Transaction;

public interface GDLinkedUserDao {

	String TABLE = "gd_linked_user";
	
	@SqlUpdate("INSERT INTO " + TABLE + " VALUES (?, ?, 0, NULL)")
	void create(long discordUserId, long gdUserId);
	
	@SqlQuery("SELECT * FROM " + TABLE + " WHERE discord_user_id = ?")
	Optional<GDLinkedUserData> getByDiscordUserId(long discordUserId);
	
	@SqlUpdate("UPDATE " + TABLE + " SET "
			+ "gd_user_id = :gdUserId, "
			+ "confirmation_token = :confirmationToken "
			+ "WHERE discord_user_id = :discordUserId")
	void setUnconfirmedLink(@BindPojo GDLinkedUserData data);
	
	@SqlUpdate("UPDATE " + TABLE + " SET "
			+ "confirmation_token = NULL, "
			+ "link_activated = 1 "
			+ "WHERE discord_user_id = :discordUserId")
	void confirmLink(long discordUserId);
	
	@SqlUpdate("DELETE FROM " + TABLE + " WHERE discord_user_id = ?")
	void delete(long discordUserId);
	
	@Transaction(TransactionIsolationLevel.SERIALIZABLE)
	default GDLinkedUserData getOrCreate(long discordUserId, long gdUserId) {
		return getByDiscordUserId(discordUserId).orElseGet(() -> {
			create(discordUserId, gdUserId);
			return getByDiscordUserId(discordUserId).orElseThrow();
		});
	}
	
	@SqlQuery("SELECT * FROM " + TABLE + " WHERE link_activated = 1 AND discord_user_id IN (<discordUserIds>)")
	List<GDLinkedUserData> getAllIn(@BindList List<Long> discordUserIds);
	
	@SqlQuery("SELECT * FROM " + TABLE + " WHERE link_activated = 1")
	List<GDLinkedUserData> getAll();
	
	@SqlQuery("SELECT * FROM gd_linked_user WHERE gd_user_id = ? AND link_activated = 1")
	List<GDLinkedUserData> getLinkedAccountsForGdUser(long gdAccountId);
}
