package com.github.alex1304.ultimategdbot.gdplugin;

import static com.github.alex1304.rdi.config.FactoryMethod.*;
import static com.github.alex1304.rdi.config.Injectable.*;
import static com.github.alex1304.ultimategdbot.api.service.CommonServices.BOT;

import java.util.Set;

import com.github.alex1304.jdash.client.AuthenticatedGDClient;
import com.github.alex1304.jdash.graphics.SpriteFactory;
import com.github.alex1304.rdi.ServiceReference;
import com.github.alex1304.rdi.config.ServiceDescriptor;
import com.github.alex1304.ultimategdbot.api.BotConfig;
import com.github.alex1304.ultimategdbot.api.service.ServiceDeclarator;
import com.github.alex1304.ultimategdbot.gdplugin.gdevent.GDEventService;
import com.github.alex1304.ultimategdbot.gdplugin.level.GDLevelService;
import com.github.alex1304.ultimategdbot.gdplugin.levelrequest.GDLevelRequestService;
import com.github.alex1304.ultimategdbot.gdplugin.user.GDUserService;

import reactor.core.publisher.Mono;

public final class GDServices implements ServiceDeclarator {
	
	public static final ServiceReference<GDService> GD = ServiceReference.ofType(GDService.class);
	public static final ServiceReference<AuthenticatedGDClient> CLIENT = ServiceReference.of("gdplugin.client", AuthenticatedGDClient.class);
	public static final ServiceReference<SpriteFactory> SPRITE_FACTORY = ServiceReference.of("gdplugin.spriteFactory", SpriteFactory.class);
	public static final ServiceReference<GDEventService> EVENT = ServiceReference.ofType(GDEventService.class);
	public static final ServiceReference<GDLevelRequestService> LEVEL_REQUEST = ServiceReference.ofType(GDLevelRequestService.class);
	public static final ServiceReference<GDLevelService> LEVEL = ServiceReference.ofType(GDLevelService.class);
	public static final ServiceReference<GDUserService> USER = ServiceReference.ofType(GDUserService.class);

	@Override
	public Set<ServiceDescriptor> declareServices(BotConfig botConfig) {
		return Set.of(
				ServiceDescriptor.builder(GD)
						.setFactoryMethod(staticFactory("create", Mono.class,
								value(botConfig, BotConfig.class),
								ref(BOT),
								ref(CLIENT),
								ref(SPRITE_FACTORY),
								ref(EVENT),
								ref(LEVEL_REQUEST),
								ref(LEVEL),
								ref(USER)))
						.build(),
				ServiceDescriptor.builder(CLIENT)
						.setFactoryMethod(externalStaticFactory(GDService.class, "createGDClient", Mono.class,
								value(botConfig, BotConfig.class)))
						.build(),
				ServiceDescriptor.builder(SPRITE_FACTORY)
						.setFactoryMethod(externalStaticFactory(GDService.class, "createSpriteFactory", Mono.class))
						.build(),
				ServiceDescriptor.builder(EVENT)
						.setFactoryMethod(constructor(
								value(botConfig, BotConfig.class),
								ref(BOT),
								ref(CLIENT),
								ref(LEVEL),
								ref(USER)))
						.build(),
				ServiceDescriptor.builder(LEVEL_REQUEST)
						.setFactoryMethod(constructor(
								ref(BOT),
								ref(LEVEL)))
						.build(),
				ServiceDescriptor.builder(LEVEL)
						.setFactoryMethod(constructor(
								ref(BOT)))
						.build(),
				ServiceDescriptor.builder(USER)
						.setFactoryMethod(constructor(
								value(botConfig, BotConfig.class),
								ref(BOT),
								ref(CLIENT),
								ref(SPRITE_FACTORY)))
						.build()
		);
	}

}
