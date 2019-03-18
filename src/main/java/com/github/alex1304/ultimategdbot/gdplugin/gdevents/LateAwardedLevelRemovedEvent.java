package com.github.alex1304.ultimategdbot.gdplugin.gdevents;

import com.github.alex1304.jdash.entity.GDLevel;
import com.github.alex1304.jdashevents.event.AwardedLevelRemovedEvent;

public class LateAwardedLevelRemovedEvent extends AwardedLevelRemovedEvent {

	public LateAwardedLevelRemovedEvent(GDLevel removedLevel) {
		super(removedLevel);
	}

}
