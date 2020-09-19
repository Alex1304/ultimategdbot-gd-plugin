package com.github.alex1304.ultimategdbot.gdplugin.database;

import static com.github.alex1304.ultimategdbot.api.database.guildconfig.ValueGetters.forOptionalGuildChannel;
import static com.github.alex1304.ultimategdbot.api.database.guildconfig.ValueGetters.forOptionalGuildRole;
import static com.github.alex1304.ultimategdbot.api.database.guildconfig.ValueGetters.forSimpleValue;
import static java.util.Objects.requireNonNullElse;

import java.util.Optional;

import org.immutables.value.Value;

import com.github.alex1304.ultimategdbot.api.Translator;
import com.github.alex1304.ultimategdbot.api.database.guildconfig.GuildChannelConfigEntry;
import com.github.alex1304.ultimategdbot.api.database.guildconfig.GuildConfigData;
import com.github.alex1304.ultimategdbot.api.database.guildconfig.GuildConfigurator;
import com.github.alex1304.ultimategdbot.api.database.guildconfig.GuildRoleConfigEntry;
import com.github.alex1304.ultimategdbot.api.database.guildconfig.IntegerConfigEntry;
import com.github.alex1304.ultimategdbot.api.database.guildconfig.Validator;

import discord4j.common.util.Snowflake;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.object.entity.Role;
import discord4j.core.object.entity.channel.Channel;

@Value.Immutable
public interface GDLevelRequestConfigData extends GuildConfigData<GDLevelRequestConfigData> {
	
	Optional<Snowflake> channelSubmissionQueueId();

	Optional<Snowflake> channelArchivedSubmissionsId();

	Optional<Snowflake> roleReviewerId();
	
	boolean isOpen();
	
	int maxQueuedSubmissionsPerUser();
	
	int minReviewsRequired();
	
	static GuildConfigurator<GDLevelRequestConfigData> configurator(GDLevelRequestConfigData initialData, Translator tr, GatewayDiscordClient gateway) {
		return GuildConfigurator.builder(tr.translate("GDStrings", "lvlreq_guildconfig_title"), initialData, GDLevelRequestConfigDao.class)
				.setDescription(tr.translate("GDStrings", "lvlreq_guildconfig_desc"))
				.addEntry(GuildChannelConfigEntry.<GDLevelRequestConfigData>builder("channel_submission_queue")
						.setDisplayName(tr.translate("GDStrings", "display_channel_submission_queue"))
						.setValueGetter(forOptionalGuildChannel(gateway, GDLevelRequestConfigData::channelSubmissionQueueId))
						.setValueSetter((data, channel) -> ImmutableGDLevelRequestConfigData.builder()
								.from(data)
								.channelSubmissionQueueId(Optional.ofNullable(channel).map(Channel::getId))
								.build()))
				.addEntry(GuildChannelConfigEntry.<GDLevelRequestConfigData>builder("channel_archived_submissions")
						.setDisplayName(tr.translate("GDStrings", "display_channel_archived_submissions"))
						.setValueGetter(forOptionalGuildChannel(gateway, GDLevelRequestConfigData::channelArchivedSubmissionsId))
						.setValueSetter((data, channel) -> ImmutableGDLevelRequestConfigData.builder()
								.from(data)
								.channelArchivedSubmissionsId(Optional.ofNullable(channel).map(Channel::getId))
								.build()))
				.addEntry(GuildRoleConfigEntry.<GDLevelRequestConfigData>builder("role_reviewer")
						.setDisplayName(tr.translate("GDStrings", "display_role_reviewer"))
						.setValueGetter(forOptionalGuildRole(gateway, GDLevelRequestConfigData::roleReviewerId))
						.setValueSetter((data, role) -> ImmutableGDLevelRequestConfigData.builder()
								.from(data)
								.roleReviewerId(Optional.ofNullable(role).map(Role::getId))
								.build()))
				.addEntry(IntegerConfigEntry.<GDLevelRequestConfigData>builder("max_queued_submissions_per_user")
						.setDisplayName(tr.translate("GDStrings", "display_max_queued_submissions_per_user"))
						.setValueGetter(forSimpleValue(GDLevelRequestConfigData::maxQueuedSubmissionsPerUser))
						.setValueSetter((data, value) -> ImmutableGDLevelRequestConfigData.builder()
								.from(data)
								.maxQueuedSubmissionsPerUser(requireNonNullElse(value, 0))
								.build())
						.setValidator(Validator.allowingIf(x -> x >= 1 && x <= 20, tr.translate("GDStrings", "validate_range", 1, 20))))
				.addEntry(IntegerConfigEntry.<GDLevelRequestConfigData>builder("min_reviews_required")
						.setDisplayName(tr.translate("GDStrings", "display_min_reviews_required"))
						.setValueGetter(forSimpleValue(GDLevelRequestConfigData::minReviewsRequired))
						.setValueSetter((data, value) -> ImmutableGDLevelRequestConfigData.builder()
								.from(data)
								.minReviewsRequired(requireNonNullElse(value, 0))
								.build())
						.setValidator(Validator.allowingIf(x -> x >= 1 && x <= 5, tr.translate("GDStrings", "validate_range", 1, 5))))
				.build();
	}
}
