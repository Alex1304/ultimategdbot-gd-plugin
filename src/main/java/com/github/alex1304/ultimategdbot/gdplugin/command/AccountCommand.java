package com.github.alex1304.ultimategdbot.gdplugin.command;

import static java.util.function.Predicate.not;

import com.github.alex1304.jdash.entity.GDMessage;
import com.github.alex1304.jdash.entity.GDUser;
import com.github.alex1304.jdash.exception.GDClientException;
import com.github.alex1304.ultimategdbot.api.command.CommandFailedException;
import com.github.alex1304.ultimategdbot.api.command.Context;
import com.github.alex1304.ultimategdbot.api.command.annotated.CommandAction;
import com.github.alex1304.ultimategdbot.api.command.annotated.CommandDescriptor;
import com.github.alex1304.ultimategdbot.api.command.annotated.CommandDoc;
import com.github.alex1304.ultimategdbot.api.util.menu.InteractiveMenu;
import com.github.alex1304.ultimategdbot.gdplugin.GDService;
import com.github.alex1304.ultimategdbot.gdplugin.database.GDLinkedUserDao;
import com.github.alex1304.ultimategdbot.gdplugin.database.GDLinkedUserData;

import discord4j.rest.http.client.ClientException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.function.TupleUtils;
import reactor.util.function.Tuples;

@CommandDescriptor(
		aliases = "account",
		shortDescription = "Allows you to manage your connection with your Geometry Dash account."
)
public class AccountCommand {

	private static final int TOKEN_LENGTH = 6;
	private final GDService gdService;
	
	public AccountCommand(GDService gdService) {
		this.gdService = gdService;
	}

	@CommandAction
	@CommandDoc("Shows your account linking status. Linking your account allows UltimateGDBot to etablish a mapping between Geometry "
			+ "Dash users and Discord users, which can unlock a lot of possibilities. For example you can use some commands by "
			+ "tagging directly a Discord user instead of typing his GD username, build a server-wide Geometry Dash leaderboard "
			+ "(see leaderboard command), and more. Use the `link` subcommand to start linking your account, "
			+ "then you need to follow instructions given by the command to complete the linking process. "
			+ "When you have followed all instructions, type `done` in chat. To unlink your account, use the subcommand `unlink`. "
			+ "Note that you can link several Discord accounts to the same GD account, but you can't link several GD accounts to "
			+ "the same Discord account. This is designed so if you lose access to your Discord account, you can still use a new "
			+ "Discord account to link.")
	public Mono<Void> run(Context ctx) {
		return ctx.bot().database()
				.withExtension(GDLinkedUserDao.class, dao -> dao.getByDiscordUserId(ctx.author().getId().asLong()))
				.flatMap(Mono::justOrEmpty)
				.filter(GDLinkedUserData::isLinkActivated)
				.flatMap(linkedUser -> gdService.getGdClient().getUserByAccountId(linkedUser.gdAccountId().orElseThrow()))
				.map(user -> Tuples.of(true, "You are currently linked to the Geometry Dash account **" + user.getName() + "**!"))
				.defaultIfEmpty(Tuples.of(false, "You are not yet linked to any Geometry Dash account!"))
				.flatMap(tuple -> ctx.reply("You can link your Discord account with your Geometry Dash account "
						+ "to get access to cool stuff in UltimateGDBot. You can for example use the `profile` "
						+ "command without arguments to display your own info, let others easily access your "
						+ "profile by mentionning you, or appear in server-wide Geometry Dash leaderboards.\n\n"
						+ tuple.getT2() + "\n"
						+ (tuple.getT1() ? "If you want to unlink your account, run `" + ctx.prefixUsed() + "account unlink`"
								: "To start linking your account, run `" + ctx.prefixUsed() + "account link <your_gd_username>`")))
				.then();
	}
	
