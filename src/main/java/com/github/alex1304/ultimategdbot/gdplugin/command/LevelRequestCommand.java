package com.github.alex1304.ultimategdbot.gdplugin.command;

import static discord4j.core.retriever.EntityRetrievalStrategy.STORE_FALLBACK_REST;
import static java.util.Objects.requireNonNullElse;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import com.github.alex1304.jdash.entity.GDLevel;
import com.github.alex1304.jdash.exception.MissingAccessException;
import com.github.alex1304.ultimategdbot.api.command.CommandFailedException;
import com.github.alex1304.ultimategdbot.api.command.Context;
import com.github.alex1304.ultimategdbot.api.command.PermissionLevel;
import com.github.alex1304.ultimategdbot.api.command.Scope;
import com.github.alex1304.ultimategdbot.api.command.annotated.CommandAction;
import com.github.alex1304.ultimategdbot.api.command.annotated.CommandDescriptor;
import com.github.alex1304.ultimategdbot.api.command.annotated.CommandDoc;
import com.github.alex1304.ultimategdbot.api.command.annotated.CommandPermission;
import com.github.alex1304.ultimategdbot.api.database.DatabaseException;
import com.github.alex1304.ultimategdbot.api.util.MessageSpecTemplate;
import com.github.alex1304.ultimategdbot.gdplugin.GDService;
import com.github.alex1304.ultimategdbot.gdplugin.database.GDLevelRequestReviewData;
import com.github.alex1304.ultimategdbot.gdplugin.database.GDLevelRequestSubmissionData;
import com.github.alex1304.ultimategdbot.gdplugin.database.GDLevelRequestConfigData;
import com.github.alex1304.ultimategdbot.gdplugin.util.GDEvents;
import com.github.alex1304.ultimategdbot.gdplugin.util.GDLevelRequests;

import discord4j.core.object.entity.Guild;
import discord4j.core.object.entity.Role;
import discord4j.core.object.entity.User;
import discord4j.core.object.entity.channel.PrivateChannel;
import discord4j.core.object.entity.channel.TextChannel;
import discord4j.rest.entity.RestMessage;
import discord4j.rest.util.Snowflake;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.function.TupleUtils;
import reactor.util.annotation.Nullable;

@CommandDescriptor(
		aliases = { "levelrequest", "lvlreq" },
		shortDescription = "Submit your levels for other players to give feedback on them.",
		scope = Scope.GUILD_ONLY
)
public class LevelRequestCommand {

	private final GDService gdService;
	
	public LevelRequestCommand(GDService gdService) {
		this.gdService = gdService;
	}

