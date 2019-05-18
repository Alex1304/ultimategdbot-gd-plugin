package com.github.alex1304.ultimategdbot.gdplugin.gdevent;

import com.github.alex1304.jdash.entity.GDTimelyLevel;
import com.github.alex1304.jdashevents.event.TimelyLevelChangedEvent;

public class LateTimelyLevelChangedEvent extends TimelyLevelChangedEvent {

	public LateTimelyLevelChangedEvent(GDTimelyLevel timelyLevel) {
		super(timelyLevel);
	}

}
