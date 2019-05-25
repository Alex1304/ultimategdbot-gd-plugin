package com.github.alex1304.ultimategdbot.gdplugin.leaderboard;

import java.util.Objects;
import java.util.Set;

import com.github.alex1304.ultimategdbot.api.Command;
import com.github.alex1304.ultimategdbot.api.CommandFailedException;
import com.github.alex1304.ultimategdbot.api.Context;
import com.github.alex1304.ultimategdbot.api.PermissionLevel;
import com.github.alex1304.ultimategdbot.api.Plugin;
import com.github.alex1304.ultimategdbot.api.utils.ArgUtils;
import com.github.alex1304.ultimategdbot.api.utils.BotUtils;
import com.github.alex1304.ultimategdbot.gdplugin.GDPlugin;
import com.github.alex1304.ultimategdbot.gdplugin.database.GDLeaderboardBans;
import com.github.alex1304.ultimategdbot.gdplugin.util.GDUtils;

import reactor.core.publisher.Mono;

public class LeaderboardUnbanCommand implements Command {
	
	private final GDPlugin plugin;

	public LeaderboardUnbanCommand(GDPlugin plugin) {
		this.plugin = Objects.requireNonNull(plugin);
	}

	@Override
	public Mono<Void> execute(Context ctx) {
		ArgUtils.requireMinimumArgCount(ctx, 2);
		return GDUtils.stringToUser(ctx.getBot(), plugin.getGdClient(), ArgUtils.concatArgs(ctx, 1))
				.flatMap(gdUser -> ctx.getBot().getDatabase().findByID(GDLeaderboardBans.class, gdUser.getAccountId())
						.switchIfEmpty(Mono.error(new CommandFailedException("This user is already unbanned.")))
						.flatMap(ctx.getBot().getDatabase()::delete)
						.then(ctx.reply("**" + gdUser.getName() + "** has been unbanned from leaderboards!"))
						.then(ctx.getBot().getEmoji("info")
								.flatMap(info -> ctx.getBot().log(info + " Leaderboard ban removed: **" + gdUser.getName()
										+ "**, by **" + ctx.getEvent()
										.getMessage()
										.getAuthor()
										.map(BotUtils::formatDiscordUsername)
										.orElse("Unknown User#0000") + "**"))))
				.then();
	}

	@Override
	public Set<String> getAliases() {
		return Set.of("unban");
	}

	@Override
	public String getDescription() {
		return "Unbans a player from server leaderboards.";
	}

	@Override
	public String getLongDescription() {
		return "Players that are banned from leaderboards won't be displayed in the results of the `leaderboard` command in any server, "
				+ "regardless of whether they have an account linked. Bans are per GD account and not per Discord account, so linking "
				+ "with a different Discord account does not allow ban evasion. This command allows you to revoke an existing ban.";
	}

	@Override
	public String getSyntax() {
		return "<gd_name>";
	}

	@Override
	public PermissionLevel getPermissionLevel() {
		return PermissionLevel.BOT_ADMIN;
	}

	@Override
	public Plugin getPlugin() {
		return plugin;
	}

}