	@CommandAction
	@CommandDoc("Check level request status in the current server. Submit your levels in the submission queue channel using "
			+ "`levelrequest submit <your_level_ID>`. Then people with the reviewer role will review your submission, and "
			+ "once you get a certain number of reviews your level will be moved to the reveiwed levels channel and you will "
			+ "be notified in DMs. You are only allowed to submit a limited number of levels at once.\n\n"
			+ "For more details on how level requests work, check out this guide: <https://github.com/Alex1304/ultimategdbot-gd-plugin"
			+ "/wiki/Level-Requests-Tutorial>")
	public Mono<Void> run(Context ctx) {
		var guildId = ctx.event().getGuildId().orElseThrow();
		return Mono.zip(ctx.bot().emoji("success"), ctx.bot().emoji("failed"))
				.flatMap(TupleUtils.function((success, failed) -> ctx.bot().database()
						.findByID(GDLevelRequestConfigData.class, guildId.asLong())
						.switchIfEmpty(Mono.fromCallable(() -> {
							var lvlReqSettings = new GDLevelRequestConfigData();
							lvlReqSettings.setGuildId(guildId.asLong());
							return lvlReqSettings;
						}).flatMap(lvlReqSettings -> ctx.bot().database().save(lvlReqSettings).thenReturn(lvlReqSettings)))
						.flatMap(lvlReqSettings -> ctx.args().tokenCount() > 1
								? Mono.error(new CommandFailedException("Hmm, did you mean \"" + ctx.prefixUsed()
										+ "lvlreq **submit** " + ctx.args().getAllAfter(1) + "\"?"))
								: Mono.just(lvlReqSettings))
						.zipWhen(lvlReqSettings -> lvlReqSettings.getReviewerRoleId() == 0 ? Mono.just("*Not configured*") : ctx.bot().gateway()
								.getRoleById(guildId, Snowflake.of(lvlReqSettings.getReviewerRoleId()))
								.map(Role::getName)
								.onErrorResume(e -> Mono.empty())
								.defaultIfEmpty("*unknown role*"))
						.flatMap(TupleUtils.function((lvlReqSettings, reviewerRole) -> ctx.reply("**__Get other players to play your "
								+ "levels and give feedback with the Level Request feature!__**\n\n" + (lvlReqSettings.getIsOpen()
								? success + " level requests are OPENED" : failed + " level requests are CLOSED") + "\n\n"
								+ "**Submission channel:** " + formatChannel(lvlReqSettings.getSubmissionQueueChannelId()) + "\n"
								+ "**Reviewed levels channel:** " + formatChannel(lvlReqSettings.getReviewedLevelsChannelId()) + "\n"
								+ "**Reviewer role:** " + reviewerRole + "\n"
								+ "**Number of reviews required:** " + formatNumber(lvlReqSettings.getMaxReviewsRequired()) + "\n"
								+ "**Max queued submissions per person:** " + formatNumber(lvlReqSettings.getMaxQueuedSubmissionsPerPerson()) + "\n\n"
								+ "Server admins can change the above values via `" + ctx.prefixUsed() + "setup`, "
								+ "and they can " + (lvlReqSettings.getIsOpen() ? "close" : "open") + " level requests by using `"
								+ ctx.prefixUsed() + "levelrequest toggle`.\nFor more details on how level requests work, check "
								+ "out this guide: <https://github.com/Alex1304/ultimategdbot-gd-plugin/wiki/Level-Requests-Tutorial>")))))
				.then();
	}
	
	@CommandAction("clean_orphan_submissions")
	@CommandDoc("Cleans orphan submissions from database (bot owner only). When a submission message is deleted "
			+ "manually from the queue, it is possible that the associated "
			+ "submission in the database does not get properly removed (for example if the MessageDeleteEvent "
			+ "isn't received). It may lead to orphan submissions, that is submissions that don't exist on Discord "
			+ "but still exist in database. This command removes those orphan submissions from database. Note that "
			+ "this orphan removal process is automatically ran once a day.")
	@CommandPermission(level = PermissionLevel.BOT_OWNER)
	public Mono<Void> runCleanOrphanSubmissions(Context ctx) {
		return GDLevelRequests.cleanOrphanSubmissions(ctx.bot())
				.then(ctx.bot().emoji("success")
						.flatMap(success -> ctx.reply(success + " Orphan submissions have been removed from database.")))
				.then();
	}
	
	@CommandAction("review")
	@CommandDoc("Add a review on a level request. You can only use this command if:\n"
				+ "- You have the reviewer role, as configured in this server\n"
				+ "- You run this command in the submission channel\n"
				+ "- The targeted submission exists and hasn't already been moved out of the queue.\n"
				+ "Note that you can still add reviews to queue levels while level requests are closed, and "
				+ "each review must not exceed 1000 characters.\n\n"
				+ "To revoke your review from a submission, the syntax is `review <submission_ID> revoke`.")
	@CommandPermission(name = "LEVEL_REQUEST_REVIEWER")
	public Mono<Void> runReview(Context ctx, long submissionId, String reviewContent) {
		final var guildId = ctx.event().getGuildId().orElseThrow();
		return ctx.channel().typeUntil(GDLevelRequests.retrieveSettings(ctx)
				.flatMap(lvlReqSettings -> doReview(ctx, submissionId, reviewContent, guildId.asLong(), lvlReqSettings, null, false)))
				.then(ctx.bot().emoji("success").flatMap(emoji -> ctx.reply(emoji + " The submission has been updated.")))
				.then();
	}
	
