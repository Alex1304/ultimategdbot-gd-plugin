package com.github.alex1304.ultimategdbot.gdplugin.database;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.jdbi.v3.core.mapper.MapMapper;
import org.jdbi.v3.sqlobject.SingleValue;
import org.jdbi.v3.sqlobject.config.RegisterRowMapper;
import org.jdbi.v3.sqlobject.customizer.BindPojo;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

import com.github.alex1304.ultimategdbot.api.guildconfig.GuildConfigDao;

@RegisterRowMapper(MapMapper.class)
public interface GDLevelRequestConfigDao extends GuildConfigDao<GDLevelRequestConfigData> {

	String TABLE = "gd_level_request_config";
	
	@Override
	@SqlUpdate("INSERT INTO " + TABLE + "(guild_id) VALUES (?)")
	void create(long guildId);

	@Override
	@SqlUpdate("UPDATE " + TABLE + " SET "
			+ "channel_submission_queue_id = DEFAULT(channel_submission_queue_id), "
			+ "channel_archived_submissions_id = DEFAULT(channel_archived_submissions_id), "
			+ "role_reviewer_id = DEFAULT(role_reviewer_id), "
			+ "open = DEFAULT(open), "
			+ "max_queued_submissions_per_user = DEFAULT(max_queued_submissions_per_user), "
			+ "min_reviews_required = DEFAULT(min_reviews_required) "
			+ "WHERE guild_id = ?")
	void reset(long guildId);

	@Override
	@SqlUpdate("UPDATE " + TABLE + " SET "
			+ "channel_submission_queue_id = :channelSubmissionQueueId, "
			+ "channel_archived_submissions_id = :channelArchivedSubmissionsId, "
			+ "role_reviewer_id = :roleReviewerId, "
			+ "open = :open, "
			+ "max_queued_submissions_per_user = :maxQueuedSubmissionsPerUser, "
			+ "min_reviews_required = :minReviewsRequired "
			+ "WHERE guild_id = :guildId")
	void update(@BindPojo GDLevelRequestConfigData data);

	@Override
	@SqlQuery("SELECT * FROM " + TABLE + " WHERE guild_id = ?")
	Optional<GDLevelRequestConfigData> get(long guildId);
	
	@SqlQuery("SELECT * FROM " + TABLE + " WHERE guild_id = ?")
	@SingleValue
	Map<String, Object> get2(long guildId);
	
	@SqlQuery("SELECT * FROM " + TABLE)
	List<GDLevelRequestConfigData> getAll();
	
	@SqlUpdate("UPDATE " + TABLE + " SET open = :open WHERE guild_id = :guildId")
	void toggleOpenState(long guildId, boolean open);
}
