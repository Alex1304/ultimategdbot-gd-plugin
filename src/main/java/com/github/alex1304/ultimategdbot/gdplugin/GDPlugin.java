package com.github.alex1304.ultimategdbot.gdplugin;

import java.io.IOException;
import java.time.Duration;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.alex1304.jdash.client.AuthenticatedGDClient;
import com.github.alex1304.jdash.client.GDClientBuilder;
import com.github.alex1304.jdash.client.GDClientBuilder.Credentials;
import com.github.alex1304.jdash.exception.BadResponseException;
import com.github.alex1304.jdash.exception.CorruptedResponseContentException;
import com.github.alex1304.jdash.exception.GDLoginFailedException;
import com.github.alex1304.jdash.exception.MissingAccessException;
import com.github.alex1304.jdash.exception.NoTimelyAvailableException;
import com.github.alex1304.jdash.exception.SpriteLoadException;
import com.github.alex1304.jdash.graphics.SpriteFactory;
import com.github.alex1304.jdash.util.GDUserIconSet;
import com.github.alex1304.jdash.util.Routes;
import com.github.alex1304.jdashevents.GDEventDispatcher;
import com.github.alex1304.jdashevents.GDEventScannerLoop;
import com.github.alex1304.jdashevents.event.GDEvent;
import com.github.alex1304.jdashevents.scanner.AwardedSectionScanner;
import com.github.alex1304.jdashevents.scanner.DailyLevelScanner;
import com.github.alex1304.jdashevents.scanner.GDEventScanner;
import com.github.alex1304.jdashevents.scanner.WeeklyDemonScanner;
import com.github.alex1304.ultimategdbot.api.Bot;
import com.github.alex1304.ultimategdbot.api.Command;
import com.github.alex1304.ultimategdbot.api.CommandErrorHandler;
import com.github.alex1304.ultimategdbot.api.CommandFailedException;
import com.github.alex1304.ultimategdbot.api.Plugin;
import com.github.alex1304.ultimategdbot.api.database.GuildSettingsEntry;
import com.github.alex1304.ultimategdbot.api.utils.BotUtils;
import com.github.alex1304.ultimategdbot.api.utils.GuildSettingsValueConverter;
import com.github.alex1304.ultimategdbot.api.utils.PropertyParser;
import com.github.alex1304.ultimategdbot.gdplugin.account.AccountCommand;
import com.github.alex1304.ultimategdbot.gdplugin.admin.ChangelogCommand;
import com.github.alex1304.ultimategdbot.gdplugin.admin.ClearCacheCommand;
import com.github.alex1304.ultimategdbot.gdplugin.database.GDLevelRequestsSettings;
import com.github.alex1304.ultimategdbot.gdplugin.database.GDSubscribedGuilds;
import com.github.alex1304.ultimategdbot.gdplugin.gdevent.GDEventSubscriber;
import com.github.alex1304.ultimategdbot.gdplugin.gdevent.GDEventsCommand;
import com.github.alex1304.ultimategdbot.gdplugin.gdevent.processor.AwardedLevelAddedEventProcessor;
import com.github.alex1304.ultimategdbot.gdplugin.gdevent.processor.AwardedLevelRemovedEventProcessor;
import com.github.alex1304.ultimategdbot.gdplugin.gdevent.processor.AwardedLevelUpdatedEventProcessor;
import com.github.alex1304.ultimategdbot.gdplugin.gdevent.processor.GDEventProcessor;
import com.github.alex1304.ultimategdbot.gdplugin.gdevent.processor.TimelyLevelChangedEventProcessor;
import com.github.alex1304.ultimategdbot.gdplugin.gdevent.processor.UserDemotedFromElderEventProcessor;
import com.github.alex1304.ultimategdbot.gdplugin.gdevent.processor.UserDemotedFromModEventProcessor;
import com.github.alex1304.ultimategdbot.gdplugin.gdevent.processor.UserPromotedToElderEventProcessor;
import com.github.alex1304.ultimategdbot.gdplugin.gdevent.processor.UserPromotedToModEventProcessor;
import com.github.alex1304.ultimategdbot.gdplugin.leaderboard.LeaderboardCommand;
import com.github.alex1304.ultimategdbot.gdplugin.level.FeaturedInfoCommand;
import com.github.alex1304.ultimategdbot.gdplugin.level.LevelCommand;
import com.github.alex1304.ultimategdbot.gdplugin.level.TimelyCommand;
import com.github.alex1304.ultimategdbot.gdplugin.levelrequest.LevelRequestCommand;
import com.github.alex1304.ultimategdbot.gdplugin.user.CheckModCommand;
import com.github.alex1304.ultimategdbot.gdplugin.user.ModListCommand;
import com.github.alex1304.ultimategdbot.gdplugin.user.ProfileCommand;
import com.github.alex1304.ultimategdbot.gdplugin.util.BroadcastPreloader;
import com.github.alex1304.ultimategdbot.gdplugin.util.GDUtils;
import com.github.alex1304.ultimategdbot.gdplugin.util.LevelRequestUtils;

