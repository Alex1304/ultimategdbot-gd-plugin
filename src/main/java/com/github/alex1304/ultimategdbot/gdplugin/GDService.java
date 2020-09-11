package com.github.alex1304.ultimategdbot.gdplugin;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
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
import com.github.alex1304.ultimategdbot.api.BotConfig;
import com.github.alex1304.ultimategdbot.api.command.CommandErrorHandler;
import com.github.alex1304.ultimategdbot.api.command.CommandFailedException;
import com.github.alex1304.ultimategdbot.api.command.CommandProvider;
import com.github.alex1304.ultimategdbot.api.command.Context;
import com.github.alex1304.ultimategdbot.api.command.PermissionLevel;
import com.github.alex1304.ultimategdbot.api.command.annotated.paramconverter.ParamConverter;
import com.github.alex1304.ultimategdbot.api.service.BotService;
import com.github.alex1304.ultimategdbot.api.service.RootServiceSetupHelper;
import com.github.alex1304.ultimategdbot.gdplugin.database.GDAwardedLevelData;
import com.github.alex1304.ultimategdbot.gdplugin.database.GDLeaderboardBanData;
import com.github.alex1304.ultimategdbot.gdplugin.database.GDLeaderboardData;
import com.github.alex1304.ultimategdbot.gdplugin.database.GDLevelRequestConfigDao;
import com.github.alex1304.ultimategdbot.gdplugin.database.GDLevelRequestConfigData;
import com.github.alex1304.ultimategdbot.gdplugin.database.GDLevelRequestReviewData;
import com.github.alex1304.ultimategdbot.gdplugin.database.GDLevelRequestSubmissionData;
import com.github.alex1304.ultimategdbot.gdplugin.database.GDLinkedUserDao;
import com.github.alex1304.ultimategdbot.gdplugin.database.GDLinkedUserData;
import com.github.alex1304.ultimategdbot.gdplugin.database.GDModDao;
import com.github.alex1304.ultimategdbot.gdplugin.database.GDModData;
import com.github.alex1304.ultimategdbot.gdplugin.gdevent.GDEventService;
import com.github.alex1304.ultimategdbot.gdplugin.level.GDLevelService;
import com.github.alex1304.ultimategdbot.gdplugin.levelrequest.GDLevelRequestService;
import com.github.alex1304.ultimategdbot.gdplugin.user.GDUserService;

import discord4j.rest.request.DiscardedRequestException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.Logger;
import reactor.util.Loggers;

public final class GDService {
	
	private static final Logger LOGGER = Loggers.getLogger(GDService.class);

	// Injected
	private final BotService bot;
	private final AuthenticatedGDClient gdClient;
	private final SpriteFactory spriteFactory;
	private final GDEventService gdEventService;
	private final GDLevelRequestService gdLevelRequestService;
	private final GDLevelService gdLevelService;
	private final GDUserService gdUserService;
	
	// Initialized via bot config
	private final int leaderboardRefreshParallelism;
	
	public static Mono<GDService> create(
			BotConfig botConfig,
			BotService bot,
			AuthenticatedGDClient gdClient,
			SpriteFactory spriteFactory,
			GDEventService gdEventService,
			GDLevelRequestService gdLevelRequestService,
			GDLevelService gdLevelService,
			GDUserService gdUserService) {
		bot.database().configureJdbi(jdbi -> {
			jdbi.getConfig(JdbiImmutables.class).registerImmutable(
					GDAwardedLevelData.class,
					GDLeaderboardBanData.class,
					GDLeaderboardData.class,
					GDLevelRequestConfigData.class,
					GDLevelRequestReviewData.class,
					GDLevelRequestSubmissionData.class,
					GDLinkedUserData.class,
					GDModData.class);
		});
		bot.database().addGuildConfigurator(GDLevelRequestConfigDao.class,
				(data, tr) -> GDLevelRequestConfigData.configurator(data, tr, bot.gateway()));
		return RootServiceSetupHelper.create(() -> {
					var gdConfig = botConfig.resource("gd");
					var leaderboardRefreshParallelism = gdConfig.readOptional("gdplugin.max_connections")
							.map(Integer::parseInt)
							.orElse(100);
					var gdService = new GDService(bot, gdClient, spriteFactory, gdEventService, gdLevelRequestService, 
							gdLevelService, gdUserService, leaderboardRefreshParallelism);
					return gdService;
				})
				.addCommandProvider(bot.command(), initCommandProvider(bot, gdClient, gdLevelRequestService, gdUserService))
				.setup();
	}
	
