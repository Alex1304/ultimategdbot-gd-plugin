package com.github.alex1304.ultimategdbot.gdplugin.util;

import static com.github.alex1304.ultimategdbot.api.util.Markdown.bold;
import static discord4j.core.retriever.EntityRetrievalStrategy.STORE_FALLBACK_REST;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import com.github.alex1304.jdash.entity.GDLevel;
import com.github.alex1304.ultimategdbot.api.Bot;
import com.github.alex1304.ultimategdbot.api.command.CommandFailedException;
import com.github.alex1304.ultimategdbot.api.command.Context;
import com.github.alex1304.ultimategdbot.api.util.DiscordFormatter;
import com.github.alex1304.ultimategdbot.api.util.MessageSpecTemplate;
import com.github.alex1304.ultimategdbot.gdplugin.database.GDLevelRequestReviews;
import com.github.alex1304.ultimategdbot.gdplugin.database.GDLevelRequestSubmissions;
import com.github.alex1304.ultimategdbot.gdplugin.database.GDLevelRequestsSettings;

import discord4j.core.event.domain.message.MessageCreateEvent;
import discord4j.core.event.domain.message.MessageDeleteEvent;
import discord4j.core.object.entity.Message;
import discord4j.core.object.entity.User;
import discord4j.rest.util.Snowflake;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.function.TupleUtils;
import reactor.retry.Retry;
import reactor.util.Logger;
import reactor.util.Loggers;

public class GDLevelRequests {
	
	private static final Logger LOGGER = Loggers.getLogger(GDLevelRequests.class);
	
	private GDLevelRequests() {}
	
	/**
	 * Retrieves the level requests settings for the guild of the specified context. This
	 * method checks and throws errors if level requests are not configured.
	 * 
	 * @param ctx the context
	 * @return the level requests settings, or an error if not configured
	 */
	public static Mono<GDLevelRequestsSettings> retrieveSettings(Context ctx) {
		Objects.requireNonNull(ctx, "ctx was null");
		var guildId = ctx.event().getGuildId().orElseThrow();
		return ctx.bot().database()
				.findByID(GDLevelRequestsSettings.class, guildId.asLong())
				.switchIfEmpty(Mono.fromCallable(() -> {
					var lvlReqSettings = new GDLevelRequestsSettings();
					lvlReqSettings.setGuildId(guildId.asLong());
					return lvlReqSettings;
				}).flatMap(lvlReqSettings -> ctx.bot().database().save(lvlReqSettings).thenReturn(lvlReqSettings)))
				.flatMap(lvlReqSettings -> !lvlReqSettings.getIsOpen() && (lvlReqSettings.getMaxQueuedSubmissionsPerPerson() == 0
						|| lvlReqSettings.getMaxReviewsRequired() == 0
						|| lvlReqSettings.getReviewedLevelsChannelId() == 0
						|| lvlReqSettings.getReviewerRoleId() == 0
						|| lvlReqSettings.getSubmissionQueueChannelId() == 0)
						? Mono.error(new CommandFailedException("Level requests are not configured."))
						: Mono.just(lvlReqSettings));
	}
	