import discord4j.core.object.entity.Message;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

public class GDPlugin implements Plugin {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(GDPlugin.class);
	
	private Bot bot;
	private AuthenticatedGDClient gdClient;
	private SpriteFactory spriteFactory;
	private Map<GDUserIconSet, String[]> iconsCache;
	private GDEventDispatcher gdEventDispatcher;
	private GDEventScannerLoop scannerLoop;
	private Map<Long, List<Message>> broadcastedLevels;
	private BroadcastPreloader preloader;
	private int eventFluxBufferSize;
	private GDEventSubscriber subscriber;
	private boolean preloadChannelsOnStartup;
	private boolean autostartScannerLoop;
	private Scheduler gdEventScheduler;
	private Set<Long> cachedSubmissionChannelIds;
	
	private final Map<String, GuildSettingsEntry<?, ?>> configEntries = new HashMap<String, GuildSettingsEntry<?, ?>>();
	private final CommandErrorHandler cmdErrorHandler = new CommandErrorHandler();
	private final Set<Command> providedCommands = Set.of(new ProfileCommand(this), new LevelCommand(this, true), new LevelCommand(this, false),
			new TimelyCommand(this, true), new TimelyCommand(this, false), new AccountCommand(this),
			new LeaderboardCommand(this), new GDEventsCommand(this), new CheckModCommand(this),
			new ModListCommand(this), new FeaturedInfoCommand(this), new ChangelogCommand(this),
			new ClearCacheCommand(this), new LevelRequestCommand(this));

