package com.github.alex1304.ultimategdbot.gdplugin;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.github.alex1304.jdash.client.AuthenticatedGDClient;
import com.github.alex1304.jdash.client.GDClientBuilder;
import com.github.alex1304.jdash.exception.GDLoginFailedException;
import com.github.alex1304.jdash.exception.SpriteLoadException;
import com.github.alex1304.jdash.graphics.SpriteFactory;
import com.github.alex1304.jdash.util.GDUserIconSet;
import com.github.alex1304.jdash.util.Routes;
import com.github.alex1304.ultimategdbot.api.Bot;
import com.github.alex1304.ultimategdbot.api.Command;
import com.github.alex1304.ultimategdbot.api.Plugin;
import com.github.alex1304.ultimategdbot.api.guildsettings.GuildSettingsEntry;
import com.github.alex1304.ultimategdbot.api.utils.PropertyParser;

public class GDPlugin implements Plugin {
	
	private AuthenticatedGDClient gdClient;
	private SpriteFactory spriteFactory;
	private Map<GDUserIconSet, String[]> iconsCache;

	@Override
	public void setup(PropertyParser parser) {
		var username = parser.parseAsString("gdplugin.username");
		var password = parser.parseAsString("gdplugin.password");
		var host = parser.parseAsStringOrDefault("gdplugin.host", Routes.BASE_URL);
		var cacheLifetime = parser.parseAsLongOrDefault("gdplugin.cache_lifetime", GDClientBuilder.DEFAULT_CACHE_LIFETIME);
		try {
			this.gdClient = GDClientBuilder.create()
					.withHost(host)
					.withCacheLifetime(cacheLifetime)
					.buildAuthenticated(username, password);
		} catch (GDLoginFailedException e) {
			throw new RuntimeException("Failed to login with the given Geometry Dash credentials", e);
		}
		try {
			this.spriteFactory = SpriteFactory.create();
		} catch (SpriteLoadException e) {
			throw new RuntimeException("An error occured when loading the GD icons sprite factory", e);
		}
		this.iconsCache = new ConcurrentHashMap<>();
	}

	@Override
	public Set<Command> getProvidedCommands() {
		return Set.of(new ProfileCommand(gdClient, spriteFactory, iconsCache));
	}

	@Override
	public String getName() {
		return "Geometry Dash";
	}

	@Override
	public Set<String> getDatabaseMappingResources() {
		return Set.of();
	}

	@Override
	public Map<String, GuildSettingsEntry<?, ?>> getGuildConfigurationEntries(Bot bot) {
		return Map.of();
	}
}
