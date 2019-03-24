package com.github.alex1304.ultimategdbot.gdplugin;

import java.time.Duration;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;

import com.github.alex1304.jdash.client.AuthenticatedGDClient;
import com.github.alex1304.jdash.exception.NoTimelyAvailableException;
import com.github.alex1304.ultimategdbot.api.Command;
import com.github.alex1304.ultimategdbot.api.Context;
import com.github.alex1304.ultimategdbot.api.PermissionLevel;
import com.github.alex1304.ultimategdbot.api.utils.BotUtils;
import com.github.alex1304.ultimategdbot.api.utils.reply.ReplyMenuBuilder;

import discord4j.core.object.entity.Channel.Type;
import reactor.core.publisher.Mono;

public class TimelyCommand implements Command {
	
	private final AuthenticatedGDClient gdClient;
	private final boolean isWeekly;

	public TimelyCommand(AuthenticatedGDClient gdClient, boolean isWeekly) {
		this.gdClient = Objects.requireNonNull(gdClient);
		this.isWeekly = isWeekly;
	}

	@Override
	public Mono<Void> execute(Context ctx) {
		var rb = new ReplyMenuBuilder(ctx, true, false);
		var timelyMono = isWeekly ? gdClient.getWeeklyDemon() : gdClient.getDailyLevel();
		var headerTitle = isWeekly ? "Weekly Demon" : "Daily Level";
		var headerLink = isWeekly ? "https://i.imgur.com/kcsP5SN.png" : "https://i.imgur.com/enpYuB8.png";
		return timelyMono.flatMap(timely -> timely.getLevel()
				.flatMap(level -> GDUtils.levelView(ctx, level, headerTitle + " #" + timely.getId(), headerLink)
						.flatMap(embed -> {
							var cooldown = Duration.ofSeconds(timely.getCooldown());
							var formattedCooldown = BotUtils.formatTimeMillis(cooldown);
							return rb.build(ctx.getEvent().getMessage().getAuthor().get().getMention() + ", here is the " + headerTitle + " of today. "
									+ "Next " + headerTitle + " in " + formattedCooldown + ".", embed);
						})))
						.then();
	}

	@Override
	public Set<String> getAliases() {
		return isWeekly ? Set.of("weekly", "weeklydemon") : Set.of("daily", "dailylevel");
	}

	@Override
	public Set<Command> getSubcommands() {
		return Set.of();
	}

	@Override
	public String getDescription() {
		return "Displays info on the current " + (isWeekly ? "Weekly Demon" : "Daily level") + ".";
	}

	@Override
	public String getLongDescription() {
		return "It shows information on the level as the `level` command would do, as well as the cooldown until next " + (isWeekly ? "Weekly Demon" : "Daily level") + ".";
	}

	@Override
	public String getSyntax() {
		return "";
	}

	@Override
	public PermissionLevel getPermissionLevel() {
		return PermissionLevel.PUBLIC;
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
					.flatMap(cross -> ctx.reply(cross + " There is no " + (isWeekly ? "Weekly Demon" : "Daily level")
							+ " available right now. Come back later!")).doOnError(__ -> {})
					.subscribe();
		});
		return map;
	}
}
