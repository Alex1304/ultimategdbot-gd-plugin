package com.github.alex1304.ultimategdbot.gdplugin;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;

import com.github.alex1304.ultimategdbot.api.Command;
import com.github.alex1304.ultimategdbot.api.CommandFailedException;
import com.github.alex1304.ultimategdbot.api.Context;
import com.github.alex1304.ultimategdbot.api.PermissionLevel;
import com.github.alex1304.ultimategdbot.api.utils.reply.ReplyMenuBuilder;

import discord4j.core.object.entity.Channel.Type;
import reactor.core.publisher.Mono;

public class AccountUnlinkCommand implements Command {

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
	public Set<Command> getSubcommands() {
		return Set.of();
	}

	@Override
	public String getDescription() {
		return "Allows you to unlink your Geometry Dash account from your Discord account.";
	}

	@Override
	public String getSyntax() {
		return "";
	}

	@Override
	public PermissionLevel getPermissionLevel() {
		return PermissionLevel.PUBLIC;
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
