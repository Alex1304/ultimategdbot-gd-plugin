package com.github.alex1304.ultimategdbot.gdplugin.leaderboard;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.alex1304.ultimategdbot.api.Command;
import com.github.alex1304.ultimategdbot.api.CommandFailedException;
import com.github.alex1304.ultimategdbot.api.Context;
import com.github.alex1304.ultimategdbot.api.PermissionLevel;
import com.github.alex1304.ultimategdbot.api.Plugin;
import com.github.alex1304.ultimategdbot.api.utils.BotUtils;
import com.github.alex1304.ultimategdbot.gdplugin.GDPlugin;
import com.github.alex1304.ultimategdbot.gdplugin.database.GDLinkedUsers;
import com.github.alex1304.ultimategdbot.gdplugin.database.GDUserStats;

import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class LeaderboardRefreshCommand implements Command {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(LeaderboardRefreshCommand.class);
	
	private final GDPlugin plugin;
	private final AtomicBoolean isLocked;

	public LeaderboardRefreshCommand(GDPlugin plugin, AtomicBoolean isLocked) {
		this.plugin = Objects.requireNonNull(plugin);
		this.isLocked = Objects.requireNonNull(isLocked);
	}

	@Override
	public Mono<Void> execute(Context ctx) {
		if (isLocked.get()) {
			return Mono.error(new CommandFailedException("Refresh is already in progress."));
		}
		isLocked.set(true);
		LOGGER.debug("Locked leaderboards");
		var cooldown = new AtomicReference<Duration>();
		var disposableProgress = new AtomicReference<Disposable>();
		var isSaving = new AtomicBoolean();
		var loaded = new AtomicLong();
		var total = new AtomicLong();
		var now = Timestamp.from(Instant.now());
		var progressRefreshRate = Duration.ofSeconds(2);
		var progress = ctx.getBot().getEmoji("info")
				.flatMap(info -> ctx.getBot().log(info + " Leaderboard refresh triggered by **" + ctx.getEvent()
						.getMessage()
						.getAuthor()
						.map(BotUtils::formatDiscordUsername)
						.orElse("Unknown User#0000") + "**"))
				.then(ctx.reply("Refreshing leaderboards..."))
				.flatMapMany(message -> Flux.interval(progressRefreshRate, progressRefreshRate)
						.map(tick -> isSaving.get() ? "Saving new player stats in database..." : "Refreshing leaderboards..."
								+ (total.get() == 0 ? "" : " (" + loaded.get() + "/" + total.get() + " users processed)"))
						.flatMap(text -> message.edit(spec -> spec.setContent(text)))
						.doFinally(signal -> message.delete()
								.then(ctx.getBot().getEmoji("success"))
								.flatMap(success -> ctx.getBot().log(success + " Leaderboard refresh finished with success!")
										.then(ctx.reply(success + " Leaderboards refreshed!")))
								.subscribe()));
		
		return ctx.getBot().getDatabase().query(GDUserStats.class, "from GDUserStats s order by s.lastRefreshed desc")
				.next()
				.map(GDUserStats::getLastRefreshed)
				.map(Timestamp::toInstant)
				.defaultIfEmpty(Instant.MIN)
				.map(lastRefreshed -> Duration.ofHours(6).minus(Duration.between(lastRefreshed, Instant.now())))
				.doOnNext(cooldown::set)
				.filter(Duration::isNegative)
				.switchIfEmpty(Mono.error(() -> new CommandFailedException("The leaderboard has already been refreshed less than 6 hours ago. "
						+ "Try again in " + BotUtils.formatTimeMillis(cooldown.get().withNanos(0)))))
				.thenMany(ctx.getBot().getDatabase().query(GDLinkedUsers.class, "from GDLinkedUsers where isLinkActivated = 1"))
				.distinct(GDLinkedUsers::getGdAccountId)
				.buffer()
				.doOnNext(buf -> total.set(buf.size()))
				.doOnNext(buf -> disposableProgress.set(progress.subscribe()))
				.flatMap(Flux::fromIterable)
				.flatMap(linkedUser -> plugin.getGdClient().getUserByAccountId(linkedUser.getGdAccountId())
						.onErrorResume(e -> Mono.fromRunnable(() -> LOGGER.warn("Failed to refresh user "
								+ linkedUser.getGdAccountId(), e))), plugin.getMaxConnections())
				.map(gdUser -> {
					loaded.incrementAndGet();
					var s = new GDUserStats();
					s.setAccountId(gdUser.getAccountId());
					s.setName(gdUser.getName());
					s.setLastRefreshed(now);
					s.setStars(gdUser.getStars());
					s.setDiamonds(gdUser.getDiamonds());
					s.setUserCoins(gdUser.getUserCoins());
					s.setSecretCoins(gdUser.getSecretCoins());
					s.setDemons(gdUser.getDemons());
					s.setCreatorPoints(gdUser.getCreatorPoints());
					return s;
				})
				.collectList()
				.doOnNext(stats -> isSaving.set(true))
				.flatMap(stats -> ctx.getBot().getDatabase().performEmptyTransaction(session -> stats.forEach(session::saveOrUpdate)))
				.doFinally(signal -> {
					isLocked.set(false);
					LOGGER.debug("Unlocked leaderboards");
					if (disposableProgress.get() != null) {
						disposableProgress.get().dispose();
					}
				})
				.then();
	}

	@Override
	public Set<String> getAliases() {
		return Set.of("refresh");
	}

	@Override
	public String getDescription() {
		return "Refreshes the leaderboard.";
	}

	@Override
	public String getLongDescription() {
		return "Leaderboard refresh is an heavy process that consists of loading profiles of all users that have linked their account "
				+ "to the bot. This command may be run once in 6 hours.";
	}

	@Override
	public String getSyntax() {
		return "";
	}

	@Override
	public PermissionLevel getPermissionLevel() {
		return PermissionLevel.BOT_ADMIN;
	}

	@Override
	public Plugin getPlugin() {
		return plugin;
	}
}
