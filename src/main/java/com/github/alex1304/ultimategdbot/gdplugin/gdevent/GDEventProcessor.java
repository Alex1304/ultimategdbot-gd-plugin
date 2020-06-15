package com.github.alex1304.ultimategdbot.gdplugin.gdevent;

import static com.github.alex1304.ultimategdbot.api.util.Markdown.bold;
import static com.github.alex1304.ultimategdbot.gdplugin.gdevent.GDEventBroadcastStrategy.CREATING;
import static com.github.alex1304.ultimategdbot.gdplugin.gdevent.GDEventBroadcastStrategy.EDITING;
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
import com.github.alex1304.ultimategdbot.api.database.DatabaseService;
import com.github.alex1304.ultimategdbot.api.emoji.EmojiService;
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
	private static final String[] AWARDED_LEVEL_ADDED_MESSAGES = new String[] {
			"A new level has just been rated on Geometry Dash!!!",
			"RobTop just assigned a star value to this level!",
			"This level can now give you star and orb rewards. Go beat it now!",
			"I've been told that another generic and bad level got rated... Oh well, I might be wrong, go see by yourself!",
			"I challenge you to beat this level. RobTop just rated it!",
			"This level is 2hard5me. But RobTop's rate button has spoken and it can now give you some cool rewards!",
			"RobTop accidentally hit the wrong key and rated this level instead of turning on his coffee machine. But it's actually a good level. Go check it out!",
			"Hey look, a new level got rated OwO Do you think you can beat it?",
			"Roses are red. Violets are blue. This newly awarded level is waiting for you."
	};
	private static final String[] AWARDED_LEVEL_REMOVED_MESSAGES = new String[] {
			"This level just got un-rated from Geometry Dash...",
			"Oh snap! RobTop decided to un-rate this level!",
			"RobTop took away stars from this level. FeelsBadMan",
			"Sad news. This level is no longer rated...",
			"NOOOOOOO I liked this level... No more stars :'("
	};
	
	private final Bot bot;
	private final GDService gdService;
	private final Map<Class<? extends GDEvent>, GDEventProperties<? extends GDEvent>> eventProperties;
	
	public GDEventProcessor(Bot bot, GDService gdService) {
		this.bot = bot;
		this.gdService = gdService;
		this.eventProperties = initEventProps();
	}

	public Mono<Void> process(GDEvent event) {
		var eventProps = eventProperties.get(event.getClass());
		if (eventProps == null) {
			LOGGER.warn("Unrecognized event type: {}", event.getClass().getName());
			return Mono.empty();
		}
		var logText = eventProps.logText(event);
		return Mono.zip(bot.service(EmojiService.class).emoji("info"), bot.service(EmojiService.class).emoji("success"), bot.service(EmojiService.class).emoji("failed"))
				.flatMap(function((info, success, failed) -> log(bot, info + " GD event fired: " + logText)
						.then(eventProps.broadcastStrategy().broadcast(bot, event, eventProps, gdService.getBroadcastResultCache())
								.elapsed()
								.flatMap(function((time, count) -> log(bot, success + " Successfully processed event " + logText + "\n"
										+ "Messages " + (eventProps.broadcastStrategy() == CREATING ? "sent" : "edited") + ": " + bold("" + count) + "\n"
										+ "Execution time: " + bold(DurationUtils.format(Duration.ofMillis(time))))))
								.onErrorResume(e -> bot.log(failed + " An error occured while dispatching event " + logText + ": " + Markdown.code(e.getClass().getName()))
										.and(Mono.fromRunnable(() -> LOGGER.error("An error occured while dispatching GD event", e)))))));
	}
	
	private static Mono<Void> log(Bot bot, String text) {
		return Mono.when(bot.log(text).onErrorResume(e -> Mono.empty()), Mono.fromRunnable(() -> LOGGER.info(text)));
	}
	
	private Map<Class<? extends GDEvent>, GDEventProperties<? extends GDEvent>> initEventProps() {
		return Map.ofEntries(
				Map.entry(AwardedLevelAddedEvent.class, new GDEventProperties<AwardedLevelAddedEvent>(
						event -> bold("Awarded Level Added") + " for level " + GDLevels.toString(event.getAddedLevel()),
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
								.then(gdService.getGdClient().searchUser("" + event.getAddedLevel().getCreatorID()).map(GDUser::getAccountId)),
						(event, eventCfg, old) -> GDLevels
								.compactView(bot, event.getAddedLevel(), "New rated level!",
										"https://i.imgur.com/asoMj1W.png")
								.map(embed -> new MessageSpecTemplate(
										mentionRoleIfSet(eventCfg, GDEventConfigData::roleAwardedLevelsId)
												+ randomFromArray(AWARDED_LEVEL_ADDED_MESSAGES),
										embed)),
						event -> "Congratulations for getting your level rated!",
						CREATING
				)),
				Map.entry(AwardedLevelRemovedEvent.class, new GDEventProperties<AwardedLevelRemovedEvent>(
						event -> bold("Awarded Level Removed") + " for level " + GDLevels.toString(event.getRemovedLevel()),
						"awarded_levels",
						GDEventConfigData::channelAwardedLevelsId,
						GDEventConfigData::roleAwardedLevelsId,
						event -> Optional.empty(),
						event -> gdService.getGdClient().searchUser("" + event.getRemovedLevel().getCreatorID()).map(GDUser::getAccountId),
						(event, eventCfg, old) -> GDLevels
								.compactView(bot, event.getRemovedLevel(), "Level un-rated...",
										"https://i.imgur.com/fPECXUz.png")
								.map(embed -> new MessageSpecTemplate(
										mentionRoleIfSet(eventCfg, GDEventConfigData::roleAwardedLevelsId)
												+ randomFromArray(AWARDED_LEVEL_REMOVED_MESSAGES),
										embed)),
						event -> "Your level has been un-rated...",
						CREATING
				)),
				Map.entry(AwardedLevelUpdatedEvent.class, new GDEventProperties<AwardedLevelUpdatedEvent>(
						event -> bold("Awarded Level Updated") + " for level " + GDLevels.toString(event.getNewLevel()),
						"awarded_levels",
						GDEventConfigData::channelAwardedLevelsId,
						GDEventConfigData::roleAwardedLevelsId,
						event -> Optional.of(event.getNewLevel().getId()),
						event -> gdService.getGdClient().searchUser("" + event.getNewLevel().getCreatorID()).map(GDUser::getAccountId),
						(event, eventCfg, old) -> GDLevels
								.compactView(bot, event.getNewLevel(),
										old.getEmbeds().get(0).getAuthor().get().getName(),
										old.getEmbeds().get(0).getAuthor().get().getIconUrl())
								.map(embed -> new MessageSpecTemplate(old.getContent(), embed)),
						event -> { throw new UnsupportedOperationException(); },
						EDITING
				)),
				Map.entry(TimelyLevelChangedEvent.class, new GDEventProperties<TimelyLevelChangedEvent>(
						event -> bold("Timely Level Changed") + " for "
								+ event.getTimelyLevel().getType() + " #" + event.getTimelyLevel().getId(),
						"timely_levels",
						GDEventConfigData::channelTimelyLevelsId,
						GDEventConfigData::roleTimelyLevelsId,
						event -> Optional.empty(),
						event -> event.getTimelyLevel().getLevel()
								.flatMap(level -> gdService.getGdClient().searchUser("" + level.getCreatorID()))
								.map(GDUser::getAccountId),
						(event, eventCfg, old) -> {
							var isWeekly = event.getTimelyLevel().getType() == TimelyType.WEEKLY;
							var headerTitle = isWeekly ? "Weekly Demon" : "Daily Level";
							var headerLink = isWeekly ? "https://i.imgur.com/kcsP5SN.png"
									: "https://i.imgur.com/enpYuB8.png";
							return event.getTimelyLevel().getLevel()
									.flatMap(level -> GDLevels.compactView(bot, level,
											headerTitle + " #" + event.getTimelyLevel().getId(), headerLink))
									.map(embed -> new MessageSpecTemplate(
											mentionRoleIfSet(eventCfg, GDEventConfigData::roleTimelyLevelsId)
													+ "There is a new " + headerTitle + " on Geometry Dash!!!",
											embed));
						},
						event -> "Congratulations for getting the "
								+ (event.getTimelyLevel().getType() == TimelyType.WEEKLY ? "Weekly Demon" : "Daily Level") + "!",
						CREATING
				)),
				Map.entry(UserPromotedToModEvent.class, new GDEventProperties<UserPromotedToModEvent>(
						event -> bold("User Promoted To Mod") + " for user " + bold(event.getUser().getName()),
						"gd_moderators",
						GDEventConfigData::channelGdModeratorsId,
						GDEventConfigData::roleGdModeratorsId,
						event -> Optional.empty(),
						event -> Mono.just(event.getUser().getAccountId()),
						(event, eventCfg, old) -> GDUsers
								.makeIconSet(bot, event.getUser(), gdService.getSpriteFactory(),
										gdService.getIconsCache(), gdService.getIconChannelId())
								.flatMap(icons -> GDUsers.userProfileView(bot, Optional.empty(), event.getUser(),
										"User promoted!", "https://i.imgur.com/zY61GDD.png", icons))
								.map(msg -> new MessageSpecTemplate(
										mentionRoleIfSet(eventCfg, GDEventConfigData::roleGdModeratorsId)
												+ "A user has been promoted to Geometry Dash moderator!",
										msg.getEmbed())),
						event -> "Congratulations! You have been promoted to Geometry Dash moderator!",
						CREATING
				)),
				Map.entry(UserPromotedToElderEvent.class, new GDEventProperties<UserPromotedToElderEvent>(
						event -> bold("User Promoted To Elder") + " for user " + bold(event.getUser().getName()),
						"gd_moderators",
						GDEventConfigData::channelGdModeratorsId,
						GDEventConfigData::roleGdModeratorsId,
						event -> Optional.empty(),
						event -> Mono.just(event.getUser().getAccountId()),
						(event, eventCfg, old) -> GDUsers
								.makeIconSet(bot, event.getUser(), gdService.getSpriteFactory(),
										gdService.getIconsCache(), gdService.getIconChannelId())
								.flatMap(icons -> GDUsers.userProfileView(bot, Optional.empty(), event.getUser(),
										"User promoted!", "https://i.imgur.com/zY61GDD.png", icons))
								.map(msg -> new MessageSpecTemplate(
										mentionRoleIfSet(eventCfg, GDEventConfigData::roleGdModeratorsId)
												+ "A user has been promoted to Geometry Dash Elder moderator!",
										msg.getEmbed())),
						event -> "Congratulations! You have been promoted to Geometry Dash Elder moderator!",
						CREATING
				)),
				Map.entry(UserDemotedFromModEvent.class, new GDEventProperties<UserDemotedFromModEvent>(
						event -> bold("User Demoted From Mod") + " for user " + bold(event.getUser().getName()),
						"gd_moderators",
						GDEventConfigData::channelGdModeratorsId,
						GDEventConfigData::roleGdModeratorsId,
						event -> Optional.empty(),
						event -> Mono.just(event.getUser().getAccountId()),
						(event, eventCfg, old) -> GDUsers
								.makeIconSet(bot, event.getUser(), gdService.getSpriteFactory(),
										gdService.getIconsCache(), gdService.getIconChannelId())
								.flatMap(icons -> GDUsers.userProfileView(bot, Optional.empty(), event.getUser(),
										"User demoted...", "https://i.imgur.com/X53HV7d.png", icons))
								.map(msg -> new MessageSpecTemplate(
										mentionRoleIfSet(eventCfg, GDEventConfigData::roleGdModeratorsId)
												+ "A user has been demoted from Geometry Dash moderator...",
										msg.getEmbed())),
						event -> "Oh snap! You have been demoted from Geometry Dash moderator...",
						CREATING
				)),
				Map.entry(UserDemotedFromElderEvent.class, new GDEventProperties<UserDemotedFromElderEvent>(
						event -> bold("User Demoted From Elder") + " for user " + bold(event.getUser().getName()),
						"gd_moderators",
						GDEventConfigData::channelGdModeratorsId,
						GDEventConfigData::roleGdModeratorsId,
						event -> Optional.empty(),
						event -> Mono.just(event.getUser().getAccountId()),
						(event, eventCfg, old) -> GDUsers
								.makeIconSet(bot, event.getUser(), gdService.getSpriteFactory(),
										gdService.getIconsCache(), gdService.getIconChannelId())
								.flatMap(icons -> GDUsers.userProfileView(bot, Optional.empty(), event.getUser(),
										"User demoted...", "https://i.imgur.com/X53HV7d.png", icons))
								.map(msg -> new MessageSpecTemplate(
										mentionRoleIfSet(eventCfg, GDEventConfigData::roleGdModeratorsId)
												+ "A user has been demoted from Geometry Dash Elder moderator...",
										msg.getEmbed())),
						event -> "Oh snap! You have been demoted from Geometry Dash Elder moderator...",
						CREATING
				))
		);
	}
	
	private static String mentionRoleIfSet(GDEventConfigData eventCfg, Function<GDEventConfigData, Optional<Snowflake>> getter) {
		if (eventCfg == null) {
			return "";
		}
		var roleIdOpt = getter.apply(eventCfg);
		return roleIdOpt.map(Snowflake::asLong).map(roleId -> "<@&" + roleId + "> ").orElse("");
	}
	
	private static String randomFromArray(String[] array) {
		return array[new Random().nextInt(array.length)];
	}
}
