package com.github.alex1304.ultimategdbot.gdplugin.level;

import com.github.alex1304.ultimategdbot.api.command.Context;
import com.github.alex1304.ultimategdbot.api.command.annotated.CommandAction;
import com.github.alex1304.ultimategdbot.api.command.annotated.CommandDoc;
import com.github.alex1304.ultimategdbot.api.command.annotated.CommandSpec;
import com.github.alex1304.ultimategdbot.gdplugin.GDServiceMediator;
import com.github.alex1304.ultimategdbot.gdplugin.util.GDUtils;

import reactor.core.publisher.Mono;

@CommandSpec(
		aliases = { "daily", "dailylevel" },
		shortDescription = "Displays info on the current Daily level."
)
public class DailyCommand {

	private final GDServiceMediator gdServiceMediator;
	
	public DailyCommand(GDServiceMediator gdServiceMediator) {
		this.gdServiceMediator = gdServiceMediator;
	}

	@CommandAction
	@CommandDoc("Displays level info as well as cooldown until the next Daily level.")
	public Mono<Void> run(Context ctx) {
		return GDUtils.displayTimelyInfo(ctx, gdServiceMediator.getGdClient(), false).then();
	}
}
