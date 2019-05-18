package com.github.alex1304.ultimategdbot.gdplugin.user;

import java.util.Objects;
import java.util.Set;

import com.github.alex1304.jdash.entity.GDUser;
import com.github.alex1304.ultimategdbot.api.Command;
import com.github.alex1304.ultimategdbot.api.CommandFailedException;
import com.github.alex1304.ultimategdbot.api.Context;
import com.github.alex1304.ultimategdbot.api.Plugin;
import com.github.alex1304.ultimategdbot.api.utils.ArgUtils;
import com.github.alex1304.ultimategdbot.gdplugin.GDPlugin;
import com.github.alex1304.ultimategdbot.gdplugin.database.GDLinkedUsers;
import com.github.alex1304.ultimategdbot.gdplugin.util.GDUtils;

import reactor.core.publisher.Mono;

public class ProfileCommand implements Command {
	
	private final GDPlugin plugin;

	public ProfileCommand(GDPlugin plugin) {
		this.plugin = Objects.requireNonNull(plugin);
	}

	@Override
	public Mono<Void> execute(Context ctx) {
		if (ctx.getArgs().size() == 1) {
			final var authorId = ctx.getEvent().getMessage().getAuthor().get().getId().asLong();
			return ctx.getBot().getDatabase().findByID(GDLinkedUsers.class, authorId)
					.filter(GDLinkedUsers::getIsLinkActivated)
					.switchIfEmpty(Mono.error(new CommandFailedException("No user specified. If you want to show your own profile, "
							+ "link your Geometry Dash account using `" + ctx.getPrefixUsed() + "account` and retry this command. Otherwise, you "
									+ "need to specify a user like so: `" + ctx.getPrefixUsed() + "profile <gd_username>`.")))
					.flatMap(linkedUser -> showProfile(ctx, plugin.getGdClient().getUserByAccountId(linkedUser.getGdAccountId())));
		}
		var input = ArgUtils.concatArgs(ctx, 1);
		return showProfile(ctx, GDUtils.stringToUser(ctx.getBot(), plugin.getGdClient(), input));
	}
	
	public Mono<Void> showProfile(Context ctx, Mono<GDUser> userMono) {
		return userMono.flatMap(user -> GDUtils.makeIconSet(ctx.getBot(), user, plugin.getSpriteFactory(), plugin.getIconsCache())
				.flatMap(urls -> GDUtils.userProfileView(ctx.getBot(), ctx.getEvent().getMessage().getAuthor(), user,
								"User profile", "https://i.imgur.com/ppg4HqJ.png", urls[0], urls[1])
						.flatMap(view -> ctx.reply(view))))
				.then();
	}

	@Override
	public Set<String> getAliases() {
		return Set.of("profile");
	}

	@Override
	public String getDescription() {
		return "Fetches a user's GD profile and displays information on it.";
	}

	@Override
	public String getLongDescription() {
		return "It can display a bunch of data regarding players, such as:\n"
				+ "- stars\n"
				+ "- demons\n"
				+ "- diamonds\n"
				+ "- creator points\n"
				+ "- user and secret coins\n"
				+ "- social links\n"
				+ "- global rank\n"
				+ "- icon set\n"
				+ "- privacy settings (whether private messages are open, friend requests are enabled, etc)";
	}

	@Override
	public String getSyntax() {
		return "<username_or_playerID>";
	}

	@Override
	public Plugin getPlugin() {
		return plugin;
	}
}
