package com.github.alex1304.ultimategdbot.gdplugin.gdevent;

import static com.github.alex1304.ultimategdbot.api.util.Markdown.bold;
import static java.util.stream.Collectors.toUnmodifiableList;
import static reactor.function.TupleUtils.function;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;

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
import com.github.alex1304.ultimategdbot.api.service.BotService;
import com.github.alex1304.ultimategdbot.api.util.Markdown;
import com.github.alex1304.ultimategdbot.api.util.MessageSpecTemplate;
import com.github.alex1304.ultimategdbot.gdplugin.database.GDAwardedLevelDao;
import com.github.alex1304.ultimategdbot.gdplugin.database.ImmutableGDAwardedLevelData;
import com.github.alex1304.ultimategdbot.gdplugin.level.GDLevelService;
import com.github.alex1304.ultimategdbot.gdplugin.user.GDUserService;
import com.github.alex1304.ultimategdbot.gdplugin.util.GDEvents;

import discord4j.common.util.Snowflake;
import discord4j.core.object.Embed.Author;
import discord4j.core.object.entity.Message;
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
	private final CrosspostQueue crosspostQueue;
	
	private final List<Snowflake> ratesChannelIds;
	private final Snowflake unratesChannelId;
	private final Snowflake dailiesChannelId;
	private final Snowflake weekliesChannelId;
	private final Snowflake modPromotionsChannelId;
	private final Snowflake modDemotionsChannelId;

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
		this.ratesChannelIds = gdConfig.readAsStream("gdplugin.event.rates_channel_ids", ",")
				.map(Snowflake::of)
				.collect(toUnmodifiableList());
		System.err.println(gdConfig);
		this.unratesChannelId = gdConfig.readAs("gdplugin.event.unrates_channel_id", Snowflake::of);
		this.dailiesChannelId = gdConfig.readAs("gdplugin.event.dailies_channel_id", Snowflake::of);
		this.weekliesChannelId = gdConfig.readAs("gdplugin.event.weeklies_channel_id", Snowflake::of);
		this.modPromotionsChannelId = gdConfig.readAs("gdplugin.event.mod_promotions_channel_id", Snowflake::of);
		this.modDemotionsChannelId = gdConfig.readAs("gdplugin.event.mod_demotions_channel_id", Snowflake::of);
		this.crosspostQueue = new CrosspostQueue(bot);
		// Activate dispatcher and loop
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
	
	Mono<Void> process(GDEvent event) {
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
								.flatMap(function((time, count) -> log(success + ' ' + tr.translate("GDStrings", "gdevproc_success") + ' ' + logText)))
								.onErrorResume(e -> bot.logging().log(failed + ' ' + tr.translate("GDStrings", "gdevproc_error")
												+ ' ' + logText + ": " + Markdown.code(e.getClass().getName()))
										.and(Mono.fromRunnable(() -> LOGGER.error("An error occured while dispatching GD event", e)))))));
	}
	
	private Mono<Integer> broadcast(GDEvent event, GDEventProperties<? extends GDEvent> eventProps) {
		if (eventProps.isUpdate()) {
			return Mono.justOrEmpty(eventProps.levelId(event).flatMap(broadcastResultCache::get))
					.flatMapMany(Flux::fromIterable)
					.flatMap(old -> eventProps.createMessageTemplate(event, old)
							.map(MessageSpecTemplate::toMessageEditSpec)
							.flatMap(old::edit))
					.collectList()
					.filter(results -> !results.isEmpty())
					.doOnNext(results -> eventProps.levelId(event).ifPresent(id -> broadcastResultCache.put(id, results)))
					.map(List::size)
					.defaultIfEmpty(0);
		}
		var tr = bot.localization();
		var logText = eventProps.logText(tr, event);
		var guildBroadcast = eventProps.createMessageTemplate(event, null)
						.flatMap(msg -> bot.gateway().rest().getChannelById(eventProps.channelId(event))
								.createMessage(GDEvents.specToRequest(msg.toMessageCreateSpec()))
								.map(data -> new Message(bot.gateway(), data)))
						.doOnNext(msg -> crosspostQueue.submit(msg, event, eventProps));
		var dmBroadcast = eventProps.recipientAccountId(event)
				.flatMapMany(gdUserService::getDiscordAccountsForGDUser)
				.flatMap(user -> user.getPrivateChannel()
						.flatMap(channel -> eventProps.createMessageTemplate(event, null)
								.map(msg -> new MessageSpecTemplate(tr.translate("GDStrings", eventProps.congratMessage(event)), msg.getEmbed()))
								.map(MessageSpecTemplate::toMessageCreateSpec)
								.flatMap(channel::createMessage))
						.flatMap(message -> bot.emoji().get("success")
								.flatMap(em -> log(em + tr.translate("GDStrings", "gdevproc_dm_log", user.getTag(), logText)))
								.thenReturn(message)))
				.onErrorResume(e -> Mono.fromRunnable(() -> LOGGER.debug("Could not DM user for GD event", e)));
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
						event -> ratesChannelIds.get(event.getAddedLevel().getStars() - 1),
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
						(event, old) -> gdLevelService
								.compactView(bot.localization(), event.getAddedLevel(),
										bot.localization().translate("GDStrings", "gdevproc_title_rate"),
										"https://i.imgur.com/asoMj1W.png")
								.map(embed -> new MessageSpecTemplate(randomString(bot.localization()
														.translate("GDStrings", "gdevproc_public_rate")),
										embed)),
						event -> "gdevproc_dm_rate",
						false
				)),
				Map.entry(AwardedLevelRemovedEvent.class, new GDEventProperties<AwardedLevelRemovedEvent>(
						(tr, event) -> tr.translate("GDStrings", "gdevproc_awarded_event_log",
								bold(event.getClass().getSimpleName()), GDLevelService.toString(event.getRemovedLevel())),
						"awarded_levels",
						event -> unratesChannelId,
						event -> Optional.empty(),
						event -> gdClient.searchUser("" + event.getRemovedLevel().getCreatorID()).map(GDUser::getAccountId),
						(event, old) -> gdLevelService
								.compactView(bot.localization(), event.getRemovedLevel(), 
										bot.localization().translate("GDStrings", "gdevproc_title_unrate"),
										"https://i.imgur.com/fPECXUz.png")
								.map(embed -> new MessageSpecTemplate(randomString(bot.localization()
												.translate("GDStrings", "gdevproc_public_unrate")),
										embed)),
						event -> "gdevproc_dm_unrate",
						false
				)),
				Map.entry(AwardedLevelUpdatedEvent.class, new GDEventProperties<AwardedLevelUpdatedEvent>(
						(tr, event) -> tr.translate("GDStrings", "gdevproc_awarded_event_log",
								bold(event.getClass().getSimpleName()), GDLevelService.toString(event.getNewLevel())),
						"awarded_levels",
						event -> { throw new UnsupportedOperationException(); },
						event -> Optional.of(event.getNewLevel().getId()),
						event -> gdClient.searchUser("" + event.getNewLevel().getCreatorID()).map(GDUser::getAccountId),
						(event, old) -> gdLevelService
								.compactView(bot.localization(), event.getNewLevel(),
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
						event -> event.getTimelyLevel().getType() == TimelyType.DAILY ? dailiesChannelId : weekliesChannelId,
						event -> Optional.empty(),
						event -> event.getTimelyLevel().getLevel()
								.flatMap(level -> gdClient.searchUser("" + level.getCreatorID()))
								.map(GDUser::getAccountId),
						(event, old) -> {
							var isWeekly = event.getTimelyLevel().getType() == TimelyType.WEEKLY;
							var headerTitle = isWeekly ? "Weekly Demon" : "Daily Level";
							var headerLink = isWeekly ? "https://i.imgur.com/kcsP5SN.png"
									: "https://i.imgur.com/enpYuB8.png";
							return event.getTimelyLevel().getLevel()
									.flatMap(level -> gdLevelService.compactView(bot.localization(), level,
											headerTitle + " #" + event.getTimelyLevel().getId(), headerLink))
									.map(embed -> new MessageSpecTemplate(randomString(bot.localization()
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
						event -> modPromotionsChannelId,
						event -> Optional.empty(),
						event -> Mono.just(event.getUser().getAccountId()),
						(event, old) -> gdUserService
								.makeIconSet(bot.localization(), event.getUser())
								.flatMap(icons -> gdUserService.userProfileView(bot.localization(), null, event.getUser(),
										bot.localization().translate("GDStrings", "gdevproc_title_promoted"),
										"https://i.imgur.com/zY61GDD.png", icons))
								.map(msg -> new MessageSpecTemplate(randomString(bot.localization().translate("GDStrings", "gdevproc_public_mod")),
										msg.getEmbed())),
						event -> "gdevproc_dm_mod",
						false
				)),
				Map.entry(UserPromotedToElderEvent.class, new GDEventProperties<UserPromotedToElderEvent>(
						(tr, event) -> tr.translate("GDStrings", "gdevproc_mod_event_log",
								bold(event.getClass().getSimpleName()), bold(event.getUser().getName())),
						"gd_moderators",
						event -> modPromotionsChannelId,
						event -> Optional.empty(),
						event -> Mono.just(event.getUser().getAccountId()),
						(event, old) -> gdUserService
								.makeIconSet(bot.localization(), event.getUser())
								.flatMap(icons -> gdUserService.userProfileView(bot.localization(), null, event.getUser(),
										bot.localization().translate("GDStrings", "gdevproc_title_promoted"),
										"https://i.imgur.com/zY61GDD.png", icons))
								.map(msg -> new MessageSpecTemplate(randomString(bot.localization()
												.translate("GDStrings", "gdevproc_public_elder")),
										msg.getEmbed())),
						event -> "gdevproc_dm_elder",
						false
				)),
				Map.entry(UserDemotedFromModEvent.class, new GDEventProperties<UserDemotedFromModEvent>(
						(tr, event) -> tr.translate("GDStrings", "gdevproc_mod_event_log",
								bold(event.getClass().getSimpleName()), bold(event.getUser().getName())),
						"gd_moderators",
						event -> modDemotionsChannelId,
						event -> Optional.empty(),
						event -> Mono.just(event.getUser().getAccountId()),
						(event, old) -> gdUserService
								.makeIconSet(bot.localization(), event.getUser())
								.flatMap(icons -> gdUserService.userProfileView(bot.localization(), null, event.getUser(),
										bot.localization().translate("GDStrings", "gdevproc_title_demoted"),
										"https://i.imgur.com/X53HV7d.png", icons))
								.map(msg -> new MessageSpecTemplate(randomString(bot.localization()
												.translate("GDStrings", "gdevproc_public_unmod")),
										msg.getEmbed())),
						event -> "gdevproc_dm_unmod",
						false
				)),
				Map.entry(UserDemotedFromElderEvent.class, new GDEventProperties<UserDemotedFromElderEvent>(
						(tr, event) -> tr.translate("GDStrings", "gdevproc_mod_event_log",
								bold(event.getClass().getSimpleName()), bold(event.getUser().getName())),
						"gd_moderators",
						event -> modDemotionsChannelId,
						event -> Optional.empty(),
						event -> Mono.just(event.getUser().getAccountId()),
						(event, old) -> gdUserService
								.makeIconSet(bot.localization(), event.getUser())
								.flatMap(icons -> gdUserService.userProfileView(bot.localization(), null, event.getUser(),
										bot.localization().translate("GDStrings", "gdevproc_title_demoted"),
										"https://i.imgur.com/X53HV7d.png", icons))
								.map(msg -> new MessageSpecTemplate(randomString(bot.localization()
												.translate("GDStrings", "gdevproc_public_unelder")),
										msg.getEmbed())),
						event -> "gdevproc_dm_unelder",
						false
				))
		);
	}
	
	private static String randomString(String str) {
		var array = str.split("///");
		return array[new Random().nextInt(array.length)];
	}
}
