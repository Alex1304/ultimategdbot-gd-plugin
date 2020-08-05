package com.github.alex1304.ultimategdbot.gdplugin.gdevent;

import static com.github.alex1304.ultimategdbot.api.util.Markdown.bold;
import static reactor.function.TupleUtils.function;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.function.Function;

import com.github.alex1304.jdash.client.AuthenticatedGDClient;
import com.github.alex1304.jdash.entity.GDTimelyLevel.TimelyType;
import com.github.alex1304.jdash.entity.GDUser;
import com.github.alex1304.jdashevents.GDEventDispatcher;
import com.github.alex1304.jdashevents.GDEventScannerLoop;
import com.github.alex1304.jdashevents.event.AwardedLevelAddedEvent;
import com.github.alex1304.jdashevents.event.AwardedLevelRemovedEvent;
import com.github.alex1304.jdashevents.event.AwardedLevelUpdatedEvent;
import com.github.alex1304.jdashevents.event.GDEvent;
import com.github.alex1304.jdashevents.event.TimelyLevelChangedEvent;
import com.github.alex1304.jdashevents.scanner.AwardedSectionScanner;
import com.github.alex1304.jdashevents.scanner.DailyLevelScanner;
import com.github.alex1304.jdashevents.scanner.GDEventScanner;
import com.github.alex1304.jdashevents.scanner.WeeklyDemonScanner;
import com.github.alex1304.ultimategdbot.api.BotConfig;
import com.github.alex1304.ultimategdbot.api.Translator;
import com.github.alex1304.ultimategdbot.api.service.BotService;
import com.github.alex1304.ultimategdbot.api.util.DurationUtils;
import com.github.alex1304.ultimategdbot.api.util.Markdown;
import com.github.alex1304.ultimategdbot.api.util.MessageSpecTemplate;
import com.github.alex1304.ultimategdbot.gdplugin.database.GDAwardedLevelDao;
import com.github.alex1304.ultimategdbot.gdplugin.database.GDEventConfigDao;
import com.github.alex1304.ultimategdbot.gdplugin.database.GDEventConfigData;
import com.github.alex1304.ultimategdbot.gdplugin.database.ImmutableGDAwardedLevelData;
import com.github.alex1304.ultimategdbot.gdplugin.level.GDLevelService;
import com.github.alex1304.ultimategdbot.gdplugin.user.GDUserService;
import com.github.alex1304.ultimategdbot.gdplugin.util.GDEvents;

import discord4j.common.util.Snowflake;
import discord4j.core.object.Embed.Author;
import discord4j.core.object.entity.Message;
import discord4j.core.object.entity.User;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;
import reactor.util.Logger;
import reactor.util.Loggers;

public final class GDEventService {
	
	private static final Logger LOGGER = Loggers.getLogger(GDEventService.class);

	private final BotService bot;
	private final AuthenticatedGDClient gdClient;
	private final GDLevelService gdLevelService;
	private final GDUserService gdUserService;

	private final GDEventDispatcher gdEventDispatcher;
	private final GDEventScannerLoop gdEventLoop;
	private final BroadcastResultCache broadcastResultCache = new BroadcastResultCache();
	private final Map<Class<? extends GDEvent>, GDEventProperties<? extends GDEvent>> eventProperties = initEventProps();
	private final Scheduler gdEventScheduler = Schedulers.boundedElastic();

	public GDEventService(
			BotConfig botConfig,
			BotService bot,
			AuthenticatedGDClient gdClient,
			GDLevelService gdLevelService,
			GDUserService gdUserService) {
		this.bot = bot;
		this.gdClient = gdClient;
		this.gdLevelService = gdLevelService;
		this.gdUserService = gdUserService;
		var gdConfig = botConfig.resource("gd");
		this.gdEventDispatcher = new GDEventDispatcher();
		var eventLoopInterval = Duration.ofSeconds(gdConfig.readOptional("gdplugin.event_loop_interval")
				.map(Integer::parseInt)
				.orElse(10));
		this.gdEventLoop = new GDEventScannerLoop(gdClient, gdEventDispatcher, initScanners(),
				eventLoopInterval);
		var autostartEventLoop = gdConfig.readOptional("gdplugin.autostart_event_loop")
				.map(Boolean::parseBoolean)
				.orElse(true);
		gdEventDispatcher.on(GDEvent.class)
				.subscribe(new GDEventSubscriber(this, gdEventScheduler));
		if (autostartEventLoop) {
			gdEventLoop.start();
		}
	}
	
