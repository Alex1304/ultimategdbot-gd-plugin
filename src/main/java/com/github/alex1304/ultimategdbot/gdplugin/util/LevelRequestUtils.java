package com.github.alex1304.ultimategdbot.gdplugin.util;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.alex1304.jdash.entity.GDLevel;
import com.github.alex1304.ultimategdbot.api.Bot;
import com.github.alex1304.ultimategdbot.api.CommandFailedException;
import com.github.alex1304.ultimategdbot.api.Context;
import com.github.alex1304.ultimategdbot.api.utils.BotUtils;
import com.github.alex1304.ultimategdbot.gdplugin.database.GDLevelRequestReviews;
import com.github.alex1304.ultimategdbot.gdplugin.database.GDLevelRequestSubmissions;
import com.github.alex1304.ultimategdbot.gdplugin.database.GDLevelRequestsSettings;
import com.github.alex1304.ultimategdbot.gdplugin.levelrequest.SubmissionMessage;

import discord4j.core.DiscordClient;
import discord4j.core.event.domain.message.MessageCreateEvent;
import discord4j.core.event.domain.message.MessageDeleteEvent;
import discord4j.core.object.entity.Message;
import discord4j.core.object.entity.User;
import discord4j.core.object.util.Snowflake;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.function.TupleUtils;

public class LevelRequestUtils {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(LevelRequestUtils.class);
	
	private LevelRequestUtils() {}
	
