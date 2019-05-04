package com.github.alex1304.ultimategdbot.gdplugin;

import java.time.Duration;
import java.util.Objects;
import java.util.Set;

import com.github.alex1304.ultimategdbot.api.Command;
import com.github.alex1304.ultimategdbot.api.Context;
import com.github.alex1304.ultimategdbot.api.Plugin;
import com.github.alex1304.ultimategdbot.api.utils.BotUtils;
import com.github.alex1304.ultimategdbot.api.utils.reply.ReplyMenuBuilder;

import reactor.core.publisher.Mono;

public class TimelyCommand implements Command {
	
	private final GDPlugin plugin;
	private final boolean isWeekly;

	public TimelyCommand(GDPlugin plugin, boolean isWeekly) {
		this.plugin = Objects.requireNonNull(plugin);
		this.isWeekly = isWeekly;
	}

	@Override
	public Mono<Void> execute(Context ctx) {
		var rb = new ReplyMenuBuilder(ctx, true, false);
		var timelyMono = isWeekly ? plugin.getGdClient().getWeeklyDemon() : plugin.getGdClient().getDailyLevel();
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
	public Plugin getPlugin() {
		return plugin;
	}
}
