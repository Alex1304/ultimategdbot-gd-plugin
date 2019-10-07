package com.github.alex1304.ultimategdbot.gdplugin.util;

import java.util.Collection;
import java.util.stream.Collectors;

public class DatabaseUtils {

	private DatabaseUtils() {
	}

	public static String in(Collection<?> l) {
		return l.stream()
				.map(Object::toString)
				.collect(Collectors.joining(",", "in (", ")"));
	}
	
	
}
