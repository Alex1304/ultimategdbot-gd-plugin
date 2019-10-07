package com.github.alex1304.ultimategdbot.gdplugin.util;

import static com.github.alex1304.ultimategdbot.gdplugin.util.GDFormatter.formatCode;

import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;

import com.github.alex1304.jdash.client.AuthenticatedGDClient;
import com.github.alex1304.jdash.entity.GDLevel;
import com.github.alex1304.jdash.entity.GDSong;
import com.github.alex1304.jdash.exception.MissingAccessException;
import com.github.alex1304.jdash.exception.NoTimelyAvailableException;
import com.github.alex1304.jdash.exception.SongNotAllowedForUseException;
import com.github.alex1304.jdash.util.GDPaginator;
import com.github.alex1304.ultimategdbot.api.Bot;
import com.github.alex1304.ultimategdbot.api.command.CommandFailedException;
import com.github.alex1304.ultimategdbot.api.command.Context;
import com.github.alex1304.ultimategdbot.api.utils.BotUtils;
import com.github.alex1304.ultimategdbot.api.utils.Markdown;
import com.github.alex1304.ultimategdbot.api.utils.UniversalMessageSpec;
import com.github.alex1304.ultimategdbot.api.utils.menu.InteractiveMenu;
import com.github.alex1304.ultimategdbot.api.utils.menu.PageNumberOutOfRangeException;
import com.github.alex1304.ultimategdbot.api.utils.menu.UnexpectedReplyException;

import discord4j.core.object.entity.Message;
import discord4j.core.spec.EmbedCreateSpec;
import discord4j.core.spec.MessageCreateSpec;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

public class GDLevels {
	
	public static final Map<String, String> DIFFICULTY_IMAGES = difficultyImages();
	public static final Map<Integer, String> GAME_VERSIONS = gameVersions();
	
	private GDLevels() {
	}
	
	public static Mono<Consumer<EmbedCreateSpec>> searchResultsEmbed(Context ctx, GDPaginator<GDLevel> results, String title, int page, int totalPages) {
		return Mono.zip(o -> o, ctx.getBot().getEmoji("copy"), ctx.getBot().getEmoji("object_overflow"), ctx.getBot().getEmoji("downloads"),
				ctx.getBot().getEmoji("like"), ctx.getBot().getEmoji("length"), ctx.getBot().getEmoji("user_coin"),
				ctx.getBot().getEmoji("user_coin_unverified"), ctx.getBot().getEmoji("star"), ctx.getBot().getEmoji("dislike"))
				.zipWith(getLevelDifficultyAndSongFromPaginator(ctx, results))
				.map(tuple -> {
					var emojis = tuple.getT1();
					var map = tuple.getT2();
					return embed -> {
						embed.setTitle(title);
						var i = 1;
						for (var level : results) {
							var coins = coinsToEmoji("" + emojis[level.hasCoinsVerified() ? 5 : 6], level.getCoinCount(), true);
							var difficultyEmoji = map.get(level).getT1();
							var song = map.get(level).getT2();
							embed.addField(String.format("`%02d` - %s %s | __**%s**__ by **%s** %s%s",
									i,
									difficultyEmoji + (level.getStars() > 0 ? " " + emojis[7] + " x" + level.getStars() : ""),
									coins.equals("None") ? "" : " " + coins,
									level.getName(),
									level.getCreatorName(),
									level.getOriginalLevelID() > 0 ? emojis[0] : "",
									level.getObjectCount() > 40000 ? emojis[1] : ""),
									String.format("%s %d \t\t %s %d \t\t %s %s\n"
											+ ":musical_note:  **%s**\n _ _",
											"" + emojis[2],
											level.getDownloads(),
											"" + (level.getLikes() > 0 ? emojis[3] : emojis[8]),
											level.getLikes(),
											"" + emojis[4],
											"" + level.getLength(),
											song), false);
							i++;
						}
						embed.addField("Page " + (page + 1) + "/" + totalPages,
								"To go to a specific page, type `page <number>`, e.g `page 3`\n"
								+ "To view more details on a specific search result item, type `select <result_number>`", false);
					};
				});
	}
	
