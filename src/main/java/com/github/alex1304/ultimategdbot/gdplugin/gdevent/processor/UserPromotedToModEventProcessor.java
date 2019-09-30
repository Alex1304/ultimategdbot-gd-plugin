package com.github.alex1304.ultimategdbot.gdplugin.gdevent.processor;

import com.github.alex1304.ultimategdbot.api.Bot;
import com.github.alex1304.ultimategdbot.gdplugin.GDServiceMediator;
import com.github.alex1304.ultimategdbot.gdplugin.gdevent.UserPromotedToModEvent;

public class UserPromotedToModEventProcessor extends UserEventProcessor<UserPromotedToModEvent> {

	public UserPromotedToModEventProcessor(GDServiceMediator gdServiceMediator, Bot bot) {
		super(UserPromotedToModEvent.class, gdServiceMediator, bot);
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
