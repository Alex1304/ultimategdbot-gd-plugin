package com.github.alex1304.ultimategdbot.gdplugin;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.github.alex1304.jdash.client.AuthenticatedGDClient;
import com.github.alex1304.jdash.graphics.SpriteFactory;
import com.github.alex1304.jdash.util.GDUserIconSet;
import com.github.alex1304.jdashevents.GDEventDispatcher;
import com.github.alex1304.jdashevents.GDEventScannerLoop;
import com.github.alex1304.ultimategdbot.gdplugin.gdevent.GDEventSubscriber;
import com.github.alex1304.ultimategdbot.gdplugin.util.BroadcastPreloader;

import discord4j.core.object.entity.Message;
import reactor.core.scheduler.Scheduler;

/**
 * Exposes various objects useful for Geometry Dash related processes.
 */
public class GDServiceMediator {

	private final AuthenticatedGDClient gdClient;
	private final SpriteFactory spriteFactory;
	private final ConcurrentHashMap<GDUserIconSet, String> iconsCache;
	private final GDEventDispatcher gdEventDispatcher;
	private final GDEventScannerLoop gdEventscannerLoop;
	private final ConcurrentHashMap<Long, List<Message>> broadcastedLevels;
	private final BroadcastPreloader broadcastPreloader;
	private final GDEventSubscriber gdEventSubscriber;
	private final Scheduler gdEventScheduler;
	private final Set<Long> cachedSubmissionChannelIds;
	private final int leaderboardRefreshParallelism;
	
	GDServiceMediator(AuthenticatedGDClient gdClient, SpriteFactory spriteFactory,
			ConcurrentHashMap<GDUserIconSet, String> iconsCache, GDEventDispatcher gdEventDispatcher,
			GDEventScannerLoop gdEventscannerLoop, ConcurrentHashMap<Long, List<Message>> broadcastedLevels,
			BroadcastPreloader broadcastPreloader, GDEventSubscriber gdEventSubscriber, Scheduler gdEventScheduler,
			Set<Long> cachedSubmissionChannelIds, int leaderboardRefreshParallelism) {
		this.gdClient = gdClient;
		this.spriteFactory = spriteFactory;
		this.iconsCache = iconsCache;
		this.gdEventDispatcher = gdEventDispatcher;
		this.gdEventscannerLoop = gdEventscannerLoop;
		this.broadcastedLevels = broadcastedLevels;
		this.broadcastPreloader = broadcastPreloader;
		this.gdEventSubscriber = gdEventSubscriber;
		this.gdEventScheduler = gdEventScheduler;
		this.cachedSubmissionChannelIds = cachedSubmissionChannelIds;
		this.leaderboardRefreshParallelism = leaderboardRefreshParallelism;
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

	public Map<Long, List<Message>> getDispatchedLevels() {
		return broadcastedLevels;
	}

	public BroadcastPreloader getBroadcastPreloader() {
		return broadcastPreloader;
	}

	public GDEventSubscriber getGdEventSubscriber() {
		return gdEventSubscriber;
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
}
