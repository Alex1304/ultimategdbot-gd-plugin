package com.github.alex1304.ultimategdbot.gdplugin;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeoutException;

import org.jdbi.v3.core.mapper.immutables.JdbiImmutables;

import com.github.alex1304.jdash.entity.GDLevel;
import com.github.alex1304.jdash.entity.GDUser;
import com.github.alex1304.jdash.exception.BadResponseException;
import com.github.alex1304.jdash.exception.CorruptedResponseContentException;
import com.github.alex1304.jdash.exception.MissingAccessException;
import com.github.alex1304.jdash.exception.NoTimelyAvailableException;
import com.github.alex1304.jdash.util.LevelSearchFilters;
import com.github.alex1304.jdashevents.event.GDEvent;
import com.github.alex1304.ultimategdbot.api.Bot;
import com.github.alex1304.ultimategdbot.api.Plugin;
import com.github.alex1304.ultimategdbot.api.PluginMetadata;
import com.github.alex1304.ultimategdbot.api.command.CommandErrorHandler;
import com.github.alex1304.ultimategdbot.api.command.CommandFailedException;
import com.github.alex1304.ultimategdbot.api.command.CommandProvider;
import com.github.alex1304.ultimategdbot.api.command.CommandService;
import com.github.alex1304.ultimategdbot.api.command.Context;
import com.github.alex1304.ultimategdbot.api.command.PermissionChecker;
import com.github.alex1304.ultimategdbot.api.command.PermissionLevel;
import com.github.alex1304.ultimategdbot.api.command.annotated.paramconverter.ParamConverter;
import com.github.alex1304.ultimategdbot.api.command.menu.InteractiveMenuService;
import com.github.alex1304.ultimategdbot.api.database.DatabaseService;
import com.github.alex1304.ultimategdbot.api.emoji.EmojiService;
import com.github.alex1304.ultimategdbot.api.service.Service;
import com.github.alex1304.ultimategdbot.api.util.VersionUtils;
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

import discord4j.common.GitProperties;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.Logger;
import reactor.util.Loggers;

public class GDPlugin implements Plugin {

	private static final Logger LOGGER = Loggers.getLogger(GDPlugin.class);
	private static final String PLUGIN_NAME = "Geometry Dash";

