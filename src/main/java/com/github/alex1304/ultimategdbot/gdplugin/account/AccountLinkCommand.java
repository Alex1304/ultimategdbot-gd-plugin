package com.github.alex1304.ultimategdbot.gdplugin.account;

import java.util.Objects;
import java.util.Set;

import com.github.alex1304.jdash.entity.GDMessage;
import com.github.alex1304.jdash.entity.GDUser;
import com.github.alex1304.jdash.exception.GDClientException;
import com.github.alex1304.jdash.util.Utils;
import com.github.alex1304.ultimategdbot.api.Command;
import com.github.alex1304.ultimategdbot.api.CommandFailedException;
import com.github.alex1304.ultimategdbot.api.Context;
import com.github.alex1304.ultimategdbot.api.Plugin;
import com.github.alex1304.ultimategdbot.api.utils.ArgUtils;
import com.github.alex1304.ultimategdbot.api.utils.reply.ReplyMenuBuilder;
import com.github.alex1304.ultimategdbot.gdplugin.GDPlugin;
import com.github.alex1304.ultimategdbot.gdplugin.database.GDLinkedUsers;
import com.github.alex1304.ultimategdbot.gdplugin.util.GDUtils;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class AccountLinkCommand implements Command {
	
	private static final int TOKEN_LENGTH = 6;
	
	private final GDPlugin plugin;
	
	public AccountLinkCommand(GDPlugin plugin) {
		this.plugin = Objects.requireNonNull(plugin);
	}

	@Override
	public Mono<Void> execute(Context ctx) {
		ArgUtils.requireMinimumArgCount(ctx, 2);
		final var input = ArgUtils.concatArgs(ctx, 1);
		final var authorId = ctx.getEvent().getMessage().getAuthor().get().getId().asLong();
		return ctx.getBot().getDatabase().findByIDOrCreate(GDLinkedUsers.class, authorId, GDLinkedUsers::setDiscordUserId)
				.filter(linkedUser -> !linkedUser.getIsLinkActivated())
				.switchIfEmpty(Mono.error(new CommandFailedException("You are already linked to a Geometry Dash account.")))
				.flatMap(linkedUser -> plugin.getGdClient().searchUser(input)
						.flatMap(user -> plugin.getGdClient().getUserByAccountId(plugin.getGdClient().getAccountID())
								.filter(gdUser -> gdUser.getAccountId() > 0)
								.switchIfEmpty(Mono.error(new CommandFailedException("This Geometry Dash user is green/unregistered. Cannot proceed to linking.")))
								.doOnNext(__ -> linkedUser.setConfirmationToken(Utils.defaultStringIfEmptyOrNull(linkedUser.getConfirmationToken(),
										GDUtils.generateAlphanumericToken(TOKEN_LENGTH))))
								.doOnNext(__ -> linkedUser.setGdAccountId(user.getAccountId()))
								.flatMap(botUser -> {
									var menuEmbedContent = new StringBuilder();
									menuEmbedContent.append("Step 1: Open Geometry Dash\n");
									menuEmbedContent.append("Step 2: Search for user \"").append(botUser.getName()).append("\" and open profile\n");
									menuEmbedContent.append("Step 3: Click the button to send a private message\n");
									menuEmbedContent.append("Step 4: In the \"Subject\" field, input `Confirm` (case insensitive)\n");
									menuEmbedContent.append("Step 5: In the \"Body\" field, input the code `").append(linkedUser.getConfirmationToken())
											.append("` (:warning: case sensitive)\n");
									menuEmbedContent.append("Step 6: Send the message, then go back to Discord in this channel and type `done`. "
											+ "If the command has timed out, just re-run the account command and type `done`\n");
									var rb = new ReplyMenuBuilder(ctx, true, true);
									rb.addItem("done", menuEmbedContent.toString(), ctx0 -> handleDone(ctx, linkedUser, user, botUser));
									rb.setHeader("Steps to confirm your account");
									return rb.build("You have requested to link your Discord account with the Geometry Dash "
											+ "account **" + user.getName() + "**. Now you need to prove that you are the owner of "
											+ "this account. Please follow the instructions below to finalize the linking "
											+ "process.\n");
								}))
						.then(ctx.getBot().getDatabase().save(linkedUser)))
				.then();
	}
	
	private Mono<Void> handleDone(Context ctx, GDLinkedUsers linkedUser, GDUser user, GDUser botUser) {
		return ctx.reply("Checking messages, please wait...")
				.flatMap(waitMessage -> plugin.getGdClient().getPrivateMessages(0)
						.flatMapMany(Flux::fromIterable)
						.filter(message -> message.getSenderID() == user.getAccountId() && message.getSubject().equalsIgnoreCase("confirm"))
						.switchIfEmpty(Mono.error(new CommandFailedException("Unable to find your confirmation message in Geometry Dash. "
								+ "Have you sent it? Read and follow the steps again and retry by typing `done` again.")))
						.next()
						.flatMap(GDMessage::getBody)
						.filter(linkedUser.getConfirmationToken()::equals)
						.switchIfEmpty(Mono.error(new CommandFailedException("The confirmation code you sent me doesn't match. "
								+ "Make sure you have typed it correctly and retry by typing `done` again. Note that it's case sensitive.")))
						.doOnNext(__ -> linkedUser.setConfirmationToken(null))
						.doOnNext(__ -> linkedUser.setIsLinkActivated(true))
						.thenEmpty(ctx.getBot().getDatabase().save(linkedUser))
						.then(ctx.getBot().getEmoji("success").flatMap(successEmoji -> ctx.reply(successEmoji + " You are now linked to "
								+ "Geometry Dash account **" + user.getName() + "**!")))
						.onErrorMap(GDClientException.class, e -> new CommandFailedException("I can't access my private messages right now. "
								+ "Retry later."))
						.doOnSuccessOrError((m, e) -> waitMessage.delete().subscribe())
						.then());
	}

	@Override
	public Set<String> getAliases() {
		return Set.of("link");
	}

	@Override
	public String getDescription() {
		return "Allows you to link a Geometry Dash account to your Discord account.";
	}

	@Override
	public String getLongDescription() {
		return "Follow the steps given by the command in order to proceed.";
	}

	@Override
	public String getSyntax() {
		return "<GD_username>";
	}

	@Override
	public Plugin getPlugin() {
		return plugin;
	}
}
