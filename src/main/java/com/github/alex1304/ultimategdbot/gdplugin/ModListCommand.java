package com.github.alex1304.ultimategdbot.gdplugin;

import java.util.Objects;
import java.util.Set;

import com.github.alex1304.ultimategdbot.api.Command;
import com.github.alex1304.ultimategdbot.api.Context;
import com.github.alex1304.ultimategdbot.api.Plugin;
import com.github.alex1304.ultimategdbot.api.utils.reply.PaginatedReplyMenuBuilder;

import reactor.core.publisher.Mono;

public class ModListCommand implements Command {
	
	private final GDPlugin plugin;

	public ModListCommand(GDPlugin plugin) {
		this.plugin = Objects.requireNonNull(plugin);
	}

	@Override
	public Mono<Void> execute(Context ctx) {
		return Mono.zip(ctx.getBot().getEmoji("elder_mod"), ctx.getBot().getEmoji("mod"), 
						ctx.getBot().getDatabase().query(GDModList.class, "from GDModList order by isElder desc, name").collectList())
				.flatMap(tuple -> {
					var rb = new PaginatedReplyMenuBuilder(this, ctx, true, false, 800);
					var sb = new StringBuilder("**__Geometry Dash Moderator List:__\n**");
					sb.append("This list is automatically updated when the `")
						.append(ctx.getPrefixUsed())
						.append("checkmod` command is used against newly promoted/demoted players.\n\n");
					for (var gdMod : tuple.getT3()) {
						sb.append(gdMod.getIsElder() ? tuple.getT1() : tuple.getT2());
						sb.append(" **").append(gdMod.getName()).append("**\n");
					}
					return rb.build(sb.toString());
				}).then();
	}

	@Override
	public Set<String> getAliases() {
		return Set.of("modlist");
	}

	@Override
	public String getDescription() {
		return "Displays the full list of last known Geometry Dash moderators.";
	}

	@Override
	public String getLongDescription() {
		return "";
	}

	@Override
	public String getSyntax() {
		return "";
	}

	@Override
	public Plugin getPlugin() {
		return plugin;
	}
}
