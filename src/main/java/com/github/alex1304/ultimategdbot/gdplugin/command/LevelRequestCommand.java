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
import com.github.alex1304.ultimategdbot.api.Translator;
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
		shortDescription = "tr:strings.gd/lvlreq_desc",
		scope = Scope.GUILD_ONLY
)
public class LevelRequestCommand {

	private final GDService gdService;
	
	public LevelRequestCommand(GDService gdService) {
		this.gdService = gdService;
	}

	@CommandAction
	@CommandDoc("tr:strings.gd/lvlreq_run")
	public Mono<Void> run(Context ctx) {
		var guildId = ctx.event().getGuildId().orElseThrow();
		return Mono.zip(ctx.bot().service(EmojiService.class).emoji("success"), ctx.bot().service(EmojiService.class).emoji("failed"))
				.flatMap(TupleUtils.function((success, failed) -> ctx.bot().service(DatabaseService.class)
						.withExtension(GDLevelRequestConfigDao.class, dao -> dao.getOrCreate(guildId.asLong()))
						.flatMap(lvlReqCfg -> ctx.args().tokenCount() > 1
								? Mono.error(new CommandFailedException(ctx.translate("strings.gd", 
										"error_submit_missing", ctx.prefixUsed(), ctx.args().getAllAfter(1))))
								: Mono.just(lvlReqCfg))
						.zipWhen(lvlReqCfg -> Mono.justOrEmpty(lvlReqCfg.roleReviewerId())
								.flatMap(roleId -> ctx.bot().gateway().getRoleById(guildId, roleId))
								.map(DiscordFormatter::formatRole)
								.defaultIfEmpty('*' + ctx.translate("strings.gd", "not_configured") + '*'))
						.flatMap(TupleUtils.function((lvlReqCfg, reviewerRole) -> ctx.reply(
								"**__" + ctx.translate("strings.gd", "lvlreq_intro") + "__**\n\n"
								+ (lvlReqCfg.isOpen()
										? success + ' ' + ctx.translate("strings.gd", "reqs_opened")
										: failed + ' ' + ctx.translate("strings.gd", "reqs_closed")) + "\n\n"
								+ "**" + ctx.translate("strings.gd", "label_submission_channel") + "** " + formatChannel(ctx, lvlReqCfg.channelSubmissionQueueId()) + "\n"
								+ "**" + ctx.translate("strings.gd", "label_archive_channel") + "** " + formatChannel(ctx, lvlReqCfg.channelArchivedSubmissionsId()) + "\n"
								+ "**" + ctx.translate("strings.gd", "label_reviewer_role") + "** " + reviewerRole + "\n"
								+ "**" + ctx.translate("strings.gd", "label_reviews_required") + "** " + lvlReqCfg.minReviewsRequired() + "\n"
								+ "**" + ctx.translate("strings.gd", "label_max_submissions") + "** " + lvlReqCfg.maxQueuedSubmissionsPerUser() + "\n\n"
								+ ctx.translate("strings.gd", "bottom_text", ctx.prefixUsed()))))))
				.then();
	}
	
	@CommandAction("clean_orphan_submissions")
	@CommandDoc("tr:strings.gd/lvlreq_run_clean_orphan_submissions")
	@CommandPermission(level = PermissionLevel.BOT_OWNER)
	public Mono<Void> runCleanOrphanSubmissions(Context ctx) {
		return GDLevelRequests.cleanOrphanSubmissions(ctx.bot())
				.then(ctx.bot().service(EmojiService.class).emoji("success")
						.flatMap(success -> ctx.reply(success + ' '
								+ ctx.translate("strings.gd", "orphan_submissions_cleaned"))))
				.then();
	}
	
	@CommandAction("review")
	@CommandDoc("tr:strings.gd/lvlreq_run_review")
	@CommandPermission(name = "LEVEL_REQUEST_REVIEWER")
	public Mono<Void> runReview(Context ctx, long submissionId, String reviewContent) {
		final var guildId = ctx.event().getGuildId().orElseThrow();
		return ctx.channel().typeUntil(GDLevelRequests.retrieveConfig(ctx)
				.flatMap(lvlReqCfg -> doReview(ctx, submissionId, reviewContent, guildId.asLong(), lvlReqCfg, null, false)))
				.then(ctx.bot().service(EmojiService.class).emoji("success")
						.flatMap(emoji -> ctx.reply(emoji + ' ' + ctx.translate("strings.gd", "submission_updated"))))
				.then();
	}
	
