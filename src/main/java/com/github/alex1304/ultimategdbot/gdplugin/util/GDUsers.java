package com.github.alex1304.ultimategdbot.gdplugin.util;

import static com.github.alex1304.ultimategdbot.gdplugin.util.GDFormatter.formatCode;
import static com.github.alex1304.ultimategdbot.gdplugin.util.GDFormatter.formatPrivacy;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.StringJoiner;
import java.util.function.Consumer;

import javax.imageio.ImageIO;

import com.github.alex1304.jdash.client.AuthenticatedGDClient;
import com.github.alex1304.jdash.entity.GDUser;
import com.github.alex1304.jdash.entity.IconType;
import com.github.alex1304.jdash.entity.Role;
import com.github.alex1304.jdash.graphics.SpriteFactory;
import com.github.alex1304.jdash.util.GDUserIconSet;
import com.github.alex1304.jdash.util.Utils;
import com.github.alex1304.ultimategdbot.api.Bot;
import com.github.alex1304.ultimategdbot.api.command.CommandFailedException;
import com.github.alex1304.ultimategdbot.api.utils.DiscordFormatter;
import com.github.alex1304.ultimategdbot.gdplugin.database.GDLinkedUsers;

import discord4j.core.object.entity.Attachment;
import discord4j.core.object.entity.MessageChannel;
import discord4j.core.object.entity.User;
import discord4j.core.object.util.Snowflake;
import discord4j.core.spec.MessageCreateSpec;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public final class GDUsers {

	private GDUsers() {
	}
	
	public static Mono<Consumer<MessageCreateSpec>> userProfileView(Bot bot, Optional<User> author, GDUser user, 
			String authorName, String authorIconUrl, String iconSetUrl) {
		return Mono.zip(o -> o, bot.getEmoji("star"), bot.getEmoji("diamond"), bot.getEmoji("user_coin"),
				bot.getEmoji("secret_coin"), bot.getEmoji("demon"), bot.getEmoji("creator_points"),
				bot.getEmoji("mod"), bot.getEmoji("elder_mod"), bot.getEmoji("global_rank"),
				bot.getEmoji("youtube"), bot.getEmoji("twitter"), bot.getEmoji("twitch"),
				bot.getEmoji("discord"), bot.getEmoji("friends"), bot.getEmoji("messages"),
				bot.getEmoji("comment_history"))
				.zipWith(getDiscordAccountsForGDUser(bot, user).collectList())
				.map(tuple -> {
					var emojis = tuple.getT1();
					var linkedAccounts = tuple.getT2();
					return mcs -> {
						final var statWidth = 9;
						if (author.isPresent()) {
							mcs.setContent(author.get().getMention() + ", here is the profile of user **" + user.getName() + "**:");
						}
						mcs.setEmbed(embed -> {
							embed.setAuthor(authorName, null, authorIconUrl);
							embed.addField(":chart_with_upwards_trend:  " + user.getName() + "'s stats", emojis[0] + "  " + formatCode(user.getStars(), statWidth) + "\n"
									+ emojis[1] + "  " + formatCode(user.getDiamonds(), statWidth) + "\n"
									+ emojis[2] + "  " + formatCode(user.getUserCoins(), statWidth) + "\n"
									+ emojis[3] + "  " + formatCode(user.getSecretCoins(), statWidth) + "\n"
									+ emojis[4] + "  " + formatCode(user.getDemons(), statWidth) + "\n"
									+ emojis[5] + "  " + formatCode(user.getCreatorPoints(), statWidth) + "\n", false);
							final var badge = user.getRole() == Role.ELDER_MODERATOR ? emojis[7] : emojis[6];
							final var mod = badge + "  **" + user.getRole().toString().replaceAll("_", " ") + "**\n";
							embed.addField("───────────", (user.getRole() != Role.USER ? mod : "")
									+ emojis[8] + "  **Global Rank:** "
									+ (user.getGlobalRank() == 0 ? "*Unranked*" : user.getGlobalRank()) + "\n"
									+ emojis[9] + "  **Youtube:** "
										+ (user.getYoutube().isEmpty() ? "*not provided*" : "[Open link](https://www.youtube.com/channel/"
										+ Utils.urlEncode(user.getYoutube()) + ")") + "\n"
									+ emojis[11] + "  **Twitch:** "
										+ (user.getTwitch().isEmpty() ? "*not provided*" : "["  + user.getTwitch()
										+ "](http://www.twitch.tv/" + Utils.urlEncode(user.getTwitch()) + ")") + "\n"
									+ emojis[10] + "  **Twitter:** "
										+ (user.getTwitter().isEmpty() ? "*not provided*" : "[@" + user.getTwitter() + "]"
										+ "(http://www.twitter.com/" + Utils.urlEncode(user.getTwitter()) + ")") + "\n"
									+ emojis[12] + "  **Discord:** " + (linkedAccounts.isEmpty() ? "*unknown*" : linkedAccounts.stream()
											.reduce(new StringJoiner(", "), (sj, l) -> sj.add(DiscordFormatter.formatUser(l)), (a, b) -> a).toString())
									+ "\n───────────\n"
									+ emojis[13] + "  **Friend requests:** " + (user.hasFriendRequestsEnabled() ? "Enabled" : "Disabled") + "\n"
									+ emojis[14] + "  **Private messages:** " + formatPrivacy(user.getPrivateMessagePolicy()) + "\n"
									+ emojis[15] + "  **Comment history:** " + formatPrivacy(user.getCommmentHistoryPolicy()) + "\n", false);
							embed.setFooter("PlayerID: " + user.getId() + " | " + "AccountID: " + user.getAccountId(), null);
							embed.setImage(iconSetUrl);
						});
					};
				});
	}
	
	public static Mono<String> makeIconSet(Bot bot, GDUser user, SpriteFactory sf, Map<GDUserIconSet, String> iconsCache) {
		final var iconSet = new GDUserIconSet(user, sf);
		final var cached = iconsCache.get(iconSet);
		if (cached != null) {
			return Mono.just(cached);
		}
		final var icons = new ArrayList<BufferedImage>();
		for (var iconType : IconType.values()) {
			icons.add(iconSet.generateIcon(iconType));
		}
		final var iconSetImg = new BufferedImage(icons.stream().mapToInt(BufferedImage::getWidth).sum(), icons.get(0).getHeight(), icons.get(0).getType());
		final var g = iconSetImg.createGraphics();
		var offset = 0;
		for (var icon : icons) {
			g.drawImage(icon, offset, 0, null);
			offset += icon.getWidth();
		}
		g.dispose();
		final var istreamIconSet = imageToInputStream(iconSetImg);
		
		return bot.getAttachmentsChannel()
				.ofType(MessageChannel.class)
				.flatMap(c -> c.createMessage(mcs -> mcs.addFile(user.getId() + "-IconSet.png", istreamIconSet)))
				.flatMap(msg -> Flux.fromIterable(msg.getAttachments())
						.next()
						.map(Attachment::getUrl))
				.doOnNext(url -> iconsCache.put(iconSet, url));
	}
	
	private static InputStream imageToInputStream(BufferedImage img) {
		try {
			final var os = new ByteArrayOutputStream(100_000);
			ImageIO.write(img, "png", os);
			return new ByteArrayInputStream(os.toByteArray());
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}
	
	public static Mono<GDUser> stringToUser(Bot bot, AuthenticatedGDClient gdClient, String str) {
		if (str.matches("<@!?[0-9]{1,19}>")) {
			var id = str.substring(str.startsWith("<@!") ? 3 : 2, str.length() - 1);
			return Mono.just(id)
					.map(Snowflake::of)
					.onErrorMap(e -> new CommandFailedException("Not a valid mention."))
					.flatMap(snowflake -> bot.getDiscordClients().flatMap(client -> client.getUserById(snowflake)).next())
					.onErrorMap(e -> new CommandFailedException("Could not resolve the mention to a valid user."))
					.flatMap(user -> bot.getDatabase().findByID(GDLinkedUsers.class, user.getId().asLong()))
					.filter(GDLinkedUsers::getIsLinkActivated)
					.flatMap(linkedUser -> gdClient.getUserByAccountId(linkedUser.getGdAccountId()))
					.switchIfEmpty(Mono.error(new CommandFailedException("This user doesn't have an associated Geometry Dash account.")));
		}
		if (!str.matches("[a-zA-Z0-9 _-]+")) {
			return Mono.error(new CommandFailedException("Your query contains invalid characters."));
		}
		return gdClient.searchUser(str);
	}
	
	/**
	 * Generates a random String made of alphanumeric characters. The length of the
	 * generated String is specified as an argument.
	 * 
	 * The following characters are excluded to avoid confusion between l and 1, O
	 * and 0, etc: <code>l, I, 1, 0, O</code>
	 * 
	 * @param n the length of the generated String
	 * @return the generated random String
	 */
	public static String generateAlphanumericToken(int n) {
		if (n < 1) {
			throw new IllegalArgumentException("n is negative");
		}
		
		final String alphabet = "23456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz";
		char[] result = new char[n];
		
		for (int i = 0 ; i < result.length ; i++)
			result[i] = alphabet.charAt(new Random().nextInt(alphabet.length()));
		
		return new String(result);
	}
	
	public static Flux<User> getDiscordAccountsForGDUser(Bot bot, GDUser gdUser) {
		return bot.getDatabase().query(GDLinkedUsers.class, "from GDLinkedUsers linkedUser where linkedUser.gdAccountId = ?0 "
				+ "and linkedUser.isLinkActivated = 1", gdUser.getAccountId())
				.flatMap(linkedUser -> bot.getDiscordClients()
						.flatMap(client -> client.getUserById(Snowflake.of(linkedUser.getDiscordUserId())))
						.take(1));
	}
}
