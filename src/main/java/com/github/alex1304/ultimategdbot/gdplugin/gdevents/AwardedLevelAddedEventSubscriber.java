package com.github.alex1304.ultimategdbot.gdplugin.gdevents;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

import com.github.alex1304.jdashevents.event.AwardedLevelAddedEvent;
import com.github.alex1304.ultimategdbot.api.Bot;
import com.github.alex1304.ultimategdbot.gdplugin.GDUtils;

import discord4j.core.object.entity.Message;
import discord4j.core.object.entity.MessageChannel;
import discord4j.core.object.entity.Role;
import discord4j.core.spec.MessageCreateSpec;
import reactor.core.publisher.Mono;

public class AwardedLevelAddedEventSubscriber extends GDEventSubscriber<AwardedLevelAddedEvent> {

	public AwardedLevelAddedEventSubscriber(Bot bot, Map<Long, List<Message>> broadcastedMessages) {
		super(bot, broadcastedMessages);
	}

	@Override
	String logText(AwardedLevelAddedEvent event) {
		return "**Awarded Level Added** for level " + GDUtils.levelToString(event.getAddedLevel());
	}

	@Override
	String databaseField() {
		return "channelAwardedLevelsId";
	}

	@Override
	Mono<Message> sendOne(AwardedLevelAddedEvent event, MessageChannel channel, Optional<Role> roleToTag)  {
		var randomMessages = new String[] {
				"A new level has just been rated on Geometry Dash!!!",
				"RobTop just assigned a star value to this level!",
				"This level can now give you star and orb rewards. Go beat it now!",
				"I've been told that another generic and bad level got rated... Oh well, I might be wrong, go see by yourself!",
				"I challenge you to beat this level. RobTop just rated it!",
				"This level is 2hard5me. But RobTop's rate button has spoken and it can now give you some cool rewards!",
				"RobTop accidentally hit the wrong key and rated this level instead of turning on his coffee machine. But it's actually a good level. Go check it out!",
				"Hey look, a new level got rated OwO Do you think you can beat it?",
				"Roses are red. Violets are blue. This newly awarded level is waiting for you."
		};
		return GDUtils.shortLevelView(bot, event.getAddedLevel(), "New rated level!", "https://i.imgur.com/asoMj1W.png").<Consumer<MessageCreateSpec>>map(embed -> mcs -> {
			mcs.setContent((roleToTag.isPresent() ? roleToTag.get().getMention() + " " : "")
					+ randomMessages[GDEventSubscriber.RANDOM_GENERATOR.nextInt(randomMessages.length)]);
			mcs.setEmbed(embed);
		}).flatMap(spec -> channel.createMessage(spec)).onErrorResume(e -> Mono.empty());
	}

	@Override
	long getBroadcastKey(AwardedLevelAddedEvent event) {
		return event.getAddedLevel().getId();
	}
}
