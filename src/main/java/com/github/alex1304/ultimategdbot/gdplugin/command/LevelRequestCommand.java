package com.github.alex1304.ultimategdbot.gdplugin.command;

import static discord4j.core.retriever.EntityRetrievalStrategy.STORE_FALLBACK_REST;
import static java.util.stream.Collectors.toUnmodifiableList;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

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
import com.github.alex1304.ultimategdbot.api.database.DatabaseService;
import com.github.alex1304.ultimategdbot.api.emoji.EmojiService;
import com.github.alex1304.ultimategdbot.api.util.DiscordFormatter;
import com.github.alex1304.ultimategdbot.api.util.MessageSpecTemplate;
import com.github.alex1304.ultimategdbot.gdplugin.GDService;
import com.github.alex1304.ultimategdbot.gdplugin.database.GDLevelRequestConfigDao;
import com.github.alex1304.ultimategdbot.gdplugin.database.GDLevelRequestConfigData;
import com.github.alex1304.ultimategdbot.gdplugin.database.GDLevelRequestReviewDao;
import com.github.alex1304.ultimategdbot.gdplugin.database.GDLevelRequestSubmissionDao;
import com.github.alex1304.ultimategdbot.gdplugin.database.GDLevelRequestSubmissionData;
import com.github.alex1304.ultimategdbot.gdplugin.database.ImmutableGDLevelRequestReviewData;
import com.github.alex1304.ultimategdbot.gdplugin.database.ImmutableGDLevelRequestSubmissionData;
import com.github.alex1304.ultimategdbot.gdplugin.util.GDEvents;
import com.github.alex1304.ultimategdbot.gdplugin.util.GDLevelRequests;

