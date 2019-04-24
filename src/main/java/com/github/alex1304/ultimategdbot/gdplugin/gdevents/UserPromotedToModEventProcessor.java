package com.github.alex1304.ultimategdbot.gdplugin.gdevents;

import com.github.alex1304.ultimategdbot.gdplugin.GDPlugin;

public class UserPromotedToModEventProcessor extends UserEventSubscriber<UserPromotedToModEvent> {

	public UserPromotedToModEventProcessor(GDPlugin plugin) {
		super(UserPromotedToModEvent.class, plugin);
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
