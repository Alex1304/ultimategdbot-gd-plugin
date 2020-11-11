package com.github.alex1304.ultimategdbot.gdplugin.user;

import static com.github.alex1304.ultimategdbot.gdplugin.util.GDFormatter.formatCode;
import static com.github.alex1304.ultimategdbot.gdplugin.util.GDFormatter.formatPrivacy;
import static discord4j.core.retriever.EntityRetrievalStrategy.STORE_FALLBACK_REST;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Random;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import javax.imageio.ImageIO;

import org.jdbi.v3.core.mapper.immutables.JdbiImmutables;

import com.github.alex1304.jdash.client.AuthenticatedGDClient;
import com.github.alex1304.jdash.entity.GDUser;
import com.github.alex1304.jdash.entity.IconType;
import com.github.alex1304.jdash.entity.Role;
import com.github.alex1304.jdash.graphics.SpriteFactory;
import com.github.alex1304.jdash.util.GDUserIconSet;
import com.github.alex1304.jdash.util.Utils;
import com.github.alex1304.ultimategdbot.api.BotConfig;
import com.github.alex1304.ultimategdbot.api.Translator;
import com.github.alex1304.ultimategdbot.api.command.CommandFailedException;
import com.github.alex1304.ultimategdbot.api.service.BotService;
import com.github.alex1304.ultimategdbot.api.util.MessageSpecTemplate;
import com.github.alex1304.ultimategdbot.gdplugin.database.GDLeaderboardBanData;
import com.github.alex1304.ultimategdbot.gdplugin.database.GDLeaderboardDao;
import com.github.alex1304.ultimategdbot.gdplugin.database.GDLeaderboardData;
import com.github.alex1304.ultimategdbot.gdplugin.database.GDLinkedUserDao;
import com.github.alex1304.ultimategdbot.gdplugin.database.GDLinkedUserData;
import com.github.alex1304.ultimategdbot.gdplugin.database.GDModData;
import com.github.alex1304.ultimategdbot.gdplugin.database.ImmutableGDLeaderboardData;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import discord4j.common.util.Snowflake;
import discord4j.core.object.entity.Attachment;
import discord4j.core.object.entity.User;
import discord4j.core.object.entity.channel.MessageChannel;
import discord4j.core.spec.EmbedCreateSpec;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

public final class GDUserService {
	
	private final BotService bot;
	private final AuthenticatedGDClient gdClient;
	private final SpriteFactory spriteFactory;
	
	private final Cache<GDUserIconSet, String> iconsCache;
	private final Snowflake iconChannelId;

	public GDUserService(
			BotConfig botConfig,
			BotService bot,
			AuthenticatedGDClient gdClient,
			SpriteFactory spriteFactory) {
		this.bot = bot;
		bot.database().configureJdbi(jdbi -> {
			jdbi.getConfig(JdbiImmutables.class).registerImmutable(
					GDLeaderboardBanData.class,
					GDLeaderboardData.class,
					GDLinkedUserData.class,
					GDModData.class);
		});
		this.gdClient = gdClient;
		this.spriteFactory = spriteFactory;
		var gdConfig = botConfig.resource("gd");
		var iconsCacheMaxSize = gdConfig.readOptional("gdplugin.icons_cache_max_size")
				.map(Integer::parseInt)
				.orElse(2048);
		this.iconsCache = Caffeine.newBuilder().maximumSize(iconsCacheMaxSize).build();
		this.iconChannelId = gdConfig.readAs("gdplugin.icon_channel_id", Snowflake::of);
	}

