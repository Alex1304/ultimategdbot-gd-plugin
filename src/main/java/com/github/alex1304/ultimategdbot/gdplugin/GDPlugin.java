package com.github.alex1304.ultimategdbot.gdplugin;

import java.io.IOException;
import java.time.Duration;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
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
import com.github.alex1304.jdash.entity.GDLevel;
import com.github.alex1304.jdash.entity.GDUser;
import com.github.alex1304.jdash.exception.BadResponseException;
import com.github.alex1304.jdash.exception.CorruptedResponseContentException;
import com.github.alex1304.jdash.exception.MissingAccessException;
import com.github.alex1304.jdash.exception.NoTimelyAvailableException;
import com.github.alex1304.jdash.graphics.SpriteFactory;
import com.github.alex1304.jdash.util.GDUserIconSet;
import com.github.alex1304.jdash.util.LevelSearchFilters;
import com.github.alex1304.jdash.util.Routes;
import com.github.alex1304.jdashevents.GDEventDispatcher;
import com.github.alex1304.jdashevents.GDEventScannerLoop;
import com.github.alex1304.jdashevents.event.GDEvent;
import com.github.alex1304.jdashevents.scanner.AwardedSectionScanner;
import com.github.alex1304.jdashevents.scanner.DailyLevelScanner;
import com.github.alex1304.jdashevents.scanner.GDEventScanner;
import com.github.alex1304.jdashevents.scanner.WeeklyDemonScanner;
import com.github.alex1304.ultimategdbot.api.Bot;
import com.github.alex1304.ultimategdbot.api.Plugin;
import com.github.alex1304.ultimategdbot.api.command.CommandErrorHandler;
import com.github.alex1304.ultimategdbot.api.command.CommandFailedException;
import com.github.alex1304.ultimategdbot.api.command.CommandProvider;
import com.github.alex1304.ultimategdbot.api.command.Context;
import com.github.alex1304.ultimategdbot.api.command.annotated.AnnotatedCommandProvider;
import com.github.alex1304.ultimategdbot.api.command.annotated.paramconverter.ParamConverter;
import com.github.alex1304.ultimategdbot.api.database.GuildSettingsEntry;
import com.github.alex1304.ultimategdbot.api.utils.BotUtils;
import com.github.alex1304.ultimategdbot.api.utils.DatabaseInputFunction;
import com.github.alex1304.ultimategdbot.api.utils.DatabaseOutputFunction;
import com.github.alex1304.ultimategdbot.api.utils.PropertyParser;
import com.github.alex1304.ultimategdbot.gdplugin.database.GDLevelRequestsSettings;
import com.github.alex1304.ultimategdbot.gdplugin.database.GDSubscribedGuilds;
import com.github.alex1304.ultimategdbot.gdplugin.gdevent.GDEventSubscriber;
import com.github.alex1304.ultimategdbot.gdplugin.gdevent.processor.AwardedLevelAddedEventProcessor;
import com.github.alex1304.ultimategdbot.gdplugin.gdevent.processor.AwardedLevelRemovedEventProcessor;
import com.github.alex1304.ultimategdbot.gdplugin.gdevent.processor.AwardedLevelUpdatedEventProcessor;
import com.github.alex1304.ultimategdbot.gdplugin.gdevent.processor.GDEventProcessor;
import com.github.alex1304.ultimategdbot.gdplugin.gdevent.processor.TimelyLevelChangedEventProcessor;
import com.github.alex1304.ultimategdbot.gdplugin.gdevent.processor.UserDemotedFromElderEventProcessor;
import com.github.alex1304.ultimategdbot.gdplugin.gdevent.processor.UserDemotedFromModEventProcessor;
import com.github.alex1304.ultimategdbot.gdplugin.gdevent.processor.UserPromotedToElderEventProcessor;
import com.github.alex1304.ultimategdbot.gdplugin.gdevent.processor.UserPromotedToModEventProcessor;
import com.github.alex1304.ultimategdbot.gdplugin.util.BroadcastPreloader;
import com.github.alex1304.ultimategdbot.gdplugin.util.GDUtils;
import com.github.alex1304.ultimategdbot.gdplugin.util.LevelRequestUtils;

