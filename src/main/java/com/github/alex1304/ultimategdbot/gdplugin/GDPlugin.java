package com.github.alex1304.ultimategdbot.gdplugin;

import java.time.Duration;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.function.Function;

import org.reactivestreams.Subscriber;

import com.github.alex1304.jdash.client.AuthenticatedGDClient;
import com.github.alex1304.jdash.client.GDClientBuilder;
import com.github.alex1304.jdash.entity.GDTimelyLevel.TimelyType;
import com.github.alex1304.jdash.exception.GDLoginFailedException;
import com.github.alex1304.jdash.exception.SpriteLoadException;
import com.github.alex1304.jdash.graphics.SpriteFactory;
import com.github.alex1304.jdash.util.GDUserIconSet;
import com.github.alex1304.jdash.util.Routes;
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
import com.github.alex1304.ultimategdbot.api.Bot;
import com.github.alex1304.ultimategdbot.api.Command;
import com.github.alex1304.ultimategdbot.api.Plugin;
import com.github.alex1304.ultimategdbot.api.guildsettings.GuildSettingsEntry;
import com.github.alex1304.ultimategdbot.api.utils.GuildSettingsValueConverter;
import com.github.alex1304.ultimategdbot.api.utils.PropertyParser;
import com.github.alex1304.ultimategdbot.gdplugin.gdevents.AwardedLevelAddedEventSubscriber;
import com.github.alex1304.ultimategdbot.gdplugin.gdevents.AwardedLevelRemovedEventSubscriber;
import com.github.alex1304.ultimategdbot.gdplugin.gdevents.AwardedLevelUpdatedEventSubscriber;
import com.github.alex1304.ultimategdbot.gdplugin.gdevents.TimelyLevelChangedEventSubscriber;
import com.github.alex1304.ultimategdbot.gdplugin.gdevents.UserDemotedFromElderEvent;
import com.github.alex1304.ultimategdbot.gdplugin.gdevents.UserDemotedFromElderEventSubscriber;
import com.github.alex1304.ultimategdbot.gdplugin.gdevents.UserDemotedFromModEvent;
import com.github.alex1304.ultimategdbot.gdplugin.gdevents.UserDemotedFromModEventSubscriber;
import com.github.alex1304.ultimategdbot.gdplugin.gdevents.UserEvent;
import com.github.alex1304.ultimategdbot.gdplugin.gdevents.UserPromotedToElderEvent;
import com.github.alex1304.ultimategdbot.gdplugin.gdevents.UserPromotedToElderEventSubscriber;
import com.github.alex1304.ultimategdbot.gdplugin.gdevents.UserPromotedToModEvent;
import com.github.alex1304.ultimategdbot.gdplugin.gdevents.UserPromotedToModEventSubscriber;

import discord4j.core.object.entity.Message;

public class GDPlugin implements Plugin {
	
	private Bot bot;
	private AuthenticatedGDClient gdClient;
	private SpriteFactory spriteFactory;
	private Map<GDUserIconSet, String[]> iconsCache;
	private GDEventDispatcher gdEventDispatcher;
	private GDEventScannerLoop scannerLoop;
	private Map<Long, List<Message>> broadcastedLevels;
	private int eventFluxBufferSize;

