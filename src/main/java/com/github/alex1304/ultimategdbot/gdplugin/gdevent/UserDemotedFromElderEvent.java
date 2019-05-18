package com.github.alex1304.ultimategdbot.gdplugin.gdevent;

import com.github.alex1304.jdash.entity.GDUser;

public class UserDemotedFromElderEvent extends UserEvent {
	public UserDemotedFromElderEvent(GDUser user) {
		super(user);
	}
}
