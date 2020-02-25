package com.github.alex1304.ultimategdbot.gdplugin;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.github.alex1304.jdash.client.AuthenticatedGDClient;
import com.github.alex1304.jdash.graphics.SpriteFactory;
import com.github.alex1304.jdash.util.GDUserIconSet;
import com.github.alex1304.jdashevents.GDEventDispatcher;
import com.github.alex1304.jdashevents.GDEventScannerLoop;
import com.github.alex1304.ultimategdbot.gdplugin.gdevent.BroadcastResultCache;

import discord4j.core.object.util.Snowflake;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

/**
 * Exposes various objects useful for Geometry Dash related processes.
 */
public class GDServiceMediator {

	private final AuthenticatedGDClient gdClient;
	private final SpriteFactory spriteFactory;
	private final ConcurrentHashMap<GDUserIconSet, String> iconsCache;
	private final GDEventDispatcher gdEventDispatcher;
	private final GDEventScannerLoop gdEventscannerLoop;
	private final BroadcastResultCache broadcastResultCache;
	private final Scheduler gdEventScheduler;
	private final Set<Long> cachedSubmissionChannelIds;
	private final int leaderboardRefreshParallelism;
	private final Snowflake iconChannelId;

	GDServiceMediator(AuthenticatedGDClient gdClient, SpriteFactory spriteFactory, GDEventDispatcher gdEventDispatcher,
			GDEventScannerLoop gdEventscannerLoop, Set<Long> cachedSubmissionChannelIds,
			int leaderboardRefreshParallelism, Snowflake iconChannelId) {
		this.gdClient = gdClient;
		this.spriteFactory = spriteFactory;
		this.iconsCache = new ConcurrentHashMap<>();
		this.gdEventDispatcher = gdEventDispatcher;
		this.gdEventscannerLoop = gdEventscannerLoop;
		this.broadcastResultCache = new BroadcastResultCache();
		this.gdEventScheduler = Schedulers.elastic();
		this.cachedSubmissionChannelIds = cachedSubmissionChannelIds;
		this.leaderboardRefreshParallelism = leaderboardRefreshParallelism;
		this.iconChannelId = iconChannelId;
	}

	public AuthenticatedGDClient getGdClient() {
		return gdClient;
	}

	public SpriteFactory getSpriteFactory() {
		return spriteFactory;
	}

	public Map<GDUserIconSet, String> getIconsCache() {
		return iconsCache;
	}

	public GDEventDispatcher getGdEventDispatcher() {
		return gdEventDispatcher;
	}

	public GDEventScannerLoop getGdEventscannerLoop() {
		return gdEventscannerLoop;
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
}
