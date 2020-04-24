package com.github.alex1304.ultimategdbot.gdplugin;

import static java.util.Collections.synchronizedSet;
import static reactor.function.TupleUtils.function;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeoutException;

import org.jdbi.v3.core.mapper.immutables.JdbiImmutables;

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
import com.github.alex1304.ultimategdbot.api.PluginBootstrap;
import com.github.alex1304.ultimategdbot.api.command.CommandErrorHandler;
import com.github.alex1304.ultimategdbot.api.command.CommandFailedException;
import com.github.alex1304.ultimategdbot.api.command.CommandProvider;
import com.github.alex1304.ultimategdbot.api.command.Context;
import com.github.alex1304.ultimategdbot.api.command.PermissionChecker;
import com.github.alex1304.ultimategdbot.api.command.PermissionLevel;
import com.github.alex1304.ultimategdbot.api.command.annotated.AnnotatedCommandProvider;
import com.github.alex1304.ultimategdbot.api.command.annotated.paramconverter.ParamConverter;
import com.github.alex1304.ultimategdbot.api.util.PropertyReader;
import com.github.alex1304.ultimategdbot.gdplugin.command.AccountCommand;
import com.github.alex1304.ultimategdbot.gdplugin.command.CheckModCommand;
import com.github.alex1304.ultimategdbot.gdplugin.command.ClearGdCacheCommand;
import com.github.alex1304.ultimategdbot.gdplugin.command.DailyCommand;
import com.github.alex1304.ultimategdbot.gdplugin.command.FeaturedInfoCommand;
import com.github.alex1304.ultimategdbot.gdplugin.command.GDEventsCommand;
import com.github.alex1304.ultimategdbot.gdplugin.command.LeaderboardCommand;
import com.github.alex1304.ultimategdbot.gdplugin.command.LevelCommand;
import com.github.alex1304.ultimategdbot.gdplugin.command.LevelRequestCommand;
import com.github.alex1304.ultimategdbot.gdplugin.command.LevelsbyCommand;
import com.github.alex1304.ultimategdbot.gdplugin.command.ModListCommand;
import com.github.alex1304.ultimategdbot.gdplugin.command.ProfileCommand;
import com.github.alex1304.ultimategdbot.gdplugin.command.WeeklyCommand;
import com.github.alex1304.ultimategdbot.gdplugin.database.GDAwardedLevelData;
import com.github.alex1304.ultimategdbot.gdplugin.database.GDEventConfigDao;
import com.github.alex1304.ultimategdbot.gdplugin.database.GDEventConfigData;
import com.github.alex1304.ultimategdbot.gdplugin.database.GDLeaderboardBanData;
import com.github.alex1304.ultimategdbot.gdplugin.database.GDLeaderboardData;
import com.github.alex1304.ultimategdbot.gdplugin.database.GDLevelRequestConfigDao;
import com.github.alex1304.ultimategdbot.gdplugin.database.GDLevelRequestConfigData;
import com.github.alex1304.ultimategdbot.gdplugin.database.GDLevelRequestReviewData;
import com.github.alex1304.ultimategdbot.gdplugin.database.GDLevelRequestSubmissionData;
import com.github.alex1304.ultimategdbot.gdplugin.database.GDLinkedUserData;
import com.github.alex1304.ultimategdbot.gdplugin.database.GDModData;
import com.github.alex1304.ultimategdbot.gdplugin.gdevent.GDEventSubscriber;
import com.github.alex1304.ultimategdbot.gdplugin.util.GDLevelRequests;
import com.github.alex1304.ultimategdbot.gdplugin.util.GDUsers;

import discord4j.rest.util.Snowflake;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.Logger;
import reactor.util.Loggers;

public class GDPluginBootstrap implements PluginBootstrap {

	private static final Logger LOGGER = Loggers.getLogger(GDPluginBootstrap.class);

