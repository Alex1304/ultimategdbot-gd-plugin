package com.github.alex1304.ultimategdbot.gdplugin.util;

import com.github.alex1304.jdash.entity.PrivacySetting;

public class GDFormatter {

	private GDFormatter() {
	}
	
	public static String formatPrivacy(PrivacySetting privacy) {
		return  privacy.name().charAt(0) + privacy.name().substring(1).replaceAll("_", " ").toLowerCase();
	}
	
	public static String formatCode(Object val, int n) {
		var sb = new StringBuilder("" + val);
		for (var i = sb.length() ; i <= n ; i++) {
			sb.insert(0, " ‌‌");
		}
		sb.insert(0, '`').append('`');
		return sb.toString();
	}
}
