package com.github.alex1304.ultimategdbot.gdplugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.ToLongFunction;

import com.github.alex1304.ultimategdbot.api.Command;
import com.github.alex1304.ultimategdbot.api.Context;
import com.github.alex1304.ultimategdbot.api.PermissionLevel;
import com.github.alex1304.ultimategdbot.api.Plugin;

import discord4j.core.DiscordClient;
import discord4j.core.object.util.Snowflake;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class GDEventsCleanDatabaseCommand implements Command {
	
	private final GDPlugin plugin;

	public GDEventsCleanDatabaseCommand(GDPlugin plugin) {
		this.plugin = Objects.requireNonNull(plugin);
	}

	@Override
	public Mono<Void> execute(Context ctx) {
		var client = ctx.getBot().getMainDiscordClient();
		return ctx.reply("Processing...")
				.flatMap(wait -> GDUtils.getExistingSubscribedGuilds(ctx.getBot(), "")
						.flatMap(subscribedGuild -> performCleanup(client, subscribedGuild))
						.collectList()
						.flatMap(subscribedList -> ctx.getBot().getDatabase()
								.performEmptyTransaction(session -> subscribedList.forEach(session::saveOrUpdate))
								.thenReturn(subscribedList))
						.map(List::size)
						.flatMap(updatedCount -> ctx.reply("Successfully cleaned up invalid channels and roles for "
								+ updatedCount + " guilds!"))
						.then(wait.delete()));
	}
	
	private Mono<GDSubscribedGuilds> performCleanup(DiscordClient client, GDSubscribedGuilds subscribedGuild) {
		var monos = new ArrayList<Mono<Boolean>>();
		addIfConfigured(monos, client, subscribedGuild, GDSubscribedGuilds::getChannelAwardedLevelsId, GDSubscribedGuilds::setChannelAwardedLevelsId, true);
		addIfConfigured(monos, client, subscribedGuild, GDSubscribedGuilds::getChannelTimelyLevelsId, GDSubscribedGuilds::setChannelTimelyLevelsId, true);
		addIfConfigured(monos, client, subscribedGuild, GDSubscribedGuilds::getChannelGdModeratorsId, GDSubscribedGuilds::setChannelGdModeratorsId, true);
		addIfConfigured(monos, client, subscribedGuild, GDSubscribedGuilds::getChannelChangelogId, GDSubscribedGuilds::setChannelChangelogId, true);
		addIfConfigured(monos, client, subscribedGuild, GDSubscribedGuilds::getRoleAwardedLevelsId, GDSubscribedGuilds::setRoleAwardedLevelsId, false);
		addIfConfigured(monos, client, subscribedGuild, GDSubscribedGuilds::getRoleTimelyLevelsId, GDSubscribedGuilds::setRoleTimelyLevelsId, false);
		addIfConfigured(monos, client, subscribedGuild, GDSubscribedGuilds::getRoleGdModeratorsId, GDSubscribedGuilds::setRoleGdModeratorsId, false);
		if (monos.isEmpty()) {
			return Mono.empty();
		}
		return Flux.merge(monos).all(x -> x).filter(x -> !x).map(__ -> subscribedGuild);
	}
	
	private void addIfConfigured(List<Mono<Boolean>> monos, DiscordClient client, GDSubscribedGuilds subscribedGuild,
			ToLongFunction<GDSubscribedGuilds> getter, BiConsumer<GDSubscribedGuilds, Long> setter, boolean isChannel) {
		var entityMono = isChannel ? client.getChannelById(Snowflake.of(getter.applyAsLong(subscribedGuild)))
				: client.getRoleById(Snowflake.of(subscribedGuild.getGuildId()), Snowflake.of(getter.applyAsLong(subscribedGuild)));
		if (getter.applyAsLong(subscribedGuild) > 0) {
			monos.add(entityMono.doOnError(e -> setter.accept(subscribedGuild, 0L))
					.map(__ -> true)
					.onErrorReturn(false));
		}
	}

	@Override
	public Set<String> getAliases() {
		return Set.of("clean_database");
	}

	@Override
	public String getDescription() {
		return "Clean up from database references to channels and roles that aren't valid anymore.";
	}

	@Override
	public String getLongDescription() {
		return "Having invalid channels and role IDs in the database has a tendency to slow down the broadcasting "
				+ "of GD events. Running this command occasionally is necessary in order to maintain maximum performances.";
	}

	@Override
	public String getSyntax() {
		return "";
	}

	@Override
	public PermissionLevel getPermissionLevel() {
		return PermissionLevel.BOT_OWNER;
	}

	@Override
	public Plugin getPlugin() {
		return plugin;
	}
}