	@CommandAction("submit")
	@CommandDoc("Submit a new level request. This command can only be used in the configured submission channel, "
			+ "and only if level requests are opened.")
	public Mono<Void> runSubmit(Context ctx, long levelId, @Nullable String youtubeLink) {
		checkYouTubeLink(youtubeLink);
		final var guildId = ctx.event().getGuildId().orElseThrow().asLong();
		final var user = ctx.event().getMessage().getAuthor().orElseThrow();
		final var lvlReqSettings = new AtomicReference<GDLevelRequestConfigData>();
		final var level = new AtomicReference<GDLevel>();
		final var guildSubmissions = new AtomicReference<Flux<GDLevelRequestSubmissionData>>();
		return ctx.channel().typeUntil(GDLevelRequests.retrieveSettings(ctx)
				.doOnNext(lvlReqSettings::set)
				.filter(lrs -> ctx.event().getMessage().getChannelId().asLong() == lrs.getSubmissionQueueChannelId())
				.switchIfEmpty(Mono.error(() -> new CommandFailedException("You can only use this command in <#"
						+ lvlReqSettings.get().getSubmissionQueueChannelId() + ">.")))
				.filter(GDLevelRequestConfigData::getIsOpen)
				.switchIfEmpty(Mono.error(new CommandFailedException("Level requests are closed, no submissions are being accepted.")))
				.doOnNext(__ -> guildSubmissions.set(GDLevelRequests.retrieveSubmissionsForGuild(ctx.bot(), guildId).cache()))
				.filterWhen(lrs -> guildSubmissions.get().all(s -> s.getLevelId() != levelId))
				.switchIfEmpty(Mono.error(new CommandFailedException("This level is already in queue.")))
				.filterWhen(lrs -> guildSubmissions.get()
						.filter(s -> !s.getIsReviewed() && s.getSubmitterId() == user.getId().asLong())
						.filterWhen(s -> ctx.bot().rest()
								.getMessageById(Snowflake.of(s.getMessageChannelId()), Snowflake.of(s.getMessageId()))
								.getData()
								.hasElement()
								.onErrorReturn(true))
						.count()
						.map(n -> n < lrs.getMaxQueuedSubmissionsPerPerson()))
				.switchIfEmpty(Mono.error(() -> new CommandFailedException("You've reached the maximum number of submissions allowed in queue per person ("
						+ lvlReqSettings.get().getMaxQueuedSubmissionsPerPerson() + "). Wait for one of your queued requests to be "
						+ "reviewed before trying again.")))
				.then(gdService.getGdClient()
						.getLevelById(levelId)
						.onErrorMap(MissingAccessException.class, e -> new CommandFailedException("Level not found."))
						.doOnNext(level::set))
				.then(Mono.fromCallable(() -> {
							var s = new GDLevelRequestSubmissionData();
							s.setGuildId(guildId);
							s.setLevelId(levelId);
							s.setSubmissionTimestamp(Timestamp.from(Instant.now()));
							s.setSubmitterId(user.getId().asLong());
							s.setYoutubeLink(requireNonNullElse(youtubeLink, ""));
							s.setIsReviewed(false);
							return s;
						})
						.flatMap(s -> ctx.bot().database().save(s).thenReturn(s)) // First save to know the submission ID
						.flatMap(s -> GDLevelRequests.buildSubmissionMessage(ctx.bot(), user, level.get(), lvlReqSettings.get(), s, List.of())
								.map(MessageSpecTemplate::toMessageCreateSpec)
								.flatMap(ctx::reply)
								.flatMap(message -> {
									s.setMessageId(message.getId().asLong());
									s.setMessageChannelId(message.getChannelId().asLong());
									return ctx.bot().database().save(s);
								})
								.onErrorResume(e -> ctx.bot().database().delete(s).then(Mono.error(e))))))
				.then(ctx.bot().emoji("success").flatMap(emoji -> ctx.reply(emoji + " Level request submitted!")))
				.then();
	}
	
