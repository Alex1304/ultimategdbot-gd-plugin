package com.github.alex1304.ultimategdbot.gdplugin.levelrequest;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import com.github.alex1304.jdash.entity.GDLevel;
import com.github.alex1304.jdash.exception.MissingAccessException;
import com.github.alex1304.ultimategdbot.api.Command;
import com.github.alex1304.ultimategdbot.api.CommandFailedException;
import com.github.alex1304.ultimategdbot.api.Context;
import com.github.alex1304.ultimategdbot.api.DatabaseException;
import com.github.alex1304.ultimategdbot.api.PermissionLevel;
import com.github.alex1304.ultimategdbot.api.Plugin;
import com.github.alex1304.ultimategdbot.api.utils.ArgUtils;
import com.github.alex1304.ultimategdbot.gdplugin.GDPlugin;
import com.github.alex1304.ultimategdbot.gdplugin.database.GDLevelRequestReviews;
import com.github.alex1304.ultimategdbot.gdplugin.database.GDLevelRequestSubmissions;
import com.github.alex1304.ultimategdbot.gdplugin.database.GDLevelRequestsSettings;
import com.github.alex1304.ultimategdbot.gdplugin.util.LevelRequestUtils;

import discord4j.core.object.entity.Channel.Type;
import discord4j.core.object.entity.Guild;
import discord4j.core.object.entity.Message;
import discord4j.core.object.entity.PrivateChannel;
import discord4j.core.object.entity.TextChannel;
import discord4j.core.object.entity.User;
import discord4j.core.object.util.Snowflake;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.function.TupleUtils;

public class LevelRequestReviewCommand implements Command {
	
	private final GDPlugin plugin;
	
	public LevelRequestReviewCommand(GDPlugin plugin) {
		this.plugin = Objects.requireNonNull(plugin);
	}

	@Override
	public Mono<Void> execute(Context ctx) {
		ArgUtils.requireMinimumArgCount(ctx, 3);
		final var guildId = ctx.getEvent().getGuildId().orElseThrow().asLong();
		final var userId = ctx.getEvent().getMessage().getAuthor().orElseThrow().getId().asLong();
		final var lvlReqSettings = new AtomicReference<GDLevelRequestsSettings>();
		final var submission = new AtomicReference<GDLevelRequestSubmissions>();
		final var review = new AtomicReference<GDLevelRequestReviews>();
		final var level = new AtomicReference<GDLevel>();
		final var submissionMsg = new AtomicReference<Message>();
		final var submitter = new AtomicReference<User>();
		final var guild = new AtomicReference<Guild>();
		final var submissionId = ArgUtils.getArgAsLong(ctx, 1);
		final var reviewContent = ArgUtils.concatArgs(ctx, 2);
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
				.then(Mono.defer(() -> plugin.getGdClient().getLevelById(submission.get().getLevelId())
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
								.map(SubmissionMessage::toMessageCreateSpec)
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
										.zipWith(updatedMessage.map(m -> new SubmissionMessage("Your level request from **"
												+ guild.get().getName() + "** has been reviewed!", m.getEmbed()).toMessageCreateSpec()))
										.flatMap(TupleUtils.function(PrivateChannel::createMessage))
										.onErrorResume(e -> Mono.empty())));
					} else {
						return updatedMessage.map(SubmissionMessage::toMessageEditSpec).flatMap(submissionMsg.get()::edit);
					}
				})
				.then(ctx.getBot().getEmoji("success").flatMap(emoji -> ctx.reply(emoji + " Review " + (isRevoke.get() ? "revoked" : "added") + "!")))
				.then();
	}

	@Override
	public Set<String> getAliases() {
		return Set.of("review");
	}

	@Override
	public String getDescription() {
		return "Add a review on a level request.";
	}

	@Override
	public String getLongDescription() {
		return "You can only use this command if:\n"
				+ "- You have the reviewer role, as configured in this server\n"
				+ "- You run this command in the submission channel\n"
				+ "- The targeted submission exists and hasn't already been moved out of the queue.\n"
				+ "Note that you can still add reviews to queue levels while level requests are closed, and "
				+ "each review must not exceed 1000 characters.\n\n"
				+ "To revoke your review from a submission, the syntax is `review <submission_ID> revoke`.";
	}

	@Override
	public String getSyntax() {
		return "<submission_ID> <review_content>";
	}
	
	@Override
	public EnumSet<Type> getChannelTypesAllowed() {
		return EnumSet.of(Type.GUILD_TEXT);
	}
	
	@Override
	public PermissionLevel getPermissionLevel() {
		return PermissionLevel.forSpecificRole(ctx -> ctx.getBot().getDatabase()
				.findByID(GDLevelRequestsSettings.class, ctx.getEvent().getGuildId().orElseThrow().asLong())
				.map(GDLevelRequestsSettings::getReviewerRoleId)
				.defaultIfEmpty(0L)
				.map(Snowflake::of));
	}

	@Override
	public Plugin getPlugin() {
		return plugin;
	}
}
