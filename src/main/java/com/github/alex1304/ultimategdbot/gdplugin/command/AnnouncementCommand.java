package com.github.alex1304.ultimategdbot.gdplugin.command;

import java.awt.Color;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

import com.github.alex1304.ultimategdbot.api.command.CommandFailedException;
import com.github.alex1304.ultimategdbot.api.command.Context;
import com.github.alex1304.ultimategdbot.api.command.PermissionLevel;
import com.github.alex1304.ultimategdbot.api.command.annotated.CommandAction;
import com.github.alex1304.ultimategdbot.api.command.annotated.CommandDescriptor;
import com.github.alex1304.ultimategdbot.api.command.annotated.CommandDoc;
import com.github.alex1304.ultimategdbot.api.command.annotated.CommandPermission;
import com.github.alex1304.ultimategdbot.api.util.menu.InteractiveMenu;
import com.github.alex1304.ultimategdbot.gdplugin.GDService;
import com.github.alex1304.ultimategdbot.gdplugin.database.GDEventConfigDao;
import com.github.alex1304.ultimategdbot.gdplugin.database.GDEventConfigData;
import com.github.alex1304.ultimategdbot.gdplugin.util.GDEvents;

import discord4j.core.object.entity.Attachment;
import discord4j.core.object.entity.User;
import discord4j.core.spec.EmbedCreateSpec;
import discord4j.rest.util.Snowflake;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import reactor.util.retry.Retry;

@CommandDescriptor(
		aliases = "announcement",
		shortDescription = "Sends a global bot announcement."
)
@CommandPermission(level = PermissionLevel.BOT_OWNER)
public class AnnouncementCommand {

	private final HttpClient fileClient = HttpClient.create().headers(h -> h.add("Content-Type", "text/plain"));
	private final GDService gdService;
	
	public AnnouncementCommand(GDService gdService) {
		this.gdService = gdService;
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
		if (ctx.event().getMessage().getAttachments().size() != 1) {
			return Mono.error(new CommandFailedException("You must attach exactly one file."));
		}
		
		var field = ctx.flags().get("channel").orElse("changelog");
		Function<GDEventConfigData, Optional<Snowflake>> func;
		switch (field) {
			case "awarded_levels":
				func = GDEventConfigData::channelAwardedLevels;
				break;
			case "timely_levels":
				func = GDEventConfigData::channelTimelyLevels;
				break;
			case "gd_moderators":
				func = GDEventConfigData::channelGdModerators;
				break;
			default:
				func = GDEventConfigData::channelChangelog;
		}
		
		return getFileContent(ctx.event().getMessage().getAttachments().stream().findAny().orElseThrow())
				.map(String::lines)
				.flatMapMany(Flux::fromStream)
				.filter(l -> !l.startsWith("#"))
				.collectList()
				.map(lines -> parse(ctx.author(), lines))
				.flatMap(embed -> InteractiveMenu.create(m -> {
							m.setContent("Here is the announcement that is going to be sent to all servers. Is this alright? React to confirm.");
							m.setEmbed(embed);
						})
						.addReactionItem("success", interaction -> ctx.reply("Sending announcement, please wait...")
								.then(ctx.bot().database().withExtension(GDEventConfigDao.class, dao -> dao.getAllWithChannel(field))
										.flatMapMany(Flux::fromIterable)
										.map(func)
										.flatMap(Mono::justOrEmpty)
										.map(ctx.bot().rest()::getChannelById)
										.publishOn(gdService.getGdEventScheduler())
										.flatMap(channel -> channel.createMessage(GDEvents.specToRequest(spec -> spec.setEmbed(embed)))
												.onErrorResume(e -> Mono.empty()))
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
			embed.setAuthor(author.getTag(), null, author.getAvatarUrl());
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
				.retryWhen(Retry.backoff(Long.MAX_VALUE, Duration.ofSeconds(1))
						.maxBackoff(Duration.ofMinutes(1))
						.filter(IOException.class::isInstance))
				.timeout(Duration.ofMinutes(2), Mono.error(new CommandFailedException("Cannot download file, Discord "
						+ "CDN took too long to respond. Try again later.")));
	}
}
