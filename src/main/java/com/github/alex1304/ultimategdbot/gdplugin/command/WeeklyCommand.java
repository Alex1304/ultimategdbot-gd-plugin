package com.github.alex1304.ultimategdbot.gdplugin.command;

import com.github.alex1304.ultimategdbot.api.command.Context;
import com.github.alex1304.ultimategdbot.api.command.annotated.CommandAction;
import com.github.alex1304.ultimategdbot.api.command.annotated.CommandDoc;
import com.github.alex1304.ultimategdbot.api.command.annotated.CommandSpec;
import com.github.alex1304.ultimategdbot.gdplugin.GDServiceMediator;
import com.github.alex1304.ultimategdbot.gdplugin.util.GDUtils;

import reactor.core.publisher.Mono;

@CommandSpec(
		aliases = { "weekly", "weeklydemon" },
		shortDescription = "Displays info on the current Weekly demon."
)
public class WeeklyCommand {

	private final GDServiceMediator gdServiceMediator;
	
	public WeeklyCommand(GDServiceMediator gdServiceMediator) {
		this.gdServiceMediator = gdServiceMediator;
	}

	@CommandAction
	@CommandDoc("Displays level info as well as cooldown until the next Weekly demon.")
	public Mono<Void> run(Context ctx) {
		return GDUtils.displayTimelyInfo(ctx, gdServiceMediator.getGdClient(), true).then();
	}
}
