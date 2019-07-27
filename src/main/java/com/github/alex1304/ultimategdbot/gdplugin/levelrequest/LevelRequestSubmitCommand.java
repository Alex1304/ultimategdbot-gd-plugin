package com.github.alex1304.ultimategdbot.gdplugin.levelrequest;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import com.github.alex1304.jdash.entity.GDLevel;
import com.github.alex1304.jdash.exception.MissingAccessException;
import com.github.alex1304.ultimategdbot.api.Command;
import com.github.alex1304.ultimategdbot.api.CommandFailedException;
import com.github.alex1304.ultimategdbot.api.Context;
import com.github.alex1304.ultimategdbot.api.Plugin;
import com.github.alex1304.ultimategdbot.api.utils.ArgUtils;
import com.github.alex1304.ultimategdbot.gdplugin.GDPlugin;
import com.github.alex1304.ultimategdbot.gdplugin.database.GDLevelRequestSubmissions;
import com.github.alex1304.ultimategdbot.gdplugin.database.GDLevelRequestsSettings;
import com.github.alex1304.ultimategdbot.gdplugin.util.LevelRequestUtils;

import discord4j.core.object.entity.Channel.Type;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class LevelRequestSubmitCommand implements Command {
	
	private final GDPlugin plugin;
	
	public LevelRequestSubmitCommand(GDPlugin plugin) {
		this.plugin = Objects.requireNonNull(plugin);
	}

	@Override
	public Mono<Void> execute(Context ctx) {
		ArgUtils.requireMinimumArgCount(ctx, 2);
		final var guildId = ctx.getEvent().getGuildId().orElseThrow().asLong();
		final var user = ctx.getEvent().getMessage().getAuthor().orElseThrow();
		final var levelId = ArgUtils.getArgAsLong(ctx, 1);
		final var youtubeLink = extractYouTubeLink(ctx);
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
				.then(plugin.getGdClient()
						.getLevelById(levelId)
						.onErrorMap(MissingAccessException.class, e -> new CommandFailedException("Level not found."))
						.doOnNext(level::set))
				.then(Mono.fromCallable(() -> {
							var s = new GDLevelRequestSubmissions();
							s.setGuildId(guildId);
							s.setLevelId(levelId);
							s.setSubmissionTimestamp(Timestamp.from(Instant.now()));
							s.setSubmitterId(user.getId().asLong());
							s.setYoutubeLink(youtubeLink);
							s.setIsReviewed(false);
							return s;
						})
						.flatMap(s -> ctx.getBot().getDatabase().save(s).thenReturn(s).onErrorReturn(s)) // First save to know the submission ID
						.flatMap(s -> LevelRequestUtils.buildSubmissionMessage(ctx.getBot(), user, level.get(), lvlReqSettings.get(), s, List.of())
								.map(SubmissionMessage::toMessageCreateSpec)
								.flatMap(ctx::reply)
								.flatMap(message -> {
									s.setMessageId(message.getId().asLong());
									return ctx.getBot().getDatabase().save(s);
								})
								.onErrorResume(e -> ctx.getBot().getDatabase().delete(s).then(Mono.error(e)))))
				.then(ctx.getBot().getEmoji("success").flatMap(emoji -> ctx.reply(emoji + " Level request submitted!")))
				.then();
	}
	
	private String extractYouTubeLink(Context ctx) {
		if (ctx.getArgs().size() == 2) {
			return "";
		} else {
			var arg2 = ctx.getArgs().get(2);
			if (arg2.matches("https?://youtu\\.be/.*") || arg2.matches("https?://www\\.youtube\\.com/watch\\?.*")) {
				return arg2;
			} else {
				throw new CommandFailedException("Invalid YouTube link");
			}
		}
	}

	@Override
	public Set<String> getAliases() {
		return Set.of("submit");
	}

	@Override
	public String getDescription() {
		return "Submit a new level request.";
	}

	@Override
	public String getLongDescription() {
		return "This command can only be used in the configured submission channel, and only if level requests are opened.";
	}

	@Override
	public String getSyntax() {
		return "<level_ID> [<youtube_link>]";
	}
	
	@Override
	public EnumSet<Type> getChannelTypesAllowed() {
		return EnumSet.of(Type.GUILD_TEXT);
	}

	@Override
	public Plugin getPlugin() {
		return plugin;
	}
}
