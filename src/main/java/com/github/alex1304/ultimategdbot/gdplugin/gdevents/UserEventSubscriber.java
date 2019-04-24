package com.github.alex1304.ultimategdbot.gdplugin.gdevents;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.github.alex1304.ultimategdbot.gdplugin.GDPlugin;
import com.github.alex1304.ultimategdbot.gdplugin.GDSubscribedGuilds;
import com.github.alex1304.ultimategdbot.gdplugin.GDUtils;

import discord4j.core.object.entity.Message;
import discord4j.core.object.entity.MessageChannel;
import discord4j.core.object.entity.PrivateChannel;
import discord4j.core.object.entity.Role;
import reactor.core.publisher.Mono;

abstract class UserEventSubscriber<E extends UserEvent> extends AbstractGDEventProcessor<E> {

	private final GDPlugin plugin;

	public UserEventSubscriber(Class<E> clazz, GDPlugin plugin) {
		super(clazz, plugin);
		this.plugin = Objects.requireNonNull(plugin);
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
		return GDUtils.makeIconSet(plugin.getBot(), event.getUser(), plugin.getSpriteFactory(), plugin.getIconsCache())
				.flatMap(urls -> GDUtils.userProfileView(plugin.getBot(), Optional.empty(), event.getUser(), authorName(), authorIconUrl(), urls[0], urls[1]))
				.map(mcs -> mcs.andThen(mcs2 -> mcs2.setContent((roleToTag.isPresent() ? roleToTag.get().getMention() + " " : "")
						+ (channel instanceof PrivateChannel ? isPromotion() ? "Congratulations for being " : "I'm sorry to announce this, but you have been " // GNOMED
						: "A user has been ") + messageContent())))
				.flatMap(channel::createMessage)
				.onErrorResume(e -> {
					e.printStackTrace();
					return Mono.empty();
				});
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
