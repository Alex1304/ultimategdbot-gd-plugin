package com.github.alex1304.ultimategdbot.gdplugin.command;

import com.github.alex1304.ultimategdbot.api.command.Context;
import com.github.alex1304.ultimategdbot.api.command.annotated.CommandAction;
import com.github.alex1304.ultimategdbot.api.command.annotated.CommandDoc;
import com.github.alex1304.ultimategdbot.api.command.annotated.CommandDescriptor;
import com.github.alex1304.ultimategdbot.gdplugin.GDService;
import com.github.alex1304.ultimategdbot.gdplugin.util.GDLevels;

import reactor.core.publisher.Mono;

@CommandDescriptor(
		aliases = { "weekly", "weeklydemon" },
		shortDescription = "tr:GDStrings/weekly_desc"
)
public class WeeklyCommand {

	private final GDService gdService;
	
	public WeeklyCommand(GDService gdService) {
		this.gdService = gdService;
	}

	@CommandAction
	@CommandDoc("tr:GDStrings/weekly_run")
	public Mono<Void> run(Context ctx) {
		return GDLevels.sendTimelyInfo(ctx, gdService.getGdClient(), true).then();
	}
}
