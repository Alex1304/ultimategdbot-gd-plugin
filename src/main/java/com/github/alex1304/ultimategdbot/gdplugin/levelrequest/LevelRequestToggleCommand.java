package com.github.alex1304.ultimategdbot.gdplugin.levelrequest;

import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import com.github.alex1304.ultimategdbot.api.Command;
import com.github.alex1304.ultimategdbot.api.CommandFailedException;
import com.github.alex1304.ultimategdbot.api.Context;
import com.github.alex1304.ultimategdbot.api.Plugin;
import com.github.alex1304.ultimategdbot.gdplugin.GDPlugin;
import com.github.alex1304.ultimategdbot.gdplugin.database.GDLevelRequestsSettings;

import reactor.core.publisher.Mono;

public class LevelRequestToggleCommand implements Command {
	
	private final GDPlugin plugin;
	
	public LevelRequestToggleCommand(GDPlugin plugin) {
		this.plugin = Objects.requireNonNull(plugin);
	}

	@Override
	public Mono<Void> execute(Context ctx) {
		var guildId = ctx.getEvent().getGuildId().orElseThrow(() -> new CommandFailedException("This command can only be run in a server."));
		var isOpening = new AtomicBoolean();
		return ctx.getBot().getDatabase()
				.findByIDOrCreate(GDLevelRequestsSettings.class, guildId.asLong(), GDLevelRequestsSettings::setGuildId)
				.flatMap(lvlReqSettings -> !lvlReqSettings.getIsOpen() && (lvlReqSettings.getMaxQueuedSubmissionsPerPerson() == 0
						|| lvlReqSettings.getMaxReviewsRequired() == 0
						|| lvlReqSettings.getReviewedLevelsChannelId() == 0
						|| lvlReqSettings.getReviewerRoleId() == 0
						|| lvlReqSettings.getSubmissionQueueChannelId() == 0)
						? Mono.error(new CommandFailedException("Looks like level requests are not configured yet. "
								+ "Configure all entries starting with `lvlreq_` in `" + ctx.getPrefixUsed() + "setup` and try again."))
						: Mono.just(lvlReqSettings))
				.doOnNext(lvlReqSettings -> isOpening.set(!lvlReqSettings.getIsOpen()))
				.doOnNext(lvlReqSettings -> lvlReqSettings.setIsOpen(isOpening.get()))
				.flatMap(ctx.getBot().getDatabase()::save)
				.then(Mono.defer(() -> ctx.reply("Level requests are now " + (isOpening.get() ? "opened" : "closed") + "!")))
				.then();
	}

	@Override
	public Set<String> getAliases() {
		return Set.of("toggle");
	}

	@Override
	public String getDescription() {
		return "Enable or disable level requests for this server.";
	}

	@Override
	public String getLongDescription() {
		return "";
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
