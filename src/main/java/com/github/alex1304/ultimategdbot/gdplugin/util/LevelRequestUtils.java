package com.github.alex1304.ultimategdbot.gdplugin.util;

import com.github.alex1304.ultimategdbot.api.CommandFailedException;
import com.github.alex1304.ultimategdbot.api.Context;
import com.github.alex1304.ultimategdbot.gdplugin.database.GDLevelRequestSubmissions;
import com.github.alex1304.ultimategdbot.gdplugin.database.GDLevelRequestsSettings;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class LevelRequestUtils {
	
	private LevelRequestUtils() {}
	
	/**
	 * Gets the level requests settings for the guild of the context. This method checks and throws errors if level
	 * requests are not configured.
	 * 
	 * @param ctx the context
	 * @return the level requests settings, or an error if not configured
	 */
	public static Mono<GDLevelRequestsSettings> getLevelRequestsSettings(Context ctx) {
		var guildId = ctx.getEvent().getGuildId().orElseThrow();
		return ctx.getBot().getDatabase()
				.findByIDOrCreate(GDLevelRequestsSettings.class, guildId.asLong(), GDLevelRequestsSettings::setGuildId)
				.flatMap(lvlReqSettings -> !lvlReqSettings.getIsOpen() && (lvlReqSettings.getMaxQueuedSubmissionsPerPerson() == 0
						|| lvlReqSettings.getMaxReviewsRequired() == 0
						|| lvlReqSettings.getReviewedLevelsChannelId() == 0
						|| lvlReqSettings.getReviewerRoleId() == 0
						|| lvlReqSettings.getSubmissionQueueChannelId() == 0)
						? Mono.error(new CommandFailedException("Level requests are not configured."))
						: Mono.just(lvlReqSettings));
	}
	
	public static Flux<GDLevelRequestSubmissions> getSubmissionsForUser(Context ctx) {
		var guildId = ctx.getEvent().getGuildId().orElseThrow().asLong();
		var userId = ctx.getEvent().getMessage().getAuthor().orElseThrow().getId().asLong();
		return ctx.getBot().getDatabase()
				.query(GDLevelRequestSubmissions.class, "from GDLevelRequestSubmissions s "
						+ "where s.guildId = ?0 "
						+ "and s.submitterId = ?1 "
						+ "order by s.submissionTimestamp", guildId, userId);
	}
}
