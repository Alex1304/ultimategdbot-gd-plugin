package com.github.alex1304.ultimategdbot.gdplugin.database;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.jdbi.v3.core.result.LinkedHashMapRowReducer;
import org.jdbi.v3.core.result.RowView;
import org.jdbi.v3.sqlobject.customizer.BindPojo;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;
import org.jdbi.v3.sqlobject.statement.UseRowReducer;

public interface GDLevelRequestSubmissionDao {
	
	String TABLE = "gd_level_request_submission";
	
	@SqlQuery("SELECT * FROM " + TABLE + " "
			+ "LEFT JOIN " + GDLevelRequestReviewDao.TABLE + " "
			+ "USING (submission_id)"
			+ "WHERE submission_id = ?")
	@UseRowReducer(SubmissionWithReviewReducer.class)
	Optional<GDLevelRequestSubmissionData> get(long id);
	
	@SqlQuery("SELECT * FROM " + TABLE + " WHERE guild_id = ? AND reviewed = 0 ORDER BY submission_timestamp")
	List<GDLevelRequestSubmissionData> getQueuedSubmissionsInGuild(long guildId);

	@SqlQuery("SELECT * FROM " + TABLE + " WHERE reviewed = 0")
	List<GDLevelRequestSubmissionData> getAllQueuedSubmissions();
	
	@SqlUpdate("DELETE FROM " + TABLE + " WHERE submission_id IN (<submissionIds>)")
	int deleteAllIn(List<Long> submissionIds);
	
	@SqlUpdate("DELETE FROM " + TABLE + " WHERE message_id = ?")
	void deleteByMessageId(long messageId);
	
	@SqlUpdate("INSERT INTO " + TABLE + " VALUES (NULL, :levelId, :youtubeLink, NULL, "
			+ "NULL, :guildId, :submitterId, :submissionTimestamp, :reviewed)")
	@GetGeneratedKeys
	long insert(@BindPojo GDLevelRequestSubmissionData data);
	
	@SqlUpdate("UPDATE " + TABLE + " SET message_channel_id = :channelId, message_id = :messageId "
			+ "WHERE submission_id = :submissionId")
	void setMessageAndChannel(long submissionId, long channelId, long messageId);
	
	@SqlUpdate("UPDATE " + TABLE + " SET message_channel_id = :channelId, message_id = :messageId, reviewed = 1 "
			+ "WHERE submission_id = :submissionId")
	void archive(long submissionId, long channelId, long messageId);
	
	@SqlUpdate("DELETE FROM " + TABLE + " WHERE submission_id = ?")
	void delete(long submissionId);
	
	class SubmissionWithReviewReducer implements LinkedHashMapRowReducer<Long, GDLevelRequestSubmissionData> {

		@Override
		public void accumulate(Map<Long, GDLevelRequestSubmissionData> container, RowView rowView) {
			var key = rowView.getColumn("submission_id", Long.class);
			var submission = container.computeIfAbsent(key,
					id -> rowView.getRow(GDLevelRequestSubmissionData.class));
			if (rowView.getColumn("review_id", Long.class) != null) {
				container.put(key, ImmutableGDLevelRequestSubmissionData.builder()
						.from(submission)
						.addReviews(rowView.getRow(GDLevelRequestReviewData.class))
						.build());
			}
		}
	}
}
