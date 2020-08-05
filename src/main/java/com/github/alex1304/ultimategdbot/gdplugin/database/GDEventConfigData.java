package com.github.alex1304.ultimategdbot.gdplugin.database;

import static com.github.alex1304.ultimategdbot.api.database.guildconfig.ValueGetters.forOptionalGuildChannel;
import static com.github.alex1304.ultimategdbot.api.database.guildconfig.ValueGetters.forOptionalGuildRole;

import java.util.Optional;

import org.immutables.value.Value;

import com.github.alex1304.ultimategdbot.api.Translator;
import com.github.alex1304.ultimategdbot.api.database.guildconfig.GuildChannelConfigEntry;
import com.github.alex1304.ultimategdbot.api.database.guildconfig.GuildConfigData;
import com.github.alex1304.ultimategdbot.api.database.guildconfig.GuildConfigurator;
import com.github.alex1304.ultimategdbot.api.database.guildconfig.GuildRoleConfigEntry;

import discord4j.common.util.Snowflake;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.object.entity.Role;
import discord4j.core.object.entity.channel.Channel;

@Value.Immutable
public interface GDEventConfigData extends GuildConfigData<GDEventConfigData> {
	
	Optional<Snowflake> channelAwardedLevelsId();
	
	Optional<Snowflake> channelTimelyLevelsId();
	
	Optional<Snowflake> channelGdModeratorsId();
	
	Optional<Snowflake> channelChangelogId();
	
	Optional<Snowflake> roleAwardedLevelsId();
	
	Optional<Snowflake> roleTimelyLevelsId();
	
	Optional<Snowflake> roleGdModeratorsId();
	
	static GuildConfigurator<GDEventConfigData> configurator(GDEventConfigData initialData, Translator tr, GatewayDiscordClient gateway) {
		return GuildConfigurator.builder(tr.translate("GDStrings", "gdevents_guildconfig_title"), initialData, GDEventConfigDao.class)
				.setDescription(tr.translate("GDStrings", "gdevents_guildconfig_desc"))
				.addEntry(GuildChannelConfigEntry.<GDEventConfigData>builder("channel_awarded_levels")
						.setDisplayName(tr.translate("GDStrings", "display_channel_awarded_levels"))
						.setValueGetter(forOptionalGuildChannel(gateway, GDEventConfigData::channelAwardedLevelsId))
						.setValueSetter((data, channel) -> ImmutableGDEventConfigData.builder()
								.from(data)
								.channelAwardedLevelsId(Optional.ofNullable(channel).map(Channel::getId))
								.build()))
				.addEntry(GuildChannelConfigEntry.<GDEventConfigData>builder("channel_timely_levels")
						.setDisplayName(tr.translate("GDStrings", "display_channel_timely_levels"))
						.setValueGetter(forOptionalGuildChannel(gateway, GDEventConfigData::channelTimelyLevelsId))
						.setValueSetter((data, channel) -> ImmutableGDEventConfigData.builder()
								.from(data)
								.channelTimelyLevelsId(Optional.ofNullable(channel).map(Channel::getId))
								.build()))
				.addEntry(GuildChannelConfigEntry.<GDEventConfigData>builder("channel_gd_moderators")
						.setDisplayName(tr.translate("GDStrings", "display_channel_gd_moderators"))
						.setValueGetter(forOptionalGuildChannel(gateway, GDEventConfigData::channelGdModeratorsId))
						.setValueSetter((data, channel) -> ImmutableGDEventConfigData.builder()
								.from(data)
								.channelGdModeratorsId(Optional.ofNullable(channel).map(Channel::getId))
								.build()))
				.addEntry(GuildRoleConfigEntry.<GDEventConfigData>builder("role_awarded_levels")
						.setDisplayName(tr.translate("GDStrings", "display_role_awarded_levels"))
						.setValueGetter(forOptionalGuildRole(gateway, GDEventConfigData::roleAwardedLevelsId))
						.setValueSetter((data, role) -> ImmutableGDEventConfigData.builder()
								.from(data)
								.roleAwardedLevelsId(Optional.ofNullable(role).map(Role::getId))
								.build()))
				.addEntry(GuildRoleConfigEntry.<GDEventConfigData>builder("role_timely_levels")
						.setDisplayName(tr.translate("GDStrings", "display_role_timely_levels"))
						.setValueGetter(forOptionalGuildRole(gateway, GDEventConfigData::roleTimelyLevelsId))
						.setValueSetter((data, role) -> ImmutableGDEventConfigData.builder()
								.from(data)
								.roleTimelyLevelsId(Optional.ofNullable(role).map(Role::getId))
								.build()))
				.addEntry(GuildRoleConfigEntry.<GDEventConfigData>builder("role_gd_moderators")
						.setDisplayName(tr.translate("GDStrings", "display_role_gd_moderators"))
						.setValueGetter(forOptionalGuildRole(gateway, GDEventConfigData::roleGdModeratorsId))
						.setValueSetter((data, role) -> ImmutableGDEventConfigData.builder()
								.from(data)
								.roleGdModeratorsId(Optional.ofNullable(role).map(Role::getId))
								.build()))
				.build();
	}
}
