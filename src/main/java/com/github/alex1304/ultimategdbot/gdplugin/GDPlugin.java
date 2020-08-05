package com.github.alex1304.ultimategdbot.gdplugin;

import static com.github.alex1304.ultimategdbot.gdplugin.GDServices.GD;

import java.util.List;

import com.github.alex1304.rdi.ServiceReference;
import com.github.alex1304.ultimategdbot.api.Plugin;
import com.github.alex1304.ultimategdbot.api.PluginMetadata;
import com.github.alex1304.ultimategdbot.api.util.VersionUtils;

import discord4j.common.GitProperties;
import reactor.core.publisher.Mono;

public final class GDPlugin implements Plugin {
	
	public static final String PLUGIN_NAME = "Geometry Dash";

	@Override
	public ServiceReference<?> rootService() {
		return GD;
	}

	@Override
	public Mono<PluginMetadata> metadata() {
		return VersionUtils.getGitProperties("META-INF/git/gd.git.properties")
				.map(props -> props.readOptional(GitProperties.APPLICATION_VERSION))
				.map(version -> PluginMetadata.builder(PLUGIN_NAME)
						.setDescription("Commands and useful features dedicated to the platformer game Geometry Dash.")
						.setVersion(version.orElse(null))
						.setDevelopers(List.of("Alex1304"))
						.setUrl("https://github.com/ultimategdbot/ultimategdbot-gd-plugin")
						.build());
	}

}