	private GDService(
			BotService bot,
			AuthenticatedGDClient gdClient,
			SpriteFactory spriteFactory,
			GDEventService gdEventService,
			GDLevelRequestService gdLevelRequestService,
			GDLevelService gdLevelService,
			GDUserService gdUserService,
			int leaderboardRefreshParallelism) {
		this.bot = bot;
		this.gdClient = gdClient;
		this.spriteFactory = spriteFactory;
		this.gdEventService = gdEventService;
		this.gdLevelRequestService = gdLevelRequestService;
		this.gdLevelService = gdLevelService;
		this.gdUserService = gdUserService;
		this.leaderboardRefreshParallelism = leaderboardRefreshParallelism;
	}
	
	private static CommandProvider initCommandProvider(
			BotService bot,
			AuthenticatedGDClient gdClient,
			GDLevelRequestService gdLevelRequestService,
			GDUserService gdUserService) {
		var cmdProvider = new CommandProvider(GDPlugin.PLUGIN_NAME, bot.command().getPermissionChecker());
		// Param converters
		cmdProvider.addParamConverter(new ParamConverter<GDUser>() {
			@Override
			public Mono<GDUser> convert(Context ctx, String input) {
				return gdUserService.stringToUser(ctx, input);
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
		bot.command().getPermissionChecker().register("LEVEL_REQUEST_REVIEWER", ctx -> bot.command()
				.getPermissionChecker()
				.isGranted(PermissionLevel.GUILD_ADMIN, ctx)
				.flatMap(isGuildAdmin -> isGuildAdmin ? Mono.just(true) : ctx.event().getMessage()
						.getAuthorAsMember()
						.flatMap(member -> gdLevelRequestService.retrieveConfig(ctx)
								.map(GDLevelRequestConfigData::roleReviewerId)
								.flatMap(Mono::justOrEmpty)
								.map(member.getRoleIds()::contains))));
		bot.command().getPermissionChecker().register("ELDER_MOD", ctx -> bot.command().getPermissionChecker()
				.isGranted(PermissionLevel.BOT_OWNER, ctx)
				.flatMap(isGranted -> isGranted ? Mono.just(true) : bot.database()
						.withExtension(GDLinkedUserDao.class, dao -> dao.getAllIn(List.of(ctx.author().getId().asLong()))
								.stream()
								.findAny())
						.flatMap(Mono::justOrEmpty)
						.flatMap(linkedUser -> bot.database().withExtension(GDModDao.class, dao -> dao.get(linkedUser.gdUserId()))
								.flatMap(Mono::justOrEmpty)
								.map(GDModData::isElder))));
		// Error handlers
		var cmdErrorHandler = new CommandErrorHandler();
		cmdErrorHandler.addHandler(CommandFailedException.class, (e, ctx) -> bot.emoji().get("cross")
				.flatMap(cross -> ctx.reply(cross + " " + e.getMessage()))
				.then());
		cmdErrorHandler.addHandler(MissingAccessException.class, (e, ctx) -> bot.emoji().get("cross")
				.flatMap(cross -> ctx.reply(cross + " Nothing found."))
				.then());
		cmdErrorHandler.addHandler(BadResponseException.class, (e, ctx) -> {
			var status = e.getResponse().status();
			return bot.emoji().get("cross")
					.flatMap(cross -> ctx.reply(cross + " Geometry Dash server returned a `" + status.code() + " "
							+ status.reasonPhrase() + "` error. Try again later."))
					.then();
		});
		cmdErrorHandler.addHandler(CorruptedResponseContentException.class, (e, ctx) -> {
			var content = e.getResponseContent();
			if (content.length() > 500) {
				content = content.substring(0, 497) + "...";
			}
			return Flux.merge(bot.emoji().get("cross").flatMap(cross -> ctx.reply(cross + " Geometry Dash server returned corrupted data."
					+ "Unable to complete your request.")), 
					bot.logging().log(":warning: Geometry Dash server returned corrupted data.\nContext dump: `"
							+ ctx + "`.\n"
							+ "Path: `" + e.getRequestPath() + "`\n"
							+ "Parameters: `" + e.getRequestParams() + "`\n"
							+ "Response: `" + content + "`\n"
							+ "Error observed when parsing response: `" + e.getCause().getClass().getCanonicalName()
									+ (e.getCause().getMessage() != null ? ": " + e.getCause().getMessage() : "") + "`"),
					Mono.fromRunnable(() -> LOGGER.warn("Geometry Dash server returned corrupted data", e))).then();
		});
		cmdErrorHandler.addHandler(DiscardedRequestException.class, (e, ctx) -> Mono.fromRunnable(() -> LOGGER.warn(e.toString())));
		cmdErrorHandler.addHandler(TimeoutException.class, (e, ctx) -> bot.emoji().get("cross")
				.flatMap(cross -> ctx.reply(cross + " Geometry Dash server took too long to respond. Try again later."))
				.then());
		cmdErrorHandler.addHandler(IOException.class, (e, ctx) -> bot.emoji().get("cross")
				.flatMap(cross -> ctx.reply(cross + " Cannot connect to Geometry Dash servers due to network issues. Try again later."))
				.then());
		cmdErrorHandler.addHandler(NoTimelyAvailableException.class, (e, ctx) -> bot.emoji().get("cross")
				.flatMap(cross -> ctx.reply(cross + " There is no Daily/Weekly available right now. Come back later!"))
				.then());
		cmdProvider.setErrorHandler(cmdErrorHandler);
		return cmdProvider;
	}
	
	public static Mono<AuthenticatedGDClient> createGDClient(BotConfig botConfig) {
		var gdConfig = botConfig.resource("gd");
		var username = gdConfig.read("gdplugin.username");
		var password = gdConfig.read("gdplugin.password");
		var host = gdConfig.readOptional("gdplugin.host").orElse(Routes.BASE_URL);
		var cacheTtl = gdConfig.readOptional("gdplugin.cache_ttl")
				.map(v -> Duration.ofMillis(Long.parseLong(v)))
				.orElse(GDClientBuilder.DEFAULT_CACHE_TTL);
		var requestTimeout = gdConfig.readOptional("gdplugin.request_timeout")
				.map(v -> Duration.ofMillis(Long.parseLong(v)))
				.orElse(GDClientBuilder.DEFAULT_REQUEST_TIMEOUT);
		return GDClientBuilder.create()
				.withHost(host)
				.withCacheTtl(cacheTtl)
				.withRequestTimeout(requestTimeout)
				.buildAuthenticated(new Credentials(username, password))
				.onErrorMap(e -> new RuntimeException("Failed to login with the given Geometry Dash credentials", e));
	}
	
	public static Mono<SpriteFactory> createSpriteFactory() {
		return Mono.fromCallable(SpriteFactory::create)
				.subscribeOn(Schedulers.boundedElastic())
				.onErrorMap(e -> new RuntimeException("An error occured when loading the GD icons sprite factory", e));
	}

	public BotService bot() {
		return bot;
	}

	public AuthenticatedGDClient client() {
		return gdClient;
	}

	public SpriteFactory spriteFactory() {
		return spriteFactory;
	}

	public GDLevelService level() {
		return gdLevelService;
	}

	public GDUserService user() {
		return gdUserService;
	}

	public GDLevelRequestService levelRequest() {
		return gdLevelRequestService;
	}

	public GDEventService event() {
		return gdEventService;
	}

	public int getLeaderboardRefreshParallelism() {
		return leaderboardRefreshParallelism;
	}
}