	@Override
	public Mono<Plugin> setup(Bot bot, PropertyReader pluginProperties) {
		// Properties
		var username = pluginProperties.read("gdplugin.username");
		var password = pluginProperties.read("gdplugin.password");
		var iconChannelId = pluginProperties.readAs("gdplugin.icon_channel_id", Snowflake::of);
		var host = pluginProperties.readOptional("gdplugin.host").orElse(Routes.BASE_URL);
		var cacheTtl = pluginProperties.readOptional("gdplugin.cache_ttl")
				.map(v -> Duration.ofMillis(Long.parseLong(v)))
				.orElse(GDClientBuilder.DEFAULT_CACHE_TTL);
		var requestTimeout = pluginProperties.readOptional("gdplugin.request_timeout")
				.map(v -> Duration.ofMillis(Long.parseLong(v)))
				.orElse(GDClientBuilder.DEFAULT_REQUEST_TIMEOUT);
		var scannerLoopInterval = Duration.ofSeconds(pluginProperties.readOptional("gdplugin.scanner_loop_interval").map(Integer::parseInt).orElse(10));
		var autostartScannerLoop = pluginProperties.readOptional("gdplugin.autostart_scanner_loop")
				.map(Boolean::parseBoolean)
				.orElse(true);
		var maxConnections = pluginProperties.readOptional("gdplugin.max_connections").map(Integer::parseInt).orElse(100);
		var iconsCacheMaxSize = pluginProperties.readOptional("gdplugin.icons_cache_max_size").map(Integer::parseInt).orElse(2048);
		// Database config
		bot.database().configureJdbi(jdbi -> {
			jdbi.getConfig(JdbiImmutables.class).registerImmutable(
					GDAwardedLevelData.class,
					GDEventConfigData.class,
					GDLeaderboardBanData.class,
					GDLeaderboardData.class,
					GDLevelRequestConfigData.class,
					GDLevelRequestReviewData.class,
					GDLevelRequestSubmissionData.class,
					GDLinkedUserData.class,
					GDModData.class);
		});
		bot.registerGuildConfigExtension(GDEventConfigDao.class);
		bot.registerGuildConfigExtension(GDLevelRequestConfigDao.class);
		// Resources
		var buildingGDClient = GDClientBuilder.create()
				.withHost(host)
				.withCacheTtl(cacheTtl)
				.withRequestTimeout(requestTimeout)
				.buildAuthenticated(new Credentials(username, password))
				.onErrorMap(e -> new RuntimeException("Failed to login with the given Geometry Dash credentials", e));
		var creatingSpriteFactory = Mono.fromCallable(SpriteFactory::create)
				.onErrorMap(e -> new RuntimeException("An error occured when loading the GD icons sprite factory", e));
		
		return Mono.zip(buildingGDClient, creatingSpriteFactory)
				.map(function((gdClient, spriteFactory) -> {
					var gdEventDispatcher = new GDEventDispatcher();
					var scannerLoop = new GDEventScannerLoop(gdClient, gdEventDispatcher, initScanners(), scannerLoopInterval);
					var cachedSubmissionChannelIds = synchronizedSet(new HashSet<Long>());
					var gdService = new GDService(gdClient, spriteFactory,
							iconsCacheMaxSize, gdEventDispatcher, scannerLoop,
							cachedSubmissionChannelIds, maxConnections, iconChannelId);
					initGDEventSubscriber(gdEventDispatcher, gdService, bot);
					var cmdProvider = initCommandProvider(gdService, bot, gdClient);
					return Plugin.builder("Geometry Dash")
							.setCommandProvider(cmdProvider)
							.onReady(() -> {
								GDLevelRequests.listenAndCleanSubmissionQueueChannels(bot, cachedSubmissionChannelIds);
								if (autostartScannerLoop) {
									scannerLoop.start();
								}
								return Mono.empty();
							})
							.build();
				}));
	}

	private static Set<GDEventScanner> initScanners() {
		return Set.of(new AwardedSectionScanner(), new DailyLevelScanner(), new WeeklyDemonScanner());
	}
	
