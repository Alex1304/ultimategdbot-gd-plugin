package com.github.alex1304.ultimategdbot.gdplugin.gdevents;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import com.github.alex1304.jdash.entity.GDUser;
import com.github.alex1304.jdashevents.event.AwardedLevelAddedEvent;
import com.github.alex1304.ultimategdbot.gdplugin.GDAwardedLevels;
import com.github.alex1304.ultimategdbot.gdplugin.GDPlugin;
import com.github.alex1304.ultimategdbot.gdplugin.GDSubscribedGuilds;
import com.github.alex1304.ultimategdbot.gdplugin.GDUtils;

import discord4j.core.object.entity.Message;
import discord4j.core.object.entity.MessageChannel;
import discord4j.core.object.entity.PrivateChannel;
import discord4j.core.object.entity.Role;
import discord4j.core.spec.MessageCreateSpec;
import reactor.core.publisher.Mono;

public class AwardedLevelAddedEventProcessor extends AbstractGDEventProcessor<AwardedLevelAddedEvent> {
	
	private static final String[] RANDOM_MESSAGES = new String[] {
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
	
	private volatile Mono<Message> testMessage;

	public AwardedLevelAddedEventProcessor(GDPlugin plugin) {
		super(AwardedLevelAddedEvent.class, plugin);
	}
	
	@Override
	public Mono<Void> process0(AwardedLevelAddedEvent t) {
		plugin.getBot().getDatabase().findByID(GDAwardedLevels.class, t.getAddedLevel().getId())
				.switchIfEmpty(Mono.just(new GDAwardedLevels()).doOnNext(awarded -> {
					awarded.setLevelId(t.getAddedLevel().getId());
					awarded.setInsertDate(Timestamp.from(Instant.now()));
					awarded.setDownloads(t.getAddedLevel().getDownloads());
					awarded.setLikes(t.getAddedLevel().getLikes());
				})).flatMap(plugin.getBot().getDatabase()::save).subscribe();
		return super.process0(t);
	}

	@Override
	String logText0(AwardedLevelAddedEvent event) {
		return (event instanceof TestEvent ? "**Test**" : "**Awarded Level Added**") + " for level " + GDUtils.levelToString(event.getAddedLevel());
	}

	@Override
	String databaseField() {
		return "channelAwardedLevelsId";
	}

	@Override
	Mono<Message> sendOne(AwardedLevelAddedEvent event, MessageChannel channel, Optional<Role> roleToTag)  {
		return GDUtils.shortLevelView(plugin.getBot(), event.getAddedLevel(), "New rated level!", "https://i.imgur.com/asoMj1W.png").<Consumer<MessageCreateSpec>>map(embed -> mcs -> {
			mcs.setContent((event instanceof LateAwardedLevelAddedEvent ? "[Late announcement] " : roleToTag.isPresent() ? roleToTag.get().getMention() + " " : "")
					+ (channel instanceof PrivateChannel ? "Congratulations for getting your level rated!"
							: RANDOM_MESSAGES[AbstractGDEventProcessor.RANDOM_GENERATOR.nextInt(RANDOM_MESSAGES.length)]));
			mcs.setEmbed(embed);
		}).flatMap(mcs -> event instanceof TestEvent ? channel.type().then(sendTestMessage()) : channel.createMessage(mcs)).onErrorResume(e -> Mono.empty());
	}
	
	Mono<Message> sendTestMessage() {
		if (testMessage == null) {
			testMessage = plugin.getBot().log("test").cache();
		}
		return testMessage;
	}

	@Override
	void onBroadcastSuccess(AwardedLevelAddedEvent event, List<Message> broadcastResult) {
		if (plugin.getBroadcastedLevels().size() >= 10) {
			var firstKey = plugin.getBroadcastedLevels().entrySet().stream().findFirst().get().getKey();
			plugin.getBroadcastedLevels().remove(firstKey);
		}
		plugin.getBroadcastedLevels().put(event.getAddedLevel().getId(), broadcastResult);
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
	Mono<Long> accountIdGetter(AwardedLevelAddedEvent event) {
		return plugin.getGdClient().searchUser("" + event.getAddedLevel().getCreatorID()).map(GDUser::getAccountId);
	}
}
