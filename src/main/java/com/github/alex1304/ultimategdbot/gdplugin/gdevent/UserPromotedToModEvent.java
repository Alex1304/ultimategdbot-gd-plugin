package com.github.alex1304.ultimategdbot.gdplugin.gdevent;

import com.github.alex1304.jdash.entity.GDUser;

public final class UserPromotedToModEvent extends UserEvent {
	public UserPromotedToModEvent(GDUser user) {
		super(user);
	}
}
