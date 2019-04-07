package com.github.alex1304.ultimategdbot.gdplugin.gdevents;

import java.util.List;
import java.util.Map;

import com.github.alex1304.jdash.client.AuthenticatedGDClient;
import com.github.alex1304.jdash.graphics.SpriteFactory;
import com.github.alex1304.jdash.util.GDUserIconSet;
import com.github.alex1304.ultimategdbot.api.Bot;
import com.github.alex1304.ultimategdbot.gdplugin.ChannelLoader;

import discord4j.core.object.entity.Message;

public class UserPromotedToModEventProcessor extends UserEventSubscriber<UserPromotedToModEvent> {

	public UserPromotedToModEventProcessor(Bot bot, ChannelLoader channelLoader, Map<Long, List<Message>> broadcastedMessages,
			SpriteFactory spriteFactory, Map<GDUserIconSet, String[]> iconsCache, AuthenticatedGDClient gdClient) {
		super(UserPromotedToModEvent.class, bot, channelLoader, broadcastedMessages, spriteFactory, iconsCache, gdClient);
	}

	@Override
	String authorName() {
		return "User promoted!";
	}

	@Override
	String authorIconUrl() {
		return "https://i.imgur.com/zY61GDD.png";
	}

	@Override
	String messageContent() {
		return "promoted to Geometry Dash Moderator!";
	}

	@Override
	String eventName() {
		return "User Promoted To Mod";
	}

	@Override
	boolean isPromotion() {
		return true;
	}
}
