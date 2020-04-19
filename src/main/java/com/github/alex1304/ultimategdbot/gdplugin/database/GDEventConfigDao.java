package com.github.alex1304.ultimategdbot.gdplugin.database;

import java.util.List;
import java.util.Optional;

import org.jdbi.v3.sqlobject.customizer.BindPojo;
import org.jdbi.v3.sqlobject.customizer.Define;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

import com.github.alex1304.ultimategdbot.api.guildconfig.GuildConfigDao;

public interface GDEventConfigDao extends GuildConfigDao<GDEventConfigData> {

	String TABLE = "gd_event_config";
	
	@Override
	@SqlUpdate("INSERT INTO " + TABLE + "(guild_id) VALUES (?)")
	void create(long guildId);

	@Override
	@SqlUpdate("UPDATE " + TABLE + " SET "
			+ "channel_awarded_levels_id = DEFAULT(channel_awarded_levels_id), "
			+ "channel_timely_levels_id = DEFAULT(channel_timely_levels_id), "
			+ "channel_gd_moderators_id = DEFAULT(channel_gd_moderators_id), "
			+ "channel_changelog_id = DEFAULT(channel_changelog_id), "
			+ "role_awarded_levels_id = DEFAULT(role_awarded_levels_id), "
			+ "role_timely_levels_id = DEFAULT(role_timely_levels_id), "
			+ "role_gd_moderators_id = DEFAULT(role_gd_moderators_id) "
			+ "WHERE guild_id = ?")
	void reset(long guildId);

	@Override
	@SqlUpdate("UPDATE " + TABLE + " SET "
			+ "channel_awarded_levels_id = :channelAwardedLevels, "
			+ "channel_timely_levels_id = :channelTimelyLevels, "
			+ "channel_gd_moderators_id = :channelGdModerators, "
			+ "channel_changelog_id = :channelChangelog, "
			+ "role_awarded_levels_id = :roleAwardedLevels, "
			+ "role_timely_levels_id = :roleTimelyLevels, "
			+ "role_gd_moderators_id = :roleGdModerators "
			+ "WHERE guild_id = :guildId")
	void update(@BindPojo GDEventConfigData data);

	@Override
	@SqlUpdate("SELECT * FROM " + TABLE + " WHERE guild_id = ?")
	Optional<GDEventConfigData> get(long guildId);
	
	@SqlQuery("SELECT * FROM " + TABLE + " WHERE channel_<channel>_id IS NOT NULL")
	List<GDEventConfigData> getAllWithChannel(@Define String channel);
}