	@Override
	public void setup(Bot bot, PropertyParser parser) {
		this.bot = bot;
		var username = parser.parseAsString("gdplugin.username");
		var password = parser.parseAsString("gdplugin.password");
		var host = parser.parseAsStringOrDefault("gdplugin.host", Routes.BASE_URL);
		var cacheTtl = parser.parseOrDefault("gdplugin.cache_ttl", v -> Duration.ofMillis(Long.parseLong(v)), GDClientBuilder.DEFAULT_CACHE_TTL);
		var maxConnections = parser.parseAsIntOrDefault("gdplugin.max_connections", GDClientBuilder.DEFAULT_MAX_CONNECTIONS);
		var requestTimeout = parser.parseOrDefault("gdplugin.request_timeout", v -> Duration.ofMillis(Long.parseLong(v)), GDClientBuilder.DEFAULT_REQUEST_TIMEOUT);
		var scannerLoopInterval = Duration.ofSeconds(parser.parseAsIntOrDefault("gdplugin.scanner_loop_interval", 10));
		this.eventFluxBufferSize = parser.parseAsIntOrDefault("gdplugin.event_flux_buffer_size", 20);
		this.preloadChannelsOnStartup = parser.parseOrDefault("gdplugin.preload_channels_on_startup", Boolean::parseBoolean, true);
		this.autostartScannerLoop = parser.parseOrDefault("gdplugin.autostart_scanner_loop", Boolean::parseBoolean, true);
		this.gdEventScheduler = Schedulers.parallel();
		try {
			this.gdClient = GDClientBuilder.create()
					.withHost(host)
					.withCacheTtl(cacheTtl)
					.withMaxConnections(maxConnections)
					.withRequestTimeout(requestTimeout)
					.buildAuthenticated(new Credentials(username, password))
					.block();
		} catch (GDLoginFailedException e) {
			throw new RuntimeException("Failed to login with the given Geometry Dash credentials", e);
		}
		try {
			this.spriteFactory = SpriteFactory.create();
		} catch (SpriteLoadException e) {
			throw new RuntimeException("An error occured when loading the GD icons sprite factory", e);
		}
		this.iconsCache = new ConcurrentHashMap<>();
		this.gdEventDispatcher = new GDEventDispatcher();
		this.scannerLoop = new GDEventScannerLoop(gdClient, gdEventDispatcher, initScanners(), scannerLoopInterval);
		this.broadcastedLevels = new LinkedHashMap<>();
		this.preloader = new BroadcastPreloader(bot.getMainDiscordClient());
		this.cachedSubmissionChannelIds = Collections.synchronizedSet(new HashSet<>());
		initGDEventSubscribers();
		
		// Config entries
		var valueConverter = new GuildSettingsValueConverter(bot);
		configEntries.put("channel_awarded_levels", new GuildSettingsEntry<>(
				GDSubscribedGuilds.class,
				GDSubscribedGuilds::getChannelAwardedLevelsId,
				GDSubscribedGuilds::setChannelAwardedLevelsId,
				valueConverter::toTextChannelId,
				valueConverter::fromChannelId
		));
		configEntries.put("channel_timely_levels", new GuildSettingsEntry<>(
				GDSubscribedGuilds.class,
				GDSubscribedGuilds::getChannelTimelyLevelsId,
				GDSubscribedGuilds::setChannelTimelyLevelsId,
				valueConverter::toTextChannelId,
				valueConverter::fromChannelId
		));
		configEntries.put("channel_gd_moderators", new GuildSettingsEntry<>(
				GDSubscribedGuilds.class,
				GDSubscribedGuilds::getChannelGdModeratorsId,
				GDSubscribedGuilds::setChannelGdModeratorsId,
				valueConverter::toTextChannelId,
				valueConverter::fromChannelId
		));
		configEntries.put("channel_changelog", new GuildSettingsEntry<>(
				GDSubscribedGuilds.class,
				GDSubscribedGuilds::getChannelChangelogId,
				GDSubscribedGuilds::setChannelChangelogId,
				valueConverter::toTextChannelId,
				valueConverter::fromChannelId
		));
		configEntries.put("role_awarded_levels", new GuildSettingsEntry<>(
				GDSubscribedGuilds.class,
				GDSubscribedGuilds::getRoleAwardedLevelsId,
				GDSubscribedGuilds::setRoleAwardedLevelsId,
				valueConverter::toRoleId,
				valueConverter::fromRoleId
		));
		configEntries.put("role_timely_levels", new GuildSettingsEntry<>(
				GDSubscribedGuilds.class,
				GDSubscribedGuilds::getRoleTimelyLevelsId,
				GDSubscribedGuilds::setRoleTimelyLevelsId,
				valueConverter::toRoleId,
				valueConverter::fromRoleId
		));
		configEntries.put("role_gd_moderators", new GuildSettingsEntry<>(
				GDSubscribedGuilds.class,
				GDSubscribedGuilds::getRoleGdModeratorsId,
				GDSubscribedGuilds::setRoleGdModeratorsId,
				valueConverter::toRoleId,
				valueConverter::fromRoleId
		));
		configEntries.put("lvlreq_submission_queue_channel", new GuildSettingsEntry<>(
				GDLevelRequestsSettings.class,
				GDLevelRequestsSettings::getSubmissionQueueChannelId,
				GDLevelRequestsSettings::setSubmissionQueueChannelId,
				(v, guildId) -> valueConverter.toTextChannelId(v, guildId)
						.doOnNext(cachedSubmissionChannelIds::add)
						.flatMap(channelId -> bot.getDatabase().findByID(GDLevelRequestsSettings.class, guildId)
								.map(GDLevelRequestsSettings::getSubmissionQueueChannelId)
								.doOnNext(cachedSubmissionChannelIds::remove)
								.thenReturn(channelId)),
				valueConverter::fromChannelId
		));
		configEntries.put("lvlreq_reviewed_levels_channel", new GuildSettingsEntry<>(
				GDLevelRequestsSettings.class,
				GDLevelRequestsSettings::getReviewedLevelsChannelId,
				GDLevelRequestsSettings::setReviewedLevelsChannelId,
				valueConverter::toTextChannelId,
				valueConverter::fromChannelId
		));
		configEntries.put("lvlreq_reviewer_role", new GuildSettingsEntry<>(
				GDLevelRequestsSettings.class,
				GDLevelRequestsSettings::getReviewerRoleId,
				GDLevelRequestsSettings::setReviewerRoleId,
				valueConverter::toRoleId,
				valueConverter::fromRoleId
		));
		configEntries.put("lvlreq_nb_reviews_required", new GuildSettingsEntry<>(
				GDLevelRequestsSettings.class,
				GDLevelRequestsSettings::getMaxReviewsRequired,
				GDLevelRequestsSettings::setMaxReviewsRequired,
				(v, guildId) -> v.equalsIgnoreCase(GuildSettingsValueConverter.NONE_VALUE)
						? Mono.just(0)
						: valueConverter.toNumberWithCheck(v, guildId, Integer::parseInt, i -> i > 0 && i <= 5, "Must be between 1 and 5"),
				(i, guildId) -> Mono.just(i == 0 ? "Not configured" : "" + i)
		));
		configEntries.put("lvlreq_max_submissions_allowed", new GuildSettingsEntry<>(
				GDLevelRequestsSettings.class,
				GDLevelRequestsSettings::getMaxQueuedSubmissionsPerPerson,
				GDLevelRequestsSettings::setMaxQueuedSubmissionsPerPerson,
				(v, guildId) -> v.equalsIgnoreCase(GuildSettingsValueConverter.NONE_VALUE)
						? Mono.just(0)
						: valueConverter.toNumberWithCheck(v, guildId, Integer::parseInt, i -> i > 0 && i <= 20, "Must be between 1 and 20"),
				(i, guildId) -> Mono.just(i == 0 ? "Not configured" : "" + i)
		));
		
		// Error handler
		cmdErrorHandler.addHandler(CommandFailedException.class, (e, ctx) -> ctx.getBot().getEmoji("cross")
				.flatMap(cross -> ctx.reply(cross + " " + e.getMessage()))
				.then());
		cmdErrorHandler.addHandler(MissingAccessException.class, (e, ctx) -> ctx.getBot().getEmoji("cross")
				.flatMap(cross -> ctx.reply(cross + " Nothing found."))
				.then());
		cmdErrorHandler.addHandler(BadResponseException.class, (e, ctx) -> {
			var status = e.getResponse().status();
			return ctx.getBot().getEmoji("cross")
					.flatMap(cross -> ctx.reply(cross + " Geometry Dash server returned a `" + status.code() + " "
							+ status.reasonPhrase() + "` error. Try again later."))
					.then();
		});
		cmdErrorHandler.addHandler(CorruptedResponseContentException.class, (e, ctx) -> {
			var content = e.getResponseContent();
			if (content.length() > 500) {
				content = content.substring(0, 497) + "...";
			}
			return Flux.merge(ctx.getBot().getEmoji("cross").flatMap(cross -> ctx.reply(cross + " Geometry Dash server returned corrupted data."
					+ "Unable to complete your request.")), 
					ctx.getBot().log(":warning: Geometry Dash server returned corrupted data.\nContext dump: `"
							+ ctx + "`.\n"
							+ "Path: `" + e.getRequestPath() + "`\n"
							+ "Parameters: `" + e.getRequestParams() + "`\n"
							+ "Response: `" + content + "`\n"
							+ "Error observed when parsing response: `" + e.getCause().getClass().getCanonicalName()
									+ (e.getCause().getMessage() != null ? ": " + e.getCause().getMessage() : "") + "`"),
					Mono.fromRunnable(() -> LOGGER.warn("Geometry Dash server returned corrupted data", e))).then();
		});
		cmdErrorHandler.addHandler(TimeoutException.class, (e, ctx) -> ctx.getBot().getEmoji("cross")
				.flatMap(cross -> ctx.reply(cross + " Geometry Dash server took too long to respond. Try again later."))
				.then());
		cmdErrorHandler.addHandler(IOException.class, (e, ctx) -> ctx.getBot().getEmoji("cross")
				.flatMap(cross -> ctx.reply(cross + " Cannot connect to Geometry Dash servers due to network issues. Try again later."))
				.then());
		cmdErrorHandler.addHandler(NoTimelyAvailableException.class, (e, ctx) -> ctx.getBot().getEmoji("cross")
				.flatMap(cross -> ctx.reply(cross + " There is no Daily/Weekly available right now. Come back later!"))
				.then());
	}
	
