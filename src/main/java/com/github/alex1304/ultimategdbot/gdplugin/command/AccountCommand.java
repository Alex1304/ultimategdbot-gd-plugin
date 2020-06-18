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
import com.github.alex1304.ultimategdbot.api.command.menu.InteractiveMenuService;
import com.github.alex1304.ultimategdbot.api.database.DatabaseService;
import com.github.alex1304.ultimategdbot.api.emoji.EmojiService;
import com.github.alex1304.ultimategdbot.gdplugin.GDService;
import com.github.alex1304.ultimategdbot.gdplugin.database.GDLinkedUserDao;
import com.github.alex1304.ultimategdbot.gdplugin.database.GDLinkedUserData;
import com.github.alex1304.ultimategdbot.gdplugin.database.ImmutableGDLinkedUserData;
import com.github.alex1304.ultimategdbot.gdplugin.util.GDUsers;

import discord4j.rest.http.client.ClientException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.function.TupleUtils;
import reactor.util.function.Tuples;

@CommandDescriptor(
		aliases = "account",
		shortDescription = "tr:strings_gd/account_desc"
)
public class AccountCommand {

	private static final int TOKEN_LENGTH = 6;
	private final GDService gdService;
	
	public AccountCommand(GDService gdService) {
		this.gdService = gdService;
	}

	@CommandAction
	@CommandDoc("tr:strings_gd/account_run")
	public Mono<Void> run(Context ctx) {
		return ctx.bot().service(DatabaseService.class)
				.withExtension(GDLinkedUserDao.class, dao -> dao.getByDiscordUserId(ctx.author().getId().asLong()))
				.flatMap(Mono::justOrEmpty)
				.filter(GDLinkedUserData::isLinkActivated)
				.flatMap(linkedUser -> gdService.getGdClient().getUserByAccountId(linkedUser.gdUserId()))
				.map(user -> Tuples.of(true, ctx.translate("strings_gd", "currently_linked", user.getName())))
				.defaultIfEmpty(Tuples.of(false, ctx.translate("strings_gd", "not_yet_linked")))
				.flatMap(tuple -> ctx.reply(ctx.translate("strings_gd", "link_intro") + "\n\n"
						+ tuple.getT2() + "\n"
						+ (tuple.getT1() ? ctx.translate("strings_gd", "how_to_unlink", ctx.prefixUsed())
								: ctx.translate("strings_gd", "how_to_link", ctx.prefixUsed()))))
				.then();
	}
	
