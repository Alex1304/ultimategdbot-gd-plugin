package com.github.alex1304.ultimategdbot.gdplugin;

import java.util.Comparator;
import java.util.Objects;
import java.util.Set;

import com.github.alex1304.ultimategdbot.api.Command;
import com.github.alex1304.ultimategdbot.api.Context;
import com.github.alex1304.ultimategdbot.api.PermissionLevel;
import com.github.alex1304.ultimategdbot.api.Plugin;
import com.github.alex1304.ultimategdbot.api.utils.BotUtils;
import com.github.alex1304.ultimategdbot.api.utils.reply.PaginatedReplyMenuBuilder;

import discord4j.core.object.util.Snowflake;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuples;

public class LeaderboardBanListCommand implements Command {
	
	private final GDPlugin plugin;

	public LeaderboardBanListCommand(GDPlugin plugin) {
		this.plugin = Objects.requireNonNull(plugin);
	}

	@Override
	public Mono<Void> execute(Context ctx) {
		return ctx.getBot().getDatabase().query(GDLeaderboardBans.class, "from GDLeaderboardBans")
				.flatMap(ban -> plugin.getGdClient().getUserByAccountId(ban.getAccountId()).map(user -> Tuples.of(ban, user)))
				.flatMap(tuple -> ctx.getBot().getMainDiscordClient().getUserById(Snowflake.of(tuple.getT1().getBannedBy()))
						.map(user -> Tuples.of(tuple.getT2(), BotUtils.formatDiscordUsername(user))))
				.onErrorResume(e -> Mono.empty())
				.collectSortedList(Comparator.comparing(tuple -> tuple.getT1().getName().toLowerCase()))
				.map(banList -> {
					var sb = new StringBuilder("__**Leaderboard ban list:**__\n\n");
					banList.forEach(ban -> sb.append(ban.getT1().getName()).append(", banned by ").append(ban.getT2()).append("\n"));
					if (banList.isEmpty()) {
						sb.append("*(No data)*\n");
					}
					return sb.toString();
				}).flatMap(new PaginatedReplyMenuBuilder(this, ctx, true, false, 800)::build)
				.then();
	}

	@Override
	public Set<String> getAliases() {
		return Set.of("ban_list", "banlist");
	}

	@Override
	public String getDescription() {
		return "Displays the list of players banned from leaderboards.";
	}

	@Override
	public String getLongDescription() {
		return "Players that are banned from leaderboards won't be displayed in the results of the `leaderboard` command in any server, "
				+ "regardless of whether they have an account linked. Bans are per GD account and not per Discord account, so linking "
				+ "with a different Discord account does not allow ban evasion. This command allows you to list people in the ban list.";
	}

	@Override
	public String getSyntax() {
		return "";
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