	@Override
	public Mono<Void> onBotReady(Bot bot) {
		LevelRequestUtils.listenAndCleanSubmissionQueueChannels(bot, cachedSubmissionChannelIds);
		if (preloadChannelsOnStartup) {
			return Mono.zip(bot.getEmoji("info"), bot.getEmoji("success"))
					.flatMap(emojis -> bot.log(emojis.getT1() + " Preloading channels and roles configured for GD event notifications...")
							.flatMap(__ -> GDUtils.preloadBroadcastChannelsAndRoles(bot, preloader))
							.elapsed()
							.flatMap(result -> bot.log(emojis.getT2() + " Successfully preloaded **" + result.getT2().getT1()
									+ "** channels and **" + result.getT2().getT2() + "** roles in **"
									+ BotUtils.formatTimeMillis(Duration.ofMillis(result.getT1())) + "**!")))
							.doAfterTerminate(this::startScannerLoopIfAutostart)
							.then();
		}
		return Mono.fromRunnable(this::startScannerLoopIfAutostart);
	}
	
	private void startScannerLoopIfAutostart() {
		if (autostartScannerLoop) {
			scannerLoop.start();
		}
	}

	private Collection<? extends GDEventScanner> initScanners() {
		return Set.of(new AwardedSectionScanner(), new DailyLevelScanner(), new WeeklyDemonScanner());
	}