	@CommandAction("link")
	@CommandDoc("tr:strings_gd/account_run_link")
	public Mono<Void> runLink(Context ctx, GDUser gdUsername) {
		final var authorId = ctx.author().getId().asLong();
		return ctx.bot().service(DatabaseService.class)
				.withExtension(GDLinkedUserDao.class, dao -> dao.getOrCreate(authorId, gdUsername.getAccountId()))
				.filter(not(GDLinkedUserData::isLinkActivated))
				.switchIfEmpty(Mono.error(new CommandFailedException(ctx.translate("strings_gd", "error_already_linked"))))
				.flatMap(linkedUser -> gdService.getGdClient().getUserByAccountId(gdService.getGdClient().getAccountID())
						.filter(gdUser -> gdUser.getAccountId() > 0)
						.switchIfEmpty(Mono.error(new CommandFailedException(ctx.translate("strings_gd", "error_unregistered_user"))))
						.flatMap(botUser -> {
							var token = linkedUser.confirmationToken().orElse(GDUsers.generateAlphanumericToken(TOKEN_LENGTH));
							var data = ImmutableGDLinkedUserData.builder()
									.from(linkedUser)
									.confirmationToken(token)
									.build();
							return ctx.bot().service(DatabaseService.class)
									.useExtension(GDLinkedUserDao.class, dao -> dao.setUnconfirmedLink(data))
									.thenReturn(Tuples.of(botUser, token));
						})
						.flatMap(TupleUtils.function((botUser, token) -> {
							var menuEmbedContent = new StringBuilder();
							menuEmbedContent.append(ctx.translate("strings_gd", "link_step_1")).append('\n');
							menuEmbedContent.append(ctx.translate("strings_gd", "link_step_2", botUser.getName())).append('\n');
							menuEmbedContent.append(ctx.translate("strings_gd", "link_step_3")).append('\n');
							menuEmbedContent.append(ctx.translate("strings_gd", "link_step_4")).append('\n');
							menuEmbedContent.append(ctx.translate("strings_gd", "link_step_5", token)).append('\n');
							menuEmbedContent.append(ctx.translate("strings_gd", "link_step_6")).append('\n');
							return ctx.bot().service(InteractiveMenuService.class).create(message -> {
										message.setContent(ctx.translate("strings_gd", "link_request", gdUsername.getName()) + '\n');
										message.setEmbed(embed -> {
											embed.setTitle(ctx.translate("strings_gd", "link_steps"));
											embed.setDescription(menuEmbedContent.toString());
										});
									})
									.addReactionItem("success", interaction -> interaction.getEvent().isAddEvent() 
											? handleDone(ctx, token, gdUsername, botUser)
													.then(Mono.<Void>fromRunnable(interaction::closeMenu))
													.onErrorResume(CommandFailedException.class, e -> ctx.bot().service(EmojiService.class).emoji("cross")
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
	@CommandDoc("tr:strings_gd/account_run_unlink")
	public Mono<Void> runUnlink(Context ctx) {
		final var authorId = ctx.event().getMessage().getAuthor().get().getId().asLong();
		return ctx.bot().service(DatabaseService.class)
				.withExtension(GDLinkedUserDao.class, dao -> dao.getByDiscordUserId(authorId))
				.switchIfEmpty(Mono.error(new CommandFailedException(ctx.translate("strings_gd", "error_not_linked"))))
				.flatMap(linkedUser -> ctx.bot().service(InteractiveMenuService.class).create(ctx.translate("strings_gd", "unlink_confirm"))
						.deleteMenuOnClose(true)
						.deleteMenuOnTimeout(true)
						.closeAfterReaction(true)
						.addReactionItem("success", interaction -> ctx.bot().service(DatabaseService.class)
							.useExtension(GDLinkedUserDao.class, dao -> dao.delete(authorId))
							.then(ctx.bot().service(EmojiService.class).emoji("success")
									.flatMap(successEmoji -> ctx.reply(successEmoji + ' ' + ctx.translate("strings_gd", "unlink_success"))))
							.then())
						.addReactionItem("cross", interaction -> Mono.empty())
						.open(ctx));
	}
	
	private Mono<Void> handleDone(Context ctx, String token, GDUser user, GDUser botUser) {
		return ctx.reply(ctx.translate("strings_gd", "checking_messages"))
				.flatMap(waitMessage -> gdService.getGdClient().getPrivateMessages(0)
						.flatMapMany(Flux::fromIterable)
						.filter(message -> message.getSenderID() == user.getAccountId() && message.getSubject().equalsIgnoreCase("confirm"))
						.switchIfEmpty(Mono.error(new CommandFailedException(ctx.translate("strings_gd", "error_confirmation_not_found"))))
						.next()
						.flatMap(GDMessage::getBody)
						.filter(body -> body.equals(token))
						.switchIfEmpty(Mono.error(new CommandFailedException(ctx.translate("strings_gd", "error_confirmation_mismatch"))))
						.then(ctx.bot().service(DatabaseService.class).useExtension(GDLinkedUserDao.class, dao -> dao.confirmLink(ctx.author().getId().asLong())))
						.then(ctx.bot().service(EmojiService.class).emoji("success").flatMap(successEmoji -> ctx.reply(successEmoji + ' '
								+ ctx.translate("strings_gd", "link_success", user.getName()))))
						.onErrorMap(GDClientException.class, e -> new CommandFailedException(ctx.translate("strings_gd", "error_pm_access")))
						.doFinally(signal -> waitMessage.delete().subscribe())
						.then());
	}
}
