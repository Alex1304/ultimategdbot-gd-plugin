package com.github.alex1304.ultimategdbot.gdplugin;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;

import com.github.alex1304.ultimategdbot.api.Command;
import com.github.alex1304.ultimategdbot.api.Context;
import com.github.alex1304.ultimategdbot.api.PermissionLevel;
import com.github.alex1304.ultimategdbot.api.utils.reply.PaginatedReplyMenuBuilder;

import discord4j.core.object.entity.Channel.Type;
import reactor.core.publisher.Mono;

public class ModListCommand implements Command {

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
	public Set<Command> getSubcommands() {
		return Set.of();
	}

	@Override
	public String getDescription() {
		return "Displays the full list of last known Geometry Dash moderators.";
	}

	@Override
	public String getSyntax() {
		return "";
	}

	@Override
	public PermissionLevel getPermissionLevel() {
		return PermissionLevel.PUBLIC;
	}

	@Override
	public EnumSet<Type> getChannelTypesAllowed() {
		return EnumSet.of(Type.GUILD_TEXT, Type.DM);
	}

	@Override
	public Map<Class<? extends Throwable>, BiConsumer<Throwable, Context>> getErrorActions() {
		return Map.of();
	}
}
