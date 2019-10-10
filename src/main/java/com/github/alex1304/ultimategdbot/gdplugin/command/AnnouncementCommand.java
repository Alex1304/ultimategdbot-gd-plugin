package com.github.alex1304.ultimategdbot.gdplugin.command;

import java.awt.Color;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import com.github.alex1304.ultimategdbot.api.command.CommandFailedException;
import com.github.alex1304.ultimategdbot.api.command.Context;
import com.github.alex1304.ultimategdbot.api.command.PermissionLevel;
import com.github.alex1304.ultimategdbot.api.command.annotated.CommandAction;
import com.github.alex1304.ultimategdbot.api.command.annotated.CommandDoc;
import com.github.alex1304.ultimategdbot.api.command.annotated.CommandSpec;
import com.github.alex1304.ultimategdbot.api.utils.DiscordFormatter;
import com.github.alex1304.ultimategdbot.api.utils.menu.InteractiveMenu;
import com.github.alex1304.ultimategdbot.gdplugin.GDServiceMediator;
import com.github.alex1304.ultimategdbot.gdplugin.database.GDSubscribedGuilds;
import com.github.alex1304.ultimategdbot.gdplugin.util.GDEvents;

import discord4j.core.object.entity.Attachment;
import discord4j.core.object.entity.User;
import discord4j.core.object.util.Snowflake;
import discord4j.core.spec.EmbedCreateSpec;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import reactor.retry.Retry;

@CommandSpec(
		aliases = "announcement",
		shortDescription = "Sends a global bot announcement.",
		permLevel = PermissionLevel.BOT_OWNER
)
public class AnnouncementCommand {

	private final HttpClient fileClient = HttpClient.create().headers(h -> h.add("Content-Type", "text/plain"));
	private final GDServiceMediator gdServiceMediator;
	
	public AnnouncementCommand(GDServiceMediator gdServiceMediator) {
		this.gdServiceMediator = gdServiceMediator;
	}
	
	@CommandAction
	@CommandDoc("This command expects one text file attached to the message. This textfile contains "
			+ "information that should be included in the announcement, in the following format:\n"
			+ "```\n"
			+ "First line is the title of the announcement\n\n"
			+ "Skip two lines, and write the title of the first section\n"
			+ "On next line, the content of the first section\n\n"
			+ "Skip two lines again and write the title of the 2nd section\n"
			+ "Then on next line the content of the 2nd section, etc etc.\n"
			+ "```\n")
	public Mono<Void> run(Context ctx) {
		if (ctx.getEvent().getMessage().getAttachments().size() != 1) {
			return Mono.error(new CommandFailedException("You must attach exactly one file."));
		}
		
		return getFileContent(ctx.getEvent().getMessage().getAttachments().stream().findAny().orElseThrow())
				.map(String::lines)
				.flatMapMany(Flux::fromStream)
				.filter(l -> !l.startsWith("#"))
				.collectList()
				.map(lines -> parse(ctx.getAuthor(), lines))
				.flatMap(embed -> InteractiveMenu.create(m -> {
							m.setContent("Here is the announcement that is going to be sent to all servers. Is this alright? React to confirm.");
							m.setEmbed(embed);
						})
						.addReactionItem("success", interaction -> ctx.reply("Sending announcement, please wait...")
								.then(GDEvents.getExistingSubscribedGuilds(ctx.getBot(), "where channelChangelogId > 0")
										.map(GDSubscribedGuilds::getChannelChangelogId)
										.map(Snowflake::of)
										.flatMap(gdServiceMediator.getBroadcastPreloader()::preloadChannel)
										.publishOn(gdServiceMediator.getGdEventScheduler())
										.flatMap(channel -> channel.createEmbed(embed).onErrorResume(e -> Mono.empty()))
										.then(ctx.reply("Announcement sent to all guilds!")))
								.then())
						.addReactionItem("cross", interaction -> Mono.fromRunnable(interaction::closeMenu))
						.deleteMenuOnClose(true)
						.open(ctx))
				.then();
	}
	
	private Consumer<EmbedCreateSpec> parse(User author, List<String> lines) {
		final var expectingFieldName = 1;
		final var expectingFieldContent = 2;
		var state = 0;
		String title = null;
		var fieldNames = new ArrayList<String>();
		var fieldContents = new ArrayList<String>();
		for (var l : lines) {
			if (title == null) {
				title = l;
				state = expectingFieldName;
			} else {
				if (state == expectingFieldName) {
					if (!l.isBlank()) {
						fieldNames.add(l);
						state = expectingFieldContent;
					}
				} else {
					if (fieldContents.size() < fieldNames.size()) {
						if (!l.isBlank()) {
							fieldContents.add(l);
						}
					} else {
						if (l.isBlank()) {
							state = expectingFieldName;
						} else {
							var lastIndex = fieldContents.size() - 1;
							var newL = fieldContents.get(lastIndex) + "\n" + l;
							fieldContents.set(lastIndex, newL);
						}
					}
				}
			}
		}
		if (title == null || fieldNames.size() != fieldContents.size()) {
			throw new CommandFailedException("The input file has invalid or malformed content.");
		}
		var fTitle = title;
		return embed -> {
			embed.setColor(Color.BLUE);
			embed.setTimestamp(Instant.now());
			embed.setAuthor(DiscordFormatter.formatUser(author), null, author.getAvatarUrl());
			embed.setTitle(fTitle);
			for (var i = 0 ; i < fieldNames.size() ; i++) {
				var name = fieldNames.get(i);
				var content = fieldContents.get(i);
				embed.addField(name, content, false);
			}
		};
	}
	
	private Mono<String> getFileContent(Attachment attachment) {
		return fileClient.get()
				.uri(attachment.getUrl())
				.responseSingle((response, content) -> {
					if (response.status().code() / 100 != 2) {
						return Mono.error(new CommandFailedException("Received " + response.status().code() + " "
								+ response.status().reasonPhrase() + " from Discord CDN"));
					}
					return content.asString();
				})
				.retryWhen(Retry.anyOf(IOException.class)
						.exponentialBackoffWithJitter(Duration.ofSeconds(1), Duration.ofMinutes(1)))
				.timeout(Duration.ofMinutes(2), Mono.error(new CommandFailedException("Cannot download file, Discord "
						+ "CDN took too long to respond. Try again later.")));
	}
}
