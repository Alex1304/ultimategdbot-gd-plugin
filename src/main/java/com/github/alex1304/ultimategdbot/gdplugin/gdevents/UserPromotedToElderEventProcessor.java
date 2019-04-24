package com.github.alex1304.ultimategdbot.gdplugin.gdevents;

import com.github.alex1304.ultimategdbot.gdplugin.GDPlugin;

public class UserPromotedToElderEventProcessor extends UserEventSubscriber<UserPromotedToElderEvent> {

	public UserPromotedToElderEventProcessor(GDPlugin plugin) {
		super(UserPromotedToElderEvent.class, plugin);
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
