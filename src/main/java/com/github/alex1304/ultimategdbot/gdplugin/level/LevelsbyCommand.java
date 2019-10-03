package com.github.alex1304.ultimategdbot.gdplugin.level;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import com.github.alex1304.jdash.entity.GDLevel;
import com.github.alex1304.jdash.entity.GDUser;
import com.github.alex1304.jdash.exception.MissingAccessException;
import com.github.alex1304.jdash.util.GDPaginator;
import com.github.alex1304.ultimategdbot.api.command.Context;
import com.github.alex1304.ultimategdbot.api.command.annotated.CommandAction;
import com.github.alex1304.ultimategdbot.api.command.annotated.CommandDoc;
import com.github.alex1304.ultimategdbot.api.command.annotated.CommandSpec;
import com.github.alex1304.ultimategdbot.api.utils.UniversalMessageSpec;
import com.github.alex1304.ultimategdbot.api.utils.menu.InteractiveMenu;
import com.github.alex1304.ultimategdbot.api.utils.menu.PageNumberOutOfRangeException;
import com.github.alex1304.ultimategdbot.api.utils.menu.UnexpectedReplyException;
import com.github.alex1304.ultimategdbot.gdplugin.GDServiceMediator;
import com.github.alex1304.ultimategdbot.gdplugin.util.GDUtils;

import discord4j.core.spec.MessageCreateSpec;
import reactor.core.publisher.Mono;

@CommandSpec(
		aliases = "levelsby",
		shortDescription = "Browse levels from a specific player in Geometry Dash."
)
public class LevelsbyCommand {

	private final GDServiceMediator gdServiceMediator;
	
	public LevelsbyCommand(GDServiceMediator gdServiceMediator) {
		this.gdServiceMediator = gdServiceMediator;
	}
	
	@CommandAction
	@CommandDoc("You can specify the user either by their name or their player ID. If several "
			+ "results are found, an interactive menu will open allowing you to navigate "
			+ "through results and select the result you want.")
	public Mono<Void> execute(Context ctx, GDUser user) {
		var currentPage = new AtomicInteger();
		var resultsOfCurrentPage = new AtomicReference<GDPaginator<GDLevel>>();
		return InteractiveMenu.createPaginatedWhen(currentPage, page -> gdServiceMediator.getGdClient()
						.getLevelsByUser(user, page)
						.doOnNext(resultsOfCurrentPage::set)
						.flatMap(results -> GDUtils.levelPaginatorView(ctx, results, user.getName() + "'s levels"))
						.map(UniversalMessageSpec::new)
						.onErrorMap(e -> e instanceof MissingAccessException && page > 0,
								e -> new PageNumberOutOfRangeException(0, page - 1)))
				.addMessageItem("select", interaction -> Mono
						.fromCallable(() -> resultsOfCurrentPage.get().asList().get(Integer.parseInt(interaction.getArgs().get(0))))
						.onErrorMap(IndexOutOfBoundsException.class, e -> new UnexpectedReplyException(
								interaction.getArgs().tokenCount() == 1 ? "Please secify a result number"
										: "Your input refers to a non-existing result."))
						.onErrorMap(NumberFormatException.class, e -> new UnexpectedReplyException("Invalid input"))
						.flatMap(level -> showLevelDetail(ctx, level)))
				.open(ctx);
	}
	
	private Mono<Void> showLevelDetail(Context ctx, GDLevel level) {
		return GDUtils.levelView(ctx, level, "Search result", "https://i.imgur.com/a9B6LyS.png")
				.<Consumer<MessageCreateSpec>>map(embed -> m -> m.setEmbed(embed))
				.flatMap(embed -> InteractiveMenu.create(embed)
						.addReactionItem("cross", interaction -> Mono.empty())
						.deleteMenuOnClose(true)
						.open(ctx));
	}
}
