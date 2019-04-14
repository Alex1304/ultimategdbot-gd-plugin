package com.github.alex1304.ultimategdbot.gdplugin.gdevents;

import java.util.List;
import java.util.Map;

import com.github.alex1304.jdash.client.AuthenticatedGDClient;
import com.github.alex1304.jdash.graphics.SpriteFactory;
import com.github.alex1304.jdash.util.GDUserIconSet;
import com.github.alex1304.ultimategdbot.api.Bot;

import discord4j.core.object.entity.Message;

public class UserPromotedToElderEventProcessor extends UserEventSubscriber<UserPromotedToElderEvent> {

	public UserPromotedToElderEventProcessor(Bot bot, Map<Long, List<Message>> broadcastedMessages,
			SpriteFactory spriteFactory, Map<GDUserIconSet, String[]> iconsCache, AuthenticatedGDClient gdClient) {
		super(UserPromotedToElderEvent.class, bot, broadcastedMessages, spriteFactory, iconsCache, gdClient);
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
		return "promoted to Geometry Dash Elder Moderator!";
	}

	@Override
	String eventName() {
		return "User Promoted To Elder";
	}

	@Override
	boolean isPromotion() {
		return true;
	}
}
