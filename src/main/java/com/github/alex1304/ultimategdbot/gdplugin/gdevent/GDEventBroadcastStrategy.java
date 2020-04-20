package com.github.alex1304.ultimategdbot.gdplugin.gdevent;

import java.util.List;

import com.github.alex1304.jdashevents.event.GDEvent;
import com.github.alex1304.ultimategdbot.api.Bot;
import com.github.alex1304.ultimategdbot.api.util.MessageSpecTemplate;
import com.github.alex1304.ultimategdbot.gdplugin.database.GDEventConfigDao;
import com.github.alex1304.ultimategdbot.gdplugin.util.GDEvents;
import com.github.alex1304.ultimategdbot.gdplugin.util.GDUsers;

import discord4j.core.object.entity.Message;
import discord4j.core.object.entity.User;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface GDEventBroadcastStrategy {
	
	static final GDEventBroadcastStrategy CREATING = new NewMessageBroadcastStrategy();
	static final GDEventBroadcastStrategy EDITING = new UpdateMessageBroadcastStrategy();
	
	Mono<Integer> broadcast(Bot bot, GDEvent event, GDEventProperties<? extends GDEvent> eventProps, BroadcastResultCache resultCache);
	
	static class NewMessageBroadcastStrategy implements GDEventBroadcastStrategy {
		@Override
		public Mono<Integer> broadcast(Bot bot, GDEvent event, GDEventProperties<? extends GDEvent> eventProps, BroadcastResultCache resultCache) {
			var guildBroadcast = bot.database().withExtension(GDEventConfigDao.class, dao -> dao.getAllWithChannel(eventProps.databaseField()))
					.flatMapMany(Flux::fromIterable)
					.flatMap(gsg -> eventProps.createMessageTemplate(event, gsg, null)
							.flatMap(msg -> bot.rest().getChannelById(eventProps.channelId(gsg))
									.createMessage(GDEvents.specToRequest(msg.toMessageCreateSpec()))
									.map(data -> new Message(bot.gateway(), data))
									.onErrorResume(e -> Mono.empty())));
			var dmBroadcast = eventProps.recipientAccountId(event)
					.flatMapMany(accountId -> GDUsers.getDiscordAccountsForGDUser(bot, accountId))
					.flatMap(User::getPrivateChannel)
					.flatMap(channel -> eventProps.createMessageTemplate(event, null, null)
							.map(msg -> new MessageSpecTemplate(eventProps.congratMessage(event), msg.getEmbed()))
							.map(MessageSpecTemplate::toMessageCreateSpec)
							.flatMap(channel::createMessage)
							.onErrorResume(e -> Mono.empty()));
			return Flux.merge(guildBroadcast, dmBroadcast)
					.collectList()
					.doOnNext(results -> eventProps.levelId(event).ifPresent(id -> resultCache.put(id, results)))
					.map(List::size);
		}
	}
	
	static class UpdateMessageBroadcastStrategy implements GDEventBroadcastStrategy {
		@Override
		public Mono<Integer> broadcast(Bot bot, GDEvent event, GDEventProperties<? extends GDEvent> eventProps, BroadcastResultCache resultCache) {
			return Mono.justOrEmpty(eventProps.levelId(event).flatMap(resultCache::get))
					.flatMapMany(Flux::fromIterable)
					.flatMap(old -> eventProps.createMessageTemplate(event, null, old)
							.map(MessageSpecTemplate::toMessageEditSpec)
							.flatMap(old::edit)
							.onErrorResume(e -> Mono.empty()))
					.collectList()
					.filter(results -> !results.isEmpty())
					.doOnNext(results -> eventProps.levelId(event).ifPresent(id -> resultCache.put(id, results)))
					.map(List::size)
					.defaultIfEmpty(0);
		}
	}
}