	@Override
	public Mono<Void> setup(Bot bot) {
		return Mono.fromRunnable(() -> {
			// Database config
			bot.service(DatabaseService.class).configureJdbi(jdbi -> {
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
			bot.service(DatabaseService.class).registerGuildConfigExtension(GDEventConfigDao.class);
			bot.service(DatabaseService.class).registerGuildConfigExtension(GDLevelRequestConfigDao.class);
			// Commands
			bot.service(CommandService.class).addProvider(initCommandProvider(bot));
			// GD service setup
			var gdService = bot.service(GDService.class);
			// Subscribe to GD events
			var subscriber = new GDEventSubscriber(bot, gdService.getGdEventScheduler());
			gdService.getGdEventDispatcher().on(GDEvent.class).subscribe(subscriber);
			// Start level requests cleanup task
			GDLevelRequests.listenAndCleanSubmissionQueueChannels(bot);
			// Start GD event loop if autostart is enabled
			if (gdService.isAutostartEventLoop()) {
				gdService.getGdEventLoop().start();
			}
		});
	}

	@Override
	public Mono<PluginMetadata> metadata() {
		return VersionUtils.getGitProperties("META-INF/git/core.git.properties")
				.map(props -> props.readOptional(GitProperties.APPLICATION_VERSION))
				.map(version -> PluginMetadata.builder(PLUGIN_NAME)
						.setDescription("Commands and useful features dedicated to the platformer game Geometry Dash.")
						.setVersion(version.orElse(null))
						.setDevelopers(List.of("Alex1304"))
						.setUrl("https://github.com/ultimategdbot/ultimategdbot-gd-plugin")
						.build());
	}
	
	@Override
	public Set<Class<? extends Service>> requiredServices() {
		return Set.of(
				CommandService.class,
				DatabaseService.class,
				EmojiService.class,
				GDService.class,
				InteractiveMenuService.class);
	}
	
	private static CommandProvider initCommandProvider(Bot bot) {
		var cmdProvider = new CommandProvider(PLUGIN_NAME);
		// Commands
		cmdProvider.addAnnotated(new AccountCommand());
		cmdProvider.addAnnotated(new CheckModCommand());
		cmdProvider.addAnnotated(new ClearGdCacheCommand());
		cmdProvider.addAnnotated(new DailyCommand());
		cmdProvider.addAnnotated(new FeaturedInfoCommand());
		cmdProvider.addAnnotated(new GDEventsCommand());
		cmdProvider.addAnnotated(new LeaderboardCommand());
		cmdProvider.addAnnotated(new LevelCommand());
		cmdProvider.addAnnotated(new LevelRequestCommand());
		cmdProvider.addAnnotated(new LevelsbyCommand());
		cmdProvider.addAnnotated(new ModListCommand());
		cmdProvider.addAnnotated(new ProfileCommand());
		cmdProvider.addAnnotated(new WeeklyCommand());
		// Param converters
		cmdProvider.addParamConverter(new ParamConverter<GDUser>() {
			@Override
			public Mono<GDUser> convert(Context ctx, String input) {
				return GDUsers.stringToUser(ctx, bot, input);
			}
			@Override
			public Class<GDUser> type() {
				return GDUser.class;
			}
		});
		cmdProvider.addParamConverter(new ParamConverter<GDLevel>() {
			@Override
			public Mono<GDLevel> convert(Context ctx, String input) {
				return bot.service(GDService.class).getGdClient()
						.searchLevels(input, LevelSearchFilters.create(), 0)
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
		permChecker.register("LEVEL_REQUEST_REVIEWER", ctx -> ctx.bot().service(CommandService.class)
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
		cmdErrorHandler.addHandler(CommandFailedException.class, (e, ctx) -> ctx.bot().service(EmojiService.class).emoji("cross")
				.flatMap(cross -> ctx.reply(cross + " " + e.getMessage()))
				.then());
		cmdErrorHandler.addHandler(MissingAccessException.class, (e, ctx) -> ctx.bot().service(EmojiService.class).emoji("cross")
				.flatMap(cross -> ctx.reply(cross + " Nothing found."))
				.then());
		cmdErrorHandler.addHandler(BadResponseException.class, (e, ctx) -> {
			var status = e.getResponse().status();
			return ctx.bot().service(EmojiService.class).emoji("cross")
					.flatMap(cross -> ctx.reply(cross + " Geometry Dash server returned a `" + status.code() + " "
							+ status.reasonPhrase() + "` error. Try again later."))
					.then();
		});
		cmdErrorHandler.addHandler(CorruptedResponseContentException.class, (e, ctx) -> {
			var content = e.getResponseContent();
			if (content.length() > 500) {
				content = content.substring(0, 497) + "...";
			}
			return Flux.merge(ctx.bot().service(EmojiService.class).emoji("cross").flatMap(cross -> ctx.reply(cross + " Geometry Dash server returned corrupted data."
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
		cmdErrorHandler.addHandler(TimeoutException.class, (e, ctx) -> ctx.bot().service(EmojiService.class).emoji("cross")
				.flatMap(cross -> ctx.reply(cross + " Geometry Dash server took too long to respond. Try again later."))
				.then());
		cmdErrorHandler.addHandler(IOException.class, (e, ctx) -> ctx.bot().service(EmojiService.class).emoji("cross")
				.flatMap(cross -> ctx.reply(cross + " Cannot connect to Geometry Dash servers due to network issues. Try again later."))
				.then());
		cmdErrorHandler.addHandler(NoTimelyAvailableException.class, (e, ctx) -> ctx.bot().service(EmojiService.class).emoji("cross")
				.flatMap(cross -> ctx.reply(cross + " There is no Daily/Weekly available right now. Come back later!"))
				.then());
		cmdProvider.setErrorHandler(cmdErrorHandler);
		return cmdProvider;
	}
}