	private static Set<GDEventScanner> initScanners() {
		return Set.of(new AwardedSectionScanner(), new DailyLevelScanner(), new WeeklyDemonScanner());
	}
	
	public GDEventDispatcher dispatcher() {
		return gdEventDispatcher;
	}
	
	public GDEventScannerLoop loop() {
		return gdEventLoop;
	}
	
	public Mono<Void> process(GDEvent event) {
		var eventProps = eventProperties.get(event.getClass());
		if (eventProps == null) {
			LOGGER.warn("Unrecognized event type: {}", event.getClass().getName());
			return Mono.empty();
		}
		var tr = bot.localization();
		var logText = eventProps.logText(tr, event);
		return Mono.zip(bot.emoji().get("info"), bot.emoji().get("success"), bot.emoji().get("failed"))
				.flatMap(function((info, success, failed) -> log(info + ' ' + tr.translate("GDStrings", "gdevproc_event_fired") + ' ' + logText)
						.then(broadcast(event, eventProps)
								.elapsed()
								.flatMap(function((time, count) -> log(success + ' ' + tr.translate("GDStrings", "gdevproc_success") + ' ' + logText + "\n"
										+ tr.translate("GDStrings", (eventProps.isUpdate()
												? "gdevproc_messages_edited"
												: "gdevproc_messages_sent")) + ' ' + bold("" + count) + "\n"
										+ tr.translate("GDStrings", "gdevproc_exec_time") + ' ' + bold(DurationUtils.format(Duration.ofMillis(time))))))
								.onErrorResume(e -> bot.logging().log(failed + ' ' + tr.translate("GDStrings", "gdevproc_error")
												+ ' ' + logText + ": " + Markdown.code(e.getClass().getName()))
										.and(Mono.fromRunnable(() -> LOGGER.error("An error occured while dispatching GD event", e)))))));
	}
	
	private Mono<Integer> broadcast(GDEvent event, GDEventProperties<? extends GDEvent> eventProps) {
		if (eventProps.isUpdate()) {
			return Mono.justOrEmpty(eventProps.levelId(event).flatMap(broadcastResultCache::get))
					.flatMapMany(Flux::fromIterable)
					.flatMap(old -> eventProps.createMessageTemplate(event, null, old)
							.map(MessageSpecTemplate::toMessageEditSpec)
							.flatMap(old::edit)
							.onErrorResume(e -> Mono.empty()))
					.collectList()
					.filter(results -> !results.isEmpty())
					.doOnNext(results -> eventProps.levelId(event).ifPresent(id -> broadcastResultCache.put(id, results)))
					.map(List::size)
					.defaultIfEmpty(0);
		}
		var tr = bot.localization();
		var guildBroadcast = bot.database()
				.withExtension(GDEventConfigDao.class, dao -> dao.getAllWithChannel(eventProps.databaseField()))
				.flatMapMany(Flux::fromIterable)
				.flatMap(gsg -> eventProps.createMessageTemplate(event, gsg, null)
						.flatMap(msg -> bot.gateway().rest().getChannelById(eventProps.channelId(gsg))
								.createMessage(GDEvents.specToRequest(msg.toMessageCreateSpec()))
								.map(data -> new Message(bot.gateway(), data))
								.onErrorResume(e -> Mono.empty())));
		var dmBroadcast = eventProps.recipientAccountId(event)
				.flatMapMany(gdUserService::getDiscordAccountsForGDUser)
				.flatMap(User::getPrivateChannel)
				.flatMap(channel -> eventProps.createMessageTemplate(event, null, null)
						.map(msg -> new MessageSpecTemplate(tr.translate("GDStrings", eventProps.congratMessage(event)), msg.getEmbed()))
						.map(MessageSpecTemplate::toMessageCreateSpec)
						.flatMap(channel::createMessage))
				.onErrorResume(e -> Mono.empty());
		return Flux.merge(guildBroadcast, dmBroadcast)
				.collectList()
				.doOnNext(results -> eventProps.levelId(event).ifPresent(id -> broadcastResultCache.put(id, results)))
				.map(List::size);
	}
	
