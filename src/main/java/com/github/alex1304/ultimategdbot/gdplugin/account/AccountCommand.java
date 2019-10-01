package com.github.alex1304.ultimategdbot.gdplugin.account;

import com.github.alex1304.jdash.entity.GDMessage;
import com.github.alex1304.jdash.entity.GDUser;
import com.github.alex1304.jdash.exception.GDClientException;
import com.github.alex1304.jdash.util.Utils;
import com.github.alex1304.ultimategdbot.api.command.CommandFailedException;
import com.github.alex1304.ultimategdbot.api.command.Context;
import com.github.alex1304.ultimategdbot.api.command.annotated.CommandAction;
import com.github.alex1304.ultimategdbot.api.command.annotated.CommandDoc;
import com.github.alex1304.ultimategdbot.api.command.annotated.CommandSpec;
import com.github.alex1304.ultimategdbot.api.utils.menu.InteractiveMenu;
import com.github.alex1304.ultimategdbot.gdplugin.GDServiceMediator;
import com.github.alex1304.ultimategdbot.gdplugin.database.GDLinkedUsers;
import com.github.alex1304.ultimategdbot.gdplugin.util.GDUtils;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuples;

@CommandSpec(
		aliases = "account",
		shortDescription = "Allows you to manage your connection with your Geometry Dash account."
)
public class AccountCommand {

	private static final int TOKEN_LENGTH = 6;
	private final GDServiceMediator gdServiceMediator;
	
	public AccountCommand(GDServiceMediator gdServiceMediator) {
		this.gdServiceMediator = gdServiceMediator;
	}

	@CommandAction
	@CommandDoc("Linking your account allows UltimateGDBot to etablish a mapping between Geometry Dash users and Discord users, "
				+ "which can unlock a lot of possibilities. For example you can use some commands by tagging directly a "
				+ "Discord user instead of typing his GD username, build a server-wide Geometry Dash leaderboard "
				+ "(see leaderboard command), and more. Use the `link` subcommand to start linking your account, "
				+ "then you need to follow instructions given by the command to complete the linking process. "
				+ "When you have followed all instructions, type `done` in chat. To unlink your account, use the subcommand `unlink`. "
				+ "Note that you can link several Discord accounts to the same GD account, but you can't link several GD accounts to "
				+ "the same Discord account. This is designed so if you lose access to your Discord account, you can still use a new "
				+ "Discord account to link.")
	public Mono<Void> run(Context ctx) {
		return ctx.getBot().getDatabase().findByID(GDLinkedUsers.class, ctx.getAuthor().getId().asLong())
				.filter(GDLinkedUsers::getIsLinkActivated)
				.flatMap(linkedUser -> gdServiceMediator.getGdClient().getUserByAccountId(linkedUser.getGdAccountId()))
				.map(user -> Tuples.of(true, "You are currently linked to the Geometry Dash account **" + user.getName() + "**!"))
				.defaultIfEmpty(Tuples.of(false, "You are not yet linked to any Geometry Dash account!"))
				.flatMap(tuple -> ctx.reply("You can link your Discord account with your Geometry Dash account "
						+ "to get access to cool stuff in UltimateGDBot. You can for example use the `profile` "
						+ "command without arguments to display your own info, let others easily access your "
						+ "profile by mentionning you, or appear in server-wide Geometry Dash leaderboards.\n\n"
						+ tuple.getT2() + "\n"
						+ (tuple.getT1() ? "If you want to unlink your account, run `" + ctx.getPrefixUsed() + "account unlink`"
								: "To start linking your account, run `" + ctx.getPrefixUsed() + "account link <your_gd_username>`")))
				.then();
	}
	
