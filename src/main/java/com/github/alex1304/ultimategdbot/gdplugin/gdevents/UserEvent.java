package com.github.alex1304.ultimategdbot.gdplugin.gdevents;

import java.util.Objects;

import com.github.alex1304.jdash.entity.GDUser;
import com.github.alex1304.jdashevents.event.GDEvent;

public abstract class UserEvent implements GDEvent {

	private final GDUser user;

	public UserEvent(GDUser user) {
		this.user = Objects.requireNonNull(user);
	}

	public GDUser getUser() {
		return user;
	}
}