	private Mono<Void> log(String text) {
		return Mono.when(bot.logging().log(text).onErrorResume(e -> Mono.empty()), Mono.fromRunnable(() -> LOGGER.info(text)));
	}
	
	private Map<Class<? extends GDEvent>, GDEventProperties<? extends GDEvent>> initEventProps() {
		return Map.ofEntries(
				Map.entry(AwardedLevelAddedEvent.class, new GDEventProperties<AwardedLevelAddedEvent>(
						(tr, event) -> tr.translate("GDStrings", "gdevproc_awarded_event_log",
								bold(event.getClass().getSimpleName()), GDLevelService.toString(event.getAddedLevel())),
						"awarded_levels",
						GDEventConfigData::channelAwardedLevelsId,
						GDEventConfigData::roleAwardedLevelsId,
						event -> Optional.of(event.getAddedLevel().getId()),
						event -> bot.database().useExtension(GDAwardedLevelDao.class, dao -> dao.insertOrUpdate(
										ImmutableGDAwardedLevelData.builder()
												.levelId(event.getAddedLevel().getId())
												.insertDate(Instant.now())
												.downloads(event.getAddedLevel().getDownloads())
												.likes(event.getAddedLevel().getLikes())
												.build()))
								.onErrorResume(e -> Mono.fromRunnable(() -> LOGGER.error("Error when saving new awarded level", e)))
								.then(gdClient.searchUser("" + event.getAddedLevel().getCreatorID()).map(GDUser::getAccountId)),
						(event, eventCfg, old) -> gdLevelService
								.compactView(adaptTranslator(eventCfg), event.getAddedLevel(),
										adaptTranslator(eventCfg).translate("GDStrings", "gdevproc_title_rate"),
										"https://i.imgur.com/asoMj1W.png")
								.map(embed -> new MessageSpecTemplate(
										mentionRoleIfSet(eventCfg, GDEventConfigData::roleAwardedLevelsId)
												+ randomString(adaptTranslator(eventCfg)
														.translate("GDStrings", "gdevproc_public_rate")),
										embed)),
						event -> "gdevproc_dm_rate",
						false
				)),
				Map.entry(AwardedLevelRemovedEvent.class, new GDEventProperties<AwardedLevelRemovedEvent>(
						(tr, event) -> tr.translate("GDStrings", "gdevproc_awarded_event_log",
								bold(event.getClass().getSimpleName()), GDLevelService.toString(event.getRemovedLevel())),
						"awarded_levels",
						GDEventConfigData::channelAwardedLevelsId,
						GDEventConfigData::roleAwardedLevelsId,
						event -> Optional.empty(),
						event -> gdClient.searchUser("" + event.getRemovedLevel().getCreatorID()).map(GDUser::getAccountId),
						(event, eventCfg, old) -> gdLevelService
								.compactView(adaptTranslator(eventCfg), event.getRemovedLevel(), 
										adaptTranslator(eventCfg).translate("GDStrings", "gdevproc_title_unrate"),
										"https://i.imgur.com/fPECXUz.png")
								.map(embed -> new MessageSpecTemplate(
										mentionRoleIfSet(eventCfg, GDEventConfigData::roleAwardedLevelsId)
										+ randomString(adaptTranslator(eventCfg)
												.translate("GDStrings", "gdevproc_public_unrate")),
										embed)),
						event -> "gdevproc_dm_unrate",
						false
				)),
				Map.entry(AwardedLevelUpdatedEvent.class, new GDEventProperties<AwardedLevelUpdatedEvent>(
						(tr, event) -> tr.translate("GDStrings", "gdevproc_awarded_event_log",
								bold(event.getClass().getSimpleName()), GDLevelService.toString(event.getNewLevel())),
						"awarded_levels",
						GDEventConfigData::channelAwardedLevelsId,
						GDEventConfigData::roleAwardedLevelsId,
						event -> Optional.of(event.getNewLevel().getId()),
						event -> gdClient.searchUser("" + event.getNewLevel().getCreatorID()).map(GDUser::getAccountId),
						(event, eventCfg, old) -> gdLevelService
								.compactView(adaptTranslator(eventCfg), event.getNewLevel(),
										old.getEmbeds().get(0).getAuthor().map(Author::getName).orElseThrow(),
										old.getEmbeds().get(0).getAuthor().map(Author::getIconUrl).orElseThrow())
								.map(embed -> new MessageSpecTemplate(old.getContent(), embed)),
						event -> { throw new UnsupportedOperationException(); },
						true
				)),
				Map.entry(TimelyLevelChangedEvent.class, new GDEventProperties<TimelyLevelChangedEvent>(
						(tr, event) -> tr.translate("GDStrings", event.getTimelyLevel().getType() == TimelyType.DAILY
										? "gdevproc_daily_event_log" : "gdevproc_weekly_event_log",
								bold(event.getClass().getSimpleName()), event.getTimelyLevel().getId()),
						"timely_levels",
						GDEventConfigData::channelTimelyLevelsId,
						GDEventConfigData::roleTimelyLevelsId,
						event -> Optional.empty(),
						event -> event.getTimelyLevel().getLevel()
								.flatMap(level -> gdClient.searchUser("" + level.getCreatorID()))
								.map(GDUser::getAccountId),
						(event, eventCfg, old) -> {
							var isWeekly = event.getTimelyLevel().getType() == TimelyType.WEEKLY;
							var headerTitle = isWeekly ? "Weekly Demon" : "Daily Level";
							var headerLink = isWeekly ? "https://i.imgur.com/kcsP5SN.png"
									: "https://i.imgur.com/enpYuB8.png";
							return event.getTimelyLevel().getLevel()
									.flatMap(level -> gdLevelService.compactView(adaptTranslator(eventCfg), level,
											headerTitle + " #" + event.getTimelyLevel().getId(), headerLink))
									.map(embed -> new MessageSpecTemplate(
											mentionRoleIfSet(eventCfg, GDEventConfigData::roleTimelyLevelsId)
											+ randomString(adaptTranslator(eventCfg)
													.translate("GDStrings", isWeekly ? "gdevproc_public_weekly"
															: "gdevproc_public_daily")),
											embed));
						},
						event -> event.getTimelyLevel().getType() == TimelyType.DAILY
								? "gdevproc_dm_daily" : "gdevproc_dm_weekly",
						false
				)),
				Map.entry(UserPromotedToModEvent.class, new GDEventProperties<UserPromotedToModEvent>(
						(tr, event) -> tr.translate("GDStrings", "gdevproc_mod_event_log",
								bold(event.getClass().getSimpleName()), bold(event.getUser().getName())),
						"gd_moderators",
						GDEventConfigData::channelGdModeratorsId,
						GDEventConfigData::roleGdModeratorsId,
						event -> Optional.empty(),
						event -> Mono.just(event.getUser().getAccountId()),
						(event, eventCfg, old) -> gdUserService
								.makeIconSet(adaptTranslator(eventCfg), event.getUser())
								.flatMap(icons -> gdUserService.userProfileView(adaptTranslator(eventCfg), null, event.getUser(),
										adaptTranslator(eventCfg).translate("GDStrings", "gdevproc_title_promoted"),
										"https://i.imgur.com/zY61GDD.png", icons))
								.map(msg -> new MessageSpecTemplate(
										mentionRoleIfSet(eventCfg, GDEventConfigData::roleGdModeratorsId)
										+ randomString(adaptTranslator(eventCfg).translate("GDStrings", "gdevproc_public_mod")),
										msg.getEmbed())),
						event -> "gdevproc_dm_mod",
						false
				)),
				Map.entry(UserPromotedToElderEvent.class, new GDEventProperties<UserPromotedToElderEvent>(
						(tr, event) -> tr.translate("GDStrings", "gdevproc_mod_event_log",
								bold(event.getClass().getSimpleName()), bold(event.getUser().getName())),
						"gd_moderators",
						GDEventConfigData::channelGdModeratorsId,
						GDEventConfigData::roleGdModeratorsId,
						event -> Optional.empty(),
						event -> Mono.just(event.getUser().getAccountId()),
						(event, eventCfg, old) -> gdUserService
								.makeIconSet(adaptTranslator(eventCfg), event.getUser())
								.flatMap(icons -> gdUserService.userProfileView(adaptTranslator(eventCfg), null, event.getUser(),
										adaptTranslator(eventCfg).translate("GDStrings", "gdevproc_title_promoted"),
										"https://i.imgur.com/zY61GDD.png", icons))
								.map(msg -> new MessageSpecTemplate(
										mentionRoleIfSet(eventCfg, GDEventConfigData::roleGdModeratorsId)
										+ randomString(adaptTranslator(eventCfg)
												.translate("GDStrings", "gdevproc_public_elder")),
										msg.getEmbed())),
						event -> "gdevproc_dm_elder",
						false
				)),
				Map.entry(UserDemotedFromModEvent.class, new GDEventProperties<UserDemotedFromModEvent>(
						(tr, event) -> tr.translate("GDStrings", "gdevproc_mod_event_log",
								bold(event.getClass().getSimpleName()), bold(event.getUser().getName())),
						"gd_moderators",
						GDEventConfigData::channelGdModeratorsId,
						GDEventConfigData::roleGdModeratorsId,
						event -> Optional.empty(),
						event -> Mono.just(event.getUser().getAccountId()),
						(event, eventCfg, old) -> gdUserService
								.makeIconSet(adaptTranslator(eventCfg), event.getUser())
								.flatMap(icons -> gdUserService.userProfileView(adaptTranslator(eventCfg), null, event.getUser(),
										adaptTranslator(eventCfg).translate("GDStrings", "gdevproc_title_demoted"),
										"https://i.imgur.com/X53HV7d.png", icons))
								.map(msg -> new MessageSpecTemplate(
										mentionRoleIfSet(eventCfg, GDEventConfigData::roleGdModeratorsId)
										+ randomString(adaptTranslator(eventCfg)
												.translate("GDStrings", "gdevproc_public_unmod")),
										msg.getEmbed())),
						event -> "gdevproc_dm_unmod",
						false
				)),
				Map.entry(UserDemotedFromElderEvent.class, new GDEventProperties<UserDemotedFromElderEvent>(
						(tr, event) -> tr.translate("GDStrings", "gdevproc_mod_event_log",
								bold(event.getClass().getSimpleName()), bold(event.getUser().getName())),
						"gd_moderators",
						GDEventConfigData::channelGdModeratorsId,
						GDEventConfigData::roleGdModeratorsId,
						event -> Optional.empty(),
						event -> Mono.just(event.getUser().getAccountId()),
						(event, eventCfg, old) -> gdUserService
								.makeIconSet(adaptTranslator(eventCfg), event.getUser())
								.flatMap(icons -> gdUserService.userProfileView(adaptTranslator(eventCfg), null, event.getUser(),
										adaptTranslator(eventCfg).translate("GDStrings", "gdevproc_title_demoted"),
										"https://i.imgur.com/X53HV7d.png", icons))
								.map(msg -> new MessageSpecTemplate(
										mentionRoleIfSet(eventCfg, GDEventConfigData::roleGdModeratorsId)
										+ randomString(adaptTranslator(eventCfg)
												.translate("GDStrings", "gdevproc_public_unelder")),
										msg.getEmbed())),
						event -> "gdevproc_dm_unelder",
						false
				))
		);
	}
	
	Translator adaptTranslator(GDEventConfigData data) {
		return Translator.to(bot.localization().getLocaleForGuild(data.guildId().asLong()));
	}
	
	private static String mentionRoleIfSet(GDEventConfigData eventCfg, Function<GDEventConfigData, Optional<Snowflake>> getter) {
		if (eventCfg == null) {
			return "";
		}
		var roleIdOpt = getter.apply(eventCfg);
		return roleIdOpt.map(Snowflake::asLong).map(roleId -> "<@&" + roleId + "> ").orElse("");
	}
	
	private static String randomString(String str) {
		var array = str.split("///");
		return array[new Random().nextInt(array.length)];
	}
}