	@CommandAction("link")
	@CommandDoc("Allows you to link a Geometry Dash account to your Discord account.")
	public Mono<Void> runLink(Context ctx, GDUser gdUsername) {
		final var authorId = ctx.getAuthor().getId().asLong();
		return ctx.getBot().getDatabase().findByID(GDLinkedUsers.class, authorId)
				.switchIfEmpty(Mono.fromCallable(() -> {
					var linkedUser = new GDLinkedUsers();
					linkedUser.setDiscordUserId(authorId);
					return linkedUser;
				})).filter(linkedUser -> !linkedUser.getIsLinkActivated())
				.switchIfEmpty(Mono.error(new CommandFailedException("You are already linked to a Geometry Dash account.")))
				.flatMap(linkedUser -> gdServiceMediator.getGdClient().getUserByAccountId(gdServiceMediator.getGdClient().getAccountID())
						.filter(gdUser -> gdUser.getAccountId() > 0)
						.switchIfEmpty(Mono.error(new CommandFailedException("This user is unregistered in Geometry Dash.")))
						.flatMap(botUser -> {
							linkedUser.setConfirmationToken(Utils.defaultStringIfEmptyOrNull(linkedUser.getConfirmationToken(),
									GDUtils.generateAlphanumericToken(TOKEN_LENGTH)));
							linkedUser.setGdAccountId(gdUsername.getAccountId());
							return ctx.getBot().getDatabase().save(linkedUser).thenReturn(botUser);
						})
						.flatMap(botUser -> {
							var menuEmbedContent = new StringBuilder();
							menuEmbedContent.append("Step 1: Open Geometry Dash\n");
							menuEmbedContent.append("Step 2: Search for user \"").append(botUser.getName()).append("\" and open profile\n");
							menuEmbedContent.append("Step 3: Click the button to send a private message\n");
							menuEmbedContent.append("Step 4: In the \"Subject\" field, input `Confirm` (case insensitive)\n");
							menuEmbedContent.append("Step 5: In the \"Body\" field, input the code `").append(linkedUser.getConfirmationToken())
									.append("` (:warning: case sensitive)\n");
							menuEmbedContent.append("Step 6: React below to indicate that you're done sending the confirmation message\n");
							return InteractiveMenu.create(message -> {
										message.setContent("You have requested to link your Discord account with the Geometry Dash "
											+ "account **" + gdUsername.getName() + "**. Now you need to prove that you are the owner of "
											+ "this account. Please follow the instructions below to finalize the linking "
											+ "process.\n");
										message.setEmbed(embed -> {
											embed.setTitle("Steps to confirm your account");
											embed.setDescription(menuEmbedContent.toString());
										});
									})
									.addReactionItem("success", interaction -> handleDone(ctx, linkedUser, gdUsername, botUser))
									.addReactionItem("cross", interaction -> Mono.empty())
									.deleteMenuOnClose(true)
									.deleteMenuOnTimeout(true)
									.closeAfterReaction(true)
									.open(ctx);
						}))
				.then();
	}
	
	@CommandAction("unlink")
	@CommandDoc("Allows you to unlink your Geometry Dash account from your Discord account.")
	public Mono<Void> runUnlink(Context ctx) {
		final var authorId = ctx.getEvent().getMessage().getAuthor().get().getId().asLong();
		return ctx.getBot().getDatabase().findByID(GDLinkedUsers.class, authorId)
				.switchIfEmpty(Mono.error(new CommandFailedException("You aren't linked to any account.")))
				.flatMap(linkedUser -> InteractiveMenu.create("Are you sure?")
						.deleteMenuOnClose(true)
						.deleteMenuOnTimeout(true)
						.closeAfterReaction(true)
						.addReactionItem("success", interaction -> ctx.getBot().getDatabase()
							.delete(linkedUser)
							.then(ctx.getBot().getEmoji("success")
									.flatMap(successEmoji -> ctx.reply(successEmoji + " Successfully unlinked your account.")))
							.then())
						.addReactionItem("cross", interaction -> Mono.empty())
						.open(ctx));
	}
	
	private Mono<Void> handleDone(Context ctx, GDLinkedUsers linkedUser, GDUser user, GDUser botUser) {
		return ctx.reply("Checking messages, please wait...")
				.flatMap(waitMessage -> gdServiceMediator.getGdClient().getPrivateMessages(0)
						.flatMapMany(Flux::fromIterable)
						.filter(message -> message.getSenderID() == user.getAccountId() && message.getSubject().equalsIgnoreCase("confirm"))
						.switchIfEmpty(Mono.error(new CommandFailedException("Unable to find your confirmation message in Geometry Dash. "
								+ "Have you sent it? Read and follow the steps again and retry by typing `done` again.")))
						.next()
						.flatMap(GDMessage::getBody)
						.filter(body -> body.equals(linkedUser.getConfirmationToken()))
						.switchIfEmpty(Mono.error(new CommandFailedException("The confirmation code you sent me doesn't match. "
								+ "Make sure you have typed it correctly and retry by typing `done` again. Note that it's case sensitive.")))
						.doOnNext(__ -> linkedUser.setConfirmationToken(null))
						.doOnNext(__ -> linkedUser.setIsLinkActivated(true))
						.then(ctx.getBot().getDatabase().save(linkedUser))
						.then(ctx.getBot().getEmoji("success").flatMap(successEmoji -> ctx.reply(successEmoji + " You are now linked to "
								+ "Geometry Dash account **" + user.getName() + "**!")))
						.onErrorMap(GDClientException.class, e -> new CommandFailedException("I can't access my private messages right now. "
								+ "Retry later."))
						.doFinally(signal -> waitMessage.delete().subscribe())
						.then());
	}
}
