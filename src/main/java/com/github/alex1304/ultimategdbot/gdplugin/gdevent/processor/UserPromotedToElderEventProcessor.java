package com.github.alex1304.ultimategdbot.gdplugin.gdevent.processor;

import com.github.alex1304.ultimategdbot.api.Bot;
import com.github.alex1304.ultimategdbot.gdplugin.GDServiceMediator;
import com.github.alex1304.ultimategdbot.gdplugin.gdevent.UserPromotedToElderEvent;

public class UserPromotedToElderEventProcessor extends UserEventProcessor<UserPromotedToElderEvent> {

	public UserPromotedToElderEventProcessor(GDServiceMediator gdServiceMediator, Bot bot) {
		super(UserPromotedToElderEvent.class, gdServiceMediator, bot);
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
