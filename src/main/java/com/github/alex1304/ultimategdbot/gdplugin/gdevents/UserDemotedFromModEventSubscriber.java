package com.github.alex1304.ultimategdbot.gdplugin.gdevents;

import java.util.List;
import java.util.Map;

import com.github.alex1304.jdash.client.AuthenticatedGDClient;
import com.github.alex1304.jdash.graphics.SpriteFactory;
import com.github.alex1304.jdash.util.GDUserIconSet;
import com.github.alex1304.ultimategdbot.api.Bot;

import discord4j.core.object.entity.Message;

public class UserDemotedFromModEventSubscriber extends UserEventSubscriber<UserDemotedFromModEvent> {

	public UserDemotedFromModEventSubscriber(Bot bot, Map<Long, List<Message>> broadcastedMessages,
			SpriteFactory spriteFactory, Map<GDUserIconSet, String[]> iconsCache, AuthenticatedGDClient gdClient) {
		super(bot, broadcastedMessages, spriteFactory, iconsCache, gdClient);
	}

	@Override
	String authorName() {
		return "User demoted...";
	}

	@Override
	String authorIconUrl() {
		return "https://i.imgur.com/X53HV7d.png";
	}

	@Override
	String messageContent() {
		return "demoted from Geometry Dash Moderator...";
	}

	@Override
	String eventName() {
		return "User Demoted From Mod";
	}

	@Override
	boolean isPromotion() {
		return false;
	}
}
