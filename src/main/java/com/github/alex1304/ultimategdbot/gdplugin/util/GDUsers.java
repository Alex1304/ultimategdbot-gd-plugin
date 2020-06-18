package com.github.alex1304.ultimategdbot.gdplugin.util;

import static com.github.alex1304.ultimategdbot.gdplugin.util.GDFormatter.formatCode;
import static com.github.alex1304.ultimategdbot.gdplugin.util.GDFormatter.formatPrivacy;
import static discord4j.core.retriever.EntityRetrievalStrategy.STORE_FALLBACK_REST;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.ArrayList;
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
import com.github.alex1304.ultimategdbot.api.Translator;
import com.github.alex1304.ultimategdbot.api.command.CommandFailedException;
import com.github.alex1304.ultimategdbot.api.database.DatabaseService;
import com.github.alex1304.ultimategdbot.api.emoji.EmojiService;
import com.github.alex1304.ultimategdbot.api.util.MessageSpecTemplate;
import com.github.alex1304.ultimategdbot.gdplugin.database.GDLinkedUserDao;
import com.github.alex1304.ultimategdbot.gdplugin.database.GDLinkedUserData;
import com.github.benmanes.caffeine.cache.Cache;

import discord4j.common.util.Snowflake;
import discord4j.core.object.entity.Attachment;
import discord4j.core.object.entity.User;
import discord4j.core.object.entity.channel.MessageChannel;
import discord4j.core.spec.EmbedCreateSpec;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

public final class GDUsers {

	private GDUsers() {
	}
	
	public static Mono<MessageSpecTemplate> userProfileView(Translator tr, Bot bot, User author, GDUser user, 
			String authorName, String authorIconUrl, String iconSetUrl) {
		var emojiService = bot.service(EmojiService.class);
		return Mono.zip(o -> o, emojiService.emoji("star"), emojiService.emoji("diamond"),
						emojiService.emoji("user_coin"), emojiService.emoji("secret_coin"), emojiService.emoji("demon"),
						emojiService.emoji("creator_points"), emojiService.emoji("mod"), emojiService.emoji("elder_mod"),
						emojiService.emoji("global_rank"), emojiService.emoji("youtube"), emojiService.emoji("twitter"),
						emojiService.emoji("twitch"), emojiService.emoji("discord"), emojiService.emoji("friends"),
						emojiService.emoji("messages"), emojiService.emoji("comment_history"))
				.zipWith(getDiscordAccountsForGDUser(bot, user.getAccountId()).collectList())
				.map(tuple -> {
					var emojis = tuple.getT1();
					var linkedAccounts = tuple.getT2();
					final var statWidth = 9;
					final var content = author == null ? "" : tr.translate("strings_gd", "profile_intro", author.getMention(), user.getName());
					Consumer<EmbedCreateSpec> embedSpec = embed -> {
						embed.setAuthor(authorName, null, authorIconUrl);
						embed.addField(":chart_with_upwards_trend:  " + tr.translate("strings_gd", "player_stats", user.getName()),
								emojis[0] + "  " + formatCode(user.getStars(), statWidth) + "\n"
								+ emojis[1] + "  " + formatCode(user.getDiamonds(), statWidth) + "\n"
								+ emojis[2] + "  " + formatCode(user.getUserCoins(), statWidth) + "\n"
								+ emojis[3] + "  " + formatCode(user.getSecretCoins(), statWidth) + "\n"
								+ emojis[4] + "  " + formatCode(user.getDemons(), statWidth) + "\n"
								+ emojis[5] + "  " + formatCode(user.getCreatorPoints(), statWidth) + "\n", false);
						final var badge = user.getRole() == Role.ELDER_MODERATOR ? emojis[7] : emojis[6];
						final var mod = badge + "  **" + user.getRole().toString().replaceAll("_", " ") + "**\n";
						embed.addField("───────────", (user.getRole() != Role.USER ? mod : "")
								+ emojis[8] + "  **" + tr.translate("strings_gd", "label_global_rank") + "** "
								+ (user.getGlobalRank() == 0 ? '*' + tr.translate("strings_gd", "unranked") + '*' : user.getGlobalRank()) + "\n"
								+ emojis[9] + "  **YouTube:** "
									+ (user.getYoutube().isEmpty()
											? '*' + tr.translate("strings_gd", "not_provided") + '*'
											: '[' + tr.translate("strings_gd", "open_link") + "](https://www.youtube.com/channel/" + Utils.urlEncode(user.getYoutube()) + ")") + "\n"
								+ emojis[11] + "  **Twitch:** "
									+ (user.getTwitch().isEmpty() ? '*' + tr.translate("strings_gd", "not_provided") + '*' : "["  + user.getTwitch()
									+ "](http://www.twitch.tv/" + Utils.urlEncode(user.getTwitch()) + ")") + "\n"
								+ emojis[10] + "  **Twitter:** "
									+ (user.getTwitter().isEmpty() ? '*' + tr.translate("strings_gd", "not_provided") + '*' : "[@" + user.getTwitter() + "]"
									+ "(http://www.twitter.com/" + Utils.urlEncode(user.getTwitter()) + ")") + "\n"
								+ emojis[12] + "  **Discord:** " + (linkedAccounts.isEmpty() ? '*' + tr.translate("strings_gd", "unknown") + '*' : linkedAccounts.stream()
										.reduce(new StringJoiner(", "), (sj, l) -> sj.add(l.getTag()), (a, b) -> a).toString())
								+ "\n───────────\n"
								+ emojis[13] + "  **" + tr.translate("strings_gd", "label_friend_requests") + "** " + (user.hasFriendRequestsEnabled() 
										? tr.translate("strings_gd", "enabled") : tr.translate("strings_gd", "disabled")) + "\n"
								+ emojis[14] + "  **" + tr.translate("strings_gd", "label_private_messages") + "** " + formatPrivacy(user.getPrivateMessagePolicy()) + "\n"
								+ emojis[15] + "  **" + tr.translate("strings_gd", "label_comment_history") + "** " + formatPrivacy(user.getCommmentHistoryPolicy()) + "\n", false);
						embed.setFooter(tr.translate("strings_gd", "label_player_id") + ' ' + user.getId() + " | "
								+ tr.translate("strings_gd", "label_account_id") + ' ' + user.getAccountId(), null);
						if (iconSetUrl.startsWith("http")) {
							embed.setImage(iconSetUrl);
						} else {
							embed.addField(":warning: " + tr.translate("strings_gd", "icon_set_fail"), iconSetUrl, false);
						}
					};
					if (content == null) {
						return new MessageSpecTemplate(embedSpec);
					}
					return new MessageSpecTemplate(content, embedSpec);
				});
	}
	
