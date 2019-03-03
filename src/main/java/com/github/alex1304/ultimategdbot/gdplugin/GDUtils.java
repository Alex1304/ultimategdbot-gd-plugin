package com.github.alex1304.ultimategdbot.gdplugin;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import javax.imageio.ImageIO;

import com.github.alex1304.jdash.entity.GDLevel;
import com.github.alex1304.jdash.entity.GDSong;
import com.github.alex1304.jdash.entity.GDUser;
import com.github.alex1304.jdash.entity.IconType;
import com.github.alex1304.jdash.entity.Role;
import com.github.alex1304.jdash.exception.BadResponseException;
import com.github.alex1304.jdash.exception.CorruptedResponseContentException;
import com.github.alex1304.jdash.exception.MissingAccessException;
import com.github.alex1304.jdash.graphics.SpriteFactory;
import com.github.alex1304.jdash.util.GDPaginator;
import com.github.alex1304.jdash.util.GDUserIconSet;
import com.github.alex1304.jdash.util.Utils;
import com.github.alex1304.ultimategdbot.api.Context;

import discord4j.core.object.entity.MessageChannel;
import discord4j.core.spec.EmbedCreateSpec;
import discord4j.core.spec.MessageCreateSpec;
import reactor.core.publisher.Mono;

public final class GDUtils {
	private GDUtils() {
	}

	public static final Map<Class<? extends Throwable>, BiConsumer<Throwable, Context>> DEFAULT_GD_ERROR_ACTIONS = defaultGDErrorActions();

	private static Map<Class<? extends Throwable>, BiConsumer<Throwable, Context>> defaultGDErrorActions() {
		var map = new HashMap<Class<? extends Throwable>, BiConsumer<Throwable, Context>>();
		map.put(MissingAccessException.class, (error, ctx) -> {
			ctx.reply(ctx.getBot().getEmoji("cross") + " Nothing found.").doOnError(__ -> {}).subscribe();
		});
		map.put(BadResponseException.class, (error, ctx) -> {
			BadResponseException e = (BadResponseException) error;
			var status = e.getResponse().status();
			ctx.reply(ctx.getBot().getEmoji("cross") + " Geometry Dash server returned a `" + status.code() + " "
							+ status.reasonPhrase() + "` error. Try again later.")
					.doOnError(__ -> {})
					.subscribe();
		});
		map.put(CorruptedResponseContentException.class, (error, ctx) -> {
			ctx.reply(ctx.getBot().getEmoji("cross") + " Geometry Dash server returned an invalid response."
							+ " Unable to show the information you requested. Sorry for the inconvenience.")
					.doOnError(__ -> {})
					.subscribe();
			ctx.getBot().log(":warning: Geometry Dash server returned an invalid response upon executing `" + ctx.getEvent().getMessage().getContent().get() + "`.")
					.doOnError(__ -> {}).subscribe();
		});
		return Collections.unmodifiableMap(map);
	}
	
	// ------------ USER PROFILE UTILS ------------ //
	
