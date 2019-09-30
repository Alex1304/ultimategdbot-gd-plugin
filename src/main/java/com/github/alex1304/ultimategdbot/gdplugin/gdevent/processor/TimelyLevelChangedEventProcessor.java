package com.github.alex1304.ultimategdbot.gdplugin.gdevent.processor;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import com.github.alex1304.jdash.entity.GDTimelyLevel.TimelyType;
import com.github.alex1304.jdash.entity.GDUser;
import com.github.alex1304.jdashevents.event.TimelyLevelChangedEvent;
import com.github.alex1304.ultimategdbot.api.Bot;
import com.github.alex1304.ultimategdbot.gdplugin.GDServiceMediator;
import com.github.alex1304.ultimategdbot.gdplugin.database.GDSubscribedGuilds;
import com.github.alex1304.ultimategdbot.gdplugin.gdevent.LateTimelyLevelChangedEvent;
import com.github.alex1304.ultimategdbot.gdplugin.util.GDUtils;

import discord4j.core.object.entity.Message;
import discord4j.core.object.entity.MessageChannel;
import discord4j.core.object.entity.PrivateChannel;
import discord4j.core.object.entity.Role;
import discord4j.core.spec.MessageCreateSpec;
import reactor.core.publisher.Mono;

public class TimelyLevelChangedEventProcessor extends AbstractGDEventProcessor<TimelyLevelChangedEvent> {
	
	public TimelyLevelChangedEventProcessor(GDServiceMediator gdServiceMediator, Bot bot) {
		super(TimelyLevelChangedEvent.class, gdServiceMediator, bot);
	}

	@Override
	String logText0(TimelyLevelChangedEvent event) {
		return "**" + (event.getTimelyLevel().getType() == TimelyType.WEEKLY ? "Weekly Demon Changed" : "Daily Level Changed")
				+ "** for " + event.getTimelyLevel().getType().toString() + " #" + event.getTimelyLevel().getId();
	}

	@Override
	String databaseField() {
		return "channelTimelyLevelsId";
	}

	@Override
	Mono<Message> sendOne(TimelyLevelChangedEvent event, MessageChannel channel, Optional<Role> roleToTag) {
		var isWeekly = event.getTimelyLevel().getType() == TimelyType.WEEKLY;
		var headerTitle = isWeekly ? "Weekly Demon" : "Daily Level";
		var headerLink = isWeekly ? "https://i.imgur.com/kcsP5SN.png" : "https://i.imgur.com/enpYuB8.png";
		return event.getTimelyLevel().getLevel()
				.flatMap(level -> GDUtils.shortLevelView(bot, level, headerTitle + " #" + event.getTimelyLevel().getId(), headerLink)
						.<Consumer<MessageCreateSpec>>map(embed -> mcs -> {
							mcs.setContent((event instanceof LateTimelyLevelChangedEvent
									? "[Late announcement] "
									: roleToTag.isPresent() ? roleToTag.get().getMention() + " " : "")
									+ (channel instanceof PrivateChannel ? "Congratulations for getting the " + headerTitle + "!"
											: "There is a new " + headerTitle + " on Geometry Dash!!!"));
							mcs.setEmbed(embed);
						}).flatMap(channel::createMessage).onErrorResume(e -> Mono.empty()));
	}

	@Override
	void onBroadcastSuccess(TimelyLevelChangedEvent event, List<Message> broadcastResult) {
	}

	@Override
	long entityFieldChannel(GDSubscribedGuilds subscribedGuild) {
		return subscribedGuild.getChannelTimelyLevelsId();
	}

	@Override
	long entityFieldRole(GDSubscribedGuilds subscribedGuild) {
		return subscribedGuild.getRoleTimelyLevelsId();
	}

	@Override
	Mono<Long> accountIdGetter(TimelyLevelChangedEvent event) {
		return event.getTimelyLevel().getLevel()
				.flatMap(level -> gdServiceMediator.getGdClient().searchUser("" + level.getCreatorID()))
				.map(GDUser::getAccountId);
	}
}
