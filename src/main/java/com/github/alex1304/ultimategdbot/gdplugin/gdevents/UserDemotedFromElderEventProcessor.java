package com.github.alex1304.ultimategdbot.gdplugin.gdevents;

import com.github.alex1304.ultimategdbot.gdplugin.GDPlugin;

public class UserDemotedFromElderEventProcessor extends UserEventSubscriber<UserDemotedFromElderEvent> {

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