	@CommandAction("submit")
	@CommandDoc("tr:strings.gd/lvlreq_run_submit")
	public Mono<Void> runSubmit(Context ctx, long levelId, @Nullable String youtubeLink) {
		checkYouTubeLink(ctx, youtubeLink);
		final var guildId = ctx.event().getGuildId().orElseThrow();
		final var user = ctx.event().getMessage().getAuthor().orElseThrow();
		final var lvlReqCfg = new AtomicReference<GDLevelRequestConfigData>();
		final var level = new AtomicReference<GDLevel>();
		final var guildSubmissions = new AtomicReference<Flux<GDLevelRequestSubmissionData>>();
		return ctx.channel().typeUntil(GDLevelRequests.retrieveConfig(ctx)
				.doOnNext(lvlReqCfg::set)
				.doOnNext(System.err::println)
				.filter(lrs -> ctx.event().getMessage().getChannelId().equals(lrs.channelSubmissionQueueId().orElseThrow()))
				.switchIfEmpty(Mono.error(() -> new CommandFailedException(ctx.translate("strings.gd", "submission_updated",
						"<#" + lvlReqCfg.get().channelSubmissionQueueId().orElseThrow().asString() + ">"))))
				.filter(GDLevelRequestConfigData::isOpen)
				.switchIfEmpty(Mono.error(new CommandFailedException(ctx.translate("strings.gd", "error_reqs_closed"))))
				.doOnNext(__ -> guildSubmissions.set(GDLevelRequests.retrieveSubmissionsForGuild(ctx.bot(), guildId.asLong()).cache()))
				.filterWhen(lrs -> guildSubmissions.get().all(s -> s.levelId() != levelId))
				.switchIfEmpty(Mono.error(new CommandFailedException(ctx.translate("strings.gd", "error_already_in_queue"))))
				.filterWhen(lrs -> guildSubmissions.get()
						.filter(s -> !s.isReviewed() && s.submitterId().equals(user.getId()))
						.filterWhen(s -> ctx.bot().rest()
								.getMessageById(s.messageChannelId().orElseThrow(), s.messageId().orElseThrow())
								.getData()
								.hasElement()
								.onErrorReturn(true))
						.count()
						.map(n -> n < lrs.maxQueuedSubmissionsPerUser()))
				.switchIfEmpty(Mono.error(() -> new CommandFailedException(ctx.translate("cmdtext_gd_lvlreq",
						"error_max_submissions_reached", lvlReqCfg.get().maxQueuedSubmissionsPerUser()))))
				.then(gdService.getGdClient()
						.getLevelById(levelId)
						.onErrorMap(MissingAccessException.class, e -> new CommandFailedException(
								ctx.translate("strings.gd", "error_level_not_found")))
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
						.flatMap(s -> GDLevelRequests.buildSubmissionMessage(ctx, ctx.bot(), user, level.get(), lvlReqCfg.get(), s, List.of())
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
				.then(ctx.bot().service(EmojiService.class).emoji("success")
						.flatMap(emoji -> ctx.reply(emoji + ' ' + ctx.translate("strings.gd", "submit_success"))))
				.then();
	}
	
	@CommandAction("toggle")
	@CommandDoc("tr:strings.gd/lvlreq_run_toggle")
	@CommandPermission(level = PermissionLevel.GUILD_ADMIN)
	public Mono<Void> runToggle(Context ctx) {
		var isOpening = new AtomicBoolean();
		return GDLevelRequests.retrieveConfig(ctx)
				.doOnNext(lvlReqCfg -> isOpening.set(!lvlReqCfg.isOpen()))
				.flatMap(lvlReqCfg -> ctx.bot().service(DatabaseService.class)
						.useExtension(GDLevelRequestConfigDao.class, dao -> dao.toggleOpenState(lvlReqCfg.guildId().asLong(), isOpening.get())))
				.then(Mono.defer(() -> ctx.reply(ctx.translate("strings.gd", isOpening.get() ? "toggle_opened" : "toggle_closed"))))
				.then();
	}
	
	@CommandAction("purge_invalid_submissions")
	@CommandDoc("tr:strings.gd/lvlreq_run_purge_invalid_submissions")
	@CommandPermission(name = "LEVEL_REQUEST_REVIEWER")
	public Mono<Void> runPurgeInvalidSubmissions(Context ctx) {
		var guildId = ctx.event().getGuildId().orElseThrow();
		return GDLevelRequests.retrieveConfig(ctx)
				.flatMap(lvlReqCfg -> GDLevelRequests.retrieveSubmissionsForGuild(ctx.bot(), guildId.asLong())
						.flatMap(submission -> gdService.getGdClient().getLevelById(submission.levelId())
								.filter(level -> level.getStars() > 0)
								.flatMap(level -> doReview(ctx, submission.submissionId(), ctx.translate("strings.gd", "rated_after_submission"),
										guildId.asLong(), lvlReqCfg, submission, true).thenReturn(1))
								.onErrorResume(MissingAccessException.class, e -> ctx.bot().rest()
										.getMessageById(submission.messageChannelId().orElseThrow(), submission.messageId().orElseThrow())
										.delete(null)
										.onErrorResume(e0 -> Mono.empty())
										.thenReturn(1)))
						.reduce(Integer::sum)
						.flatMap(count -> ctx.bot().service(EmojiService.class).emoji("success")
								.flatMap(emoji -> ctx.reply(emoji + ' ' + ctx.translate("strings.gd", "purge_success"))))
						.switchIfEmpty(ctx.bot().service(EmojiService.class).emoji("cross").flatMap(emoji -> ctx.reply(emoji + ' '
								+ ctx.translate("strings.gd", "nothing_to_purge")))))
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
			return Mono.error(new CommandFailedException(ctx.translate("strings.gd", "error_review_overflow")));
		}
		return Mono.justOrEmpty(submissionObj)
				.switchIfEmpty(ctx.bot().service(DatabaseService.class).withExtension(GDLevelRequestSubmissionDao.class, dao -> dao.get(submissionId))
						.<GDLevelRequestSubmissionData>flatMap(Mono::justOrEmpty)
						.doOnNext(submission::set)
						.filter(s -> s.guildId().asLong() == guildId)
						.filter(s -> !s.isReviewed())
						.switchIfEmpty(Mono.error(new CommandFailedException(ctx.translate("strings.gd", "error_submission_already_moved"))))
						.filter(s -> !s.submitterId().equals(userId))
						.switchIfEmpty(Mono.error(new CommandFailedException(ctx.translate("strings.gd", "error_review_own_submission")))))
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
				.switchIfEmpty(Mono.error(new CommandFailedException(ctx.translate("strings.gd", "error_submission_not_found", submissionId))))
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
						.onErrorMap(MissingAccessException.class, e -> new CommandFailedException(ctx.translate("strings.gd", "error_level_deleted")))))
				.thenMany(Flux.defer(() -> Flux.fromIterable(submission.get().reviews())))
				.collectList()
				.flatMap(reviewList -> {
					var updatedMessage = GDLevelRequests.buildSubmissionMessage(ctx, ctx.bot(), submitter.get(),
							level.get(), lvlReqCfg, submission.get(), reviewList).cache();
					if (forceMove || reviewList.size() >= lvlReqCfg.minReviewsRequired()) {
						return submissionMsg.get().delete(null)
								.then(updatedMessage)
								.map(MessageSpecTemplate::toMessageCreateSpec)
								.flatMap(spec -> ctx.bot().gateway()
										.getChannelById(lvlReqCfg.channelArchivedSubmissionsId().orElseThrow())
										.ofType(TextChannel.class)
										.flatMap(channel -> channel.createMessage(spec))
										.flatMap(message -> ctx.bot().service(DatabaseService.class)
												.useExtension(GDLevelRequestSubmissionDao.class, dao -> dao.archive(
														submission.get().submissionId(),
														message.getChannelId().asLong(),
														message.getId().asLong())))
								).and(Mono.defer(() -> submitter.get().getPrivateChannel()
										.zipWith(updatedMessage.map(m -> new MessageSpecTemplate(ctx.translate("cmdtext_gd_lvlreq",
												"dm_title", guild.get().getName()), m.getEmbed()).toMessageCreateSpec()))
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
	
	private static void checkYouTubeLink(Translator tr, String youtubeLink) {
		if (youtubeLink != null && !youtubeLink.matches("https?://youtu\\.be/.*")
				&& !youtubeLink.matches("https?://(.*\\.)?youtube\\.com/watch\\?.*")) {
			throw new CommandFailedException(tr.translate("strings.gd", "error_yt_invalid"));
		}
	}
	
	private static String formatChannel(Translator tr, Optional<Snowflake> idOptional) {
		return idOptional.map(Snowflake::asString)
				.map(id -> "<#" + id + ">")
				.orElse('*' + tr.translate("strings.gd", "not_configured") + '*');
	}
}
