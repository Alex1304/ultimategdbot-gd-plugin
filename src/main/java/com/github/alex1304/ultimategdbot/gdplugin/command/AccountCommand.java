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
import com.github.alex1304.ultimategdbot.api.service.Root;
import com.github.alex1304.ultimategdbot.gdplugin.GDService;
import com.github.alex1304.ultimategdbot.gdplugin.database.GDLinkedUserDao;
import com.github.alex1304.ultimategdbot.gdplugin.database.GDLinkedUserData;
import com.github.alex1304.ultimategdbot.gdplugin.database.ImmutableGDLinkedUserData;
import com.github.alex1304.ultimategdbot.gdplugin.user.GDUserService;

import discord4j.rest.http.client.ClientException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.function.TupleUtils;
import reactor.util.function.Tuples;

@CommandDescriptor(
		aliases = "account",
		shortDescription = "tr:GDStrings/account_desc"
)
public final class AccountCommand {

	private static final int TOKEN_LENGTH = 6;
	
	@Root
	private GDService gd;

	@CommandAction
	@CommandDoc("tr:GDStrings/account_run")
	public Mono<Void> run(Context ctx) {
		return gd.bot().database()
				.withExtension(GDLinkedUserDao.class, dao -> dao.getByDiscordUserId(ctx.author().getId().asLong()))
				.flatMap(Mono::justOrEmpty)
				.filter(GDLinkedUserData::isLinkActivated)
				.flatMap(linkedUser -> gd.client().getUserByAccountId(linkedUser.gdUserId()))
				.map(user -> Tuples.of(true, ctx.translate("GDStrings", "currently_linked", user.getName())))
				.defaultIfEmpty(Tuples.of(false, ctx.translate("GDStrings", "not_yet_linked")))
				.flatMap(tuple -> ctx.reply(ctx.translate("GDStrings", "link_intro") + "\n\n"
						+ tuple.getT2() + "\n"
						+ (tuple.getT1() ? ctx.translate("GDStrings", "how_to_unlink", ctx.prefixUsed())
								: ctx.translate("GDStrings", "how_to_link", ctx.prefixUsed()))))
				.then();
	}
	
	@CommandAction("link")
	@CommandDoc("tr:GDStrings/account_run_link")
	public Mono<Void> runLink(Context ctx, GDUser gdUsername) {
		final var authorId = ctx.author().getId().asLong();
		return gd.bot().database()
				.withExtension(GDLinkedUserDao.class, dao -> dao.getOrCreate(authorId, gdUsername.getAccountId()))
				.filter(not(GDLinkedUserData::isLinkActivated))
				.switchIfEmpty(Mono.error(new CommandFailedException(ctx.translate("GDStrings", "error_already_linked"))))
				.flatMap(linkedUser -> gd.client().getUserByAccountId(gd.client().getAccountID())
						.filter(gdUser -> gdUser.getAccountId() > 0)
						.switchIfEmpty(Mono.error(new CommandFailedException(ctx.translate("GDStrings", "error_unregistered_user"))))
						.flatMap(botUser -> {
							var token = linkedUser.confirmationToken().orElse(GDUserService.generateAlphanumericToken(TOKEN_LENGTH));
							var data = ImmutableGDLinkedUserData.builder()
									.from(linkedUser)
									.confirmationToken(token)
									.build();
							return gd.bot().database()
									.useExtension(GDLinkedUserDao.class, dao -> dao.setUnconfirmedLink(data))
									.thenReturn(Tuples.of(botUser, token));
						})
						.flatMap(TupleUtils.function((botUser, token) -> {
							var menuEmbedContent = new StringBuilder();
							menuEmbedContent.append(ctx.translate("GDStrings", "link_step_1")).append('\n');
							menuEmbedContent.append(ctx.translate("GDStrings", "link_step_2", botUser.getName())).append('\n');
							menuEmbedContent.append(ctx.translate("GDStrings", "link_step_3")).append('\n');
							menuEmbedContent.append(ctx.translate("GDStrings", "link_step_4")).append('\n');
							menuEmbedContent.append(ctx.translate("GDStrings", "link_step_5", token)).append('\n');
							menuEmbedContent.append(ctx.translate("GDStrings", "link_step_6")).append('\n');
							return gd.bot().interactiveMenu().create(message -> {
										message.setContent(ctx.translate("GDStrings", "link_request", gdUsername.getName()) + '\n');
										message.setEmbed(embed -> {
											embed.setTitle(ctx.translate("GDStrings", "link_steps"));
											embed.setDescription(menuEmbedContent.toString());
										});
									})
									.addReactionItem("success", interaction -> interaction.getEvent().isAddEvent() 
											? handleDone(ctx, token, gdUsername, botUser)
													.then(Mono.<Void>fromRunnable(interaction::closeMenu))
													.onErrorResume(CommandFailedException.class, e -> gd.bot().emoji().get("cross")
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
	@CommandDoc("tr:GDStrings/account_run_unlink")
	public Mono<Void> runUnlink(Context ctx) {
		final var authorId = ctx.event().getMessage().getAuthor().get().getId().asLong();
		return gd.bot().database()
				.withExtension(GDLinkedUserDao.class, dao -> dao.getByDiscordUserId(authorId))
				.switchIfEmpty(Mono.error(new CommandFailedException(ctx.translate("GDStrings", "error_not_linked"))))
				.flatMap(linkedUser -> gd.bot().interactiveMenu().create(ctx.translate("GDStrings", "unlink_confirm"))
						.deleteMenuOnClose(true)
						.deleteMenuOnTimeout(true)
						.closeAfterReaction(true)
						.addReactionItem("success", interaction -> gd.bot().database()
							.useExtension(GDLinkedUserDao.class, dao -> dao.delete(authorId))
							.then(gd.bot().emoji().get("success")
									.flatMap(successEmoji -> ctx.reply(successEmoji + ' ' + ctx.translate("GDStrings", "unlink_success"))))
							.then())
						.addReactionItem("cross", interaction -> Mono.empty())
						.open(ctx));
	}
	
	private Mono<Void> handleDone(Context ctx, String token, GDUser user, GDUser botUser) {
		return ctx.reply(ctx.translate("GDStrings", "checking_messages"))
				.flatMap(waitMessage -> gd.client().getPrivateMessages(0)
						.flatMapMany(Flux::fromIterable)
						.filter(message -> message.getSenderID() == user.getAccountId() && message.getSubject().equalsIgnoreCase("confirm"))
						.switchIfEmpty(Mono.error(new CommandFailedException(ctx.translate("GDStrings", "error_confirmation_not_found"))))
						.next()
						.flatMap(GDMessage::getBody)
						.filter(body -> body.equals(token))
						.switchIfEmpty(Mono.error(new CommandFailedException(ctx.translate("GDStrings", "error_confirmation_mismatch"))))
						.then(gd.bot().database().useExtension(GDLinkedUserDao.class, dao -> dao.confirmLink(ctx.author().getId().asLong())))
						.then(gd.bot().emoji().get("success").flatMap(successEmoji -> ctx.reply(successEmoji + ' '
								+ ctx.translate("GDStrings", "link_success", user.getName()))))
						.onErrorMap(GDClientException.class, e -> new CommandFailedException(ctx.translate("GDStrings", "error_pm_access")))
						.doFinally(signal -> waitMessage.delete().subscribe())
						.then());
	}
}
