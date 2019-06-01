package com.github.alex1304.ultimategdbot.gdplugin.levelrequest;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

import com.github.alex1304.ultimategdbot.api.Command;
import com.github.alex1304.ultimategdbot.api.Context;
import com.github.alex1304.ultimategdbot.api.Plugin;
import com.github.alex1304.ultimategdbot.gdplugin.GDPlugin;
import com.github.alex1304.ultimategdbot.gdplugin.database.GDLevelRequestsSettings;

import discord4j.core.object.entity.Channel.Type;
import discord4j.core.object.entity.Role;
import discord4j.core.object.util.Snowflake;
import reactor.core.publisher.Mono;
import reactor.function.TupleUtils;

public class LevelRequestCommand implements Command {
	
	private static final String HEADER = "**__Get other players to play your levels and give feedback with the Level Request feature!__**\n\n";
	
	private final GDPlugin plugin;
	
	public LevelRequestCommand(GDPlugin plugin) {
		this.plugin = Objects.requireNonNull(plugin);
	}

	@Override
	public Mono<Void> execute(Context ctx) {
		var guildId = ctx.getEvent().getGuildId().orElseThrow();
		return Mono.zip(ctx.getBot().getEmoji("success"), ctx.getBot().getEmoji("failed"))
				.flatMap(TupleUtils.function((success, failed) -> ctx.getBot().getDatabase()
						//.findByIDOrCreate(GDLevelRequestsSettings.class, guildId.asLong(), GDLevelRequestsSettings::setGuildId)
						.findByID(GDLevelRequestsSettings.class, guildId.asLong())
						.switchIfEmpty(Mono.fromCallable(() -> {
							var lvlReqSettings = new GDLevelRequestsSettings();
							lvlReqSettings.setGuildId(guildId.asLong());
							return lvlReqSettings;
						}).flatMap(lvlReqSettings -> ctx.getBot().getDatabase().save(lvlReqSettings).thenReturn(lvlReqSettings)))
						.zipWhen(lvlReqSettings -> lvlReqSettings.getReviewerRoleId() == 0 ? Mono.just("*Not configured*") : ctx.getBot().getMainDiscordClient()
								.getRoleById(guildId, Snowflake.of(lvlReqSettings.getReviewerRoleId()))
								.map(Role::getName)
								.onErrorResume(e -> Mono.empty())
								.defaultIfEmpty("*unknown role*"))
						.flatMap(TupleUtils.function((lvlReqSettings, reviewerRole) -> ctx.reply(HEADER + (lvlReqSettings.getIsOpen()
								? success + " level requests are OPENED" : failed + " level requests are CLOSED") + "\n\n"
								+ "**Submission channel:** " + formatChannel(lvlReqSettings.getSubmissionQueueChannelId()) + "\n"
								+ "**Reviewed levels channel:** " + formatChannel(lvlReqSettings.getReviewedLevelsChannelId()) + "\n"
								+ "**Reviewer role:** " + reviewerRole + "\n"
								+ "**Number of reviews required:** " + formatNumber(lvlReqSettings.getMaxReviewsRequired()) + "\n"
								+ "**Max queued submissions per person:** " + formatNumber(lvlReqSettings.getMaxQueuedSubmissionsPerPerson()) + "\n\n"
								+ "Server admins can change the above values via `" + ctx.getPrefixUsed() + "setup`, "
								+ "and they can " + (lvlReqSettings.getIsOpen() ? "close" : "open") + " level requests by using `"
								+ ctx.getPrefixUsed() + "levelrequest toggle`.")))))
				.then();
	}
	
	private static String formatChannel(long id) {
		return id == 0 ? "*Not configured*" : "<#" + id + ">";
	}
	
	private static String formatNumber(int n) {
		return n == 0 ? "*Not configured*" : "" + n;
	}

	@Override
	public Set<String> getAliases() {
		return Set.of("levelrequest", "lvlreq");
	}
	
	@Override
	public Set<Command> getSubcommands() {
		return Set.of(new LevelRequestToggleCommand(plugin), new LevelRequestSubmitCommand(plugin), new LevelRequestReviewCommand(plugin));
	}

	@Override
	public String getDescription() {
		return "Submit your levels for other players to give feedback on them.";
	}

	@Override
	public String getLongDescription() {
		return "Submit your levels in the submission queue channel using `levelrequest submit <your_level_ID>`. Then people with the "
				+ "reviewer role will review your submission, and once you get a certain number of reviews your level will be moved to "
				+ "the reveiwed levels channel and you will be notified in DMs. You are only allowed to submit a limited number of "
				+ "levels at once.\n\n"
				+ "For more details on how level requests work, check out this guide: <https://github.com/Alex1304/ultimategdbot-gd-plugin"
				+ "/wiki/Level-Requests-Tutorial>";
	}

	@Override
	public String getSyntax() {
		return "";
	}
	
	@Override
	public EnumSet<Type> getChannelTypesAllowed() {
		return EnumSet.of(Type.GUILD_TEXT);
	}

	@Override
	public Plugin getPlugin() {
		return plugin;
	}
}
