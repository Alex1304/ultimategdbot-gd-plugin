package com.github.alex1304.ultimategdbot.gdplugin;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;

import com.github.alex1304.jdash.client.AuthenticatedGDClient;
import com.github.alex1304.ultimategdbot.api.Command;
import com.github.alex1304.ultimategdbot.api.Context;
import com.github.alex1304.ultimategdbot.api.PermissionLevel;
import com.github.alex1304.ultimategdbot.api.utils.BotUtils;
import com.github.alex1304.ultimategdbot.api.utils.reply.PaginatedReplyMenuBuilder;

import discord4j.core.object.entity.Channel.Type;
import discord4j.core.object.util.Snowflake;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuples;

public class LeaderboardBanListCommand implements Command {
	
	private final AuthenticatedGDClient gdClient;
	
	public LeaderboardBanListCommand(AuthenticatedGDClient gdClient) {
		this.gdClient = Objects.requireNonNull(gdClient);
	}

	@Override
	public Mono<Void> execute(Context ctx) {
		return ctx.getBot().getDatabase().query(GDLeaderboardBans.class, "from GDLeaderboardBans")
				.flatMap(ban -> gdClient.getUserByAccountId(ban.getAccountId()).map(user -> Tuples.of(ban, user)))
				.flatMap(tuple -> ctx.getBot().getDiscordClients().next()
						.flatMap(client -> client.getUserById(Snowflake.of(tuple.getT1().getBannedBy())))
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
	public Set<Command> getSubcommands() {
		return Set.of();
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
	public EnumSet<Type> getChannelTypesAllowed() {
		return EnumSet.of(Type.GUILD_TEXT, Type.DM);
	}

	@Override
	public Map<Class<? extends Throwable>, BiConsumer<Throwable, Context>> getErrorActions() {
		return GDUtils.DEFAULT_GD_ERROR_ACTIONS;
	}

}