	/**
	 * Retrieves all submissions for the user in the guild of the specified context.
	 * 
	 * @param ctx the context
	 * @return a Flux emitting all submissions before completing.
	 */
	public static Flux<GDLevelRequestSubmissions> retrieveSubmissionsForGuild(Bot bot, long guildId) {
		return bot.database().query(GDLevelRequestSubmissions.class, "from GDLevelRequestSubmissions s "
						+ "where s.guildId = ?0 and s.isReviewed = 0 "
						+ "order by s.submissionTimestamp", guildId);
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
	public static Mono<MessageSpecTemplate> buildSubmissionMessage(Bot bot, User author, GDLevel level,
			GDLevelRequestsSettings lvlReqSettings, GDLevelRequestSubmissions submission, List<GDLevelRequestReviews> reviews) {
		Objects.requireNonNull(bot, "bot was null");
		Objects.requireNonNull(author, "author was null");
		Objects.requireNonNull(level, "level was null");
		Objects.requireNonNull(submission.getYoutubeLink(), "youtubeLink was null");
		Objects.requireNonNull(reviews, "reviews was null");
		final var formatUser = DiscordFormatter.formatUser(author) + " (`" + author.getId().asLong() + "`)";
		return Mono.zip(GDLevels.compactView(bot, level, "Level request", "https://i.imgur.com/yC9P4sT.png"),
				Flux.fromIterable(reviews)
						.map(GDLevelRequestReviews::getReviewerId)
						.map(Snowflake::of)
						.flatMap(id -> bot.gateway()
								.withRetrievalStrategy(STORE_FALLBACK_REST)
								.getUserById(id)
								.onErrorResume(e -> Mono.empty()))
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
									.map(DiscordFormatter::formatUser)
									.findAny()
									.orElse("Unknown User\\#0000")
									+ " (`" + review.getReviewerId() + "`)";
							embedSpec.addField(":pencil: " + reviewerName, review.getReviewContent(), false);
						}
					});
					return new MessageSpecTemplate(content, embed);
				}));
	}
	
	/**
	 * Retrieves all reviews for the given submission.
	 * 
	 * @param bot the bot instance
	 * @param submission the submission to get reviews on
	 * @return a Flux of all reviews for the given submission
	 */
	public static Flux<GDLevelRequestReviews> retrieveReviewsForSubmission(Bot bot, GDLevelRequestSubmissions submission) {
		return bot.database().query(GDLevelRequestReviews.class, "select r from GDLevelRequestReviews r "
				+ "inner join r.submission s "
				+ "where s.id = ?0 "
				+ "order by r.reviewTimestamp", submission.getId());
	}
	
	/**
	 * Removes from database the submissions which associated Discord message is
	 * deleted. If the Discord message is inaccessible (e.g the bot can't see the
	 * submission channel or the message history anymore), the submission is
	 * untouched.
	 * 
	 * @param bot the bot
	 * @return a Mono completing when the process is done.
	 */
	public static Mono<Void> cleanOrphanSubmissions(Bot bot) {
		return bot.database()
				.query(GDLevelRequestSubmissions.class, "from GDLevelRequestSubmissions s where s.messageChannelId > 0 and s.messageId > 0 and s.isReviewed = 0")
				.filterWhen(submission -> bot.rest()
						.getMessageById(Snowflake.of(submission.getMessageChannelId()), Snowflake.of(submission.getMessageId()))
						.getData()
						.hasElement()
						.onErrorReturn(true)
						.map(b -> !b))
				.map(GDLevelRequestSubmissions::getId)
				.collectList()
				.flatMap(submissionsToDelete -> submissionsToDelete.isEmpty() ? Mono.just(0) : bot.database()
						.performTransaction(session -> session
								.createQuery("delete from GDLevelRequestSubmissions where id in ?0")
								.setParameter(0, submissionsToDelete)
								.executeUpdate()))
				.doOnNext(count -> LOGGER.debug("Cleaned from database {} orphan level request submissions", count))
				.flatMap(count -> bot.emoji("info")
						.flatMap(info -> bot.log(info + " Cleaned from database " + bold("" + count) + " orphan level request submissions.")))
				.then();
	}
	
	/**
	 * Keep submission queue channels for level requests clean from messages that
	 * aren't submissions.
	 * 
	 * @param bot the bot
	 */
	public static void listenAndCleanSubmissionQueueChannels(Bot bot, Set<Long> cachedSubmissionChannelIds) {
		bot.database().query(GDLevelRequestsSettings.class, "from GDLevelRequestsSettings")
				.map(GDLevelRequestsSettings::getSubmissionQueueChannelId)
				.collect(() -> cachedSubmissionChannelIds, Set::add)
				.onErrorResume(e -> Mono.empty())
				.thenMany(bot.gateway().on(MessageCreateEvent.class))
				.filter(event -> cachedSubmissionChannelIds.contains(event.getMessage().getChannelId().asLong()))
				.filterWhen(event -> event.getClient().getSelfId().map(selfId -> !event.getMessage().getAuthor().map(User::getId).map(selfId::equals).orElse(false)
						|| !event.getMessage().getContent().startsWith("**Submission ID:**")))
				.flatMap(event -> Mono.just(event)
						.delayElement(Duration.ofSeconds(15))
						.flatMap(__ -> event.getMessage().delete().onErrorResume(e -> Mono.empty())))
				.retryWhen(Retry.any().doOnRetry(retryCtx -> LOGGER.error("Error while cleaning level requests submission queue channels", retryCtx.exception())))
				.subscribe();
		
		bot.gateway().on(MessageDeleteEvent.class)
				.filter(event -> cachedSubmissionChannelIds.contains(event.getChannelId().asLong()))
				.map(event -> event.getMessage().map(Message::getId).map(Snowflake::asLong).orElse(0L))
				.filter(id -> id > 0)
				.flatMap(id -> bot.database().query(GDLevelRequestSubmissions.class, "from GDLevelRequestSubmissions s where s.messageId = ?0", id))
				.flatMap(submission -> retrieveReviewsForSubmission(bot, submission)
						.flatMap(bot.database()::delete)
						.then()
						.thenReturn(submission))
				.flatMap(bot.database()::delete)
				.retryWhen(Retry.any().doOnRetry(retryCtx -> LOGGER.error("Error while processing MessageDeleteEvent in submission queue channels", retryCtx.exception())))
				.subscribe();
		
		// Backward compatibility process
		bot.database()
				.query(GDLevelRequestsSettings.class, "from GDLevelRequestsSettings")
				.collect(Collectors.toMap(GDLevelRequestsSettings::getGuildId, GDLevelRequestsSettings::getSubmissionQueueChannelId))
				.flatMap(submissionChannels -> bot.database()
						.query(GDLevelRequestSubmissions.class, "from GDLevelRequestSubmissions s where (s.messageChannelId = 0 or s.messageChannelId is null) and s.isReviewed = 0")
						.collectList()
						.flatMap(submissions -> bot.database().performEmptyTransaction(session -> {
							for (var submission : submissions) {
								var channelId = submissionChannels.get(submission.getGuildId());
								if (channelId != null && channelId > 0) {
									submission.setMessageChannelId(channelId);
									session.saveOrUpdate(submission);
								}
							}
						})))
				.subscribe();
		
		Flux.interval(Duration.ofHours(12), Duration.ofDays(1))
				.flatMap(tick -> cleanOrphanSubmissions(bot)
						.onErrorResume(e -> Mono.fromRunnable(() -> LOGGER.warn("Error while cleaning orphan level request submissions", e))))
				.subscribe();
		
	}
}
