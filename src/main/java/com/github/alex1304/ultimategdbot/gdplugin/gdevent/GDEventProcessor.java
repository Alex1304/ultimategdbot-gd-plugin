package com.github.alex1304.ultimategdbot.gdplugin.gdevent;

import static com.github.alex1304.ultimategdbot.api.util.Markdown.bold;
import static com.github.alex1304.ultimategdbot.gdplugin.gdevent.GDEventBroadcastStrategy.CREATING;
import static com.github.alex1304.ultimategdbot.gdplugin.gdevent.GDEventBroadcastStrategy.EDITING;
import static com.github.alex1304.ultimategdbot.gdplugin.util.GDLevels.compactView;
import static reactor.function.TupleUtils.function;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.function.Function;

import com.github.alex1304.jdash.entity.GDTimelyLevel.TimelyType;
import com.github.alex1304.jdash.entity.GDUser;
import com.github.alex1304.jdashevents.event.AwardedLevelAddedEvent;
import com.github.alex1304.jdashevents.event.AwardedLevelRemovedEvent;
import com.github.alex1304.jdashevents.event.AwardedLevelUpdatedEvent;
import com.github.alex1304.jdashevents.event.GDEvent;
import com.github.alex1304.jdashevents.event.TimelyLevelChangedEvent;
import com.github.alex1304.ultimategdbot.api.Bot;
import com.github.alex1304.ultimategdbot.api.Translator;
import com.github.alex1304.ultimategdbot.api.database.DatabaseService;
import com.github.alex1304.ultimategdbot.api.emoji.EmojiService;
import com.github.alex1304.ultimategdbot.api.localization.LocalizationService;
import com.github.alex1304.ultimategdbot.api.util.DurationUtils;
import com.github.alex1304.ultimategdbot.api.util.Markdown;
import com.github.alex1304.ultimategdbot.api.util.MessageSpecTemplate;
import com.github.alex1304.ultimategdbot.gdplugin.GDService;
import com.github.alex1304.ultimategdbot.gdplugin.database.GDAwardedLevelDao;
import com.github.alex1304.ultimategdbot.gdplugin.database.GDEventConfigData;
import com.github.alex1304.ultimategdbot.gdplugin.database.ImmutableGDAwardedLevelData;
import com.github.alex1304.ultimategdbot.gdplugin.util.GDLevels;
import com.github.alex1304.ultimategdbot.gdplugin.util.GDUsers;

import discord4j.common.util.Snowflake;
import reactor.core.publisher.Mono;
import reactor.util.Logger;
import reactor.util.Loggers;

public class GDEventProcessor {
	
	private static final Logger LOGGER = Loggers.getLogger(GDEventProcessor.class);
	
	private final Bot bot;
	private final Map<Class<? extends GDEvent>, GDEventProperties<? extends GDEvent>> eventProperties;
	
	public GDEventProcessor(Bot bot) {
		this.bot = bot;
		this.eventProperties = initEventProps();
	}

	public Mono<Void> process(GDEvent event) {
		var eventProps = eventProperties.get(event.getClass());
		if (eventProps == null) {
			LOGGER.warn("Unrecognized event type: {}", event.getClass().getName());
			return Mono.empty();
		}
		var defaultTr = bot;
		var logText = eventProps.logText(defaultTr, event);
		var emojiService = bot.service(EmojiService.class);
		return Mono.zip(emojiService.emoji("info"), emojiService.emoji("success"), emojiService.emoji("failed"))
				.flatMap(function((info, success, failed) -> log(bot, info + ' ' + defaultTr.translate("GDStrings", "gdevproc_event_fired") + ' ' + logText)
						.then(eventProps.broadcastStrategy().broadcast(bot, event, eventProps, bot.service(GDService.class).getBroadcastResultCache())
								.elapsed()
								.flatMap(function((time, count) -> log(bot, success + ' ' + defaultTr.translate("GDStrings", "gdevproc_success") + ' ' + logText + "\n"
										+ defaultTr.translate("GDStrings", (eventProps.broadcastStrategy() == CREATING
												? "gdevproc_messages_sent"
												: "gdevproc_messages_edited")) + ' ' + bold("" + count) + "\n"
										+ defaultTr.translate("GDStrings", "gdevproc_exec_time") + ' ' + bold(DurationUtils.format(Duration.ofMillis(time))))))
								.onErrorResume(e -> bot.log(failed + ' ' + defaultTr.translate("GDStrings", "gdevproc_error")
												+ ' ' + logText + ": " + Markdown.code(e.getClass().getName()))
										.and(Mono.fromRunnable(() -> LOGGER.error("An error occured while dispatching GD event", e)))))));
	}
	