	public static Mono<String> makeIconSet(Translator tr, Bot bot, GDUser user, SpriteFactory sf, Cache<GDUserIconSet, String> iconsCache, Snowflake iconChannelId) {
		return Mono.defer(() -> {
			final var iconSet = new GDUserIconSet(user, sf);
			final var cached = iconsCache.getIfPresent(iconSet);
			if (cached != null) {
				return Mono.just(cached);
			}
			final var icons = new ArrayList<BufferedImage>();
			try {
				for (var iconType : IconType.values()) {
						icons.add(iconSet.generateIcon(iconType));
				}
			} catch (IllegalArgumentException e) {
				return Mono.error(e);
			}
			final var iconSetImg = new BufferedImage(icons.stream().mapToInt(BufferedImage::getWidth).sum(), icons.get(0).getHeight(), icons.get(0).getType());
			final var g = iconSetImg.createGraphics();
			var offset = 0;
			for (var icon : icons) {
				g.drawImage(icon, offset, 0, null);
				offset += icon.getWidth();
			}
			g.dispose();
			
			try {
				final var istreamIconSet = imageToInputStream(iconSetImg);
				return bot.gateway().getChannelById(iconChannelId)
						.ofType(MessageChannel.class)
						.flatMap(c -> c.createMessage(mcs -> mcs.addFile(user.getId() + "-IconSet.png", istreamIconSet)))
						.flatMap(msg -> Flux.fromIterable(msg.getAttachments()).next())
						.filter(att -> att.getSize() > 0)
						.timeout(Duration.ofSeconds(30), Mono.empty())
						.switchIfEmpty(Mono.error(new CommandFailedException(tr.translate("strings_gd", "error_icon_set_upload_failed"))))
						.map(Attachment::getUrl)
						.doOnNext(url -> iconsCache.put(iconSet, url));
			} catch (IOException e) {
				return Mono.error(e);
			}
		}).subscribeOn(Schedulers.boundedElastic());
	}
	
	private static InputStream imageToInputStream(BufferedImage img) throws IOException {
		final var os = new ByteArrayOutputStream(100_000);
		ImageIO.write(img, "png", os);
		return new ByteArrayInputStream(os.toByteArray());
	}
	
	public static Mono<GDUser> stringToUser(Translator tr, Bot bot, AuthenticatedGDClient gdClient, String str) {
		if (str.matches("<@!?[0-9]{1,19}>")) {
			var id = str.substring(str.startsWith("<@!") ? 3 : 2, str.length() - 1);
			return Mono.just(id)
					.map(Snowflake::of)
					.onErrorMap(e -> new CommandFailedException(tr.translate("strings_gd", "error_invalid_mention")))
					.flatMap(snowflake -> bot.gateway().withRetrievalStrategy(STORE_FALLBACK_REST).getUserById(snowflake))
					.onErrorMap(e -> new CommandFailedException(tr.translate("strings_gd", "error_mention_resolve")))
					.flatMap(user -> bot.service(DatabaseService.class).withExtension(GDLinkedUserDao.class, dao -> dao.getByDiscordUserId(user.getId().asLong())))
					.flatMap(Mono::justOrEmpty)
					.filter(GDLinkedUserData::isLinkActivated)
					.flatMap(linkedUser -> gdClient.getUserByAccountId(linkedUser.gdUserId()))
					.switchIfEmpty(Mono.error(new CommandFailedException(tr.translate("strings_gd", "error_no_gd_account"))));
		}
		if (!str.matches("[a-zA-Z0-9 _-]+")) {
			return Mono.error(new CommandFailedException(tr.translate("strings_gd", "error_invalid_characters")));
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
	
	public static Flux<User> getDiscordAccountsForGDUser(Bot bot, long gdUserId) {
		return bot.service(DatabaseService.class)
				.withExtension(GDLinkedUserDao.class, dao -> dao.getLinkedAccountsForGdUser(gdUserId))
				.flatMapMany(Flux::fromIterable)
				.flatMap(linkedUser -> bot.gateway().withRetrievalStrategy(STORE_FALLBACK_REST)
						.getUserById(linkedUser.discordUserId()));
	}
}
