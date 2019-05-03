package com.github.alex1304.ultimategdbot.gdplugin;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import com.github.alex1304.ultimategdbot.api.Command;
import com.github.alex1304.ultimategdbot.api.Context;
import com.github.alex1304.ultimategdbot.api.InvalidSyntaxException;
import com.github.alex1304.ultimategdbot.api.PermissionLevel;
import com.github.alex1304.ultimategdbot.api.utils.ArgUtils;
import com.github.alex1304.ultimategdbot.api.utils.BotUtils;

import discord4j.core.object.entity.Channel.Type;
import discord4j.core.object.util.Snowflake;
import discord4j.core.spec.MessageCreateSpec;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

public class ChangelogCommand implements Command {
	
	private final GDPlugin plugin;
	
	public ChangelogCommand(GDPlugin plugin) {
		this.plugin = Objects.requireNonNull(plugin);
	}

	@Override
	public Mono<Void> execute(Context ctx) {
		ArgUtils.requireMinimumArgCount(ctx, 6);
		var title = ctx.getArgs().get(1);
		var titleList = new ArrayList<String>();
		var contentList = new ArrayList<String>();
		for (var i = 2 ; i < ctx.getArgs().size() ; i++) {
			switch ((i - 2) % 4) {
				case 0:
					if (!ctx.getArgs().get(i).equals("|")) {
						return Mono.error(new InvalidSyntaxException(this));
					}
					break;
				case 1:
					titleList.add(ctx.getArgs().get(i));
					break;
				case 2:
					if (!ctx.getArgs().get(i).equals("-")) {
						return Mono.error(new InvalidSyntaxException(this));
					}
					break;
				case 3:
					contentList.add(ctx.getArgs().get(i));
					break;
			}
		}
		if (titleList.size() != contentList.size()) {
			return Mono.error(new InvalidSyntaxException(this));
		}
		Consumer<MessageCreateSpec> changelog = mcs -> mcs.setEmbed(embed -> {
			var author = ctx.getEvent().getMessage().getAuthor().get();
			embed.setColor(Color.BLUE);
			embed.setTitle(title);
			embed.setAuthor(BotUtils.formatDiscordUsername(author).replaceAll("\\\\", ""), null, author.getAvatarUrl());
			for (var i = 0 ; i < titleList.size() ; i++) {
				embed.addField(titleList.get(i), contentList.get(i), false);
			}
		});
		return ctx.reply("Sending changelog, please wait...")
				.then(GDUtils.getExistingSubscribedGuilds(ctx.getBot(), "where channelChangelogId > 0")
						.map(GDSubscribedGuilds::getChannelChangelogId)
						.map(Snowflake::of)
						.concatMap(plugin.getPreloader()::preloadChannel)
						.publishOn(GDUtils.GDEVENT_SCHEDULER)
						.flatMap(channel -> channel.createMessage(changelog).onErrorResume(e -> Mono.empty()))
						.then(ctx.reply("Changelog sent to all guilds!")))
				.then();
	}

	@Override
	public Set<String> getAliases() {
		return Set.of("changelog");
	}

	@Override
	public Set<Command> getSubcommands() {
		return Set.of();
	}

	@Override
	public String getDescription() {
		return "Sends a changelog message across all servers.";
	}

	@Override
	public String getLongDescription() {
		return "<title> | \"title1\" - \"content1\" |  \"title2\" - \"content2\" | ...";
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
		return Map.of();
	}
}