	public static Mono<Consumer<EmbedCreateSpec>> detailedView(Context ctx, GDLevel level, String authorName, String authorIconUrl) {
		return Mono.zip(o -> o, ctx.getBot().getEmoji("play"), ctx.getBot().getEmoji("downloads"), ctx.getBot().getEmoji("dislike"),
				ctx.getBot().getEmoji("like"), ctx.getBot().getEmoji("length"), ctx.getBot().getEmoji("lock"),
				ctx.getBot().getEmoji("copy"), ctx.getBot().getEmoji("object_overflow"), ctx.getBot().getEmoji("user_coin"),
				ctx.getBot().getEmoji("user_coin_unverified"))
				.zipWith(Mono.zip(level.download(), formatSongPrimaryMetadata(level.getSong()),
						formatSongSecondaryMetadata(ctx, level.getSong())))
				.map(tuple -> {
					final var emojis = tuple.getT1();
					final var data = tuple.getT2().getT1();
					final var songInfo = ":musical_note:   " + tuple.getT2().getT2();
					final var songInfo2 = tuple.getT2().getT3();
					final var dlWidth = 9;
					return embed -> {
						embed.setAuthor(authorName, null, authorIconUrl);
						embed.setThumbnail(getDifficultyImageForLevel(level));
						var title = emojis[0] + "  __" + level.getName() + "__ by " + level.getCreatorName() + "";
						var description = "**Description:** " + (level.getDescription().isEmpty() ? "*(No description provided)*"
								: Markdown.escape(level.getDescription()));
						var coins = "Coins: " + coinsToEmoji("" + emojis[level.hasCoinsVerified() ? 8 : 9], level.getCoinCount(), false);
						var downloadLikesLength = emojis[1] + " " + formatCode(level.getDownloads(), dlWidth) + "\n"
								+ (level.getLikes() < 0 ? emojis[2] + " " : emojis[3] + " ") + formatCode(level.getLikes(), dlWidth) + "\n"
								+ emojis[4] + " " + formatCode(level.getLength(), dlWidth);
						var objCount = "**Object count:** ";
						if (level.getObjectCount() > 0 || level.getLevelVersion() >= 21) {
							if (level.getObjectCount() == 65535)
								objCount += ">";
							objCount += level.getObjectCount();
						} else
							objCount += "_Unknown_";
						objCount += "\n";
						var extraInfo = new StringBuilder();
						extraInfo.append("**Level ID:** " + level.getId() + "\n");
						extraInfo.append("**Level version:** " + level.getLevelVersion() + "\n");
						extraInfo.append("**Minimum GD version required to play this level:** " + formatGameVersion(level.getGameVersion()) + "\n");
						extraInfo.append(objCount);
						var pass = "";
						if (data.getPass() == -2)
							pass = "Yes, no passcode required";
						else if (data.getPass() == -1)
							pass = "No";
						else
							pass = "Yes, " + emojis[5] + " passcode: " + String.format("||%06d||", data.getPass());
						extraInfo.append("**Copyable:** " + pass + "\n");
						extraInfo.append("**Uploaded:** " + data.getUploadTimestamp() + " ago\n");
						extraInfo.append("**Last updated:** " + data.getLastUpdatedTimestamp() + " ago\n");
						if (level.getOriginalLevelID() > 0)
							extraInfo.append(emojis[6] + " **Original:** " + level.getOriginalLevelID() + "\n");
						if (level.getObjectCount() > 40000)
							extraInfo.append(emojis[7] + " **This level may lag on low end devices**\n");
						embed.addField(title, description, false);
						embed.addField(coins, downloadLikesLength + "\n_ _", false);
						embed.addField(songInfo, songInfo2 + "\n_ _\n" + extraInfo, false);
					};
				});
	}
	
