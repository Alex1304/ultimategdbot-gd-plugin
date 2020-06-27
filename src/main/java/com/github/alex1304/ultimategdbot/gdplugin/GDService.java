package com.github.alex1304.ultimategdbot.gdplugin;

import static java.util.Collections.synchronizedSet;

import java.util.HashSet;
import java.util.Set;

import com.github.alex1304.jdash.client.AuthenticatedGDClient;
import com.github.alex1304.jdash.graphics.SpriteFactory;
import com.github.alex1304.jdash.util.GDUserIconSet;
import com.github.alex1304.jdashevents.GDEventDispatcher;
import com.github.alex1304.jdashevents.GDEventScannerLoop;
import com.github.alex1304.ultimategdbot.api.service.Service;
import com.github.alex1304.ultimategdbot.gdplugin.gdevent.BroadcastResultCache;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import discord4j.common.util.Snowflake;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

/**
 * Exposes various objects useful for Geometry Dash related processes.
 */
public class GDService implements Service {

	private final AuthenticatedGDClient gdClient;
	private final SpriteFactory spriteFactory;
	private final Cache<GDUserIconSet, String> iconsCache;
	private final GDEventDispatcher gdEventDispatcher;
	private final GDEventScannerLoop gdEventLoop;
	private final BroadcastResultCache broadcastResultCache;
	private final Scheduler gdEventScheduler;
	private final Set<Long> cachedSubmissionChannelIds;
	private final int leaderboardRefreshParallelism;
	private final Snowflake iconChannelId;
	private final boolean autostartEventLoop;
	private final int gdEventsMinMembers;

	GDService(AuthenticatedGDClient gdClient, SpriteFactory spriteFactory, int iconsCacheMaxSize,
			GDEventDispatcher gdEventDispatcher, GDEventScannerLoop gdEventLoop, int leaderboardRefreshParallelism,
			Snowflake iconChannelId, boolean autostartEventLoop, int gdEventsMinMembers) {
		this.gdClient = gdClient;
		this.spriteFactory = spriteFactory;
		this.iconsCache = Caffeine.newBuilder().maximumSize(iconsCacheMaxSize).build();
		this.gdEventDispatcher = gdEventDispatcher;
		this.gdEventLoop = gdEventLoop;
		this.broadcastResultCache = new BroadcastResultCache();
		this.gdEventScheduler = Schedulers.boundedElastic();
		this.cachedSubmissionChannelIds = synchronizedSet(new HashSet<Long>());
		this.leaderboardRefreshParallelism = leaderboardRefreshParallelism;
		this.iconChannelId = iconChannelId;
		this.autostartEventLoop = autostartEventLoop;
		this.gdEventsMinMembers = gdEventsMinMembers;
	}

	public AuthenticatedGDClient getGdClient() {
		return gdClient;
	}

	public SpriteFactory getSpriteFactory() {
		return spriteFactory;
	}

	public Cache<GDUserIconSet, String> getIconsCache() {
		return iconsCache;
	}

	public GDEventDispatcher getGdEventDispatcher() {
		return gdEventDispatcher;
	}

	public GDEventScannerLoop getGdEventLoop() {
		return gdEventLoop;
	}

	public BroadcastResultCache getBroadcastResultCache() {
		return broadcastResultCache;
	}

	public Scheduler getGdEventScheduler() {
		return gdEventScheduler;
	}

	public Set<Long> getCachedSubmissionChannelIds() {
		return cachedSubmissionChannelIds;
	}

	public int getLeaderboardRefreshParallelism() {
		return leaderboardRefreshParallelism;
	}

	public Snowflake getIconChannelId() {
		return iconChannelId;
	}
	
	public boolean isAutostartEventLoop() {
		return autostartEventLoop;
	}

	public int getGdEventsMinMembers() {
		return gdEventsMinMembers;
	}
}