	@CommandAction("toggle")
	@CommandDoc("Enable or disable level requests for this server.")
	@CommandPermission(level = PermissionLevel.GUILD_ADMIN)
	public Mono<Void> runToggle(Context ctx) {
		var isOpening = new AtomicBoolean();
		return GDLevelRequests.retrieveSettings(ctx)
				.doOnNext(lvlReqSettings -> isOpening.set(!lvlReqSettings.getIsOpen()))
				.doOnNext(lvlReqSettings -> lvlReqSettings.setIsOpen(isOpening.get()))
				.flatMap(ctx.bot().database()::save)
				.then(Mono.defer(() -> ctx.reply("Level requests are now " + (isOpening.get() ? "opened" : "closed") + "!")))
				.then();
	}
	
	@CommandAction("purge_invalid_submissions")
	@CommandDoc("Removes all submissions referring to levels that were deleted or that were already rated.")
	@CommandPermission(name = "LEVEL_REQUEST_REVIEWER")
	public Mono<Void> runPurgeInvalidSubmissions(Context ctx) {
		var guildId = ctx.event().getGuildId().orElseThrow();
		return GDLevelRequests.retrieveSettings(ctx)
				.flatMap(lvlReqSettings -> GDLevelRequests.retrieveSubmissionsForGuild(ctx.bot(), guildId.asLong())
						.flatMap(submission -> gdService.getGdClient().getLevelById(submission.getLevelId())
								.filter(level -> level.getStars() > 0)
								.flatMap(level -> doReview(ctx, submission.getId(), "Got rated after being submitted.",
										guildId.asLong(), lvlReqSettings, submission, true).thenReturn(1))
								.onErrorResume(MissingAccessException.class, e -> ctx.bot().rest()
										.getMessageById(Snowflake.of(submission.getMessageChannelId()), Snowflake.of(submission.getMessageId()))
										.delete(null)
										.onErrorResume(e0 -> Mono.empty())
										.thenReturn(1)))
						.reduce(Integer::sum)
						.flatMap(count -> ctx.bot().emoji("success")
								.flatMap(emoji -> ctx.reply(emoji + " Successfully purged **" + count + "** invalid submissions.")))
						.switchIfEmpty(ctx.bot().emoji("cross").flatMap(emoji -> ctx.reply(emoji + " No submissions to purge."))))
				.then();
	}
	
