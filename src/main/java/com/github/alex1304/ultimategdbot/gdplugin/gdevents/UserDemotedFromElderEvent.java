package com.github.alex1304.ultimategdbot.gdplugin.gdevents;

import com.github.alex1304.jdash.entity.GDUser;

public class UserDemotedFromElderEvent extends UserEvent {
	public UserDemotedFromElderEvent(GDUser user) {
		super(user);
	}
}
