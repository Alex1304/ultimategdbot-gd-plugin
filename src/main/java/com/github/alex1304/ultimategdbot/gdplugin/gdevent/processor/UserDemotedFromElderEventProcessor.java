package com.github.alex1304.ultimategdbot.gdplugin.gdevent.processor;

import com.github.alex1304.ultimategdbot.api.Bot;
import com.github.alex1304.ultimategdbot.gdplugin.GDServiceMediator;
import com.github.alex1304.ultimategdbot.gdplugin.gdevent.UserDemotedFromElderEvent;

public class UserDemotedFromElderEventProcessor extends UserEventProcessor<UserDemotedFromElderEvent> {

	public UserDemotedFromElderEventProcessor(GDServiceMediator gdServiceMediator, Bot bot) {
		super(UserDemotedFromElderEvent.class, gdServiceMediator, bot);
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
		return "demoted from Geometry Dash Elder Moderator...";
	}

	@Override
	String eventName() {
		return "User Demoted From Elder";
	}

	@Override
	boolean isPromotion() {
		return false;
	}
}
