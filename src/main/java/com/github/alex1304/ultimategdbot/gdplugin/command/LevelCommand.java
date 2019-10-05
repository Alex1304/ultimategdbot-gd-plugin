package com.github.alex1304.ultimategdbot.gdplugin.command;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import com.github.alex1304.jdash.entity.GDLevel;
import com.github.alex1304.jdash.exception.MissingAccessException;
import com.github.alex1304.jdash.util.GDPaginator;
import com.github.alex1304.jdash.util.LevelSearchFilters;
import com.github.alex1304.ultimategdbot.api.command.CommandFailedException;
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
		aliases = "level",
		shortDescription = "Searches for online levels in Geometry Dash."
)
public class LevelCommand {

	private final GDServiceMediator gdServiceMediator;
	
	public LevelCommand(GDServiceMediator gdServiceMediator) {
		this.gdServiceMediator = gdServiceMediator;
	}
	
	@CommandAction
	@CommandDoc("Searches for online levels in Geometry Dash. You can specify the level either by "
			+ "its name or its ID. If several results are found, an interactive menu will open "
			+ "allowing you to navigate through results and select the result you want.")
	public Mono<Void> execute(Context ctx, String query) {
		if (!query.matches("[a-zA-Z0-9 _-]+")) {
			return Mono.error(new CommandFailedException("Your query contains invalid characters."));
		}
		var currentPage = new AtomicInteger();
		var resultsOfCurrentPage = new AtomicReference<GDPaginator<GDLevel>>();
		return gdServiceMediator.getGdClient()
				.searchLevels(query, LevelSearchFilters.create(), 0)
				.doOnNext(resultsOfCurrentPage::set)
				.flatMap(results -> results.asList().size() == 1 ? showLevelDetail(ctx, results.asList().get(0), false)
						: InteractiveMenu.createPaginatedWhen(currentPage, page -> results
								.goTo(page)
								.doOnNext(resultsOfCurrentPage::set)
								.flatMap(newResults -> GDUtils.levelPaginatorView(ctx, newResults, "Search results for `" + query + "`"))
								.map(UniversalMessageSpec::new)
								.onErrorMap(MissingAccessException.class, e -> new PageNumberOutOfRangeException(0, page - 1)))
						.addMessageItem("select", interaction -> Mono
								.fromCallable(() -> resultsOfCurrentPage.get().asList().get(Integer.parseInt(interaction.getArgs().get(1))))
								.onErrorMap(IndexOutOfBoundsException.class, e -> new UnexpectedReplyException(
										interaction.getArgs().tokenCount() == 1 ? "Please secify a result number"
												: "Your input refers to a non-existing result."))
								.onErrorMap(NumberFormatException.class, e -> new UnexpectedReplyException("Invalid input"))
								.flatMap(level -> showLevelDetail(ctx, level, true)))
						.open(ctx));
	}
	
	private Mono<Void> showLevelDetail(Context ctx, GDLevel level, boolean withCloseOption) {
		return GDUtils.levelView(ctx, level, "Search result", "https://i.imgur.com/a9B6LyS.png")
				.<Consumer<MessageCreateSpec>>map(embed -> m -> m.setEmbed(embed))
				.flatMap(embed -> !withCloseOption ? ctx.reply(embed).then() : InteractiveMenu.create(embed)
						.addReactionItem("cross", interaction -> Mono.empty())
						.deleteMenuOnClose(true)
						.open(ctx));
	}
}