	private Mono<Void> doReview(Context ctx, long submissionId, String reviewContent, long guildId, 
			GDLevelRequestConfigData lvlReqSettings, @Nullable GDLevelRequestSubmissionData submissionObj, boolean forceMove) {
		final var userId = ctx.event().getMessage().getAuthor().orElseThrow().getId().asLong();
		final var submission = new AtomicReference<GDLevelRequestSubmissionData>(submissionObj);
		final var review = new AtomicReference<GDLevelRequestReviewData>();
		final var level = new AtomicReference<GDLevel>();
		final var submissionMsg = new AtomicReference<RestMessage>();
		final var submitter = new AtomicReference<User>();
		final var guild = new AtomicReference<Guild>();
		final var isRevoke = reviewContent.equalsIgnoreCase("revoke");
		final var reviewsOnSubmission = Flux.defer(() -> GDLevelRequests.retrieveReviewsForSubmission(ctx.bot(), submission.get()));
		if (reviewContent.length() > 1000) {
			return Mono.error(new CommandFailedException("Review content must not exceed 1000 characters."));
		}
		return Mono.justOrEmpty(submissionObj)
				.switchIfEmpty(ctx.bot().database().findByID(GDLevelRequestSubmissionData.class, submissionId)
						.doOnNext(submission::set)
						.filter(s -> s.getGuildId() == guildId)
						.filter(s -> !s.getIsReviewed())
						.switchIfEmpty(Mono.error(new CommandFailedException("This submission has already been moved.")))
						.filter(s -> s.getSubmitterId() != userId)
						.switchIfEmpty(Mono.error(new CommandFailedException("You can't review your own submission."))))
				.filterWhen(s -> Mono.just(ctx.bot().rest()
						.getMessageById(Snowflake.of(s.getMessageChannelId()), Snowflake.of(s.getMessageId())))
						.doOnNext(submissionMsg::set)
						.flatMap(__ -> ctx.bot().gateway()
								.withRetrievalStrategy(STORE_FALLBACK_REST)
								.getUserById(Snowflake.of(s.getSubmitterId()))
								.doOnNext(submitter::set))
						.flatMap(__ -> ctx.event().getGuild())
								.doOnNext(guild::set)
						.hasElement())
				.switchIfEmpty(Mono.error(new CommandFailedException("Unable to find submission of ID " + submissionId + ".")))
				.thenMany(reviewsOnSubmission)
				.filter(r -> r.getReviewerId() == userId)
				.next()
				.flatMap(r -> isRevoke ? ctx.bot().database().delete(r).then(Mono.empty()) : Mono.just(r))
				.switchIfEmpty(Mono.fromCallable(() -> {
					if (isRevoke) {
						return null;
					}
					var r = new GDLevelRequestReviewData();
					review.set(r);
					return r;
				}))
				.doOnNext(r -> {
					r.setReviewContent(reviewContent);
					r.setReviewerId(userId);
					r.setReviewTimestamp(Timestamp.from(Instant.now()));
					r.setSubmission(submission.get());
				})
				.flatMap(ctx.bot().database()::save)
				.onErrorMap(DatabaseException.class, e -> new CommandFailedException("Something went wrong when saving your review. Try again.", e))
				.then(Mono.defer(() -> gdService.getGdClient().getLevelById(submission.get().getLevelId())
						.doOnNext(level::set)
						.onErrorMap(MissingAccessException.class, e -> new CommandFailedException("Cannot " + (isRevoke ? "revoke" : "add")
								+ " review: the level associated to this submission doesn't seem to exist on GD servers anymore. "
								+ "You may want to delete this submission, or run `" + ctx.prefixUsed() + "lvlreq purge_invalid_submissions`."))))
				.thenMany(reviewsOnSubmission)
				.collectList()
				.flatMap(reviewList -> {
					var updatedMessage = GDLevelRequests.buildSubmissionMessage(ctx.bot(), submitter.get(),
							level.get(), lvlReqSettings, submission.get(), reviewList).cache();
					if (forceMove || reviewList.size() >= lvlReqSettings.getMaxReviewsRequired()) {
						return submissionMsg.get().delete(null)
								.then(updatedMessage)
								.map(MessageSpecTemplate::toMessageCreateSpec)
								.flatMap(spec -> ctx.bot().gateway()
										.getChannelById(Snowflake.of(lvlReqSettings.getReviewedLevelsChannelId()))
										.ofType(TextChannel.class)
										.flatMap(channel -> channel.createMessage(spec))
										.flatMap(message -> {
											submission.get().setMessageId(message.getId().asLong());
											submission.get().setMessageChannelId(message.getChannelId().asLong());
											submission.get().setIsReviewed(true);
											return ctx.bot().database().save(submission.get());
										}))
								.and(Mono.defer(() -> submitter.get().getPrivateChannel()
										.zipWith(updatedMessage.map(m -> new MessageSpecTemplate("Your level request from **"
												+ guild.get().getName() + "** has been reviewed!", m.getEmbed()).toMessageCreateSpec()))
										.flatMap(TupleUtils.function(PrivateChannel::createMessage))
										.onErrorResume(e -> Mono.empty())));
					} else {
						return updatedMessage.map(MessageSpecTemplate::toMessageEditSpec)
								.map(GDEvents::editSpecToRequest)
								.flatMap(submissionMsg.get()::edit);
					}
				})
				.then();
	}
	
	private static void checkYouTubeLink(String youtubeLink) {
		if (youtubeLink != null && !youtubeLink.matches("https?://youtu\\.be/.*")
				&& !youtubeLink.matches("https?://(.*\\.)?youtube\\.com/watch\\?.*")) {
			throw new CommandFailedException("Invalid YouTube link");
		}
	}
	
	private static String formatChannel(long id) {
		return id == 0 ? "*Not configured*" : "<#" + id + ">";
	}
	
	private static String formatNumber(int n) {
		return n == 0 ? "*Not configured*" : "" + n;
	}
}
