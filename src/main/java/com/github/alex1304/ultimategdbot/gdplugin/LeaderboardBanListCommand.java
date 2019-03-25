package com.github.alex1304.ultimategdbot.gdplugin;

import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;

import com.github.alex1304.jdash.client.AuthenticatedGDClient;
import com.github.alex1304.jdash.entity.GDUser;
import com.github.alex1304.ultimategdbot.api.Command;
import com.github.alex1304.ultimategdbot.api.Context;
import com.github.alex1304.ultimategdbot.api.PermissionLevel;
import com.github.alex1304.ultimategdbot.api.utils.reply.PaginatedReplyMenuBuilder;

import discord4j.core.object.entity.Channel.Type;
import reactor.core.publisher.Mono;

public class LeaderboardBanListCommand implements Command {
	
	private final AuthenticatedGDClient gdClient;
	
	public LeaderboardBanListCommand(AuthenticatedGDClient gdClient) {
		this.gdClient = Objects.requireNonNull(gdClient);
	}

	@Override
	public Mono<Void> execute(Context ctx) {
		return ctx.getBot().getDatabase().query(GDLeaderboardBans.class, "from GDLeaderboardBans")
				.flatMap(ban -> gdClient.getUserByAccountId(ban.getAccountId()))
				.onErrorResume(e -> Mono.empty())
				.map(GDUser::getName)
				.collectSortedList(String.CASE_INSENSITIVE_ORDER)
				.map(banList -> {
					var sb = new StringBuilder("__**Leaderboard ban list:**__\n\n");
					banList.forEach(ban -> sb.append(ban).append("\n"));
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
		return Map.of();
	}

}
