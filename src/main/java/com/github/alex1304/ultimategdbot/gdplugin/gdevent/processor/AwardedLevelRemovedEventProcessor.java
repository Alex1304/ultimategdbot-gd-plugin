package com.github.alex1304.ultimategdbot.gdplugin.gdevent.processor;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import com.github.alex1304.jdash.entity.GDUser;
import com.github.alex1304.jdashevents.event.AwardedLevelRemovedEvent;
import com.github.alex1304.ultimategdbot.gdplugin.GDPlugin;
import com.github.alex1304.ultimategdbot.gdplugin.database.GDSubscribedGuilds;
import com.github.alex1304.ultimategdbot.gdplugin.gdevent.LateAwardedLevelRemovedEvent;
import com.github.alex1304.ultimategdbot.gdplugin.util.GDUtils;

import discord4j.core.object.entity.Message;
import discord4j.core.object.entity.MessageChannel;
import discord4j.core.object.entity.PrivateChannel;
import discord4j.core.object.entity.Role;
import discord4j.core.spec.MessageCreateSpec;
import reactor.core.publisher.Mono;

public class AwardedLevelRemovedEventProcessor extends AbstractGDEventProcessor<AwardedLevelRemovedEvent> {
	
	private static final String[] RANDOM_MESSAGES = new String[] {
			"This level just got un-rated from Geometry Dash...",
			"Oh snap! RobTop decided to un-rate this level!",
			"RobTop took away stars from this level. FeelsBadMan",
			"Sad news. This level is no longer rated...",
			"NOOOOOOO I liked this level... No more stars :'("
	};

	public AwardedLevelRemovedEventProcessor(GDPlugin plugin) {
		super(AwardedLevelRemovedEvent.class, plugin);
	}

	@Override
	String logText0(AwardedLevelRemovedEvent event) {
		return "**Awarded Level Removed** for level " + GDUtils.levelToString(event.getRemovedLevel());
	}

	@Override
	String databaseField() {
		return "channelAwardedLevelsId";
	}

	@Override
	Mono<Message> sendOne(AwardedLevelRemovedEvent event, MessageChannel channel, Optional<Role> roleToTag) {
		return GDUtils.shortLevelView(plugin.getBot(), event.getRemovedLevel(), "Level un-rated...", "https://i.imgur.com/fPECXUz.png").<Consumer<MessageCreateSpec>>map(embed -> mcs -> {
			mcs.setContent((event instanceof LateAwardedLevelRemovedEvent ? "[Late announcement] " : roleToTag.isPresent() ? roleToTag.get().getMention() + " " : "")
					+ (channel instanceof PrivateChannel ? "I'm sorry to announce this, but your level got unrated..."
							: RANDOM_MESSAGES[AbstractGDEventProcessor.RANDOM_GENERATOR.nextInt(RANDOM_MESSAGES.length)]));
			mcs.setEmbed(embed);
		}).flatMap(channel::createMessage).onErrorResume(e -> Mono.empty());
	}

	@Override
	void onBroadcastSuccess(AwardedLevelRemovedEvent event, List<Message> broadcastResult) {
	}

	@Override
	long entityFieldChannel(GDSubscribedGuilds subscribedGuild) {
		return subscribedGuild.getChannelAwardedLevelsId();
	}

	@Override
	long entityFieldRole(GDSubscribedGuilds subscribedGuild) {
		return subscribedGuild.getRoleAwardedLevelsId();
	}

	@Override
	Mono<Long> accountIdGetter(AwardedLevelRemovedEvent event) {
		return plugin.getGdClient().searchUser("" + event.getRemovedLevel().getCreatorID()).map(GDUser::getAccountId);
	}
}
