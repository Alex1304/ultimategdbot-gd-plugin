package com.github.alex1304.ultimategdbot.gdplugin.levelrequest;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.EnumSet;
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
		final var userId = ctx.getEvent().getMessage().getAuthor().orElseThrow().getId().asLong();
		final var levelId = ArgUtils.getArgAsLong(ctx, 1);
		final var youtubeLink = ctx.getArgs().size() == 2 ? "" : ctx.getArgs().get(2);
		final var lvlReqSettings = new AtomicReference<GDLevelRequestsSettings>();
		final var level = new AtomicReference<GDLevel>();
		return LevelRequestUtils.getLevelRequestsSettings(ctx)
				.doOnNext(lvlReqSettings::set)
				.filter(__ -> ctx.getEvent().getMessage().getChannelId().asLong() == lvlReqSettings.get().getSubmissionQueueChannelId())
				.switchIfEmpty(Mono.error(new CommandFailedException("You can only use this command in <#"
						+ lvlReqSettings.get().getSubmissionQueueChannelId() + ">")))
				.filter(__ -> lvlReqSettings.get().getIsOpen())
				.switchIfEmpty(Mono.error(new CommandFailedException("Level requests are closed, no submissions are being accepted.")))
				.filterWhen(__ -> LevelRequestUtils.getSubmissionsForUser(ctx).count().map(n -> n < lvlReqSettings.get().getMaxQueuedSubmissionsPerPerson()))
				.switchIfEmpty(Mono.error(new CommandFailedException("You've reached the maximum number of submissions allowed in queue per person ("
						+ lvlReqSettings.get().getMaxQueuedSubmissionsPerPerson() + "). Wait for one of your queued requests to be "
						+ "reviewed before trying again.")))
				.then(plugin.getGdClient()
						.getLevelById(levelId)
						.onErrorMap(MissingAccessException.class, e -> new CommandFailedException("Level not found."))
						.doOnNext(level::set))
				.then(/* Build and send the submission message */)
				.then(Mono.fromCallable(() -> new GDLevelRequestSubmissions())
						.doOnNext(submission -> submission.setGuildId(guildId))
						.doOnNext(submission -> submission.setLevelId(levelId))
						.doOnNext(submission -> submission.setSubmissionTimestamp(Timestamp.from(Instant.now())))
						.doOnNext(submission -> submission.setSubmitterId(userId))
						.doOnNext(submission -> submission.setYoutubeLink(youtubeLink)) // TODO: check if YT link is valid
						.flatMap(ctx.getBot().getDatabase()::save))
				.then();
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
		return "<level_ID>";
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
