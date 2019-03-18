package com.github.alex1304.ultimategdbot.gdplugin.gdevents;

import com.github.alex1304.jdash.entity.GDLevel;
import com.github.alex1304.jdashevents.event.AwardedLevelAddedEvent;

public class LateAwardedLevelAddedEvent extends AwardedLevelAddedEvent {

	public LateAwardedLevelAddedEvent(GDLevel addedLevel) {
		super(addedLevel);
	}

}