	private void initGDEventSubscribers() {
		Set<GDEventProcessor> processors = Set.of(
				new AwardedLevelAddedEventProcessor(this),
				new AwardedLevelRemovedEventProcessor(this),
				new AwardedLevelUpdatedEventProcessor(this),
				new TimelyLevelChangedEventProcessor(this),
				new UserPromotedToModEventProcessor(this),
				new UserPromotedToElderEventProcessor(this),
				new UserDemotedFromModEventProcessor(this),
				new UserDemotedFromElderEventProcessor(this));
		this.subscriber = new GDEventSubscriber(Flux.fromIterable(processors));
		gdEventDispatcher.on(GDEvent.class)
			.onBackpressureBuffer(eventFluxBufferSize, event -> bot.log(":warning: Due to backpressure, the following event has been rejected: "
						+ findLogText(processors, event) + "\n"
						+ "You will need to push it through manually via the `" + bot.getDefaultPrefix() + "gdevents dispatch` command.").subscribe())
			.subscribe(subscriber);
	}
	
	private String findLogText(Set<GDEventProcessor> processors, GDEvent event) {
		return processors.stream()
				.map(processor -> processor.logText(event))
				.filter(text -> !text.isEmpty())
				.findFirst().orElse("**[Unknown Event]**");
	}
	
	@Override
	public Set<Command> getProvidedCommands() {
		return providedCommands;
	}

	@Override
	public String getName() {
		return "Geometry Dash";
	}

	@Override
	public Set<String> getDatabaseMappingResources() {
		return Set.of("/GDLinkedUsers.hbm.xml", "/GDSubscribedGuilds.hbm.xml", "/GDModList.hbm.xml",
				"/GDLeaderboardBans.hbm.xml", "/GDAwardedLevels.hbm.xml", "/GDLevelRequestsSettings.hbm.xml",
				"/GDLevelRequestSubmissions.hbm.xml", "/GDLevelRequestReviews.hbm.xml", "/GDUserStats.hbm.xml");
	}

	@Override
	public Map<String, GuildSettingsEntry<?, ?>> getGuildConfigurationEntries() {
		return configEntries;
	}

	@Override
	public CommandErrorHandler getCommandErrorHandler() {
		return cmdErrorHandler;
	}
	
	public Bot getBot() {
		return bot;
	}

	public AuthenticatedGDClient getGdClient() {
		return gdClient;
	}

	public SpriteFactory getSpriteFactory() {
		return spriteFactory;
	}

	public Map<GDUserIconSet, String[]> getIconsCache() {
		return iconsCache;
	}

	public GDEventDispatcher getGdEventDispatcher() {
		return gdEventDispatcher;
	}

	public GDEventScannerLoop getScannerLoop() {
		return scannerLoop;
	}

	public Map<Long, List<Message>> getBroadcastedLevels() {
		return broadcastedLevels;
	}

	public BroadcastPreloader getPreloader() {
		return preloader;
	}

	public int getEventFluxBufferSize() {
		return eventFluxBufferSize;
	}

	public GDEventSubscriber getSubscriber() {
		return subscriber;
	}

	public boolean isPreloadChannelsOnStartup() {
		return preloadChannelsOnStartup;
	}

	public Scheduler getGdEventScheduler() {
		return gdEventScheduler;
	}
}
