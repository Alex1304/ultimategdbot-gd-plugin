package com.github.alex1304.ultimategdbot.gdplugin.database;

import static com.github.alex1304.ultimategdbot.api.database.guildconfig.ValueGetters.forOptionalGuildChannel;
import static com.github.alex1304.ultimategdbot.api.database.guildconfig.ValueGetters.*;

import java.util.Optional;

import org.immutables.value.Value;

import com.github.alex1304.ultimategdbot.api.Bot;
import com.github.alex1304.ultimategdbot.api.database.guildconfig.GuildChannelConfigEntry;
import com.github.alex1304.ultimategdbot.api.database.guildconfig.GuildConfigData;
import com.github.alex1304.ultimategdbot.api.database.guildconfig.GuildConfigurator;
import com.github.alex1304.ultimategdbot.api.database.guildconfig.GuildRoleConfigEntry;
import com.github.alex1304.ultimategdbot.api.database.guildconfig.IntegerConfigEntry;
import com.github.alex1304.ultimategdbot.api.database.guildconfig.Validator;

import discord4j.core.object.entity.Role;
import discord4j.core.object.entity.channel.Channel;
import discord4j.common.util.Snowflake;

@Value.Immutable
public interface GDLevelRequestConfigData extends GuildConfigData<GDLevelRequestConfigData> {
	
	Optional<Snowflake> channelSubmissionQueueId();

	Optional<Snowflake> channelArchivedSubmissionsId();

	Optional<Snowflake> roleReviewerId();
	
	boolean isOpen();
	
	int maxQueuedSubmissionsPerUser();
	
	int minReviewsRequired();
	
	@Override
	default GuildConfigurator<GDLevelRequestConfigData> configurator(Bot bot) {
		return GuildConfigurator.builder("Geometry Dash Level Requests", this, GDLevelRequestConfigDao.class)
				.setDescription("A full-fledged level request system right in your Discord server! "
						+ "Configure a channel where to receive requests, and a role to enable the "
						+ "ability to review them. See more details on how it works here: "
						+ "<https://github.com/ultimategdbot/ultimategdbot-gd-plugin/wiki/Level-Requests-Tutorial>")
				.addEntry(GuildChannelConfigEntry.<GDLevelRequestConfigData>builder("channel_submission_queue")
						.setDisplayName("submission queue channel")
						.setValueGetter(forOptionalGuildChannel(bot, GDLevelRequestConfigData::channelSubmissionQueueId))
						.setValueSetter((data, channel) -> ImmutableGDLevelRequestConfigData.builder()
								.from(data)
								.channelSubmissionQueueId(Optional.ofNullable(channel).map(Channel::getId))
								.build()))
				.addEntry(GuildChannelConfigEntry.<GDLevelRequestConfigData>builder("channel_archived_submissions")
						.setDisplayName("archived submissions channel")
						.setValueGetter(forOptionalGuildChannel(bot, GDLevelRequestConfigData::channelArchivedSubmissionsId))
						.setValueSetter((data, channel) -> ImmutableGDLevelRequestConfigData.builder()
								.from(data)
								.channelArchivedSubmissionsId(Optional.ofNullable(channel).map(Channel::getId))
								.build()))
				.addEntry(GuildRoleConfigEntry.<GDLevelRequestConfigData>builder("role_reviewer")
						.setDisplayName("reviewer role")
						.setValueGetter(forOptionalGuildRole(bot, GDLevelRequestConfigData::roleReviewerId))
						.setValueSetter((data, role) -> ImmutableGDLevelRequestConfigData.builder()
								.from(data)
								.roleReviewerId(Optional.ofNullable(role).map(Role::getId))
								.build()))
				.addEntry(IntegerConfigEntry.<GDLevelRequestConfigData>builder("max_queued_submissions_per_user")
						.setDisplayName("max queued submissions per user")
						.setValueGetter(forSimpleValue(GDLevelRequestConfigData::maxQueuedSubmissionsPerUser))
						.setValueSetter((data, value) -> ImmutableGDLevelRequestConfigData.builder()
								.from(data)
								.maxQueuedSubmissionsPerUser(value)
								.build())
						.setValidator(Validator.allowingIf(x -> x >= 1 && x <= 20, "must be between 1 and 20")))
				.addEntry(IntegerConfigEntry.<GDLevelRequestConfigData>builder("min_reviews_required")
						.setDisplayName("min reviews required")
						.setValueGetter(forSimpleValue(GDLevelRequestConfigData::minReviewsRequired))
						.setValueSetter((data, value) -> ImmutableGDLevelRequestConfigData.builder()
								.from(data)
								.minReviewsRequired(value)
								.build())
						.setValidator(Validator.allowingIf(x -> x >= 1 && x <= 5, "must be between 1 and 5")))
				.build();
	}
}