import discord4j.common.util.Snowflake;
import discord4j.core.object.entity.Guild;
import discord4j.core.object.entity.User;
import discord4j.core.object.entity.channel.PrivateChannel;
import discord4j.core.object.entity.channel.TextChannel;
import discord4j.rest.entity.RestMessage;
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
		return Mono.zip(ctx.bot().service(EmojiService.class).emoji("success"), ctx.bot().service(EmojiService.class).emoji("failed"))
				.flatMap(TupleUtils.function((success, failed) -> ctx.bot().service(DatabaseService.class)
						.withExtension(GDLevelRequestConfigDao.class, dao -> dao.getOrCreate(guildId.asLong()))
						.flatMap(lvlReqCfg -> ctx.args().tokenCount() > 1
								? Mono.error(new CommandFailedException("Hmm, did you mean \"" + ctx.prefixUsed()
										+ "lvlreq **submit** " + ctx.args().getAllAfter(1) + "\"?"))
								: Mono.just(lvlReqCfg))
						.zipWhen(lvlReqCfg -> Mono.justOrEmpty(lvlReqCfg.roleReviewerId())
								.flatMap(roleId -> ctx.bot().gateway().getRoleById(guildId, roleId))
								.map(DiscordFormatter::formatRole)
								.defaultIfEmpty("*Not configured*"))
						.flatMap(TupleUtils.function((lvlReqCfg, reviewerRole) -> ctx.reply("**__Get other players to play your "
								+ "levels and give feedback with the Level Request feature!__**\n\n" + (lvlReqCfg.isOpen()
								? success + " level requests are OPENED" : failed + " level requests are CLOSED") + "\n\n"
								+ "**Submission channel:** " + formatChannel(lvlReqCfg.channelSubmissionQueueId()) + "\n"
								+ "**Reviewed levels channel:** " + formatChannel(lvlReqCfg.channelArchivedSubmissionsId()) + "\n"
								+ "**Reviewer role:** " + reviewerRole + "\n"
								+ "**Number of reviews required:** " + lvlReqCfg.minReviewsRequired() + "\n"
								+ "**Max queued submissions per person:** " + lvlReqCfg.maxQueuedSubmissionsPerUser() + "\n\n"
								+ "Server admins can change the above values via `" + ctx.prefixUsed() + "setup`, "
								+ "and they can " + (lvlReqCfg.isOpen() ? "close" : "open") + " level requests by using `"
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
				.then(ctx.bot().service(EmojiService.class).emoji("success")
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
		return ctx.channel().typeUntil(GDLevelRequests.retrieveConfig(ctx)
				.flatMap(lvlReqCfg -> doReview(ctx, submissionId, reviewContent, guildId.asLong(), lvlReqCfg, null, false)))
				.then(ctx.bot().service(EmojiService.class).emoji("success").flatMap(emoji -> ctx.reply(emoji + " The submission has been updated.")))
				.then();
	}
	
	@CommandAction("submit")
	@CommandDoc("Submit a new level request. This command can only be used in the configured submission channel, "
			+ "and only if level requests are opened.")
	public Mono<Void> runSubmit(Context ctx, long levelId, @Nullable String youtubeLink) {
		checkYouTubeLink(youtubeLink);
		final var guildId = ctx.event().getGuildId().orElseThrow();
		final var user = ctx.event().getMessage().getAuthor().orElseThrow();
		final var lvlReqCfg = new AtomicReference<GDLevelRequestConfigData>();
		final var level = new AtomicReference<GDLevel>();
		final var guildSubmissions = new AtomicReference<Flux<GDLevelRequestSubmissionData>>();
		return ctx.channel().typeUntil(GDLevelRequests.retrieveConfig(ctx)
				.doOnNext(lvlReqCfg::set)
				.doOnNext(System.err::println)
				.filter(lrs -> ctx.event().getMessage().getChannelId().equals(lrs.channelSubmissionQueueId().orElseThrow()))
				.switchIfEmpty(Mono.error(() -> new CommandFailedException("You can only use this command in <#"
						+ lvlReqCfg.get().channelSubmissionQueueId().orElseThrow().asString() + ">.")))
				.filter(GDLevelRequestConfigData::isOpen)
				.switchIfEmpty(Mono.error(new CommandFailedException("Level requests are closed, no submissions are being accepted.")))
				.doOnNext(__ -> guildSubmissions.set(GDLevelRequests.retrieveSubmissionsForGuild(ctx.bot(), guildId.asLong()).cache()))
				.filterWhen(lrs -> guildSubmissions.get().all(s -> s.levelId() != levelId))
				.switchIfEmpty(Mono.error(new CommandFailedException("This level is already in queue.")))
				.filterWhen(lrs -> guildSubmissions.get()
						.filter(s -> !s.isReviewed() && s.submitterId().equals(user.getId()))
						.filterWhen(s -> ctx.bot().rest()
								.getMessageById(s.messageChannelId().orElseThrow(), s.messageId().orElseThrow())
								.getData()
								.hasElement()
								.onErrorReturn(true))
						.count()
						.map(n -> n < lrs.maxQueuedSubmissionsPerUser()))
				.switchIfEmpty(Mono.error(() -> new CommandFailedException("You've reached the maximum number of submissions allowed in queue per person ("
						+ lvlReqCfg.get().maxQueuedSubmissionsPerUser() + "). Wait for one of your queued requests to be "
						+ "reviewed before trying again.")))
				.then(gdService.getGdClient()
						.getLevelById(levelId)
						.onErrorMap(MissingAccessException.class, e -> new CommandFailedException("Level not found."))
						.doOnNext(level::set))
				.then(Mono.fromCallable(() -> ImmutableGDLevelRequestSubmissionData.builder()
								.submissionId(0) // unknown yet
								.guildId(guildId)
								.levelId(levelId)
								.submissionTimestamp(Instant.now())
								.submitterId(user.getId())
								.youtubeLink(Optional.ofNullable(youtubeLink))
								.isReviewed(false)
								.build())
						.flatMap(s -> ctx.bot().service(DatabaseService.class)
								.withExtension(GDLevelRequestSubmissionDao.class, dao -> dao.insert(s))
								.map(id -> ImmutableGDLevelRequestSubmissionData.builder()
										.from(s)
										.submissionId(id)
										.build()))
						.flatMap(s -> GDLevelRequests.buildSubmissionMessage(ctx.bot(), user, level.get(), lvlReqCfg.get(), s, List.of())
								.map(MessageSpecTemplate::toMessageCreateSpec)
								.flatMap(ctx::reply)
								.flatMap(message -> ctx.bot().service(DatabaseService.class)
										.useExtension(GDLevelRequestSubmissionDao.class, dao -> dao.setMessageAndChannel(
												s.submissionId(), 
												message.getChannelId().asLong(), 
												message.getId().asLong())))
								.doOnError(Throwable::printStackTrace)
								.onErrorResume(e -> ctx.bot().service(DatabaseService.class)
										.useExtension(GDLevelRequestSubmissionDao.class, dao -> dao.delete(s.submissionId()))))))
				.then(ctx.bot().service(EmojiService.class).emoji("success").flatMap(emoji -> ctx.reply(emoji + " Level request submitted!")))
				.then();
	}
	
	@CommandAction("toggle")
	@CommandDoc("Enable or disable level requests for this server.")
	@CommandPermission(level = PermissionLevel.GUILD_ADMIN)
	public Mono<Void> runToggle(Context ctx) {
		var isOpening = new AtomicBoolean();
		return GDLevelRequests.retrieveConfig(ctx)
				.doOnNext(lvlReqCfg -> isOpening.set(!lvlReqCfg.isOpen()))
				.flatMap(lvlReqCfg -> ctx.bot().service(DatabaseService.class)
						.useExtension(GDLevelRequestConfigDao.class, dao -> dao.toggleOpenState(lvlReqCfg.guildId().asLong(), isOpening.get())))
				.then(Mono.defer(() -> ctx.reply("Level requests are now " + (isOpening.get() ? "opened" : "closed") + "!")))
				.then();
	}
	
	@CommandAction("purge_invalid_submissions")
	@CommandDoc("Removes all submissions referring to levels that were deleted or that were already rated.")
	@CommandPermission(name = "LEVEL_REQUEST_REVIEWER")
	public Mono<Void> runPurgeInvalidSubmissions(Context ctx) {
		var guildId = ctx.event().getGuildId().orElseThrow();
		return GDLevelRequests.retrieveConfig(ctx)
				.flatMap(lvlReqCfg -> GDLevelRequests.retrieveSubmissionsForGuild(ctx.bot(), guildId.asLong())
						.flatMap(submission -> gdService.getGdClient().getLevelById(submission.levelId())
								.filter(level -> level.getStars() > 0)
								.flatMap(level -> doReview(ctx, submission.submissionId(), "Got rated after being submitted.",
										guildId.asLong(), lvlReqCfg, submission, true).thenReturn(1))
								.onErrorResume(MissingAccessException.class, e -> ctx.bot().rest()
										.getMessageById(submission.messageChannelId().orElseThrow(), submission.messageId().orElseThrow())
										.delete(null)
										.onErrorResume(e0 -> Mono.empty())
										.thenReturn(1)))
						.reduce(Integer::sum)
						.flatMap(count -> ctx.bot().service(EmojiService.class).emoji("success")
								.flatMap(emoji -> ctx.reply(emoji + " Successfully purged **" + count + "** invalid submissions.")))
						.switchIfEmpty(ctx.bot().service(EmojiService.class).emoji("cross").flatMap(emoji -> ctx.reply(emoji + " No submissions to purge."))))
				.then();
	}
	
	private Mono<Void> doReview(Context ctx, long submissionId, String reviewContent, long guildId, 
			GDLevelRequestConfigData lvlReqCfg, @Nullable GDLevelRequestSubmissionData submissionObj, boolean forceMove) {
		final var userId = ctx.author().getId();
		final var submission = new AtomicReference<GDLevelRequestSubmissionData>(submissionObj);
		final var level = new AtomicReference<GDLevel>();
		final var submissionMsg = new AtomicReference<RestMessage>();
		final var submitter = new AtomicReference<User>();
		final var guild = new AtomicReference<Guild>();
		final var isRevoke = reviewContent.equalsIgnoreCase("revoke");
		if (reviewContent.length() > 1000) {
			return Mono.error(new CommandFailedException("Review content must not exceed 1000 characters."));
		}
		return Mono.justOrEmpty(submissionObj)
				.switchIfEmpty(ctx.bot().service(DatabaseService.class).withExtension(GDLevelRequestSubmissionDao.class, dao -> dao.get(submissionId))
						.<GDLevelRequestSubmissionData>flatMap(Mono::justOrEmpty)
						.doOnNext(submission::set)
						.filter(s -> s.guildId().asLong() == guildId)
						.filter(s -> !s.isReviewed())
						.switchIfEmpty(Mono.error(new CommandFailedException("This submission has already been moved.")))
						.filter(s -> !s.submitterId().equals(userId))
						.switchIfEmpty(Mono.error(new CommandFailedException("You can't review your own submission."))))
				.filterWhen(s -> Mono.just(ctx.bot().rest()
						.getMessageById(s.messageChannelId().orElseThrow(), s.messageId().orElseThrow()))
						.doOnNext(submissionMsg::set)
						.flatMap(__ -> ctx.bot().gateway()
								.withRetrievalStrategy(STORE_FALLBACK_REST)
								.getUserById(s.submitterId())
								.doOnNext(submitter::set))
						.flatMap(__ -> ctx.event().getGuild())
								.doOnNext(guild::set)
						.hasElement())
				.switchIfEmpty(Mono.error(new CommandFailedException("Unable to find submission of ID " + submissionId + ".")))
				.thenMany(Flux.defer(() -> Flux.fromIterable(submission.get().reviews())))
				.filter(r -> r.reviewerId().equals(userId))
				.next()
				.flatMap(r -> isRevoke ? ctx.bot().service(DatabaseService.class)
						.useExtension(GDLevelRequestReviewDao.class, dao -> dao.delete(r.reviewId()))
						.then(Mono.empty()) : Mono.just(r))
				.map(r -> ImmutableGDLevelRequestReviewData.builder()
						.from(r))
				.switchIfEmpty(Mono.fromCallable(() -> ImmutableGDLevelRequestReviewData.builder()
							.reviewId(0)
							.reviewContent(reviewContent)
							.reviewerId(userId)
							.reviewTimestamp(Instant.now())
							.submissionId(submission.get().submissionId())))
				.map(ImmutableGDLevelRequestReviewData.Builder::build)
				.doOnNext(r -> submission.set(ImmutableGDLevelRequestSubmissionData.builder()
						.from(submission.get())
						.reviews(Stream.concat(submission.get().reviews().stream()
								.filter(rv -> rv.reviewId() != r.reviewId()), Stream.of(r))
								.collect(toUnmodifiableList()))
						.build()))
				.flatMap(r -> ctx.bot().service(DatabaseService.class).useExtension(GDLevelRequestReviewDao.class, dao -> dao.insert(r)))
				.then(Mono.defer(() -> gdService.getGdClient().getLevelById(submission.get().levelId())
						.doOnNext(level::set)
						.onErrorMap(MissingAccessException.class, e -> new CommandFailedException("Cannot " + (isRevoke ? "revoke" : "add")
								+ " review: the level associated to this submission doesn't seem to exist on GD servers anymore. "
								+ "You may want to delete this submission, or run `" + ctx.prefixUsed() + "lvlreq purge_invalid_submissions`."))))
				.thenMany(Flux.defer(() -> Flux.fromIterable(submission.get().reviews())))
				.collectList()
				.flatMap(reviewList -> {
					var updatedMessage = GDLevelRequests.buildSubmissionMessage(ctx.bot(), submitter.get(),
							level.get(), lvlReqCfg, submission.get(), reviewList).cache();
					if (forceMove || reviewList.size() >= lvlReqCfg.minReviewsRequired()) {
						return submissionMsg.get().delete(null)
								.then(updatedMessage)
								.map(MessageSpecTemplate::toMessageCreateSpec)
								.flatMap(spec -> ctx.bot().gateway()
										.getChannelById(lvlReqCfg.channelArchivedSubmissionsId().orElseThrow())
										.ofType(TextChannel.class)
										.flatMap(channel -> channel.createMessage(spec))
//										.flatMap(message -> {
//											submission.get().setMessageId(message.getId().asLong());
//											submission.get().setMessageChannelId(message.getChannelId().asLong());
//											submission.get().setIsReviewed(true);
//											return ctx.bot().service(DatabaseService.class).save(submission.get());
//										}))
										.flatMap(message -> ctx.bot().service(DatabaseService.class)
												.useExtension(GDLevelRequestSubmissionDao.class, dao -> dao.archive(
														submission.get().submissionId(),
														message.getChannelId().asLong(),
														message.getId().asLong())))
								).and(Mono.defer(() -> submitter.get().getPrivateChannel()
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
	
	private static String formatChannel(Optional<Snowflake> idOptional) {
		return idOptional.map(Snowflake::asString)
				.map(id -> "<#" + id + ">")
				.orElse("*Not configured*");
	}
}
