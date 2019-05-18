package com.github.alex1304.ultimategdbot.gdplugin.gdevent.processor;

import com.github.alex1304.ultimategdbot.gdplugin.GDPlugin;
import com.github.alex1304.ultimategdbot.gdplugin.gdevent.UserPromotedToElderEvent;

public class UserPromotedToElderEventProcessor extends UserEventProcessor<UserPromotedToElderEvent> {

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