	public static Mono<Consumer<EmbedCreateSpec>> compactView(Bot bot, GDLevel level, String authorName, String authorIconUrl) {
		return Mono.zip(o -> o, bot.getEmoji("play"), bot.getEmoji("downloads"), bot.getEmoji("dislike"),
				bot.getEmoji("like"), bot.getEmoji("length"), bot.getEmoji("copy"),
				bot.getEmoji("object_overflow"), bot.getEmoji("user_coin"), bot.getEmoji("user_coin_unverified"))
				.zipWith(formatSongPrimaryMetadata(level.getSong()))
				.map(tuple -> {
					final var emojis = tuple.getT1();
					final var songInfo = ":musical_note:   " + tuple.getT2();
					final var dlWidth = 9;
					return embed -> {
						embed.setAuthor(authorName, null, authorIconUrl);
						embed.setThumbnail(getDifficultyImageForLevel(level));
						var title = emojis[0] + "  __" + level.getName() + "__ by " + level.getCreatorName() + "" +
								(level.getOriginalLevelID() > 0 ? " " + emojis[5] : "") +
								(level.getObjectCount() > 40_000 ? " " + emojis[6] : "");
						var coins = "Coins: " + coinsToEmoji("" + emojis[level.hasCoinsVerified() ? 7 : 8], level.getCoinCount(), false);
						var downloadLikesLength = emojis[1] + " " + formatCode(level.getDownloads(), dlWidth) + "\n"
								+ (level.getLikes() < 0 ? emojis[2] + " " : emojis[3] + " ") + formatCode(level.getLikes(), dlWidth) + "\n"
								+ emojis[4] + " " + formatCode(level.getLength(), dlWidth);
						embed.addField(title, downloadLikesLength, false);
						embed.addField(coins, songInfo, false);
						embed.setFooter("Level ID: " + level.getId(), null);
					};
				});
	}
	
	public static Mono<Void> searchAndSend(Context ctx, String header, Supplier<Mono<GDPaginator<GDLevel>>> searchFactory) {
		var currentPage = new AtomicInteger();
		var resultsOfCurrentPage = new AtomicReference<GDPaginator<GDLevel>>();
		return searchFactory.get()
				.doOnNext(resultsOfCurrentPage::set)
				.flatMap(results -> results.asList().size() == 1 ? sendSelectedSearchResult(ctx, results.asList().get(0), false)
						: InteractiveMenu.createAsyncPaginated(currentPage, ctx.getBot().getDefaultPaginationControls(), page -> results
								.goTo(page)
								.doOnNext(resultsOfCurrentPage::set)
								.flatMap(newResults -> searchResultsEmbed(ctx, newResults, header, currentPage.get(), results.getTotalNumberOfPages()))
								.map(UniversalMessageSpec::new)
								.onErrorMap(MissingAccessException.class, e -> new PageNumberOutOfRangeException(page, 0, results.getTotalNumberOfPages() - 1))
								.onErrorMap(IllegalArgumentException.class, e -> new PageNumberOutOfRangeException(page, 0, results.getTotalNumberOfPages() - 1)))
						.addMessageItem("select", interaction -> Mono
								.fromCallable(() -> resultsOfCurrentPage.get().asList().get(Integer.parseInt(interaction.getArgs().get(1))))
								.onErrorMap(IndexOutOfBoundsException.class, e -> new UnexpectedReplyException(
										interaction.getArgs().tokenCount() == 1 ? "Please secify a result number"
												: "Your input refers to a non-existing result."))
								.onErrorMap(NumberFormatException.class, e -> new UnexpectedReplyException("Invalid input"))
								.flatMap(level -> sendSelectedSearchResult(ctx, level, true)))
						.open(ctx));
	}
	
	private static Mono<Void> sendSelectedSearchResult(Context ctx, GDLevel level, boolean withCloseOption) {
		return detailedView(ctx, level, "Search result", "https://i.imgur.com/a9B6LyS.png")
				.<Consumer<MessageCreateSpec>>map(embed -> m -> m.setEmbed(embed))
				.flatMap(m -> !withCloseOption ? ctx.reply(m).then() : InteractiveMenu.create(m)
						.addReactionItem("cross", interaction -> Mono.empty())
						.deleteMenuOnClose(true)
						.open(ctx));
	}
	
