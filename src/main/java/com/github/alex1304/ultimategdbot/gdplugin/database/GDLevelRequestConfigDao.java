package com.github.alex1304.ultimategdbot.gdplugin.database;

import java.util.Optional;

import org.jdbi.v3.sqlobject.customizer.BindPojo;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

import com.github.alex1304.ultimategdbot.api.guildconfig.GuildConfigDao;

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
			+ "is_open = DEFAULT(is_open), "
			+ "max_queued_submissions_per_user = DEFAULT(max_queued_submissions_per_user), "
			+ "min_reviews_required = DEFAULT(min_reviews_required) "
			+ "WHERE guild_id = ?")
	void reset(long guildId);

	@Override
	@SqlUpdate("UPDATE " + TABLE + " SET "
			+ "channel_submission_queue_id = :channelSubmissionQueue, "
			+ "channel_archived_submissions_id = :channelArchivedSubmissions, "
			+ "role_reviewer_id = :roleReviewer, "
			+ "is_open = :isOpen, "
			+ "max_queued_submissions_per_user = :maxQueuedSubmissionPerUser, "
			+ "min_reviews_required = :minReviewsRequired "
			+ "WHERE guild_id = :guildId")
	void update(@BindPojo GDLevelRequestConfigData data);

	@Override
	@SqlUpdate("SELECT * FROM " + TABLE + " WHERE guild_id = ?")
	Optional<GDLevelRequestConfigData> get(long guildId);
}