	private static Mono<Void> log(Bot bot, String text) {
		return Mono.when(bot.log(text).onErrorResume(e -> Mono.empty()), Mono.fromRunnable(() -> LOGGER.info(text)));
	}
	
	private Map<Class<? extends GDEvent>, GDEventProperties<? extends GDEvent>> initEventProps() {
		return Map.ofEntries(
				Map.entry(AwardedLevelAddedEvent.class, new GDEventProperties<AwardedLevelAddedEvent>(
						(tr, event) -> tr.translate("GDStrings", "gdevproc_awarded_event_log",
								bold(event.getClass().getSimpleName()), GDLevels.toString(event.getAddedLevel())),
						"awarded_levels",
						GDEventConfigData::channelAwardedLevelsId,
						GDEventConfigData::roleAwardedLevelsId,
						event -> Optional.of(event.getAddedLevel().getId()),
						event -> bot.service(DatabaseService.class).useExtension(GDAwardedLevelDao.class, dao -> dao.insertOrUpdate(
										ImmutableGDAwardedLevelData.builder()
												.levelId(event.getAddedLevel().getId())
												.insertDate(Instant.now())
												.downloads(event.getAddedLevel().getDownloads())
												.likes(event.getAddedLevel().getLikes())
												.build()))
								.onErrorResume(e -> Mono.fromRunnable(() -> LOGGER.error("Error when saving new awarded level", e)))
								.then(bot.service(GDService.class).getGdClient().searchUser("" + event.getAddedLevel().getCreatorID()).map(GDUser::getAccountId)),
						(event, eventCfg, old) -> GDLevels
								.compactView(adaptTranslator(bot, eventCfg), bot, event.getAddedLevel(),
										adaptTranslator(bot, eventCfg).translate("GDStrings", "gdevproc_title_rate"),
										"https://i.imgur.com/asoMj1W.png")
								.map(embed -> new MessageSpecTemplate(
										mentionRoleIfSet(eventCfg, GDEventConfigData::roleAwardedLevelsId)
												+ randomString(adaptTranslator(bot, eventCfg)
														.translate("GDStrings", "gdevproc_public_rate")),
										embed)),
						event -> "gdevproc_dm_rate",
						CREATING
				)),
				Map.entry(AwardedLevelRemovedEvent.class, new GDEventProperties<AwardedLevelRemovedEvent>(
						(tr, event) -> tr.translate("GDStrings", "gdevproc_awarded_event_log",
								bold(event.getClass().getSimpleName()), GDLevels.toString(event.getRemovedLevel())),
						"awarded_levels",
						GDEventConfigData::channelAwardedLevelsId,
						GDEventConfigData::roleAwardedLevelsId,
						event -> Optional.empty(),
						event -> bot.service(GDService.class).getGdClient().searchUser("" + event.getRemovedLevel().getCreatorID()).map(GDUser::getAccountId),
						(event, eventCfg, old) -> GDLevels
								.compactView(adaptTranslator(bot, eventCfg), bot, event.getRemovedLevel(), 
										adaptTranslator(bot, eventCfg).translate("GDStrings", "gdevproc_title_unrate"),
										"https://i.imgur.com/fPECXUz.png")
								.map(embed -> new MessageSpecTemplate(
										mentionRoleIfSet(eventCfg, GDEventConfigData::roleAwardedLevelsId)
										+ randomString(adaptTranslator(bot, eventCfg)
												.translate("GDStrings", "gdevproc_public_unrate")),
										embed)),
						event -> "gdevproc_dm_unrate",
						CREATING
				)),
				Map.entry(AwardedLevelUpdatedEvent.class, new GDEventProperties<AwardedLevelUpdatedEvent>(
						(tr, event) -> tr.translate("GDStrings", "gdevproc_awarded_event_log",
								bold(event.getClass().getSimpleName()), GDLevels.toString(event.getNewLevel())),
						"awarded_levels",
						GDEventConfigData::channelAwardedLevelsId,
						GDEventConfigData::roleAwardedLevelsId,
						event -> Optional.of(event.getNewLevel().getId()),
						event -> bot.service(GDService.class).getGdClient().searchUser("" + event.getNewLevel().getCreatorID()).map(GDUser::getAccountId),
						(event, eventCfg, old) -> GDLevels
								.compactView(adaptTranslator(bot, eventCfg), bot, event.getNewLevel(),
										old.getEmbeds().get(0).getAuthor().get().getName(),
										old.getEmbeds().get(0).getAuthor().get().getIconUrl())
								.map(embed -> new MessageSpecTemplate(old.getContent(), embed)),
						event -> { throw new UnsupportedOperationException(); },
						EDITING
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
								.flatMap(level -> bot.service(GDService.class).getGdClient().searchUser("" + level.getCreatorID()))
								.map(GDUser::getAccountId),
						(event, eventCfg, old) -> {
							var isWeekly = event.getTimelyLevel().getType() == TimelyType.WEEKLY;
							var headerTitle = isWeekly ? "Weekly Demon" : "Daily Level";
							var headerLink = isWeekly ? "https://i.imgur.com/kcsP5SN.png"
									: "https://i.imgur.com/enpYuB8.png";
							return event.getTimelyLevel().getLevel()
									.flatMap(level -> compactView(adaptTranslator(bot, eventCfg), bot, level,
											headerTitle + " #" + event.getTimelyLevel().getId(), headerLink))
									.map(embed -> new MessageSpecTemplate(
											mentionRoleIfSet(eventCfg, GDEventConfigData::roleTimelyLevelsId)
											+ randomString(adaptTranslator(bot, eventCfg)
													.translate("GDStrings", isWeekly ? "gdevproc_public_weekly"
															: "gdevproc_public_daily")),
											embed));
						},
						event -> event.getTimelyLevel().getType() == TimelyType.DAILY
								? "gdevproc_dm_daily" : "gdevproc_dm_weekly",
						CREATING
				)),
				Map.entry(UserPromotedToModEvent.class, new GDEventProperties<UserPromotedToModEvent>(
						(tr, event) -> tr.translate("GDStrings", "gdevproc_mod_event_log",
								bold(event.getClass().getSimpleName()), bold(event.getUser().getName())),
						"gd_moderators",
						GDEventConfigData::channelGdModeratorsId,
						GDEventConfigData::roleGdModeratorsId,
						event -> Optional.empty(),
						event -> Mono.just(event.getUser().getAccountId()),
						(event, eventCfg, old) -> GDUsers
								.makeIconSet(adaptTranslator(bot, eventCfg), bot, event.getUser(), bot.service(GDService.class).getSpriteFactory(),
										bot.service(GDService.class).getIconsCache(), bot.service(GDService.class).getIconChannelId())
								.flatMap(icons -> GDUsers.userProfileView(adaptTranslator(bot, eventCfg), bot, null, event.getUser(),
										adaptTranslator(bot, eventCfg).translate("GDStrings", "gdevproc_title_promoted"),
										"https://i.imgur.com/zY61GDD.png", icons))
								.map(msg -> new MessageSpecTemplate(
										mentionRoleIfSet(eventCfg, GDEventConfigData::roleGdModeratorsId)
										+ randomString(adaptTranslator(bot, eventCfg)
												.translate("GDStrings", "gdevproc_public_mod")),
										msg.getEmbed())),
						event -> "gdevproc_dm_mod",
						CREATING
				)),
				Map.entry(UserPromotedToElderEvent.class, new GDEventProperties<UserPromotedToElderEvent>(
						(tr, event) -> tr.translate("GDStrings", "gdevproc_mod_event_log",
								bold(event.getClass().getSimpleName()), bold(event.getUser().getName())),
						"gd_moderators",
						GDEventConfigData::channelGdModeratorsId,
						GDEventConfigData::roleGdModeratorsId,
						event -> Optional.empty(),
						event -> Mono.just(event.getUser().getAccountId()),
						(event, eventCfg, old) -> GDUsers
								.makeIconSet(adaptTranslator(bot, eventCfg), bot, event.getUser(), bot.service(GDService.class).getSpriteFactory(),
										bot.service(GDService.class).getIconsCache(), bot.service(GDService.class).getIconChannelId())
								.flatMap(icons -> GDUsers.userProfileView(adaptTranslator(bot, eventCfg), bot, null, event.getUser(),
										adaptTranslator(bot, eventCfg).translate("GDStrings", "gdevproc_title_promoted"),
										"https://i.imgur.com/zY61GDD.png", icons))
								.map(msg -> new MessageSpecTemplate(
										mentionRoleIfSet(eventCfg, GDEventConfigData::roleGdModeratorsId)
										+ randomString(adaptTranslator(bot, eventCfg)
												.translate("GDStrings", "gdevproc_public_elder")),
										msg.getEmbed())),
						event -> "gdevproc_dm_elder",
						CREATING
				)),
				Map.entry(UserDemotedFromModEvent.class, new GDEventProperties<UserDemotedFromModEvent>(
						(tr, event) -> tr.translate("GDStrings", "gdevproc_mod_event_log",
								bold(event.getClass().getSimpleName()), bold(event.getUser().getName())),
						"gd_moderators",
						GDEventConfigData::channelGdModeratorsId,
						GDEventConfigData::roleGdModeratorsId,
						event -> Optional.empty(),
						event -> Mono.just(event.getUser().getAccountId()),
						(event, eventCfg, old) -> GDUsers
								.makeIconSet(adaptTranslator(bot, eventCfg), bot, event.getUser(), bot.service(GDService.class).getSpriteFactory(),
										bot.service(GDService.class).getIconsCache(), bot.service(GDService.class).getIconChannelId())
								.flatMap(icons -> GDUsers.userProfileView(adaptTranslator(bot, eventCfg), bot, null, event.getUser(),
										adaptTranslator(bot, eventCfg).translate("GDStrings", "gdevproc_title_demoted"),
										"https://i.imgur.com/X53HV7d.png", icons))
								.map(msg -> new MessageSpecTemplate(
										mentionRoleIfSet(eventCfg, GDEventConfigData::roleGdModeratorsId)
										+ randomString(adaptTranslator(bot, eventCfg)
												.translate("GDStrings", "gdevproc_public_unmod")),
										msg.getEmbed())),
						event -> "gdevproc_dm_unmod",
						CREATING
				)),
				Map.entry(UserDemotedFromElderEvent.class, new GDEventProperties<UserDemotedFromElderEvent>(
						(tr, event) -> tr.translate("GDStrings", "gdevproc_mod_event_log",
								bold(event.getClass().getSimpleName()), bold(event.getUser().getName())),
						"gd_moderators",
						GDEventConfigData::channelGdModeratorsId,
						GDEventConfigData::roleGdModeratorsId,
						event -> Optional.empty(),
						event -> Mono.just(event.getUser().getAccountId()),
						(event, eventCfg, old) -> GDUsers
								.makeIconSet(adaptTranslator(bot, eventCfg), bot, event.getUser(), bot.service(GDService.class).getSpriteFactory(),
										bot.service(GDService.class).getIconsCache(), bot.service(GDService.class).getIconChannelId())
								.flatMap(icons -> GDUsers.userProfileView(adaptTranslator(bot, eventCfg), bot, null, event.getUser(),
										adaptTranslator(bot, eventCfg).translate("GDStrings", "gdevproc_title_demoted"),
										"https://i.imgur.com/X53HV7d.png", icons))
								.map(msg -> new MessageSpecTemplate(
										mentionRoleIfSet(eventCfg, GDEventConfigData::roleGdModeratorsId)
										+ randomString(adaptTranslator(bot, eventCfg)
												.translate("GDStrings", "gdevproc_public_unelder")),
										msg.getEmbed())),
						event -> "gdevproc_dm_unelder",
						CREATING
				))
		);
	}
	
	static Translator adaptTranslator(Bot bot, GDEventConfigData data) {
		if (data == null || !bot.hasService(LocalizationService.class)) {
			return bot;
		}
		return Translator.to(bot.service(LocalizationService.class).getLocaleForGuild(data.guildId().asLong()));
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
