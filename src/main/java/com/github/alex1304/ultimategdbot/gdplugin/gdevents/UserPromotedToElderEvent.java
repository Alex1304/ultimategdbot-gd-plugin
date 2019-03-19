package com.github.alex1304.ultimategdbot.gdplugin.gdevents;

import com.github.alex1304.jdash.entity.GDUser;

public class UserPromotedToElderEvent extends UserEvent {
	public UserPromotedToElderEvent(GDUser user) {
		super(user);
	}
}