import discord4j.core.object.entity.Message;
import discord4j.core.object.entity.TextChannel;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

public class GDPlugin implements Plugin {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(GDPlugin.class);
	
	private Bot bot;
	private volatile AuthenticatedGDClient gdClient;
	private volatile SpriteFactory spriteFactory;
	private ConcurrentHashMap<GDUserIconSet, String[]> iconsCache;
	private GDEventDispatcher gdEventDispatcher;
	private GDEventScannerLoop scannerLoop;
	private ConcurrentHashMap<Long, List<Message>> broadcastedLevels;
	private BroadcastPreloader preloader;
	private GDEventSubscriber subscriber;
	private boolean preloadChannelsOnStartup;
	private boolean autostartScannerLoop;
	private Scheduler gdEventScheduler;
	private Set<Long> cachedSubmissionChannelIds;
	private int maxConnections;
	private volatile GDServiceMediator gdServiceMediator;
	
	private final Map<String, GuildSettingsEntry<?, ?>> configEntries = new HashMap<String, GuildSettingsEntry<?, ?>>();
	private final AnnotatedCommandProvider cmdProvider = new AnnotatedCommandProvider();

	@Override
	public Mono<Void> setup(Bot bot, PropertyParser parser) {
		var username = parser.parseAsString("gdplugin.username");
		var password = parser.parseAsString("gdplugin.password");
		var host = parser.parseAsStringOrDefault("gdplugin.host", Routes.BASE_URL);
		var cacheTtl = parser.parseOrDefault("gdplugin.cache_ttl", v -> Duration.ofMillis(Long.parseLong(v)), GDClientBuilder.DEFAULT_CACHE_TTL);
		var requestTimeout = parser.parseOrDefault("gdplugin.request_timeout", v -> Duration.ofMillis(Long.parseLong(v)), GDClientBuilder.DEFAULT_REQUEST_TIMEOUT);
		var scannerLoopInterval = Duration.ofSeconds(parser.parseAsIntOrDefault("gdplugin.scanner_loop_interval", 10));
		this.bot = bot;
		this.preloadChannelsOnStartup = parser.parseOrDefault("gdplugin.preload_channels_on_startup", Boolean::parseBoolean, true);
		this.autostartScannerLoop = parser.parseOrDefault("gdplugin.autostart_scanner_loop", Boolean::parseBoolean, true);
		this.gdEventScheduler = Schedulers.elastic();
		this.maxConnections = parser.parseAsIntOrDefault("gdplugin.max_connections", 100);
		this.iconsCache = new ConcurrentHashMap<>();
		this.gdEventDispatcher = new GDEventDispatcher();
		this.scannerLoop = new GDEventScannerLoop(gdClient, gdEventDispatcher, initScanners(), scannerLoopInterval);
		this.broadcastedLevels = new ConcurrentHashMap<>();
		this.preloader = new BroadcastPreloader(bot.getMainDiscordClient());
		this.cachedSubmissionChannelIds = Collections.synchronizedSet(new HashSet<>());
		return Mono.when(
				GDClientBuilder.create()
						.withHost(host)
						.withCacheTtl(cacheTtl)
						.withRequestTimeout(requestTimeout)
						.buildAuthenticated(new Credentials(username, password))
						.onErrorMap(e -> new RuntimeException("Failed to login with the given Geometry Dash credentials", e))
						.doOnNext(gdClient -> this.gdClient = gdClient)
						.then(Mono.fromRunnable(this::initParamConverters)),
				Mono.fromCallable(SpriteFactory::create)
						.onErrorMap(e -> new RuntimeException("An error occured when loading the GD icons sprite factory", e))
						.doOnNext(spriteFactory -> this.spriteFactory = spriteFactory),
				Mono.fromRunnable(this::initGDEventSubscribers),
				Mono.fromRunnable(this::initConfigEntries),
				Mono.fromRunnable(this::initErrorHandler))
				.then(Mono.fromRunnable(() -> this.gdServiceMediator = new GDServiceMediator(gdClient, spriteFactory,
						iconsCache, gdEventDispatcher, scannerLoop, broadcastedLevels, preloader, subscriber,
						gdEventScheduler, cachedSubmissionChannelIds, maxConnections)));
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
									+ BotUtils.formatDuration(Duration.ofMillis(result.getT1())) + "**!")))
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
	
	private void initConfigEntries() {
		configEntries.put("channel_awarded_levels", new GuildSettingsEntry<>(
				GDSubscribedGuilds.class,
				GDSubscribedGuilds::getChannelAwardedLevelsId,
				GDSubscribedGuilds::setChannelAwardedLevelsId,
				DatabaseInputFunction.toChannelId(bot, TextChannel.class),
				DatabaseOutputFunction.fromChannelId(bot)
		));
		configEntries.put("channel_timely_levels", new GuildSettingsEntry<>(
				GDSubscribedGuilds.class,
				GDSubscribedGuilds::getChannelTimelyLevelsId,
				GDSubscribedGuilds::setChannelTimelyLevelsId,
				DatabaseInputFunction.toChannelId(bot, TextChannel.class),
				DatabaseOutputFunction.fromChannelId(bot)
		));
		configEntries.put("channel_gd_moderators", new GuildSettingsEntry<>(
				GDSubscribedGuilds.class,
				GDSubscribedGuilds::getChannelGdModeratorsId,
				GDSubscribedGuilds::setChannelGdModeratorsId,
				DatabaseInputFunction.toChannelId(bot, TextChannel.class),
				DatabaseOutputFunction.fromChannelId(bot)
		));
		configEntries.put("channel_changelog", new GuildSettingsEntry<>(
				GDSubscribedGuilds.class,
				GDSubscribedGuilds::getChannelChangelogId,
				GDSubscribedGuilds::setChannelChangelogId,
				DatabaseInputFunction.toChannelId(bot, TextChannel.class),
				DatabaseOutputFunction.fromChannelId(bot)
		));
		configEntries.put("role_awarded_levels", new GuildSettingsEntry<>(
				GDSubscribedGuilds.class,
				GDSubscribedGuilds::getRoleAwardedLevelsId,
				GDSubscribedGuilds::setRoleAwardedLevelsId,
				DatabaseInputFunction.toRoleId(bot),
				DatabaseOutputFunction.fromRoleId(bot)
		));
		configEntries.put("role_timely_levels", new GuildSettingsEntry<>(
				GDSubscribedGuilds.class,
				GDSubscribedGuilds::getRoleTimelyLevelsId,
				GDSubscribedGuilds::setRoleTimelyLevelsId,
				DatabaseInputFunction.toRoleId(bot),
				DatabaseOutputFunction.fromRoleId(bot)
		));
		configEntries.put("role_gd_moderators", new GuildSettingsEntry<>(
				GDSubscribedGuilds.class,
				GDSubscribedGuilds::getRoleGdModeratorsId,
				GDSubscribedGuilds::setRoleGdModeratorsId,
				DatabaseInputFunction.toRoleId(bot),
				DatabaseOutputFunction.fromRoleId(bot)
		));
		configEntries.put("lvlreq_submission_queue_channel", new GuildSettingsEntry<>(
				GDLevelRequestsSettings.class,
				GDLevelRequestsSettings::getSubmissionQueueChannelId,
				GDLevelRequestsSettings::setSubmissionQueueChannelId,
				(v, guildId) -> DatabaseInputFunction.toChannelId(bot, TextChannel.class)
						.apply(v, guildId)
						.doOnNext(cachedSubmissionChannelIds::add)
						.flatMap(channelId -> bot.getDatabase().findByID(GDLevelRequestsSettings.class, guildId)
								.map(GDLevelRequestsSettings::getSubmissionQueueChannelId)
								.doOnNext(cachedSubmissionChannelIds::remove)
								.thenReturn(channelId)),
				DatabaseOutputFunction.fromChannelId(bot)
		));
		configEntries.put("lvlreq_reviewed_levels_channel", new GuildSettingsEntry<>(
				GDLevelRequestsSettings.class,
				GDLevelRequestsSettings::getReviewedLevelsChannelId,
				GDLevelRequestsSettings::setReviewedLevelsChannelId,
				DatabaseInputFunction.toChannelId(bot, TextChannel.class),
				DatabaseOutputFunction.fromChannelId(bot)
		));
		configEntries.put("lvlreq_reviewer_role", new GuildSettingsEntry<>(
				GDLevelRequestsSettings.class,
				GDLevelRequestsSettings::getReviewerRoleId,
				GDLevelRequestsSettings::setReviewerRoleId,
				DatabaseInputFunction.toRoleId(bot),
				DatabaseOutputFunction.fromRoleId(bot)
		));
		configEntries.put("lvlreq_nb_reviews_required", new GuildSettingsEntry<>(
				GDLevelRequestsSettings.class,
				GDLevelRequestsSettings::getMaxReviewsRequired,
				GDLevelRequestsSettings::setMaxReviewsRequired,
				DatabaseInputFunction.to(Integer::parseInt)
						.withValueCheck(i -> i > 0 && i <= 5, "Must be between 1 and 5"),
				DatabaseOutputFunction.from(i -> i == 0 ? "Not configured" : "" + i)
		));
		configEntries.put("lvlreq_max_submissions_allowed", new GuildSettingsEntry<>(
				GDLevelRequestsSettings.class,
				GDLevelRequestsSettings::getMaxQueuedSubmissionsPerPerson,
				GDLevelRequestsSettings::setMaxQueuedSubmissionsPerPerson,
				DatabaseInputFunction.to(Integer::parseInt)
						.withValueCheck(i -> i > 0 && i <= 20, "Must be between 1 and 20"),
				DatabaseOutputFunction.from(i -> i == 0 ? "Not configured" : "" + i)
		));
	}
	
	private void initErrorHandler() {
		var cmdErrorHandler = new CommandErrorHandler();
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
		cmdProvider.setErrorHandler(cmdErrorHandler);
	}
	
	private void initParamConverters() {
		cmdProvider.addParamConverter(new ParamConverter<GDUser>() {
			@Override
			public Mono<GDUser> convert(Context ctx, String input) {
				return GDUtils.stringToUser(bot, gdClient, input);
			}
			@Override
			public Class<GDUser> type() {
				return GDUser.class;
			}
		});
		cmdProvider.addParamConverter(new ParamConverter<GDLevel>() {
			@Override
			public Mono<GDLevel> convert(Context ctx, String input) {
				return gdClient.searchLevels(input, LevelSearchFilters.create(), 0)
						.flatMapMany(Flux::fromIterable)
						.next();
			}
			@Override
			public Class<GDLevel> type() {
				return GDLevel.class;
			}
		});
	}

	private Collection<? extends GDEventScanner> initScanners() {
		return Set.of(new AwardedSectionScanner(), new DailyLevelScanner(), new WeeklyDemonScanner());
	}

	private void initGDEventSubscribers() {
		Set<GDEventProcessor> processors = Set.of(
				new AwardedLevelAddedEventProcessor(gdServiceMediator, bot),
				new AwardedLevelRemovedEventProcessor(gdServiceMediator, bot),
				new AwardedLevelUpdatedEventProcessor(gdServiceMediator, bot),
				new TimelyLevelChangedEventProcessor(gdServiceMediator, bot),
				new UserPromotedToModEventProcessor(gdServiceMediator, bot),
				new UserPromotedToElderEventProcessor(gdServiceMediator, bot),
				new UserDemotedFromModEventProcessor(gdServiceMediator, bot),
				new UserDemotedFromElderEventProcessor(gdServiceMediator, bot));
		this.subscriber = new GDEventSubscriber(Flux.fromIterable(processors));
		gdEventDispatcher.on(GDEvent.class)
			.onBackpressureBuffer()
			.subscribe(subscriber);
	}
	
	@Override
	public CommandProvider getCommandProvider() {
		return cmdProvider;
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
}
