package com.github.alex1304.ultimategdbot.gdplugin;

import java.time.Duration;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.github.alex1304.jdash.client.AuthenticatedGDClient;
import com.github.alex1304.jdash.client.GDClientBuilder;
import com.github.alex1304.jdash.exception.GDLoginFailedException;
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
import com.github.alex1304.ultimategdbot.api.Plugin;
import com.github.alex1304.ultimategdbot.api.database.GuildSettingsEntry;
import com.github.alex1304.ultimategdbot.api.utils.GuildSettingsValueConverter;
import com.github.alex1304.ultimategdbot.api.utils.PropertyParser;
import com.github.alex1304.ultimategdbot.gdplugin.gdevents.AwardedLevelAddedEventProcessor;
import com.github.alex1304.ultimategdbot.gdplugin.gdevents.AwardedLevelRemovedEventProcessor;
import com.github.alex1304.ultimategdbot.gdplugin.gdevents.AwardedLevelUpdatedEventProcessor;
import com.github.alex1304.ultimategdbot.gdplugin.gdevents.GDEventProcessor;
import com.github.alex1304.ultimategdbot.gdplugin.gdevents.GDEventSubscriber;
import com.github.alex1304.ultimategdbot.gdplugin.gdevents.TimelyLevelChangedEventProcessor;
import com.github.alex1304.ultimategdbot.gdplugin.gdevents.UserDemotedFromElderEventProcessor;
import com.github.alex1304.ultimategdbot.gdplugin.gdevents.UserDemotedFromModEventProcessor;
import com.github.alex1304.ultimategdbot.gdplugin.gdevents.UserPromotedToElderEventProcessor;
import com.github.alex1304.ultimategdbot.gdplugin.gdevents.UserPromotedToModEventProcessor;

import discord4j.core.object.entity.Message;
import reactor.core.publisher.Flux;

public class GDPlugin implements Plugin {
	
	private Bot bot;
	private AuthenticatedGDClient gdClient;
	private SpriteFactory spriteFactory;
	private Map<GDUserIconSet, String[]> iconsCache;
	private GDEventDispatcher gdEventDispatcher;
	private GDEventScannerLoop scannerLoop;
	private Map<Long, List<Message>> broadcastedLevels;
	private ChannelLoader channelLoader;
	private int eventFluxBufferSize;
	private GDEventSubscriber subscriber;

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
		var eventFluxBufferSize = parser.parseAsIntOrDefault("gdplugin.event_flux_buffer_size", 20);
		try {
			this.gdClient = GDClientBuilder.create()
					.withHost(host)
					.withCacheTtl(cacheTtl)
					.withMaxConnections(maxConnections)
					.withRequestTimeout(requestTimeout)
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
		this.broadcastedLevels = new LinkedHashMap<>();
		this.channelLoader = new ChannelLoader(bot.getDiscordClients().blockFirst());
		this.eventFluxBufferSize = eventFluxBufferSize;
		initGDEventSubscribers();
	}

	private Collection<? extends GDEventScanner> initScanners() {
		return Set.of(new AwardedSectionScanner(), new DailyLevelScanner(), new WeeklyDemonScanner());
	}

	private void initGDEventSubscribers() {
		Set<GDEventProcessor> processors = Set.of(
				new AwardedLevelAddedEventProcessor(bot, channelLoader, broadcastedLevels, gdClient),
				new AwardedLevelRemovedEventProcessor(bot, channelLoader, broadcastedLevels, gdClient),
				new AwardedLevelUpdatedEventProcessor(bot, broadcastedLevels),
				new TimelyLevelChangedEventProcessor(bot, channelLoader, broadcastedLevels, gdClient),
				new UserPromotedToModEventProcessor(bot, channelLoader, broadcastedLevels, spriteFactory, iconsCache, gdClient),
				new UserPromotedToElderEventProcessor(bot, channelLoader, broadcastedLevels, spriteFactory, iconsCache, gdClient),
				new UserDemotedFromModEventProcessor(bot, channelLoader, broadcastedLevels, spriteFactory, iconsCache, gdClient),
				new UserDemotedFromElderEventProcessor(bot, channelLoader, broadcastedLevels, spriteFactory, iconsCache, gdClient));
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
	public void onBotReady() {
		scannerLoop.start();
	}

	@Override
	public Set<Command> getProvidedCommands() {
		return Set.of(new ProfileCommand(gdClient, spriteFactory, iconsCache), new LevelCommand(gdClient, true),
				new LevelCommand(gdClient, false), new TimelyCommand(gdClient, true),
				new TimelyCommand(gdClient, false), new AccountCommand(gdClient), new LeaderboardCommand(gdClient),
				new GDEventsCommand(gdClient, gdEventDispatcher, scannerLoop, broadcastedLevels, subscriber, channelLoader),
				new CheckModCommand(gdClient, gdEventDispatcher), new ModListCommand(),
				new FeaturedInfoCommand(gdClient), new ChangelogCommand(channelLoader),
				new ClearCacheCommand(gdClient));
	}

	@Override
	public String getName() {
		return "Geometry Dash";
	}

	@Override
	public Set<String> getDatabaseMappingResources() {
		return Set.of("/GDLinkedUsers.hbm.xml", "/GDSubscribedGuilds.hbm.xml", "/GDModList.hbm.xml", "/GDLeaderboardBans.hbm.xml", "/GDAwardedLevels.hbm.xml");
	}

	@Override
	public Map<String, GuildSettingsEntry<?, ?>> getGuildConfigurationEntries() {
		var map = new HashMap<String, GuildSettingsEntry<?, ?>>();
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
