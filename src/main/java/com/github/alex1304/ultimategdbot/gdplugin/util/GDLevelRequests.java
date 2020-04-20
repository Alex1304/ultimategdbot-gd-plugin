package com.github.alex1304.ultimategdbot.gdplugin.util;

import static com.github.alex1304.ultimategdbot.api.util.Markdown.bold;
import static discord4j.core.retriever.EntityRetrievalStrategy.STORE_FALLBACK_REST;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import com.github.alex1304.jdash.entity.GDLevel;
import com.github.alex1304.ultimategdbot.api.Bot;
import com.github.alex1304.ultimategdbot.api.command.CommandFailedException;
import com.github.alex1304.ultimategdbot.api.command.Context;
import com.github.alex1304.ultimategdbot.api.util.MessageSpecTemplate;
import com.github.alex1304.ultimategdbot.gdplugin.database.GDLevelRequestConfigDao;
import com.github.alex1304.ultimategdbot.gdplugin.database.GDLevelRequestConfigData;
import com.github.alex1304.ultimategdbot.gdplugin.database.GDLevelRequestReviewData;
import com.github.alex1304.ultimategdbot.gdplugin.database.GDLevelRequestSubmissionDao;
import com.github.alex1304.ultimategdbot.gdplugin.database.GDLevelRequestSubmissionData;

