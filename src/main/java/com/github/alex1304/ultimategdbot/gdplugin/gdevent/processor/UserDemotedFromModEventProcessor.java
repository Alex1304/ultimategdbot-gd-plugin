package com.github.alex1304.ultimategdbot.gdplugin.gdevent.processor;

import com.github.alex1304.ultimategdbot.gdplugin.GDPlugin;
import com.github.alex1304.ultimategdbot.gdplugin.gdevent.UserDemotedFromModEvent;

public class UserDemotedFromModEventProcessor extends UserEventProcessor<UserDemotedFromModEvent> {

	public UserDemotedFromModEventProcessor(GDPlugin plugin) {
		super(UserDemotedFromModEvent.class, plugin);
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
