package com.github.alex1304.ultimategdbot.gdplugin.gdevent.processor;

import com.github.alex1304.ultimategdbot.gdplugin.GDPlugin;
import com.github.alex1304.ultimategdbot.gdplugin.gdevent.UserPromotedToModEvent;

public class UserPromotedToModEventProcessor extends UserEventProcessor<UserPromotedToModEvent> {

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
