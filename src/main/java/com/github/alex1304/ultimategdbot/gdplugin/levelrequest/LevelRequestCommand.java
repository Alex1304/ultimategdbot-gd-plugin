package com.github.alex1304.ultimategdbot.gdplugin.levelrequest;

import static java.util.Objects.requireNonNullElse;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import com.github.alex1304.jdash.entity.GDLevel;
import com.github.alex1304.jdash.exception.MissingAccessException;
import com.github.alex1304.ultimategdbot.api.DatabaseException;
import com.github.alex1304.ultimategdbot.api.command.CommandFailedException;
import com.github.alex1304.ultimategdbot.api.command.Context;
import com.github.alex1304.ultimategdbot.api.command.PermissionLevel;
import com.github.alex1304.ultimategdbot.api.command.Scope;
import com.github.alex1304.ultimategdbot.api.command.annotated.CommandAction;
import com.github.alex1304.ultimategdbot.api.command.annotated.CommandDoc;
import com.github.alex1304.ultimategdbot.api.command.annotated.CommandSpec;
import com.github.alex1304.ultimategdbot.api.utils.UniversalMessageSpec;
import com.github.alex1304.ultimategdbot.gdplugin.GDServiceMediator;
import com.github.alex1304.ultimategdbot.gdplugin.database.GDLevelRequestReviews;
import com.github.alex1304.ultimategdbot.gdplugin.database.GDLevelRequestSubmissions;
import com.github.alex1304.ultimategdbot.gdplugin.database.GDLevelRequestsSettings;
import com.github.alex1304.ultimategdbot.gdplugin.util.LevelRequestUtils;

import discord4j.core.object.entity.Guild;
import discord4j.core.object.entity.Message;
import discord4j.core.object.entity.PrivateChannel;
import discord4j.core.object.entity.Role;
import discord4j.core.object.entity.TextChannel;
import discord4j.core.object.entity.User;
import discord4j.core.object.util.Snowflake;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.function.TupleUtils;
import reactor.util.annotation.Nullable;

@CommandSpec(
		aliases = { "levelrequest", "lvlreq" },
		shortDescription = "Submit your levels for other players to give feedback on them.",
		scope = Scope.GUILD_ONLY
)
public class LevelRequestCommand {

	private final GDServiceMediator gdServiceMediator;
	
	public LevelRequestCommand(GDServiceMediator gdServiceMediator) {
		this.gdServiceMediator = gdServiceMediator;
	}

