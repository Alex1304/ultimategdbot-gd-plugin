package com.github.alex1304.ultimategdbot.gdplugin.command;

import com.github.alex1304.jdash.entity.GDUser;
import com.github.alex1304.ultimategdbot.api.command.CommandFailedException;
import com.github.alex1304.ultimategdbot.api.command.Context;
import com.github.alex1304.ultimategdbot.api.command.annotated.CommandAction;
import com.github.alex1304.ultimategdbot.api.command.annotated.CommandDescriptor;
import com.github.alex1304.ultimategdbot.api.command.annotated.CommandDoc;
import com.github.alex1304.ultimategdbot.api.database.DatabaseService;
import com.github.alex1304.ultimategdbot.api.util.MessageSpecTemplate;
import com.github.alex1304.ultimategdbot.gdplugin.GDService;
import com.github.alex1304.ultimategdbot.gdplugin.database.GDLinkedUserDao;
import com.github.alex1304.ultimategdbot.gdplugin.database.GDLinkedUserData;
import com.github.alex1304.ultimategdbot.gdplugin.util.GDUsers;

import reactor.core.publisher.Mono;
import reactor.util.annotation.Nullable;

@CommandDescriptor(
		aliases = "profile",
		shortDescription = "Fetches a user's GD profile and displays information on it."
)
public class ProfileCommand {

	private final GDService gdService;
	
	public ProfileCommand(GDService gdService) {
		this.gdService = gdService;
	}

	@CommandAction
	@CommandDoc("Fetches a user's GD profile and displays information on it. It can display a bunch of data about players, such as:\n"
				+ "- stars\n"
				+ "- demons\n"
				+ "- diamonds\n"
				+ "- creator points\n"
				+ "- user and secret coins\n"
				+ "- social links\n"
				+ "- global rank\n"
				+ "- icon set\n"
				+ "- privacy settings (whether private messages are open, friend requests are enabled, etc)")
	public Mono<Void> run(Context ctx, @Nullable GDUser gdUser) {
		return Mono.justOrEmpty(gdUser)
				.switchIfEmpty(ctx.bot().service(DatabaseService.class)
						.withExtension(GDLinkedUserDao.class, dao -> dao.getByDiscordUserId(ctx.author().getId().asLong()))
						.flatMap(Mono::justOrEmpty)
						.filter(GDLinkedUserData::isLinkActivated)
						.switchIfEmpty(Mono.error(new CommandFailedException("No user specified. If you want to "
								+ "show your own profile, link your Geometry Dash account using `"
								+ ctx.prefixUsed() + "account` and retry this command. Otherwise, you "
								+ "need to specify a user like so: `" + ctx.prefixUsed() + "profile <gd_username>`.")))
						.map(GDLinkedUserData::gdUserId)
						.flatMap(Mono::justOrEmpty)
						.flatMap(gdService.getGdClient()::getUserByAccountId))
				.flatMap(user -> GDUsers.makeIconSet(ctx.bot(), user, gdService.getSpriteFactory(), gdService.getIconsCache(), gdService.getIconChannelId())
						.onErrorResume(e -> Mono.just(e.getMessage()))
						.flatMap(icons -> GDUsers.userProfileView(ctx.bot(), ctx.event().getMessage().getAuthor(), user,
										"User profile", "https://i.imgur.com/ppg4HqJ.png", icons)
								.map(MessageSpecTemplate::toMessageCreateSpec)
								.flatMap(ctx::reply)))
				.then();
	}
}
