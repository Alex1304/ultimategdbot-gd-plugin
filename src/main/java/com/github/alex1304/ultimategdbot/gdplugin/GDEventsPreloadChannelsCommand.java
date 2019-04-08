package com.github.alex1304.ultimategdbot.gdplugin;

import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;

import com.github.alex1304.ultimategdbot.api.Command;
import com.github.alex1304.ultimategdbot.api.Context;
import com.github.alex1304.ultimategdbot.api.PermissionLevel;

import discord4j.core.object.entity.Channel.Type;
import discord4j.core.object.util.Snowflake;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class GDEventsPreloadChannelsCommand implements Command {
	
	private final ChannelLoader channelLoader;

	public GDEventsPreloadChannelsCommand(ChannelLoader channelLoader) {
		this.channelLoader = Objects.requireNonNull(channelLoader);
	}

	@Override
	public Mono<Void> execute(Context ctx) {
		return ctx.reply("Processing...").flatMap(wait -> Flux.concat(GDUtils.getExistingSubscribedGuilds(ctx.getBot(), "where channelAwardedLevelsId > 0")
								.map(GDSubscribedGuilds::getChannelAwardedLevelsId), 
						GDUtils.getExistingSubscribedGuilds(ctx.getBot(), "where channelTimelyLevelsId > 0")
								.map(GDSubscribedGuilds::getChannelTimelyLevelsId), 
						GDUtils.getExistingSubscribedGuilds(ctx.getBot(), "where channelGdModeratorsId > 0")
								.map(GDSubscribedGuilds::getChannelGdModeratorsId), 
						GDUtils.getExistingSubscribedGuilds(ctx.getBot(), "where channelChangelogId > 0")
								.map(GDSubscribedGuilds::getChannelChangelogId))
						.distinct()
						.map(Snowflake::of)
						.concatMap(channelLoader::load)
						.count()
						.flatMap(count -> ctx.reply("Sucessfully preloaded " + count + " channels!"))
						.then(wait.delete()))
				.then();
	}

	@Override
	public Set<String> getAliases() {
		return Set.of("preload_channels");
	}

	@Override
	public Set<Command> getSubcommands() {
		return Set.of();
	}

	@Override
	public String getDescription() {
		return "Preload the channels that are subscribed to GD events.";
	}

	@Override
	public String getLongDescription() {
		return "Can help in improving performances.";
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
	public EnumSet<Type> getChannelTypesAllowed() {
		return EnumSet.of(Type.GUILD_TEXT, Type.DM);
	}

	@Override
	public Map<Class<? extends Throwable>, BiConsumer<Throwable, Context>> getErrorActions() {
		return GDUtils.DEFAULT_GD_ERROR_ACTIONS;
	}

}