	public Mono<MessageSpecTemplate> userProfileView(Translator tr, User author, GDUser user, 
			String authorName, String authorIconUrl, String iconSetUrl) {
		return Mono.zip(o -> o, bot.emoji().get("star"), bot.emoji().get("diamond"),
						bot.emoji().get("user_coin"), bot.emoji().get("secret_coin"), bot.emoji().get("demon"),
						bot.emoji().get("creator_points"), bot.emoji().get("mod"), bot.emoji().get("elder_mod"),
						bot.emoji().get("global_rank"), bot.emoji().get("youtube"), bot.emoji().get("twitter"),
						bot.emoji().get("twitch"), bot.emoji().get("discord"), bot.emoji().get("friends"),
						bot.emoji().get("messages"), bot.emoji().get("comment_history"))
				.zipWith(getDiscordAccountsForGDUser(user.getAccountId()).collectList())
				.map(tuple -> {
					var emojis = tuple.getT1();
					var linkedAccounts = tuple.getT2();
					final var statWidth = 9;
					final var content = author == null ? "" : tr.translate("GDStrings", "profile_intro", author.getMention(), user.getName());
					Consumer<EmbedCreateSpec> embedSpec = embed -> {
						embed.setAuthor(authorName, null, authorIconUrl);
						embed.addField(":chart_with_upwards_trend:  " + tr.translate("GDStrings", "player_stats", user.getName()),
								emojis[0] + "  " + formatCode(user.getStars(), statWidth) + "\n"
								+ emojis[1] + "  " + formatCode(user.getDiamonds(), statWidth) + "\n"
								+ emojis[2] + "  " + formatCode(user.getUserCoins(), statWidth) + "\n"
								+ emojis[3] + "  " + formatCode(user.getSecretCoins(), statWidth) + "\n"
								+ emojis[4] + "  " + formatCode(user.getDemons(), statWidth) + "\n"
								+ emojis[5] + "  " + formatCode(user.getCreatorPoints(), statWidth) + "\n", false);
						final var badge = user.getRole() == Role.ELDER_MODERATOR ? emojis[7] : emojis[6];
						final var mod = badge + "  **" + user.getRole().toString().replaceAll("_", " ") + "**\n";
						embed.addField("───────────", (user.getRole() != Role.USER ? mod : "")
								+ emojis[8] + "  **" + tr.translate("GDStrings", "label_global_rank") + "** "
								+ (user.getGlobalRank() == 0 ? '*' + tr.translate("GDStrings", "unranked") + '*' : user.getGlobalRank()) + "\n"
								+ emojis[9] + "  **YouTube:** "
									+ (user.getYoutube().isEmpty()
											? '*' + tr.translate("GDStrings", "not_provided") + '*'
											: '[' + tr.translate("GDStrings", "open_link") + "](https://www.youtube.com/channel/"
													+ Utils.urlEncode(user.getYoutube()) + ")") + "\n"
								+ emojis[11] + "  **Twitch:** "
									+ (user.getTwitch().isEmpty() ? '*' + tr.translate("GDStrings", "not_provided") + '*'
											: "["  + user.getTwitch()
									+ "](http://www.twitch.tv/" + Utils.urlEncode(user.getTwitch()) + ")") + "\n"
								+ emojis[10] + "  **Twitter:** "
									+ (user.getTwitter().isEmpty() ? '*' + tr.translate("GDStrings", "not_provided") + '*'
											: "[@" + user.getTwitter() + "]"
									+ "(http://www.twitter.com/" + Utils.urlEncode(user.getTwitter()) + ")") + "\n"
								+ emojis[12] + "  **Discord:** " + (linkedAccounts.isEmpty() ? '*' + tr.translate("GDStrings", "unknown")
										+ '*' : linkedAccounts.stream().map(User::getTag).collect(Collectors.joining(", ")))
								+ "\n───────────\n"
								+ emojis[13] + "  **" + tr.translate("GDStrings", "label_friend_requests") + "** " + (user.hasFriendRequestsEnabled() 
										? tr.translate("GDStrings", "enabled") : tr.translate("GDStrings", "disabled")) + "\n"
								+ emojis[14] + "  **" + tr.translate("GDStrings", "label_private_messages") + "** "
										+ formatPrivacy(tr, user.getPrivateMessagePolicy()) + "\n"
								+ emojis[15] + "  **" + tr.translate("GDStrings", "label_comment_history") + "** "
										+ formatPrivacy(tr, user.getCommmentHistoryPolicy()) + "\n", false);
						embed.setFooter(tr.translate("GDStrings", "label_player_id") + ' ' + user.getId() + " | "
								+ tr.translate("GDStrings", "label_account_id") + ' ' + user.getAccountId(), null);
						if (iconSetUrl.startsWith("http")) {
							embed.setImage(iconSetUrl);
						} else {
							embed.addField(":warning: " + tr.translate("GDStrings", "icon_set_fail"), iconSetUrl, false);
						}
					};
					if (content == null) {
						return new MessageSpecTemplate(embedSpec);
					}
					return new MessageSpecTemplate(content, embedSpec);
				});
	}
	