	@Override
	public void setup(Bot bot, PropertyParser parser) {
		this.bot = bot;
		var username = parser.parseAsString("gdplugin.username");
		var password = parser.parseAsString("gdplugin.password");
		var host = parser.parseAsStringOrDefault("gdplugin.host", Routes.BASE_URL);
		var cacheTtl = parser.parseAsLongOrDefault("gdplugin.cache_ttl", GDClientBuilder.DEFAULT_CACHE_TTL);
		var maxConnections = parser.parseAsIntOrDefault("gdplugin.max_connections", GDClientBuilder.DEFAULT_MAX_CONNECTIONS);
		var scannerLoopInterval = Duration.ofSeconds(parser.parseAsIntOrDefault("gdplugin.scanner_loop_interval", 10));
		var eventFluxBufferSize = parser.parseAsIntOrDefault("gdplugin.event_flux_buffer_size", 20);
		try {
			this.gdClient = GDClientBuilder.create()
					.withHost(host)
					.withCacheTtl(cacheTtl)
					.withMaxConnections(maxConnections)
					.buildAuthenticated(username, password);
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
		this.broadcastedLevels = new ConcurrentHashMap<>();
		this.eventFluxBufferSize = eventFluxBufferSize;
		initGDEventSubscribers();
	}

	private Collection<? extends GDEventScanner> initScanners() {
		return Set.of(new AwardedSectionScanner(), new DailyLevelScanner(), new WeeklyDemonScanner());
	}

	private void initGDEventSubscribers() {
		subscribeToGDEvent(AwardedLevelAddedEvent.class, event -> "**Awarded Level Added** for level " + GDUtils.levelToString(event.getAddedLevel()),
				new AwardedLevelAddedEventSubscriber(bot, broadcastedLevels));
		subscribeToGDEvent(AwardedLevelRemovedEvent.class, event -> "**Awarded Level Removed** for level " + GDUtils.levelToString(event.getRemovedLevel()),
				new AwardedLevelRemovedEventSubscriber(bot, broadcastedLevels));
		subscribeToGDEvent(AwardedLevelUpdatedEvent.class, event -> "**Awarded Level Updated** for level " + GDUtils.levelToString(event.getNewLevel()),
				new AwardedLevelUpdatedEventSubscriber(bot, broadcastedLevels));
		subscribeToGDEvent(TimelyLevelChangedEvent.class, event -> "**" + (event.getTimelyLevel().getType() == TimelyType.WEEKLY ? "Weekly Demon Changed"
						: "Daily Level Changed") + "** for " + event.getTimelyLevel().getType().toString() + " #" + event.getTimelyLevel().getId(),
				new TimelyLevelChangedEventSubscriber(bot, broadcastedLevels));
		// GD mods
		BiFunction<UserEvent, String, String> logTextMod = (event, name) -> "**" + name + "** for user **"
				+ event.getUser().getName() + "** (" + event.getUser().getAccountId() + ")";
		subscribeToGDEvent(UserPromotedToModEvent.class, event -> logTextMod.apply(event, "User Promoted To Mod"),
				new UserPromotedToModEventSubscriber(bot, broadcastedLevels, spriteFactory, iconsCache));
		subscribeToGDEvent(UserPromotedToElderEvent.class, event -> logTextMod.apply(event, "User Promoted To Elder"),
				new UserPromotedToElderEventSubscriber(bot, broadcastedLevels, spriteFactory, iconsCache));
		subscribeToGDEvent(UserDemotedFromModEvent.class, event -> logTextMod.apply(event, "User Demoted From Mod"),
				new UserDemotedFromModEventSubscriber(bot, broadcastedLevels, spriteFactory, iconsCache));
		subscribeToGDEvent(UserDemotedFromElderEvent.class, event -> logTextMod.apply(event, "User Demoted From Elder"),
				new UserDemotedFromElderEventSubscriber(bot, broadcastedLevels, spriteFactory, iconsCache));
	}
	
	private <E extends GDEvent> void subscribeToGDEvent(Class<E> clazz, Function<E, String> logText, Subscriber<E> instance) {
		gdEventDispatcher.on(clazz)
				.onBackpressureBuffer(eventFluxBufferSize, event -> bot.log(":warning: Due to backpressure, the GD event dispatcher "
						+ "has rejected event " + logText.apply(event) + "\n"
						+ "You will need to push it through manually via the `" + bot.getDefaultPrefix() + "gdevents dispatch` command.").subscribe())
				.subscribe(instance);
	}
	
	@Override
	public void onBotReady() {
		scannerLoop.start();
	}

	@Override
	public Set<Command> getProvidedCommands() {
		return Set.of(new ProfileCommand(gdClient, spriteFactory, iconsCache), new LevelCommand(gdClient, true), new LevelCommand(gdClient, false),
				new TimelyCommand(gdClient, true), new TimelyCommand(gdClient, false), new AccountCommand(gdClient), new LeaderboardCommand(gdClient),
				new GDEventsCommand(gdClient, gdEventDispatcher, scannerLoop, broadcastedLevels), new CheckModCommand(gdClient, gdEventDispatcher),
				new ModListCommand());
	}

	@Override
	public String getName() {
		return "Geometry Dash";
	}

	@Override
	public Set<String> getDatabaseMappingResources() {
		return Set.of("/GDLinkedUsers.hbm.xml", "/GDSubscribedGuilds.hbm.xml", "/GDModList.hbm.xml");
	}

	@Override
	public Map<String, GuildSettingsEntry<?, ?>> getGuildConfigurationEntries() {
		var map = new LinkedHashMap<String, GuildSettingsEntry<?, ?>>();
		var valueConverter = new GuildSettingsValueConverter(bot);
		map.put("channel_awarded_levels", new GuildSettingsEntry<>(
				GDSubscribedGuilds.class,
				GDSubscribedGuilds::getChannelAwardedLevelsId,
				GDSubscribedGuilds::setChannelAwardedLevelsId,
				valueConverter::toTextChannelId,
				valueConverter::fromChannelId
		));
		map.put("channel_timely_levels", new GuildSettingsEntry<>(
				GDSubscribedGuilds.class,
				GDSubscribedGuilds::getChannelTimelyLevelsId,
				GDSubscribedGuilds::setChannelTimelyLevelsId,
				valueConverter::toTextChannelId,
				valueConverter::fromChannelId
		));
		map.put("channel_gd_moderators", new GuildSettingsEntry<>(
				GDSubscribedGuilds.class,
				GDSubscribedGuilds::getChannelGdModeratorsId,
				GDSubscribedGuilds::setChannelGdModeratorsId,
				valueConverter::toTextChannelId,
				valueConverter::fromChannelId
		));
		map.put("channel_changelog", new GuildSettingsEntry<>(
				GDSubscribedGuilds.class,
				GDSubscribedGuilds::getChannelChangelogId,
				GDSubscribedGuilds::setChannelChangelogId,
				valueConverter::toTextChannelId,
				valueConverter::fromChannelId
		));
		map.put("role_awarded_levels", new GuildSettingsEntry<>(
				GDSubscribedGuilds.class,
				GDSubscribedGuilds::getRoleAwardedLevelsId,
				GDSubscribedGuilds::setRoleAwardedLevelsId,
				valueConverter::toRoleId,
				valueConverter::fromRoleId
		));
		map.put("role_timely_levels", new GuildSettingsEntry<>(
				GDSubscribedGuilds.class,
				GDSubscribedGuilds::getRoleTimelyLevelsId,
				GDSubscribedGuilds::setRoleTimelyLevelsId,
				valueConverter::toRoleId,
				valueConverter::fromRoleId
		));
		map.put("role_gd_moderators", new GuildSettingsEntry<>(
				GDSubscribedGuilds.class,
				GDSubscribedGuilds::getRoleGdModeratorsId,
				GDSubscribedGuilds::setRoleGdModeratorsId,
				valueConverter::toRoleId,
				valueConverter::fromRoleId
		));
		return map;
	}
}