	public static Mono<Message> sendTimelyInfo(Context ctx, AuthenticatedGDClient gdClient, boolean isWeekly) {
		var timelyMono = isWeekly ? gdClient.getWeeklyDemon() : gdClient.getDailyLevel();
		var headerTitle = isWeekly ? "Weekly demon" : "Daily level" ;
		var headerLink = isWeekly ? "https://i.imgur.com/kcsP5SN.png" : "https://i.imgur.com/enpYuB8.png";
		return timelyMono
				.flatMap(timely -> timely.getLevel()
						.flatMap(level -> detailedView(ctx, level, headerTitle + " #" + timely.getId(), headerLink)
								.flatMap(embed -> {
									var cooldown = Duration.ofSeconds(timely.getCooldown());
									var formattedCooldown = BotUtils.formatDuration(cooldown);
									return ctx.reply(message -> {
										message.setContent(ctx.getEvent().getMessage().getAuthor().get().getMention()
												+ ", here is the " + headerTitle + " of today. "
												+ "Next " + headerTitle + " in " + formattedCooldown + ".");
										message.setEmbed(embed);
									});
								})))
				.onErrorMap(NoTimelyAvailableException.class, e -> new CommandFailedException("There is no "
						+ headerTitle + " set in-game at the moment. Come back later!"));
	}
	
	public static String toString(GDLevel level) {
		return "__" + level.getName() + "__ by " + level.getCreatorName() + " (" + level.getId() + ")";
	}
	
	private static String getDifficultyImageForLevel(GDLevel level) {
		var difficulty = new StringBuilder();
		difficulty.append(level.getStars()).append("-");
		if (level.isDemon())
			difficulty.append("demon-").append(level.getDemonDifficulty().toString().toLowerCase());
		else if (level.isAuto())
			difficulty.append("auto");
		else
			difficulty.append(level.getDifficulty().toString().toLowerCase());
		if (level.isEpic())
			difficulty.append("-epic");
		else if (level.getFeaturedScore() > 0)
			difficulty.append("-featured");
		return DIFFICULTY_IMAGES.getOrDefault(difficulty.toString(), "https://i.imgur.com/T3YfK5d.png");
	}
	
	private static String formatGameVersion(int v) {
		if (v < 10)
			return "<1.6";
		if (GAME_VERSIONS.containsKey(v))
			return GAME_VERSIONS.get(v);
		
		var vStr = String.format("%02d", v);
		if (vStr.length() <= 1)
			return vStr;
		
		return vStr.substring(0, vStr.length() - 1) + "." + vStr.charAt(vStr.length() - 1);
	}
	