	private static CommandProvider initCommandProvider(GDService gdService, Bot bot, AuthenticatedGDClient gdClient) {
		var cmdProvider = new AnnotatedCommandProvider();
		// Commands
		cmdProvider.addAnnotated(new AccountCommand(gdService));
		cmdProvider.addAnnotated(new CheckModCommand(gdService));
		cmdProvider.addAnnotated(new ClearGdCacheCommand(gdService));
		cmdProvider.addAnnotated(new DailyCommand(gdService));
		cmdProvider.addAnnotated(new FeaturedInfoCommand(gdService));
		cmdProvider.addAnnotated(new GDEventsCommand(gdService));
		cmdProvider.addAnnotated(new LeaderboardCommand(gdService));
		cmdProvider.addAnnotated(new LevelCommand(gdService));
		cmdProvider.addAnnotated(new LevelRequestCommand(gdService));
		cmdProvider.addAnnotated(new LevelsbyCommand(gdService));
		cmdProvider.addAnnotated(new ModListCommand());
		cmdProvider.addAnnotated(new ProfileCommand(gdService));
		cmdProvider.addAnnotated(new WeeklyCommand(gdService));
		// Param converters
		cmdProvider.addParamConverter(new ParamConverter<GDUser>() {
			@Override
			public Mono<GDUser> convert(Context ctx, String input) {
				return GDUsers.stringToUser(bot, gdClient, input);
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
		// Permission checker
		var permChecker = new PermissionChecker();
		permChecker.register("LEVEL_REQUEST_REVIEWER", ctx -> ctx.bot().commandKernel()
				.getPermissionChecker()
				.isGranted(PermissionLevel.GUILD_ADMIN, ctx)
				.flatMap(isGuildAdmin -> isGuildAdmin ? Mono.just(true) : ctx.event().getMessage()
						.getAuthorAsMember()
						.flatMap(member -> GDLevelRequests.retrieveConfig(ctx)
								.map(GDLevelRequestConfigData::roleReviewerId)
								.flatMap(Mono::justOrEmpty)
								.map(member.getRoleIds()::contains))));
		cmdProvider.setPermissionChecker(permChecker);
		// Error handlers
		var cmdErrorHandler = new CommandErrorHandler();
		cmdErrorHandler.addHandler(CommandFailedException.class, (e, ctx) -> ctx.bot().emoji("cross")
				.flatMap(cross -> ctx.reply(cross + " " + e.getMessage()))
				.then());
		cmdErrorHandler.addHandler(MissingAccessException.class, (e, ctx) -> ctx.bot().emoji("cross")
				.flatMap(cross -> ctx.reply(cross + " Nothing found."))
				.then());
		cmdErrorHandler.addHandler(BadResponseException.class, (e, ctx) -> {
			var status = e.getResponse().status();
			return ctx.bot().emoji("cross")
					.flatMap(cross -> ctx.reply(cross + " Geometry Dash server returned a `" + status.code() + " "
							+ status.reasonPhrase() + "` error. Try again later."))
					.then();
		});
		cmdErrorHandler.addHandler(CorruptedResponseContentException.class, (e, ctx) -> {
			var content = e.getResponseContent();
			if (content.length() > 500) {
				content = content.substring(0, 497) + "...";
			}
			return Flux.merge(ctx.bot().emoji("cross").flatMap(cross -> ctx.reply(cross + " Geometry Dash server returned corrupted data."
					+ "Unable to complete your request.")), 
					ctx.bot().log(":warning: Geometry Dash server returned corrupted data.\nContext dump: `"
							+ ctx + "`.\n"
							+ "Path: `" + e.getRequestPath() + "`\n"
							+ "Parameters: `" + e.getRequestParams() + "`\n"
							+ "Response: `" + content + "`\n"
							+ "Error observed when parsing response: `" + e.getCause().getClass().getCanonicalName()
									+ (e.getCause().getMessage() != null ? ": " + e.getCause().getMessage() : "") + "`"),
					Mono.fromRunnable(() -> LOGGER.warn("Geometry Dash server returned corrupted data", e))).then();
		});
		cmdErrorHandler.addHandler(TimeoutException.class, (e, ctx) -> ctx.bot().emoji("cross")
				.flatMap(cross -> ctx.reply(cross + " Geometry Dash server took too long to respond. Try again later."))
				.then());
		cmdErrorHandler.addHandler(IOException.class, (e, ctx) -> ctx.bot().emoji("cross")
				.flatMap(cross -> ctx.reply(cross + " Cannot connect to Geometry Dash servers due to network issues. Try again later."))
				.then());
		cmdErrorHandler.addHandler(NoTimelyAvailableException.class, (e, ctx) -> ctx.bot().emoji("cross")
				.flatMap(cross -> ctx.reply(cross + " There is no Daily/Weekly available right now. Come back later!"))
				.then());
		cmdProvider.setErrorHandler(cmdErrorHandler);
		return cmdProvider;
	}
	
	private static GDEventSubscriber initGDEventSubscriber(GDEventDispatcher gdEventDispatcher, GDService gdService, Bot bot) {
		var subscriber = new GDEventSubscriber(bot, gdService);
		gdEventDispatcher.on(GDEvent.class).subscribe(subscriber);
		return subscriber;
	}

	@Override
	public Mono<PropertyReader> initPluginProperties() {
		return PropertyReader.fromPropertiesFile(Path.of(".", "config", "gd.properties"));
	}
}
