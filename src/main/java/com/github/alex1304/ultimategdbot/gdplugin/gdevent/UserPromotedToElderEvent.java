package com.github.alex1304.ultimategdbot.gdplugin.gdevent;

import com.github.alex1304.jdash.entity.GDUser;

public final class UserPromotedToElderEvent extends UserEvent {
	public UserPromotedToElderEvent(GDUser user) {
		super(user);
	}
}