	public Mono<String> makeIconSet(Translator tr, GDUser user) {
		return Mono.defer(() -> {
			final var iconSet = new GDUserIconSet(user, spriteFactory);
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
			final var iconSetImg = new BufferedImage(icons.stream().mapToInt(BufferedImage::getWidth).sum(),
					icons.get(0).getHeight(), icons.get(0).getType());
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
						.switchIfEmpty(Mono.error(new CommandFailedException(tr
								.translate("GDStrings", "error_icon_set_upload_failed"))))
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
	
	public Mono<GDUser> stringToUser(Translator tr, String str) {
		if (str.matches("<@!?[0-9]{1,19}>")) {
			var id = str.substring(str.startsWith("<@!") ? 3 : 2, str.length() - 1);
			return Mono.just(id)
					.map(Snowflake::of)
					.onErrorMap(e -> new CommandFailedException(tr.translate("GDStrings", "error_invalid_mention")))
					.flatMap(snowflake -> bot.gateway().withRetrievalStrategy(STORE_FALLBACK_REST).getUserById(snowflake))
					.onErrorMap(e -> new CommandFailedException(tr.translate("GDStrings", "error_mention_resolve")))
					.flatMap(user -> bot.database().withExtension(GDLinkedUserDao.class, dao -> dao
							.getByDiscordUserId(user.getId().asLong())))
					.flatMap(Mono::justOrEmpty)
					.filter(GDLinkedUserData::isLinkActivated)
					.flatMap(linkedUser -> gdClient.getUserByAccountId(linkedUser.gdUserId()).flatMap(this::saveUserStats))
					.switchIfEmpty(Mono.error(new CommandFailedException(tr.translate("GDStrings", "error_no_gd_account"))));
		}
		if (!str.matches("[a-zA-Z0-9 _-]+")) {
			return Mono.error(new CommandFailedException(tr.translate("GDStrings", "error_invalid_characters")));
		}
		return gdClient.searchUser(str);
	}
	
	public Mono<GDUser> saveUserStats(GDUser gdUser) {
		return bot.database()
				.useExtension(GDLeaderboardDao.class, dao -> dao.cleanInsert(ImmutableGDLeaderboardData.builder()
						.accountId(gdUser.getAccountId())
						.name(gdUser.getName())
						.lastRefreshed(Instant.now())
						.stars(gdUser.getStars())
						.diamonds(gdUser.getDiamonds())
						.userCoins(gdUser.getUserCoins())
						.secretCoins(gdUser.getSecretCoins())
						.demons(gdUser.getDemons())
						.creatorPoints(gdUser.getCreatorPoints())
						.build()))
				.thenReturn(gdUser);
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
		
		var rand = new Random();
		var alphabet = "23456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz";
		var result = new char[n];
		
		for (int i = 0 ; i < result.length ; i++) {
			result[i] = alphabet.charAt(rand.nextInt(alphabet.length()));
		}
		
		return new String(result);
	}
	
	public Flux<User> getDiscordAccountsForGDUser(long gdUserId) {
		return bot.database()
				.withExtension(GDLinkedUserDao.class, dao -> dao.getLinkedAccountsForGdUser(gdUserId))
				.flatMapMany(Flux::fromIterable)
				.flatMap(linkedUser -> bot.gateway().withRetrievalStrategy(STORE_FALLBACK_REST)
						.getUserById(linkedUser.discordUserId()));
	}
}
