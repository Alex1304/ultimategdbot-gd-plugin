package com.github.alex1304.ultimategdbot.gdplugin.database;

import java.util.List;
import java.util.Optional;

import org.jdbi.v3.sqlobject.customizer.BindPojo;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

public interface GDModDao {
	
	String TABLE = "gd_mod";
	
	@SqlQuery("SELECT * FROM " + TABLE + " WHERE account_id = ?")
	Optional<GDModData> get(long accountId);
	
	@SqlUpdate("INSERT INTO " + TABLE + " VALUES (:accountId, :name, :elder)")
	void insert(@BindPojo GDModData data);
	
	@SqlUpdate("UPDATE " + TABLE + " SET name = :name, elder = :elder WHERE account_id = :accountId")
	void update(@BindPojo GDModData data);
	
	@SqlUpdate("DELETE FROM " + TABLE + " WHERE account_id = ?")
	void delete(long accountId);
	
	@SqlQuery("SELECT * FROM " + TABLE + " ORDER BY elder DESC, name")
	List<GDModData> getAll();
}
