package com.github.alex1304.ultimategdbot.gdplugin.command;

import com.github.alex1304.ultimategdbot.api.command.Context;
import com.github.alex1304.ultimategdbot.api.command.annotated.CommandAction;
import com.github.alex1304.ultimategdbot.api.command.annotated.CommandDescriptor;
import com.github.alex1304.ultimategdbot.api.command.annotated.CommandDoc;
import com.github.alex1304.ultimategdbot.api.service.Root;
import com.github.alex1304.ultimategdbot.gdplugin.GDService;

import reactor.core.publisher.Mono;

@CommandDescriptor(
		aliases = { "daily", "dailylevel" },
		shortDescription = "tr:GDStrings/daily_desc"
)
public final class DailyCommand {

	@Root
	private GDService gd;
	
	@CommandAction
	@CommandDoc("tr:GDStrings/daily_run")
	public Mono<Void> run(Context ctx) {
		return gd.level().sendTimelyInfo(ctx, gd.client(), false).then();
	}
}
