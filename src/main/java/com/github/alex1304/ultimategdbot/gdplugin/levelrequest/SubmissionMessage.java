package com.github.alex1304.ultimategdbot.gdplugin.levelrequest;

import java.util.Objects;
import java.util.function.Consumer;

import discord4j.core.spec.EmbedCreateSpec;
import discord4j.core.spec.MessageCreateSpec;
import discord4j.core.spec.MessageEditSpec;

public class SubmissionMessage {
	
	private final String content;
	private final Consumer<EmbedCreateSpec> embed;
	
	public SubmissionMessage(String content, Consumer<EmbedCreateSpec> embed) {
		this.content = Objects.requireNonNull(content);
		this.embed = Objects.requireNonNull(embed);
	}
	
	public Consumer<MessageCreateSpec> toMessageCreateSpec() {
		return spec -> {
			spec.setContent(content);
			spec.setEmbed(embed);
		};
	}
	
	public Consumer<MessageEditSpec> toMessageEditSpec() {
		return spec -> {
			spec.setContent(content);
			spec.setEmbed(embed);
		};
	}
}
