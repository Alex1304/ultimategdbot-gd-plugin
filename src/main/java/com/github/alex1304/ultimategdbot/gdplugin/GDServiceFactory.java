package com.github.alex1304.ultimategdbot.gdplugin;

import static reactor.function.TupleUtils.function;

import java.time.Duration;
import java.util.Set;

import com.github.alex1304.jdash.client.GDClientBuilder;
import com.github.alex1304.jdash.client.GDClientBuilder.Credentials;
import com.github.alex1304.jdash.graphics.SpriteFactory;
import com.github.alex1304.jdash.util.Routes;
import com.github.alex1304.jdashevents.GDEventDispatcher;
import com.github.alex1304.jdashevents.GDEventScannerLoop;
import com.github.alex1304.jdashevents.scanner.AwardedSectionScanner;
import com.github.alex1304.jdashevents.scanner.DailyLevelScanner;
import com.github.alex1304.jdashevents.scanner.GDEventScanner;
import com.github.alex1304.jdashevents.scanner.WeeklyDemonScanner;
import com.github.alex1304.ultimategdbot.api.Bot;
import com.github.alex1304.ultimategdbot.api.service.ServiceFactory;

import discord4j.common.util.Snowflake;
import reactor.core.publisher.Mono;

public class GDServiceFactory implements ServiceFactory<GDService> {

	@Override
	public Mono<GDService> create(Bot bot) {
		// Properties
		var gdConfig = bot.config("gd");
		var username = gdConfig.read("gdplugin.username");
		var password = gdConfig.read("gdplugin.password");
		var iconChannelId = gdConfig.readAs("gdplugin.icon_channel_id", Snowflake::of);
		var host = gdConfig.readOptional("gdplugin.host").orElse(Routes.BASE_URL);
		var cacheTtl = gdConfig.readOptional("gdplugin.cache_ttl")
				.map(v -> Duration.ofMillis(Long.parseLong(v)))
				.orElse(GDClientBuilder.DEFAULT_CACHE_TTL);
		var requestTimeout = gdConfig.readOptional("gdplugin.request_timeout")
				.map(v -> Duration.ofMillis(Long.parseLong(v)))
				.orElse(GDClientBuilder.DEFAULT_REQUEST_TIMEOUT);
		var eventLoopInterval = Duration.ofSeconds(gdConfig.readOptional("gdplugin.event_loop_interval")
				.map(Integer::parseInt)
				.orElse(10));
		var autostartEventLoop = gdConfig.readOptional("gdplugin.autostart_event_loop")
				.map(Boolean::parseBoolean)
				.orElse(true);
		var maxConnections = gdConfig.readOptional("gdplugin.max_connections")
				.map(Integer::parseInt)
				.orElse(100);
		var iconsCacheMaxSize = gdConfig.readOptional("gdplugin.icons_cache_max_size")
				.map(Integer::parseInt)
				.orElse(2048);
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
					var eventLoop = new GDEventScannerLoop(gdClient, gdEventDispatcher, initScanners(),
							eventLoopInterval);
					return new GDService(gdClient, spriteFactory, iconsCacheMaxSize, gdEventDispatcher,
							eventLoop, maxConnections, iconChannelId, autostartEventLoop);
				}));
	}

	@Override
	public Class<GDService> serviceClass() {
		return GDService.class;
	}

	private static Set<GDEventScanner> initScanners() {
		return Set.of(new AwardedSectionScanner(), new DailyLevelScanner(), new WeeklyDemonScanner());
	}
}
