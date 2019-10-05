package com.github.alex1304.ultimategdbot.gdplugin.command;

import java.util.HashSet;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import com.github.alex1304.jdash.entity.GDLevel;
import com.github.alex1304.jdash.exception.MissingAccessException;
import com.github.alex1304.ultimategdbot.api.command.CommandFailedException;
import com.github.alex1304.ultimategdbot.api.command.Context;
import com.github.alex1304.ultimategdbot.api.command.annotated.CommandAction;
import com.github.alex1304.ultimategdbot.api.command.annotated.CommandDoc;
import com.github.alex1304.ultimategdbot.api.command.annotated.CommandSpec;
import com.github.alex1304.ultimategdbot.gdplugin.GDServiceMediator;
import com.github.alex1304.ultimategdbot.gdplugin.util.GDUtils;

import discord4j.core.object.entity.Message;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

@CommandSpec(
		aliases = "featuredinfo",
		shortDescription = "Finds the exact position of a level in the Featured section."
)
public class FeaturedInfoCommand {

	private final GDServiceMediator gdServiceMediator;
	
	public FeaturedInfoCommand(GDServiceMediator gdServiceMediator) {
		this.gdServiceMediator = gdServiceMediator;
	}

	@CommandAction
	@CommandDoc("Finds the exact position of a level in the Featured section. Levels are sorted "
			+ "in the Featured section by a score. This score is given by "
			+ "RobTop and determines its position in the Featured section. The bot uses this "
			+ "score in order to perform a dichotomous search in the Featured section, "
			+ "allowing it to find the position of any level in only a few seconds, regardless "
			+ "of how far back it is.")
	public Mono<Void> run(Context ctx, GDLevel level) {
		final var score = level.getFeaturedScore();
		if (score == 0) {
			return Mono.error(new CommandFailedException("This level is not featured."));
		}
		final var initialMax = 3000;
		final var min = new AtomicInteger();
		final var max = new AtomicInteger(initialMax);
		final var currentPage = new AtomicInteger();
		final var isFindingFirstPageWithMatchingScore = new AtomicBoolean();
		final var continueAlgo = new AtomicBoolean(true);
		final var result = new AtomicReference<Tuple2<Integer, Integer>>();
		final var alreadyVisitedPages = new HashSet<Integer>();
		return ctx.reply("Searching, please wait...")
				.flatMap(waitMessage -> Mono.defer(() -> gdServiceMediator.getGdClient().browseFeaturedLevels(currentPage.get())
						.flatMap(paginator -> {
							alreadyVisitedPages.add(currentPage.get());
							final var scoreOfFirst = paginator.asList().get(0).getFeaturedScore();
							if (isFindingFirstPageWithMatchingScore.get()) {
								var i = 1;
								for (var l : paginator) {
									if (level.equals(l)) {
										result.set(Tuples.of(currentPage.get(), i));
										continueAlgo.set(false);
										break;
									}
									if (l.getFeaturedScore() < score) {
										continueAlgo.set(false);
										break;
									}
									i++;
								}
								if (continueAlgo.get()) {
									currentPage.incrementAndGet();
								}
							} else {
								if (scoreOfFirst > score) {
									min.set(currentPage.get());
									currentPage.set(Math.max(currentPage.get() + 1, (min.get() + max.get()) / 2));
									if (currentPage.get() > initialMax) {
										max.addAndGet(initialMax);
									}
								} else {
									max.set(currentPage.get() - 1);
									currentPage.set((min.get() + max.get()) / 2);
								}
								if (alreadyVisitedPages.contains(currentPage.get())) {
									isFindingFirstPageWithMatchingScore.set(true);
								}
							}
							return Mono.empty();
						})
						.onErrorResume(MissingAccessException.class, e -> Mono.fromRunnable(() -> {
							if (isFindingFirstPageWithMatchingScore.get()) {
								continueAlgo.set(false);
							} else {
								max.set(currentPage.get() - 1);
								currentPage.set((min.get() + max.get()) / 2);
								if (alreadyVisitedPages.contains(currentPage.get())) {
									isFindingFirstPageWithMatchingScore.set(true);
								}
							}
						})))
						.repeat(continueAlgo::get)
						.then(Mono.defer(() -> result.get() == null
								? Mono.error(new CommandFailedException("Unable to find " + GDUtils.levelToString(level) + " in the Featured section."))
								: sendResult(waitMessage, ctx, level, result.get().getT1(), result.get().getT2())))
						.doOnTerminate(() -> waitMessage.delete().onErrorResume(e -> Mono.empty()).subscribe())
						.then());
	}
	
	private Mono<Message> sendResult(Message waitMessage, Context ctx, GDLevel level, int page, int position) {
		return waitMessage.delete().then(ctx.getEvent().getMessage().getAuthor().map(author -> ctx.reply(author.getMention() + ", "
						+ GDUtils.levelToString(level) + " is currently placed in page **" + (page + 1)
						+ "** of the Featured section at position " + position)).orElse(Mono.empty()));
	}
}
