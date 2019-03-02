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

import com.github.alex1304.jdash.entity.GDUser;
import com.github.alex1304.jdash.entity.IconType;
import com.github.alex1304.jdash.entity.Role;
import com.github.alex1304.jdash.exception.BadResponseException;
import com.github.alex1304.jdash.exception.CorruptedResponseContentException;
import com.github.alex1304.jdash.exception.MissingAccessException;
import com.github.alex1304.jdash.graphics.SpriteFactory;
import com.github.alex1304.jdash.util.GDUserIconSet;
import com.github.alex1304.jdash.util.Utils;
import com.github.alex1304.ultimategdbot.api.Context;

import discord4j.core.object.entity.MessageChannel;
import discord4j.core.spec.MessageCreateSpec;
import reactor.core.publisher.Mono;

public final class GDUtils {
	private GDUtils() {
	}

	public static final Map<Class<? extends Throwable>, BiConsumer<Throwable, Context>> DEFAULT_GD_ERROR_ACTIONS = defaultGDErrorActions();

	private static Map<Class<? extends Throwable>, BiConsumer<Throwable, Context>> defaultGDErrorActions() {
		var map = new HashMap<Class<? extends Throwable>, BiConsumer<Throwable, Context>>();
		map.put(MissingAccessException.class, (error, ctx) -> {
			ctx.getBot().getEmoji("cross").flatMap(emoji -> ctx.reply(emoji + " Nothing found.")).subscribe();
		});
		map.put(BadResponseException.class, (error, ctx) -> {
			BadResponseException e = (BadResponseException) error;
			var status = e.getResponse().status();
			ctx.getBot().getEmoji("cross").flatMap(emoji -> ctx.reply(emoji + " Geometry Dash server returned a `"
					+ status.code() + " " + status.reasonPhrase() + "` error. Try again later.")).subscribe();
		});
		map.put(CorruptedResponseContentException.class, (error, ctx) -> {
			ctx.getBot().getEmoji("cross")
					.flatMap(emoji -> ctx.reply(emoji + " Geometry Dash server returned an invalid response."
							+ " Unable to show the information you requested. Sorry for the inconvenience."))
					.subscribe();
			ctx.getBot().logStackTrace(ctx, error).subscribe();
		});
		return Collections.unmodifiableMap(map);
	}
	
	public static Consumer<MessageCreateSpec> profileEmbed(Context ctx, GDUser user, String iconUrl, String iconSetUrl) {
		return mcs -> {
			var author = ctx.getEvent().getMessage().getAuthor();
			if (author.isPresent()) {
				mcs.setContent(author.get().getMention() + ", here is the profile of user **" + user.getName() + "**:");
			}
			mcs.setEmbed(embed -> {
				embed.setAuthor("User profile", null, "https://i.imgur.com/ppg4HqJ.png");
				var emojis = Mono.zip(o -> o, ctx.getBot().getEmoji("star"), ctx.getBot().getEmoji("diamond"),
						ctx.getBot().getEmoji("user_coin"), ctx.getBot().getEmoji("secret_coin"),
						ctx.getBot().getEmoji("demon"), ctx.getBot().getEmoji("creator_points"),
						ctx.getBot().getEmoji("mod"), ctx.getBot().getEmoji("elder_mod"),
						ctx.getBot().getEmoji("global_rank"), ctx.getBot().getEmoji("youtube"),
						ctx.getBot().getEmoji("twitter"), ctx.getBot().getEmoji("twitch"), ctx.getBot().getEmoji("discord"),
						ctx.getBot().getEmoji("blank")).block();
				embed.addField(":chart_with_upwards_trend:  " + user.getName() + "'s stats", emojis[0] + "  " + formatStat(user.getStars()) + emojis[13]
						+ emojis[1] + "  " + formatStat(user.getDiamonds()) + "\n"
						+ emojis[2] + "  " + formatStat(user.getUserCoins()) + emojis[13]
						+ emojis[3] + "  " + formatStat(user.getSecretCoins()) + "\n"
						+ emojis[4] + "  " + formatStat(user.getDemons()) + emojis[13]
						+ emojis[5] + "  " + formatStat(user.getCreatorPoints()) + "\n", false);
				var badge = user.getRole() == Role.ELDER_MODERATOR ? emojis[7] : emojis[6];
				var mod = badge + "  **" + user.getRole().toString().replaceAll("_", " ") + "**\n";
				embed.addField("───────────", (user.getRole() != Role.USER ? mod : "")
						+ emojis[8] + "  **Global Rank:** "
						+ (user.getGlobalRank() == 0 ? "*Unranked*" : user.getGlobalRank()) + "\n"
						+ emojis[9] + "  **Youtube:** "
							+ (user.getYoutube().isEmpty() ? "*not provided*" : "[Open link](https://www.youtube.com/channel/"
							+ Utils.urlEncode(user.getYoutube()) + ")") + "\n"
						+ emojis[10] + "  **Twitch:** "
							+ (user.getTwitch().isEmpty() ? "*not provided*" : "["  + user.getTwitch()
							+ "](http://www.twitch.tv/" + Utils.urlEncode(user.getTwitch()) + ")") + "\n"
						+ emojis[11] + "  **Twitter:** "
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
	
	public static Mono<String[]> makeIconSet(Context ctx, GDUser user, SpriteFactory sf) {
		var iconSet = new GDUserIconSet(user, sf);
		var mainIcon = iconSet.generateIcon(user.getMainIconType());
		var icons = new ArrayList<BufferedImage>();
		for (var iconType : IconType.values()) {
			icons.add(iconSet.generateIcon(iconType));
		}
		var iconSetImg = new BufferedImage(icons.stream().mapToInt(BufferedImage::getWidth).sum(), mainIcon.getHeight(), mainIcon.getType());
		var g = iconSetImg.createGraphics();
		var offset = 0;
		for (var icon : icons) {
			g.drawImage(icon, offset, 0, null);
			offset += icon.getWidth();
		}
		var istreamMain = imageToInputStream(mainIcon);
		var istreamIconSet = imageToInputStream(iconSetImg);
		
		return ctx.getBot().getAttachmentsChannel().ofType(MessageChannel.class).flatMap(c -> c.createMessage(mcs -> {
			mcs.addFile(user.getId() + "-Main.png", istreamMain);
			mcs.addFile(user.getId() + "-IconSet.png", istreamIconSet);
		})).map(msg -> {
			String[] urls = new String[2];
			for (var a : msg.getAttachments()) {
				urls[a.getFilename().endsWith("Main.png") ? 0 : 1] = a.getUrl();
			}
			return urls;
		});
	}
	
	private static InputStream imageToInputStream(BufferedImage img) {
		try {
			var os = new ByteArrayOutputStream();
			ImageIO.write(img, "png", os);
			return new ByteArrayInputStream(os.toByteArray());
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}
}