	public static Consumer<MessageCreateSpec> userProfileView(Context ctx, GDUser user, String iconUrl, String iconSetUrl) {
		return mcs -> {
			final var author = ctx.getEvent().getMessage().getAuthor();
			if (author.isPresent()) {
				mcs.setContent(author.get().getMention() + ", here is the profile of user **" + user.getName() + "**:");
			}
			mcs.setEmbed(embed -> {
				embed.setAuthor("User profile", null, "https://i.imgur.com/ppg4HqJ.png");
				final var eStar = ctx.getBot().getEmoji("star");
				final var eDiamond = ctx.getBot().getEmoji("diamond");
				final var eUserCoin = ctx.getBot().getEmoji("user_coin");
				final var eSecretCoin = ctx.getBot().getEmoji("secret_coin");
				final var eDemon = ctx.getBot().getEmoji("demon");
				final var eCreatorPoints = ctx.getBot().getEmoji("creator_points");
				final var eMod = ctx.getBot().getEmoji("mod");
				final var eElder = ctx.getBot().getEmoji("elder_mod");
				final var eGlobalRank = ctx.getBot().getEmoji("global_rank");
				final var eYoutube = ctx.getBot().getEmoji("youtube");
				final var eTwitter = ctx.getBot().getEmoji("twitter");
				final var eTwitch = ctx.getBot().getEmoji("twitch");
//				final var eDiscord = ctx.getBot().getEmoji("discord");
				final var eBlank = ctx.getBot().getEmoji("blank");
				embed.addField(":chart_with_upwards_trend:  " + user.getName() + "'s stats", eStar + "  " + formatStat(user.getStars()) + eBlank
						+ eDiamond + "  " + formatStat(user.getDiamonds()) + "\n"
						+ eUserCoin + "  " + formatStat(user.getUserCoins()) + eBlank
						+ eSecretCoin + "  " + formatStat(user.getSecretCoins()) + "\n"
						+ eDemon + "  " + formatStat(user.getDemons()) + eBlank
						+ eCreatorPoints + "  " + formatStat(user.getCreatorPoints()) + "\n", false);
				final var badge = user.getRole() == Role.ELDER_MODERATOR ? eElder : eMod;
				final var mod = badge + "  **" + user.getRole().toString().replaceAll("_", " ") + "**\n";
				embed.addField("────────", (user.getRole() != Role.USER ? mod : "")
						+ eGlobalRank + "  **Global Rank:** "
						+ (user.getGlobalRank() == 0 ? "*Unranked*" : user.getGlobalRank()) + "\n"
						+ eYoutube + "  **Youtube:** "
							+ (user.getYoutube().isEmpty() ? "*not provided*" : "[Open link](https://www.youtube.com/channel/"
							+ Utils.urlEncode(user.getYoutube()) + ")") + "\n"
						+ eTwitch + "  **Twitch:** "
							+ (user.getTwitch().isEmpty() ? "*not provided*" : "["  + user.getTwitch()
							+ "](http://www.twitch.tv/" + Utils.urlEncode(user.getTwitch()) + ")") + "\n"
						+ eTwitter + "  **Twitter:** "
							+ (user.getTwitter().isEmpty() ? "*not provided*" : "[@" + user.getTwitter() + "]"
							+ "(http://www.twitter.com/" + Utils.urlEncode(user.getTwitter()) + ")") + "\n"
						+ "", false);
				embed.setFooter("PlayerID: " + user.getId() + " | " + "AccountID: " + user.getAccountId(), null);
				embed.setThumbnail(iconUrl);
				embed.setImage(iconSetUrl);
			});
		};
	}
	
	private static String formatStat(int stat) {
		return String.format("`% 6d`", stat).replaceAll(" ", "‌‌ ");
	}
	
	public static Mono<String[]> makeIconSet(Context ctx, GDUser user, SpriteFactory sf, Map<GDUserIconSet, String[]> iconsCache) {
		final var iconSet = new GDUserIconSet(user, sf);
		final var cached = iconsCache.get(iconSet);
		if (cached != null) {
			return Mono.just(cached);
		}
		final var mainIcon = iconSet.generateIcon(user.getMainIconType());
		final var icons = new ArrayList<BufferedImage>();
		for (var iconType : IconType.values()) {
			icons.add(iconSet.generateIcon(iconType));
		}
		final var iconSetImg = new BufferedImage(icons.stream().mapToInt(BufferedImage::getWidth).sum(), mainIcon.getHeight(), mainIcon.getType());
		final var g = iconSetImg.createGraphics();
		var offset = 0;
		for (var icon : icons) {
			g.drawImage(icon, offset, 0, null);
			offset += icon.getWidth();
		}
		g.dispose();
		final var istreamMain = imageToInputStream(mainIcon);
		final var istreamIconSet = imageToInputStream(iconSetImg);
		
		return ctx.getBot().getAttachmentsChannel().ofType(MessageChannel.class).flatMap(c -> c.createMessage(mcs -> {
			mcs.addFile(user.getId() + "-Main.png", istreamMain);
			mcs.addFile(user.getId() + "-IconSet.png", istreamIconSet);
		})).map(msg -> {
			String[] urls = new String[2];
			for (var a : msg.getAttachments()) {
				urls[a.getFilename().endsWith("Main.png") ? 0 : 1] = a.getUrl();
			}
			iconsCache.put(iconSet, urls);
			return urls;
		});
	}
	
