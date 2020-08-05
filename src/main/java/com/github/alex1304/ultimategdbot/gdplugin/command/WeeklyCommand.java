package com.github.alex1304.ultimategdbot.gdplugin.command;

import com.github.alex1304.ultimategdbot.api.command.Context;
import com.github.alex1304.ultimategdbot.api.command.annotated.CommandAction;
import com.github.alex1304.ultimategdbot.api.command.annotated.CommandDescriptor;
import com.github.alex1304.ultimategdbot.api.command.annotated.CommandDoc;
import com.github.alex1304.ultimategdbot.api.service.Root;
import com.github.alex1304.ultimategdbot.gdplugin.GDService;

import reactor.core.publisher.Mono;

@CommandDescriptor(
		aliases = { "weekly", "weeklydemon" },
		shortDescription = "tr:GDStrings/weekly_desc"
)
public final class WeeklyCommand {

	@Root
	private GDService gd;
	
	@CommandAction
	@CommandDoc("tr:GDStrings/weekly_run")
	public Mono<Void> run(Context ctx) {
		return gd.level().sendTimelyInfo(ctx, gd.client(), true).then();
	}
}