	@CommandAction("link")
	@CommandDoc("Allows you to link a Geometry Dash account to your Discord account.")
	public Mono<Void> runLink(Context ctx, GDUser gdUsername) {
		final var authorId = ctx.author().getId().asLong();
		return ctx.bot().database()
				.withExtension(GDLinkedUserDao.class, dao -> dao.getOrCreate(authorId, gdUsername.getAccountId()))
				.filter(not(GDLinkedUserData::isLinkActivated))
				.switchIfEmpty(Mono.error(new CommandFailedException("You are already linked to a Geometry Dash account.")))
				.flatMap(linkedUser -> gdService.getGdClient().getUserByAccountId(gdService.getGdClient().getAccountID())
						.filter(gdUser -> gdUser.getAccountId() > 0)
						.switchIfEmpty(Mono.error(new CommandFailedException("This user is unregistered in Geometry Dash.")))
						.flatMap(botUser -> {
//							linkedUser.setConfirmationToken(Utils.defaultStringIfEmptyOrNull(linkedUser.getConfirmationToken(),
//									GDUsers.generateAlphanumericToken(TOKEN_LENGTH)));
//							linkedUser.setGdAccountId(gdUsername.getAccountId());
//							return ctx.bot().database().save(linkedUser).thenReturn(botUser);
							return Mono.just(Tuples.of(botUser, ""));
						})
						.flatMap(TupleUtils.function((botUser, token) -> {
							var menuEmbedContent = new StringBuilder();
							menuEmbedContent.append("Step 1: Open Geometry Dash\n");
							menuEmbedContent.append("Step 2: Search for user \"").append(botUser.getName()).append("\" and open profile\n");
							menuEmbedContent.append("Step 3: Click the button to send a private message\n");
							menuEmbedContent.append("Step 4: In the \"Subject\" field, input `Confirm` (case insensitive)\n");
							menuEmbedContent.append("Step 5: In the \"Body\" field, input the code `").append(linkedUser.confirmationToken())
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
									.addReactionItem("success", interaction -> interaction.getEvent().isAddEvent() 
											? handleDone(ctx, token, gdUsername, botUser)
													.then(Mono.<Void>fromRunnable(interaction::closeMenu))
													.onErrorResume(CommandFailedException.class, e -> ctx.bot().emoji("cross")
															.flatMap(cross -> ctx.reply(cross + " " + e.getMessage()))
															.and(interaction.getMenuMessage()
																	.removeReaction(interaction.getEvent().getEmoji(), ctx.author().getId())
																	.onErrorResume(ClientException.isStatusCode(403, 404), e0 -> Mono.empty())))
											: Mono.empty())
									.addReactionItem("cross", interaction -> Mono.fromRunnable(interaction::closeMenu))
									.deleteMenuOnClose(true)
									.deleteMenuOnTimeout(true)
									.closeAfterReaction(false)
									.open(ctx);
						})))
				.then();
	}
	
	@CommandAction("unlink")
	@CommandDoc("Allows you to unlink your Geometry Dash account from your Discord account.")
	public Mono<Void> runUnlink(Context ctx) {
		final var authorId = ctx.event().getMessage().getAuthor().get().getId().asLong();
		return ctx.bot().database()
				.withExtension(GDLinkedUserDao.class, dao -> dao.getByDiscordUserId(authorId))
				.switchIfEmpty(Mono.error(new CommandFailedException("You aren't linked to any account.")))
				.flatMap(linkedUser -> InteractiveMenu.create("Are you sure?")
						.deleteMenuOnClose(true)
						.deleteMenuOnTimeout(true)
						.closeAfterReaction(true)
						.addReactionItem("success", interaction -> ctx.bot().database()
							.useExtension(GDLinkedUserDao.class, dao -> dao.delete(authorId))
							.then(ctx.bot().emoji("success")
									.flatMap(successEmoji -> ctx.reply(successEmoji + " Successfully unlinked your account.")))
							.then())
						.addReactionItem("cross", interaction -> Mono.empty())
						.open(ctx));
	}
	
	private Mono<Void> handleDone(Context ctx, String token, GDUser user, GDUser botUser) {
		return ctx.reply("Checking messages, please wait...")
				.flatMap(waitMessage -> gdService.getGdClient().getPrivateMessages(0)
						.flatMapMany(Flux::fromIterable)
						.filter(message -> message.getSenderID() == user.getAccountId() && message.getSubject().equalsIgnoreCase("confirm"))
						.switchIfEmpty(Mono.error(new CommandFailedException("Unable to find your confirmation message in Geometry Dash. "
								+ "Have you sent it? Follow the steps again and retry by clicking the reaction again.")))
						.next()
						.flatMap(GDMessage::getBody)
						.filter(body -> body.equals(token))
						.switchIfEmpty(Mono.error(new CommandFailedException("The confirmation code you sent me doesn't match. "
								+ "Make sure you have typed it correctly and retry by clicking the reaction again. Note that it's case sensitive.")))
						.then(ctx.bot().database().useExtension(GDLinkedUserDao.class, dao -> dao.confirmLink(ctx.author().getId().asLong())))
						.then(ctx.bot().emoji("success").flatMap(successEmoji -> ctx.reply(successEmoji + " You are now linked to "
								+ "Geometry Dash account **" + user.getName() + "**!")))
						.onErrorMap(GDClientException.class, e -> new CommandFailedException("I can't access my private messages right now. "
								+ "Retry later."))
						.doFinally(signal -> waitMessage.delete().subscribe())
						.then());
	}
}