	/**
	 * Gets the level requests settings for the guild of the specified context. This
	 * method checks and throws errors if level requests are not configured.
	 * 
	 * @param ctx the context
	 * @return the level requests settings, or an error if not configured
	 */
	public static Mono<GDLevelRequestsSettings> getLevelRequestsSettings(Context ctx) {
		Objects.requireNonNull(ctx, "ctx was null");
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
	
	/**
	 * Gets all submissions for the user in the guild of the specified context.
	 * 
	 * @param ctx the context
	 * @return a Flux emitting all submissions before completing.
	 */
	public static Flux<GDLevelRequestSubmissions> getSubmissionsForUser(Context ctx) {
		Objects.requireNonNull(ctx, "ctx was null");
		var guildId = ctx.getEvent().getGuildId().orElseThrow().asLong();
		var userId = ctx.getEvent().getMessage().getAuthor().orElseThrow().getId().asLong();
		return ctx.getBot().getDatabase()
				.query(GDLevelRequestSubmissions.class, "from GDLevelRequestSubmissions s "
						+ "where s.guildId = ?0 "
						+ "and s.submitterId = ?1 "
						+ "order by s.submissionTimestamp", guildId, userId);
	}

	/**
	 * Builds the submission message from the given data.
	 * 
	 * @param bot            the bot instance
	 * @param author         the submission author
	 * @param level          the level
	 * @param lvlReqSettings the settings for level requests in the current server
	 * @param submission     the submission
	 * @param reviews        the list of reviews
	 * @return a Mono emitting the submission message
	 */
	public static Mono<SubmissionMessage> buildSubmissionMessage(Bot bot, User author, GDLevel level,
			GDLevelRequestsSettings lvlReqSettings, GDLevelRequestSubmissions submission, List<GDLevelRequestReviews> reviews) {
		Objects.requireNonNull(bot, "bot was null");
		Objects.requireNonNull(author, "author was null");
		Objects.requireNonNull(level, "level was null");
		Objects.requireNonNull(submission.getYoutubeLink(), "youtubeLink was null");
		Objects.requireNonNull(reviews, "reviews was null");
		final var formatUser = BotUtils.formatDiscordUsername(author) + " (`" + author.getId().asLong() + "`)";
		return Mono.zip(GDUtils.shortLevelView(bot, level, "Level request", "https://i.imgur.com/yC9P4sT.png"),
				Flux.fromIterable(reviews)
						.map(GDLevelRequestReviews::getReviewerId)
						.map(Snowflake::of)
						.flatMap(bot.getMainDiscordClient()::getUserById)
						.onErrorContinue((error, obj) -> {})
						.collectList())
				.map(TupleUtils.function((embedSpecConsumer, reviewers) -> {
					var content = "**Submission ID:** `" + submission.getId() + "`\n"
							+ "**Author:** " + formatUser + "\n"
							+ "**Level ID:** `" + level.getId() + "`\n"
							+ "**YouTube link:** " + (submission.getYoutubeLink().isBlank() ? "*Not provided*" : submission.getYoutubeLink());
					var embed = embedSpecConsumer.andThen(embedSpec -> {
						embedSpec.addField("───────────", "**Reviews:** " + reviews.size() + "/" + lvlReqSettings.getMaxReviewsRequired(), false);
						for (var review : reviews) {
							var reviewerName = reviewers.stream()
									.filter(r -> r.getId().asLong() == review.getReviewerId())
									.map(BotUtils::formatDiscordUsername)
									.findAny()
									.orElse("Unknown User\\#0000")
									+ " (`" + review.getReviewerId() + "`)";
							embedSpec.addField(":pencil: " + reviewerName, review.getReviewContent(), false);
						}
					});
					return new SubmissionMessage(content, embed);
				}));
	}
	
	/**
	 * Gets all reviews for the given submission.
	 * 
	 * @param bot the bot instance
	 * @param submission the submission to get reviews on
	 * @return a Flux of all reviews for the given submission
	 */
	public static Flux<GDLevelRequestReviews> getReviewsForSubmission(Bot bot, GDLevelRequestSubmissions submission) {
		return bot.getDatabase().query(GDLevelRequestReviews.class, "select r from GDLevelRequestReviews r "
				+ "inner join r.submission s "
				+ "where s.id = ?0 "
				+ "order by r.reviewTimestamp", submission.getId());
	}
	
	/**
	 * Keep submission queue channels for level requests clean from messages that
	 * aren't submissions.
	 * 
	 * @param bot the bot
	 */
	public static void listenAndCleanSubmissionQueueChannels(Bot bot, Set<Long> cachedSubmissionChannelIds) {
		bot.getDatabase().query(GDLevelRequestsSettings.class, "from GDLevelRequestsSettings")
			.map(GDLevelRequestsSettings::getSubmissionQueueChannelId)
			.collect(() -> cachedSubmissionChannelIds, Set::add)
			.onErrorResume(e -> Mono.empty())
			.thenMany(bot.getDiscordClients())
			.map(DiscordClient::getEventDispatcher)
			.flatMap(dispatcher -> dispatcher.on(MessageCreateEvent.class))
			.filter(event -> cachedSubmissionChannelIds.contains(event.getMessage().getChannelId().asLong()))
			.filter(event -> !event.getMessage().getAuthor().map(User::getId).equals(event.getClient().getSelfId())
					|| !event.getMessage().getContent().orElse("").startsWith("**Submission ID:**"))
			.flatMap(event -> Flux.fromIterable(cachedSubmissionChannelIds)
					.filter(id -> id == event.getMessage().getChannelId().asLong())
					.next()
					.delayElement(Duration.ofSeconds(8))
					.flatMap(__ -> event.getMessage().delete().onErrorResume(e -> Mono.empty())))
			.onErrorContinue((error, obj) -> LOGGER.error("Error while cleaning level requests submission queue channels on " + obj, error))
			.subscribe();
		
		bot.getDiscordClients()
			.map(DiscordClient::getEventDispatcher)
			.flatMap(dispatcher -> dispatcher.on(MessageDeleteEvent.class))
			.filter(event -> event.getMessage().map(m -> cachedSubmissionChannelIds.contains(m.getChannelId().asLong())).orElse(false))
			.map(event -> event.getMessage().map(Message::getId).map(Snowflake::asLong).orElse(0L))
			.filter(id -> id > 0)
			.flatMap(id -> bot.getDatabase().query(GDLevelRequestSubmissions.class, "from GDLevelRequestSubmissions s where s.messageId = ?0", id))
			.flatMap(bot.getDatabase()::delete)
			.onErrorContinue((error, obj) -> LOGGER.error("Error while processing MessageDeleteEvent in submission queue channels on " + obj, error))
			.subscribe();
	}
}
