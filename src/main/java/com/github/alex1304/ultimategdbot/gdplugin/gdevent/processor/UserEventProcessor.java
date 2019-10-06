package com.github.alex1304.ultimategdbot.gdplugin.gdevent.processor;

import java.util.List;
import java.util.Optional;

import com.github.alex1304.ultimategdbot.api.Bot;
import com.github.alex1304.ultimategdbot.gdplugin.GDServiceMediator;
import com.github.alex1304.ultimategdbot.gdplugin.database.GDSubscribedGuilds;
import com.github.alex1304.ultimategdbot.gdplugin.gdevent.UserEvent;
import com.github.alex1304.ultimategdbot.gdplugin.util.GDUsers;

import discord4j.core.object.entity.Message;
import discord4j.core.object.entity.MessageChannel;
import discord4j.core.object.entity.PrivateChannel;
import discord4j.core.object.entity.Role;
import reactor.core.publisher.Mono;

abstract class UserEventProcessor<E extends UserEvent> extends AbstractGDEventProcessor<E> {

	public UserEventProcessor(Class<E> clazz, GDServiceMediator gdServiceMediator, Bot bot) {
		super(clazz, gdServiceMediator, bot);
	}

	@Override
	String logText0(E event) {
		return "**" + eventName() + "** for user **" + event.getUser().getName() + "** (" + event.getUser().getAccountId() + ")";
	}

	@Override
	String databaseField() {
		return "channelGdModeratorsId";
	}

	@Override
	long entityFieldChannel(GDSubscribedGuilds subscribedGuild) {
		return subscribedGuild.getChannelGdModeratorsId();
	}

	@Override
	long entityFieldRole(GDSubscribedGuilds subscribedGuild) {
		return subscribedGuild.getRoleGdModeratorsId();
	}

	@Override
	Mono<Message> sendOne(E event, MessageChannel channel, Optional<Role> roleToTag) {
		return GDUsers.makeIconSet(bot, event.getUser(), gdServiceMediator.getSpriteFactory(), gdServiceMediator.getIconsCache())
				.flatMap(urls -> GDUsers.userProfileView(bot, Optional.empty(), event.getUser(), authorName(), authorIconUrl(), urls[0], urls[1]))
				.map(mcs -> mcs.andThen(mcs2 -> mcs2.setContent((roleToTag.isPresent() ? roleToTag.get().getMention() + " " : "")
						+ (channel instanceof PrivateChannel ? isPromotion() ? "Congratulations for being " : "I'm sorry to announce this, but you have been " // GNOMED
						: "A user has been ") + messageContent())))
				.flatMap(channel::createMessage)
				.onErrorResume(e -> Mono.empty());
	}

	@Override
	void onBroadcastSuccess(E event, List<Message> broadcastResult) {
	}
	
	abstract String authorName();
	abstract String authorIconUrl();
	abstract String messageContent();
	abstract String eventName();
	abstract boolean isPromotion();

	@Override
	Mono<Long> accountIdGetter(E event) {
		return Mono.just(event.getUser().getAccountId());
	}
}