import discord4j.core.event.domain.message.MessageCreateEvent;
import discord4j.core.event.domain.message.MessageDeleteEvent;
import discord4j.core.object.entity.Message;
import discord4j.core.object.entity.User;
import discord4j.rest.util.Snowflake;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.function.TupleUtils;
import reactor.util.Logger;
import reactor.util.Loggers;
import reactor.util.retry.Retry;

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
	public static Mono<GDLevelRequestConfigData> retrieveSettings(Context ctx) {
		Objects.requireNonNull(ctx, "ctx was null");
		var guildId = ctx.event().getGuildId().orElseThrow();
		return ctx.bot().database()
				.withExtension(GDLevelRequestConfigDao.class, dao -> dao.getOrCreate(guildId.asLong()))
				.flatMap(lvlReqSettings -> !lvlReqSettings.isOpen()
						&& (lvlReqSettings.channelSubmissionQueue().isEmpty()
						|| lvlReqSettings.channelArchivedSubmissions().isEmpty()
						|| lvlReqSettings.roleReviewer().isEmpty())
						? Mono.error(new CommandFailedException("Level requests are not configured."))
						: Mono.just(lvlReqSettings));
	}
	
	/**
	 * Retrieves all submissions for the user in the guild of the specified context.
	 * 
	 * @param ctx the context
	 * @return a Flux emitting all submissions before completing.
	 */
	public static Flux<GDLevelRequestSubmissionData> retrieveSubmissionsForGuild(Bot bot, long guildId) {
		return bot.database()
				.withExtension(GDLevelRequestSubmissionDao.class, dao -> dao.getQueuedSubmissionsInGuild(guildId))
				.flatMapMany(Flux::fromIterable);
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
			GDLevelRequestConfigData lvlReqSettings, GDLevelRequestSubmissionData submission, List<GDLevelRequestReviewData> reviews) {
		Objects.requireNonNull(bot, "bot was null");
		Objects.requireNonNull(author, "author was null");
		Objects.requireNonNull(level, "level was null");
		Objects.requireNonNull(reviews, "reviews was null");
		final var formatUser = author.getTag() + " (`" + author.getId().asLong() + "`)";
		return Mono.zip(GDLevels.compactView(bot, level, "Level request", "https://i.imgur.com/yC9P4sT.png"),
				Flux.fromIterable(reviews)
						.map(GDLevelRequestReviewData::reviewerId)
						.flatMap(id -> bot.gateway()
								.withRetrievalStrategy(STORE_FALLBACK_REST)
								.getUserById(id)
								.onErrorResume(e -> Mono.empty()))
						.collectList())
				.map(TupleUtils.function((embedSpecConsumer, reviewers) -> {
					var content = "**Submission ID:** `" + submission.submissionId() + "`\n"
							+ "**Author:** " + formatUser + "\n"
							+ "**Level ID:** `" + level.getId() + "`\n"
							+ "**YouTube link:** " + submission.youtubeLink().orElse("*Not provided*");
					var embed = embedSpecConsumer.andThen(embedSpec -> {
						embedSpec.addField("───────────", "**Reviews:** " + reviews.size() + "/" + lvlReqSettings.minReviewsRequired(), false);
						for (var review : reviews) {
							var reviewerName = reviewers.stream()
									.filter(r -> r.getId().equals(review.reviewerId()))
									.map(User::getTag)
									.findAny()
									.orElse("Unknown User\\#0000")
									+ " (`" + review.reviewerId().asString() + "`)";
							embedSpec.addField(":pencil: " + reviewerName, review.reviewContent(), false);
						}
					});
					return new MessageSpecTemplate(content, embed);
				}));
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
				.withExtension(GDLevelRequestSubmissionDao.class, GDLevelRequestSubmissionDao::getAllQueuedSubmissions)
				.flatMapMany(Flux::fromIterable)
				.filterWhen(submission -> bot.rest()
						.getMessageById(submission.messageChannelId().orElseThrow(), submission.messageId().orElseThrow())
						.getData()
						.hasElement()
						.onErrorReturn(true)
						.map(b -> !b))
				.map(GDLevelRequestSubmissionData::submissionId)
				.collectList()
				.flatMap(submissionsToDelete -> submissionsToDelete.isEmpty() ? Mono.just(0) : bot.database()
						.withExtension(GDLevelRequestSubmissionDao.class, dao -> dao.deleteAllIn(submissionsToDelete)))
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
		bot.database().withExtension(GDLevelRequestConfigDao.class, GDLevelRequestConfigDao::getAll)
				.<GDLevelRequestConfigData>flatMapMany(Flux::fromIterable)
				.map(GDLevelRequestConfigData::channelSubmissionQueue)
				.<Snowflake>flatMap(Mono::justOrEmpty)
				.<Long>map(Snowflake::asLong)
				.collect(() -> cachedSubmissionChannelIds, Set::add)
				.onErrorResume(e -> Mono.empty())
				.thenMany(bot.gateway().on(MessageCreateEvent.class))
				.filter(event -> cachedSubmissionChannelIds.contains(event.getMessage().getChannelId().asLong()))
				.filterWhen(event -> event.getClient().getSelfId().map(selfId -> !event.getMessage().getAuthor().map(User::getId).map(selfId::equals).orElse(false)
						|| !event.getMessage().getContent().startsWith("**Submission ID:**")))
				.flatMap(event -> Mono.just(event)
						.delayElement(Duration.ofSeconds(15))
						.flatMap(__ -> event.getMessage().delete().onErrorResume(e -> Mono.empty())))
				.retryWhen(Retry.indefinitely()
						.doBeforeRetry(retry -> LOGGER.error("Error while cleaning level requests submission queue channels", retry.failure())))
				.subscribe();
		
		bot.gateway().on(MessageDeleteEvent.class)
				.filter(event -> cachedSubmissionChannelIds.contains(event.getChannelId().asLong()))
				.map(event -> event.getMessage().map(Message::getId).map(Snowflake::asLong).orElse(0L))
				.filter(id -> id > 0)
				.flatMap(id -> bot.database().useExtension(GDLevelRequestSubmissionDao.class, dao -> dao.deleteByMessageId(id)))
				.retryWhen(Retry.indefinitely()
						.doBeforeRetry(retry -> LOGGER.error("Error while processing MessageDeleteEvent in submission queue channels", retry.failure())))
				.subscribe();
		
		Flux.interval(Duration.ofHours(12), Duration.ofDays(1))
				.flatMap(tick -> cleanOrphanSubmissions(bot)
						.onErrorResume(e -> Mono.fromRunnable(() -> LOGGER.warn("Error while cleaning orphan level request submissions", e))))
				.subscribe();
		
	}
}