	@CommandAction
	@CommandDoc("Submit your levels in the submission queue channel using `levelrequest submit <your_level_ID>`. Then people with the "
				+ "reviewer role will review your submission, and once you get a certain number of reviews your level will be moved to "
				+ "the reveiwed levels channel and you will be notified in DMs. You are only allowed to submit a limited number of "
				+ "levels at once.\n\n"
				+ "For more details on how level requests work, check out this guide: <https://github.com/Alex1304/ultimategdbot-gd-plugin"
				+ "/wiki/Level-Requests-Tutorial>")
	public Mono<Void> run(Context ctx) {
		var guildId = ctx.getEvent().getGuildId().orElseThrow();
		return Mono.zip(ctx.getBot().getEmoji("success"), ctx.getBot().getEmoji("failed"))
				.flatMap(TupleUtils.function((success, failed) -> ctx.getBot().getDatabase()
						.findByID(GDLevelRequestsSettings.class, guildId.asLong())
						.switchIfEmpty(Mono.fromCallable(() -> {
							var lvlReqSettings = new GDLevelRequestsSettings();
							lvlReqSettings.setGuildId(guildId.asLong());
							return lvlReqSettings;
						}).flatMap(lvlReqSettings -> ctx.getBot().getDatabase().save(lvlReqSettings).thenReturn(lvlReqSettings)))
						.flatMap(lvlReqSettings -> ctx.getArgs().tokenCount() > 1
								? Mono.error(new CommandFailedException("Hmm, did you mean \"" + ctx.getPrefixUsed()
										+ "lvlreq **submit** " + ctx.getArgs().getAllAfter(1) + "\"?"))
								: Mono.just(lvlReqSettings))
						.zipWhen(lvlReqSettings -> lvlReqSettings.getReviewerRoleId() == 0 ? Mono.just("*Not configured*") : ctx.getBot().getMainDiscordClient()
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
								+ "Server admins can change the above values via `" + ctx.getPrefixUsed() + "setup`, "
								+ "and they can " + (lvlReqSettings.getIsOpen() ? "close" : "open") + " level requests by using `"
								+ ctx.getPrefixUsed() + "levelrequest toggle`.\nFor more details on how level requests work, check "
								+ "out this guide: <https://github.com/Alex1304/ultimategdbot-gd-plugin/wiki/Level-Requests-Tutorial>")))))
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
	public Mono<Void> runReview(Context ctx, long submissionId, String reviewContent) {
		return ctx.getAuthor().asMember(ctx.getEvent().getGuildId().orElseThrow())
				.flatMap(member -> ctx.getBot().getDatabase()
						.findByID(GDLevelRequestsSettings.class, ctx.getEvent().getGuildId().orElseThrow().asLong())
						.map(GDLevelRequestsSettings::getReviewerRoleId)
						.defaultIfEmpty(0L)
						.map(Snowflake::of)
						.filter(member.getRoleIds()::contains))
				.switchIfEmpty(Mono.error(new CommandFailedException("You are not a level reviewer in this server.")))
				.then(Mono.defer(() -> {
					final var guildId = ctx.getEvent().getGuildId().orElseThrow().asLong();
					final var userId = ctx.getEvent().getMessage().getAuthor().orElseThrow().getId().asLong();
					final var lvlReqSettings = new AtomicReference<GDLevelRequestsSettings>();
					final var submission = new AtomicReference<GDLevelRequestSubmissions>();
					final var review = new AtomicReference<GDLevelRequestReviews>();
					final var level = new AtomicReference<GDLevel>();
					final var submissionMsg = new AtomicReference<Message>();
					final var submitter = new AtomicReference<User>();
					final var guild = new AtomicReference<Guild>();
					final var isRevoke = new AtomicBoolean(reviewContent.equalsIgnoreCase("revoke"));
					final var reviewsOnSubmission = Flux.defer(() -> LevelRequestUtils.getReviewsForSubmission(ctx.getBot(), submission.get()));
					if (reviewContent.length() > 1000) {
						return Mono.error(new CommandFailedException("Review content must not exceed 1000 characters."));
					}
					return LevelRequestUtils.getLevelRequestsSettings(ctx)
							.doOnNext(lvlReqSettings::set)
							.filter(lrs -> ctx.getEvent().getMessage().getChannelId().asLong() == lrs.getSubmissionQueueChannelId())
							.switchIfEmpty(Mono.error(() -> new CommandFailedException("You can only use this command in <#"
									+ lvlReqSettings.get().getSubmissionQueueChannelId() + ">.")))
							.then(ctx.getBot().getDatabase().findByID(GDLevelRequestSubmissions.class, submissionId))
							.doOnNext(submission::set)
							.filter(s -> s.getGuildId() == guildId)
							.filterWhen(s -> ctx.getBot().getMainDiscordClient()
									.getMessageById(ctx.getEvent().getMessage().getChannelId(), Snowflake.of(s.getMessageId()))
									.doOnNext(submissionMsg::set)
									.flatMap(__ -> ctx.getBot().getMainDiscordClient()
											.getUserById(Snowflake.of(s.getSubmitterId()))
											.doOnNext(submitter::set))
									.flatMap(__ -> ctx.getEvent().getGuild())
											.doOnNext(guild::set)
									.hasElement())
							.switchIfEmpty(Mono.error(new CommandFailedException("Unable to find submission of ID " + submissionId + ".")))
							.filter(s -> !s.getIsReviewed())
							.switchIfEmpty(Mono.error(new CommandFailedException("This submission has already been moved.")))
							.filter(s -> s.getSubmitterId() != userId)
							.switchIfEmpty(Mono.error(new CommandFailedException("You can't review your own submission.")))
							.thenMany(reviewsOnSubmission)
							.filter(r -> r.getReviewerId() == userId)
							.next()
							.flatMap(r -> isRevoke.get() ? ctx.getBot().getDatabase().delete(r).then(Mono.empty()) : Mono.just(r))
							.switchIfEmpty(Mono.fromCallable(() -> {
								if (isRevoke.get()) {
									return null;
								}
								var r = new GDLevelRequestReviews();
								review.set(r);
								return r;
							}))
							.doOnNext(r -> {
								r.setReviewContent(reviewContent);
								r.setReviewerId(userId);
								r.setReviewTimestamp(Timestamp.from(Instant.now()));
								r.setSubmission(submission.get());
							})
							.flatMap(ctx.getBot().getDatabase()::save)
							.onErrorMap(DatabaseException.class, e -> new CommandFailedException("Something went wrong when saving your review. Try again.", e))
							.then(Mono.defer(() -> gdServiceMediator.getGdClient().getLevelById(submission.get().getLevelId())
									.doOnNext(level::set)
									.onErrorMap(MissingAccessException.class, e -> new CommandFailedException("Cannot " + (isRevoke.get() ? "revoke" : "add")
											+ " review: the level associated to this submission doesn't seem to exist on GD servers anymore. "
											+ "You may want to delete this submission."))))
							.thenMany(reviewsOnSubmission)
							.collectList()
							.flatMap(reviewList -> {
								var updatedMessage = LevelRequestUtils.buildSubmissionMessage(ctx.getBot(), submitter.get(),
										level.get(), lvlReqSettings.get(), submission.get(), reviewList).cache();
								if (reviewList.size() >= lvlReqSettings.get().getMaxReviewsRequired()) {
									return submissionMsg.get().delete()
											.then(updatedMessage)
											.map(UniversalMessageSpec::toMessageCreateSpec)
											.flatMap(spec -> ctx.getBot().getMainDiscordClient()
													.getChannelById(Snowflake.of(lvlReqSettings.get().getReviewedLevelsChannelId()))
													.ofType(TextChannel.class)
													.flatMap(channel -> channel.createMessage(spec))
													.flatMap(message -> {
														submission.get().setMessageId(message.getId().asLong());
														submission.get().setIsReviewed(true);
														return ctx.getBot().getDatabase().save(submission.get());
													}))
											.and(Mono.defer(() -> submitter.get().getPrivateChannel()
													.zipWith(updatedMessage.map(m -> new UniversalMessageSpec("Your level request from **"
															+ guild.get().getName() + "** has been reviewed!", m.getEmbed()).toMessageCreateSpec()))
													.flatMap(TupleUtils.function(PrivateChannel::createMessage))
													.onErrorResume(e -> Mono.empty())));
								} else {
									return updatedMessage.map(UniversalMessageSpec::toMessageEditSpec).flatMap(submissionMsg.get()::edit);
								}
							})
							.then(ctx.getBot().getEmoji("success").flatMap(emoji -> ctx.reply(emoji + " Review " + (isRevoke.get() ? "revoked" : "added") + "!")))
							.then();
				}));
	}
	
	@CommandAction("submit")
	@CommandDoc("Submit a new level request. This command can only be used in the configured submission channel, "
			+ "and only if level requests are opened.")
	public Mono<Void> runSubmit(Context ctx, long levelId, @Nullable String youtubeLink) {
		checkYouTubeLink(youtubeLink);
		final var guildId = ctx.getEvent().getGuildId().orElseThrow().asLong();
		final var user = ctx.getEvent().getMessage().getAuthor().orElseThrow();
		final var lvlReqSettings = new AtomicReference<GDLevelRequestsSettings>();
		final var level = new AtomicReference<GDLevel>();
		final var guildSubmissions = new AtomicReference<Flux<GDLevelRequestSubmissions>>();
		return LevelRequestUtils.getLevelRequestsSettings(ctx)
				.doOnNext(lvlReqSettings::set)
				.filter(lrs -> ctx.getEvent().getMessage().getChannelId().asLong() == lrs.getSubmissionQueueChannelId())
				.switchIfEmpty(Mono.error(() -> new CommandFailedException("You can only use this command in <#"
						+ lvlReqSettings.get().getSubmissionQueueChannelId() + ">.")))
				.filter(GDLevelRequestsSettings::getIsOpen)
				.switchIfEmpty(Mono.error(new CommandFailedException("Level requests are closed, no submissions are being accepted.")))
				.doOnNext(__ -> guildSubmissions.set(LevelRequestUtils.getSubmissionsForGuild(ctx.getBot().getDatabase(), guildId).cache()))
				.filterWhen(lrs -> guildSubmissions.get().all(s -> s.getIsReviewed() || s.getLevelId() != levelId))
				.switchIfEmpty(Mono.error(new CommandFailedException("This level is already in queue.")))
				.filterWhen(lrs -> guildSubmissions.get().filter(s -> !s.getIsReviewed() && s.getSubmitterId() == user.getId().asLong())
						.count().map(n -> n < lrs.getMaxQueuedSubmissionsPerPerson()))
				.switchIfEmpty(Mono.error(() -> new CommandFailedException("You've reached the maximum number of submissions allowed in queue per person ("
						+ lvlReqSettings.get().getMaxQueuedSubmissionsPerPerson() + "). Wait for one of your queued requests to be "
						+ "reviewed before trying again.")))
				.then(gdServiceMediator.getGdClient()
						.getLevelById(levelId)
						.onErrorMap(MissingAccessException.class, e -> new CommandFailedException("Level not found."))
						.doOnNext(level::set))
				.then(Mono.fromCallable(() -> {
							var s = new GDLevelRequestSubmissions();
							s.setGuildId(guildId);
							s.setLevelId(levelId);
							s.setSubmissionTimestamp(Timestamp.from(Instant.now()));
							s.setSubmitterId(user.getId().asLong());
							s.setYoutubeLink(requireNonNullElse(youtubeLink, ""));
							s.setIsReviewed(false);
							return s;
						})
						.flatMap(s -> ctx.getBot().getDatabase().save(s).thenReturn(s).onErrorReturn(s)) // First save to know the submission ID
						.flatMap(s -> LevelRequestUtils.buildSubmissionMessage(ctx.getBot(), user, level.get(), lvlReqSettings.get(), s, List.of())
								.map(UniversalMessageSpec::toMessageCreateSpec)
								.flatMap(ctx::reply)
								.flatMap(message -> {
									s.setMessageId(message.getId().asLong());
									return ctx.getBot().getDatabase().save(s);
								})
								.onErrorResume(e -> ctx.getBot().getDatabase().delete(s).then(Mono.error(e)))))
				.then(ctx.getBot().getEmoji("success").flatMap(emoji -> ctx.reply(emoji + " Level request submitted!")))
				.then();
	}
	
	@CommandAction("toggle")
	@CommandDoc("Enable or disable level requests for this server.")
	public Mono<Void> runToggle(Context ctx) {
		var isOpening = new AtomicBoolean();
		return PermissionLevel.SERVER_ADMIN.checkGranted(ctx)
				.thenMany(LevelRequestUtils.getLevelRequestsSettings(ctx))
				.doOnNext(lvlReqSettings -> isOpening.set(!lvlReqSettings.getIsOpen()))
				.doOnNext(lvlReqSettings -> lvlReqSettings.setIsOpen(isOpening.get()))
				.flatMap(ctx.getBot().getDatabase()::save)
				.then(Mono.defer(() -> ctx.reply("Level requests are now " + (isOpening.get() ? "opened" : "closed") + "!")))
				.then();
	}
	
	private void checkYouTubeLink(String youtubeLink) {
		if (youtubeLink != null && !youtubeLink.matches("https?://youtu\\.be/.*")
				&& !youtubeLink.matches("https?://www\\.youtube\\.com/watch\\?.*")) {
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
