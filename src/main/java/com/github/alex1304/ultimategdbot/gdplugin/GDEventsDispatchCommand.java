package com.github.alex1304.ultimategdbot.gdplugin;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;

import com.github.alex1304.jdash.client.AuthenticatedGDClient;
import com.github.alex1304.jdash.exception.NoTimelyAvailableException;
import com.github.alex1304.jdashevents.GDEventDispatcher;
import com.github.alex1304.jdashevents.event.AwardedLevelAddedEvent;
import com.github.alex1304.jdashevents.event.AwardedLevelRemovedEvent;
import com.github.alex1304.jdashevents.event.AwardedLevelUpdatedEvent;
import com.github.alex1304.jdashevents.event.GDEvent;
import com.github.alex1304.jdashevents.event.TimelyLevelChangedEvent;
import com.github.alex1304.ultimategdbot.api.Command;
import com.github.alex1304.ultimategdbot.api.Context;
import com.github.alex1304.ultimategdbot.api.InvalidSyntaxException;
import com.github.alex1304.ultimategdbot.api.PermissionLevel;
import com.github.alex1304.ultimategdbot.api.utils.ArgUtils;
import com.github.alex1304.ultimategdbot.gdplugin.gdevents.LateAwardedLevelAddedEvent;
import com.github.alex1304.ultimategdbot.gdplugin.gdevents.LateAwardedLevelRemovedEvent;
import com.github.alex1304.ultimategdbot.gdplugin.gdevents.LateTimelyLevelChangedEvent;
import com.github.alex1304.ultimategdbot.gdplugin.gdevents.TestEvent;

import discord4j.core.object.entity.Channel.Type;
import reactor.core.publisher.Mono;

public class GDEventsDispatchCommand implements Command {

	private final AuthenticatedGDClient gdClient;
	private final GDEventDispatcher gdEventDispatcher;

	public GDEventsDispatchCommand(AuthenticatedGDClient gdClient, GDEventDispatcher gdEventDispatcher) {
		this.gdClient = Objects.requireNonNull(gdClient);
		this.gdEventDispatcher = Objects.requireNonNull(gdEventDispatcher);
	}

	@Override
	public Mono<Void> execute(Context ctx) {
		ArgUtils.requireMinimumArgCount(ctx, 2);
		Mono<GDEvent> eventToDispatch;
		switch (ctx.getArgs().get(1)) {
			case "daily_level_changed":
				eventToDispatch = gdClient.getDailyLevel().map(TimelyLevelChangedEvent::new);
				break;
			case "late_daily_level_changed":
				eventToDispatch = gdClient.getDailyLevel().map(LateTimelyLevelChangedEvent::new);
				break;
			case "weekly_demon_changed":
				eventToDispatch = gdClient.getWeeklyDemon().map(TimelyLevelChangedEvent::new);
				break;
			case "late_weekly_demon_changed":
				eventToDispatch = gdClient.getWeeklyDemon().map(LateTimelyLevelChangedEvent::new);
				break;
			case "awarded_level_added":
				eventToDispatch = gdClient.getLevelById(convertSecondArgToID(ctx)).map(AwardedLevelAddedEvent::new);
				break;
			case "late_awarded_level_added":
				eventToDispatch = gdClient.getLevelById(convertSecondArgToID(ctx)).map(LateAwardedLevelAddedEvent::new);
				break;
			case "awarded_level_removed":
				eventToDispatch = gdClient.getLevelById(convertSecondArgToID(ctx)).map(AwardedLevelRemovedEvent::new);
				break;
			case "late_awarded_level_removed":
				eventToDispatch = gdClient.getLevelById(convertSecondArgToID(ctx)).map(LateAwardedLevelRemovedEvent::new);
				break;
			case "awarded_level_updated":
				eventToDispatch = gdClient.getLevelById(convertSecondArgToID(ctx)).map(level -> new AwardedLevelUpdatedEvent(level, level));
				break;
			case "test":
				eventToDispatch = gdClient.getLevelById(convertSecondArgToID(ctx)).map(TestEvent::new);
				break;
			default:
				return Mono.error(new InvalidSyntaxException(this));
		}
		
		return eventToDispatch.doOnNext(gdEventDispatcher::dispatch)
				.then(ctx.getBot().getEmoji("success").flatMap(emoji -> ctx.reply(emoji + " Event has been dispatched.")))
				.then();
	}
	
	private long convertSecondArgToID(Context ctx) {
		ArgUtils.requireMinimumArgCount(ctx, 3, "Please specify a levelID");
		return ArgUtils.getArgAsLong(ctx, 2);
	}

	@Override
	public Set<String> getAliases() {
		return Set.of("dispatch");
	}

	@Override
	public Set<Command> getSubcommands() {
		return Set.of();
	}

	@Override
	public String getDescription() {
		return "Manually dispatches a new event.";
	}

	@Override
	public String getLongDescription() {
		return "<event_name> can be one of:\n"
				+ "- `daily_level_changed`\n"
				+ "- `late_daily_level_changed`\n"
				+ "- `weekly_demon_changed`\n"
				+ "- `late_weekly_demon_changed`\n"
				+ "- `awarded_level_added <level_id>`\n"
				+ "- `late_awarded_level_added <level_id>`\n"
				+ "- `awarded_level_removed <level_id>`\n"
				+ "- `late_awarded_level_removed <level_id>`\n"
				+ "- `awarded_level_updated <level_id>`\n";
	}

	@Override
	public String getSyntax() {
		return "<event_name>";
	}

	@Override
	public PermissionLevel getPermissionLevel() {
		return PermissionLevel.BOT_OWNER;
	}

	@Override
	public EnumSet<Type> getChannelTypesAllowed() {
		return EnumSet.of(Type.GUILD_TEXT, Type.DM);
	}

	@Override
	public Map<Class<? extends Throwable>, BiConsumer<Throwable, Context>> getErrorActions() {
		var map = new HashMap<Class<? extends Throwable>, BiConsumer<Throwable, Context>>(GDUtils.DEFAULT_GD_ERROR_ACTIONS);
		map.put(NoTimelyAvailableException.class, (error, ctx) -> {
			ctx.getBot().getEmoji("cross")
					.flatMap(cross -> ctx.reply(cross + " There is no Daily/Weekly available right now. Come back later!"))
					.doOnError(__ -> {})
					.subscribe();
		});
		return map;
	}
}