	private static InputStream imageToInputStream(BufferedImage img) {
		try {
			final var os = new ByteArrayOutputStream(100_000);
			ImageIO.write(img, "png", os);
			return new ByteArrayInputStream(os.toByteArray());
		} catch (IOException e) {
			throw new UncheckedIOException(e); // Should never happen
		}
	}

	// ------------ LEVEL UTILS ------------ //
	
	public static Consumer<EmbedCreateSpec> levelPaginatorView(Context ctx, GDPaginator<GDLevel> paginator) {
		final var searchQuery = String.join(" ", ctx.getArgs().subList(1, ctx.getArgs().size()));
		final var eCopy = ctx.getBot().getEmoji("copy");
		final var eObjectOverflow = ctx.getBot().getEmoji("object_overflow");
		final var eDownloads = ctx.getBot().getEmoji("downloads");
		final var eLike = ctx.getBot().getEmoji("like");
		final var eLength = ctx.getBot().getEmoji("length");
		return embed -> {
			embed.setTitle("Search results for \"" + searchQuery + "\"");
			var i = 1;
			for (var level : paginator) {
				var coins = GDUtils.coinsToEmoji(ctx, level.getCoinCount(), level.hasCoinsVerified(), true);
				embed.addField(String.format("`%02d` - %s%s | __**%s**__ by **%s** %s%s",
						i,
						GDUtils.difficultyToEmoji(ctx, level),
						coins.equals("None") ? "" : " " + coins,
						level.getName(),
						level.getCreatorName(),
						level.getOriginalLevelID() > 0 ? eCopy : "",
						level.getObjectCount() > 40000 ? eObjectOverflow : ""),
						String.format("%s %d \t\t %s %d \t\t %s %s\n"
						+ ":musical_note:  **%s**\n _ _",
						String.valueOf(eDownloads),
						level.getDownloads(),
						String.valueOf(eLike),
						level.getLikes(),
						String.valueOf(eLength),
						String.valueOf(level.getLength()),
						GDUtils.formatSongPrimaryMetadata(level.getSong())
				), false);
				i++;
			}
		};
	}
	
	private static String coinsToEmoji(Context ctx, int n, boolean verified, boolean shorten) {
		final var emoji = ctx.getBot().getEmoji("user_coin" + (verified ? "" : "_unverified"));
		final var output = new StringBuilder();
		if (shorten) {
			if (n <= 0)
				return "";
			output.append(emoji);
			output.append(" x");
			output.append(n);
		} else {
			if (n <= 0)
				return "None";
			
			for (int i = 1 ; i <= n && i <= 3 ; i++) {
				output.append(emoji);
				output.append(" ");
			}
		}
		
		return output.toString();
	}
	
	private static String difficultyToEmoji(Context ctx, GDLevel lvl) {
		var difficulty = "icon_";
		if (lvl.isDemon())
			difficulty += "demon_" + lvl.getDemonDifficulty().toString();
		else if (lvl.isAuto())
			difficulty += "auto";
		else
			difficulty += lvl.getDifficulty().toString();
		if (lvl.isEpic())
			difficulty += "_epic";
		else if (lvl.getFeaturedScore() > 0)
			difficulty += "_featured";
		var output = ctx.getBot().getEmoji(difficulty);
		if (lvl.getStars() > 0)
			output += ctx.getBot().getEmoji("star") + " x" + lvl.getStars();
		return output;
	}
	
	public static String formatSongPrimaryMetadata(Mono<GDSong> monoSong) {
		return monoSong.map(song -> "__" + song.getSongTitle() + "__ by " + song.getSongAuthorName()).onErrorReturn("Unknown song").block();
	}

	public static String formatSongSecondaryMetadata(Context ctx, Mono<GDSong> monoSong) {
		final var ePlay = ctx.getBot().getEmoji("play");
		final var eDlSong = ctx.getBot().getEmoji("download_song");
		return monoSong.map(song -> song.isCustom()
				? "SongID: " + song.getId() + " - Size: " + song.getSongSize() + "MB\n" + ePlay
						+ " [Play on Newgrounds](https://www.newgrounds.com/audio/listen/" + song.getId() + ")  "
						+ eDlSong + " [Download MP3](" + song.getDownloadURL() + ")"
				: "Geometry Dash native audio track").onErrorReturn("Song unavailable").block();
	}
}