	private static String coinsToEmoji(String emoji, int n, boolean shorten) {
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
	
	private static Mono<Map<GDLevel, Tuple2<String, String>>> getLevelDifficultyAndSongFromPaginator(Context ctx, GDPaginator<GDLevel> levels) {
		return ctx.getBot().getEmoji("star")
				.flatMap(starEmoji -> Flux.fromIterable(levels)
						.flatMap(level -> {
							var difficulty = new StringBuilder("icon_");
							if (level.isDemon())
								difficulty.append("demon_").append(level.getDemonDifficulty().toString());
							else if (level.isAuto())
								difficulty.append("auto");
							else
								difficulty.append(level.getDifficulty().toString());
							if (level.isEpic())
								difficulty.append("_epic");
							else if (level.getFeaturedScore() > 0)
								difficulty.append("_featured");
							return formatSongPrimaryMetadata(level.getSong())
									.map(songFormat -> Tuples.of(level, Tuples.of(difficulty.toString(), songFormat)));
									
						})
						
						.flatMap(tuple -> ctx.getBot().getEmoji(tuple.getT2().getT1())
								.map(emoji -> Tuples.of(tuple.getT1(), Tuples.of(emoji, tuple.getT2().getT2()))))
						.collectMap(Tuple2::getT1, Tuple2::getT2));
	}
	
	private static Mono<String> formatSongPrimaryMetadata(Mono<GDSong> monoSong) {
		return monoSong.map(song -> "__" + song.getSongTitle() + "__ by " + song.getSongAuthorName())
				.onErrorReturn(SongNotAllowedForUseException.class, ":warning: Song is not allowed for use")
				.onErrorReturn(":warning: Unknown song");
	}

	private static Mono<String> formatSongSecondaryMetadata(Context ctx, Mono<GDSong> monoSong) {
		return Mono.zip(ctx.getBot().getEmoji("play"), ctx.getBot().getEmoji("download_song"))
				.flatMap(emojis -> {
					final var ePlay = emojis.getT1();
					final var eDlSong = emojis.getT2();
					return monoSong.map(song -> song.isCustom()
							? "SongID: " + song.getId() + " - Size: " + song.getSongSize() + "MB\n" + ePlay
									+ " [Play on Newgrounds](https://www.newgrounds.com/audio/listen/" + song.getId() + ")  "
									+ eDlSong + " [Download MP3](" + song.getDownloadURL() + ")"
							: "Geometry Dash native audio track").onErrorReturn("Song info unavailable");
				});
	}
	
	private static Map<Integer, String> gameVersions() {
		var map = new HashMap<Integer, String>();
		map.put(10, "1.7");
		map.put(11, "1.8");
		return Collections.unmodifiableMap(map);
	}

	private static Map<String, String> difficultyImages() {
		var map = new HashMap<String, String>();
		map.put("6-harder-featured", "https://i.imgur.com/b7J4AXi.png");
		map.put("0-insane-epic", "https://i.imgur.com/GdS2f8f.png");
		map.put("0-harder", "https://i.imgur.com/5lT74Xj.png");
		map.put("4-hard-epic", "https://i.imgur.com/toyo1Cd.png");
		map.put("4-hard", "https://i.imgur.com/XnUynAa.png");
		map.put("6-harder", "https://i.imgur.com/e499HCB.png");
		map.put("5-hard-epic", "https://i.imgur.com/W11eyJ9.png");
		map.put("6-harder-epic", "https://i.imgur.com/9x1ddvD.png");
		map.put("5-hard", "https://i.imgur.com/Odx0nAT.png");
		map.put("1-auto-featured", "https://i.imgur.com/DplWGja.png");
		map.put("5-hard-featured", "https://i.imgur.com/HiyX5DD.png");
		map.put("8-insane-featured", "https://i.imgur.com/PYJ5T0x.png");
		map.put("0-auto-featured", "https://i.imgur.com/eMwuWmx.png");
		map.put("8-insane", "https://i.imgur.com/RDVJDaO.png");
		map.put("7-harder-epic", "https://i.imgur.com/X3N5sm1.png");
		map.put("0-normal-epic", "https://i.imgur.com/VyV8II6.png");
		map.put("0-demon-hard-featured", "https://i.imgur.com/lVdup3A.png");
		map.put("8-insane-epic", "https://i.imgur.com/N2pjW2W.png");
		map.put("3-normal-epic", "https://i.imgur.com/S3PhlDs.png");
		map.put("0-normal-featured", "https://i.imgur.com/Q1MYgu4.png");
		map.put("2-easy", "https://i.imgur.com/yG1U6RP.png");
		map.put("0-hard-featured", "https://i.imgur.com/8DeaxfL.png");
		map.put("0-demon-hard-epic", "https://i.imgur.com/xLFubIn.png");
		map.put("1-auto", "https://i.imgur.com/Fws2s3b.png");
		map.put("0-demon-hard", "https://i.imgur.com/WhrTo7w.png");
		map.put("0-easy", "https://i.imgur.com/kWHZa5d.png");
		map.put("2-easy-featured", "https://i.imgur.com/Kyjevk1.png");
		map.put("0-insane-featured", "https://i.imgur.com/t8JmuIw.png");
		map.put("0-hard", "https://i.imgur.com/YV4Afz2.png");
		map.put("0-na", "https://i.imgur.com/T3YfK5d.png");
		map.put("7-harder", "https://i.imgur.com/dJoUDUk.png");
		map.put("0-na-featured", "https://i.imgur.com/C4oMYGU.png");
		map.put("3-normal", "https://i.imgur.com/cx8tv98.png");
		map.put("0-harder-featured", "https://i.imgur.com/n5kA2Tv.png");
		map.put("0-harder-epic", "https://i.imgur.com/Y7bgUu9.png");
		map.put("0-na-epic", "https://i.imgur.com/hDBDGzX.png");
		map.put("1-auto-epic", "https://i.imgur.com/uzYx91v.png");
		map.put("0-easy-featured", "https://i.imgur.com/5p9eTaR.png");
		map.put("0-easy-epic", "https://i.imgur.com/k2lJftM.png");
		map.put("0-hard-epic", "https://i.imgur.com/SqnA9kJ.png");
		map.put("3-normal-featured", "https://i.imgur.com/1v3p1A8.png");
		map.put("0-normal", "https://i.imgur.com/zURUazz.png");
		map.put("6-harder-featured", "https://i.imgur.com/b7J4AXi.png");
		map.put("2-easy-epic", "https://i.imgur.com/wl575nH.png");
		map.put("7-harder-featured", "https://i.imgur.com/v50cZBZ.png");
		map.put("0-auto", "https://i.imgur.com/7xI8EOp.png");
		map.put("0-insane", "https://i.imgur.com/PeOvWuq.png");
		map.put("4-hard-featured", "https://i.imgur.com/VW4yufj.png");
		map.put("0-auto-epic", "https://i.imgur.com/QuRBnpB.png");
		map.put("10-demon-hard", "https://i.imgur.com/jLBD7cO.png");
		map.put("9-insane-featured", "https://i.imgur.com/byhPbgR.png");
		map.put("10-demon-hard-featured", "https://i.imgur.com/7deDmTQ.png");
		map.put("10-demon-hard-epic", "https://i.imgur.com/xtrTl4r.png");
		map.put("9-insane", "https://i.imgur.com/5VA2qDb.png");
		map.put("9-insane-epic", "https://i.imgur.com/qmfey5L.png");
		// Demon difficulties
		map.put("0-demon-medium-epic", "https://i.imgur.com/eEEzM6I.png");
		map.put("10-demon-medium-epic", "https://i.imgur.com/ghco42q.png");
		map.put("10-demon-insane", "https://i.imgur.com/nLZqoyQ.png");
		map.put("0-demon-extreme-epic", "https://i.imgur.com/p250YUh.png");
		map.put("0-demon-easy-featured", "https://i.imgur.com/r2WNVw0.png");
		map.put("10-demon-easy", "https://i.imgur.com/0zM0VuT.png");
		map.put("10-demon-medium", "https://i.imgur.com/lvpPepA.png");
		map.put("10-demon-insane-epic", "https://i.imgur.com/2BWY8pO.png");
		map.put("10-demon-medium-featured", "https://i.imgur.com/kkAZv5O.png");
		map.put("0-demon-extreme-featured", "https://i.imgur.com/4MMF8uE.png");
		map.put("0-demon-extreme", "https://i.imgur.com/v74cX5I.png");
		map.put("0-demon-medium", "https://i.imgur.com/H3Swqhy.png");
		map.put("0-demon-medium-featured", "https://i.imgur.com/IaeyGY4.png");
		map.put("0-demon-insane", "https://i.imgur.com/fNC1iFH.png");
		map.put("0-demon-easy-epic", "https://i.imgur.com/idesUcS.png");
		map.put("10-demon-easy-epic", "https://i.imgur.com/wUGOGJ7.png");
		map.put("10-demon-insane-featured", "https://i.imgur.com/RWqIpYL.png");
		map.put("10-demon-easy-featured", "https://i.imgur.com/fFq5lbN.png");
		map.put("0-demon-insane-featured", "https://i.imgur.com/1MpbSRR.png");
		map.put("0-demon-insane-epic", "https://i.imgur.com/ArGfdeh.png");
		map.put("10-demon-extreme", "https://i.imgur.com/DEr1HoM.png");
		map.put("0-demon-easy", "https://i.imgur.com/45GaxRN.png");
		map.put("10-demon-extreme-epic", "https://i.imgur.com/gFndlkZ.png");
		map.put("10-demon-extreme-featured", "https://i.imgur.com/xat5en2.png");
		return Collections.unmodifiableMap(map);
	}
}