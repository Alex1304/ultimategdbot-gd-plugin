package com.github.alex1304.ultimategdbot.gdplugin.util;

import com.github.alex1304.jdash.entity.PrivacySetting;
import com.github.alex1304.ultimategdbot.api.Translator;

public class GDFormatter {

	private GDFormatter() {
	}
	
	public static String formatPrivacy(Translator tr, PrivacySetting privacy) {
		return tr.translate("GDStrings", "privacy_" + privacy.name().toLowerCase());
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
