package com.github.alex1304.ultimategdbot.gdplugin.account;

import java.util.Objects;
import java.util.Set;

import com.github.alex1304.ultimategdbot.api.Command;
import com.github.alex1304.ultimategdbot.api.CommandFailedException;
import com.github.alex1304.ultimategdbot.api.Context;
import com.github.alex1304.ultimategdbot.api.Plugin;
import com.github.alex1304.ultimategdbot.api.utils.reply.ReplyMenuBuilder;
import com.github.alex1304.ultimategdbot.gdplugin.GDPlugin;
import com.github.alex1304.ultimategdbot.gdplugin.database.GDLinkedUsers;

import reactor.core.publisher.Mono;

public class AccountUnlinkCommand implements Command {
	
	private final GDPlugin plugin;
	
	public AccountUnlinkCommand(GDPlugin plugin) {
		this.plugin = Objects.requireNonNull(plugin);
	}

	@Override
	public Mono<Void> execute(Context ctx) {
		final var authorId = ctx.getEvent().getMessage().getAuthor().get().getId().asLong();
		return ctx.getBot().getDatabase().findByID(GDLinkedUsers.class, authorId)
				.switchIfEmpty(Mono.error(new CommandFailedException("You aren't linked to any account.")))
				.flatMap(linkedUser -> {
					var rb = new ReplyMenuBuilder(ctx, true, true);
					rb.setHeader("Are you sure?");
					rb.addItem("confirm", "To confirm your action and unlink your account, type `confirm`", ctx0 -> ctx0.getBot().getDatabase()
							.delete(linkedUser)
							.then(ctx.getBot().getEmoji("success").flatMap(successEmoji -> ctx.reply(successEmoji + " Successfully unlinked your account.")))
							.then());
					return rb.build(null);
				}).then();
	}

	@Override
	public Set<String> getAliases() {
		return Set.of("unlink");
	}

	@Override
	public String getDescription() {
		return "Allows you to unlink your Geometry Dash account from your Discord account.";
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
