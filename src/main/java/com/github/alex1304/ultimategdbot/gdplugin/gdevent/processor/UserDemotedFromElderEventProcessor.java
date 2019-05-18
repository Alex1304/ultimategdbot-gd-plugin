package com.github.alex1304.ultimategdbot.gdplugin.gdevent.processor;

import com.github.alex1304.ultimategdbot.gdplugin.GDPlugin;
import com.github.alex1304.ultimategdbot.gdplugin.gdevent.UserDemotedFromElderEvent;

public class UserDemotedFromElderEventProcessor extends UserEventProcessor<UserDemotedFromElderEvent> {

	public UserDemotedFromElderEventProcessor(GDPlugin plugin) {
		super(UserDemotedFromElderEvent.class, plugin);
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
