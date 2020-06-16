package com.github.alex1304.ultimategdbot.gdplugin.database;

import static com.github.alex1304.ultimategdbot.api.database.guildconfig.ValueGetters.forOptionalGuildChannel;
import static com.github.alex1304.ultimategdbot.api.database.guildconfig.ValueGetters.forOptionalGuildRole;

import java.util.Optional;

import org.immutables.value.Value;

import com.github.alex1304.ultimategdbot.api.Bot;
import com.github.alex1304.ultimategdbot.api.Translator;
import com.github.alex1304.ultimategdbot.api.database.guildconfig.GuildChannelConfigEntry;
import com.github.alex1304.ultimategdbot.api.database.guildconfig.GuildConfigData;
import com.github.alex1304.ultimategdbot.api.database.guildconfig.GuildConfigurator;
import com.github.alex1304.ultimategdbot.api.database.guildconfig.GuildRoleConfigEntry;
import com.github.alex1304.ultimategdbot.api.database.guildconfig.Validator;

import discord4j.common.util.Snowflake;
import discord4j.core.object.entity.Role;
import discord4j.core.object.entity.channel.Channel;
import discord4j.core.object.entity.channel.GuildChannel;

@Value.Immutable
public interface GDEventConfigData extends GuildConfigData<GDEventConfigData> {
	
	int MIN_MEMBERS_REQUIRED = 200;
	
	Optional<Snowflake> channelAwardedLevelsId();
	
	Optional<Snowflake> channelTimelyLevelsId();
	
	Optional<Snowflake> channelGdModeratorsId();
	
	Optional<Snowflake> channelChangelogId();
	
	Optional<Snowflake> roleAwardedLevelsId();
	
	Optional<Snowflake> roleTimelyLevelsId();
	
	Optional<Snowflake> roleGdModeratorsId();
	
	@Override
	default GuildConfigurator<GDEventConfigData> configurator(Translator tr, Bot bot) {
		return GuildConfigurator.builder(tr.translate("guildconfig_gd_events", "title"), this, GDEventConfigDao.class)
				.setDescription(tr.translate("guildconfig_gd_events", "desc"))
				.addEntry(GuildChannelConfigEntry.<GDEventConfigData>builder("channel_awarded_levels")
						.setDisplayName(tr.translate("guildconfig_gd_events", "display_channel_awarded_levels"))
						.setValueGetter(forOptionalGuildChannel(bot, GDEventConfigData::channelAwardedLevelsId))
						.setValueSetter((data, channel) -> ImmutableGDEventConfigData.builder()
								.from(data)
								.channelAwardedLevelsId(Optional.ofNullable(channel).map(Channel::getId))
								.build())
						.setValidator(channelValidatorHasEnoughMembers(tr, bot)))
				.addEntry(GuildChannelConfigEntry.<GDEventConfigData>builder("channel_timely_levels")
						.setDisplayName(tr.translate("guildconfig_gd_events", "display_channel_timely_levels"))
						.setValueGetter(forOptionalGuildChannel(bot, GDEventConfigData::channelTimelyLevelsId))
						.setValueSetter((data, channel) -> ImmutableGDEventConfigData.builder()
								.from(data)
								.channelTimelyLevelsId(Optional.ofNullable(channel).map(Channel::getId))
								.build())
						.setValidator(channelValidatorHasEnoughMembers(tr, bot)))
				.addEntry(GuildChannelConfigEntry.<GDEventConfigData>builder("channel_gd_moderators")
						.setDisplayName(tr.translate("guildconfig_gd_events", "display_channel_gd_moderators"))
						.setValueGetter(forOptionalGuildChannel(bot, GDEventConfigData::channelGdModeratorsId))
						.setValueSetter((data, channel) -> ImmutableGDEventConfigData.builder()
								.from(data)
								.channelGdModeratorsId(Optional.ofNullable(channel).map(Channel::getId))
								.build())
						.setValidator(channelValidatorHasEnoughMembers(tr, bot)))
				.addEntry(GuildRoleConfigEntry.<GDEventConfigData>builder("role_awarded_levels")
						.setDisplayName(tr.translate("guildconfig_gd_events", "display_role_awarded_levels"))
						.setValueGetter(forOptionalGuildRole(bot, GDEventConfigData::roleAwardedLevelsId))
						.setValueSetter((data, role) -> ImmutableGDEventConfigData.builder()
								.from(data)
								.roleAwardedLevelsId(Optional.ofNullable(role).map(Role::getId))
								.build())
						.setValidator(roleValidatorHasEnoughMembers(tr, bot)))
				.addEntry(GuildRoleConfigEntry.<GDEventConfigData>builder("role_timely_levels")
						.setDisplayName(tr.translate("guildconfig_gd_events", "display_role_timely_levels"))
						.setValueGetter(forOptionalGuildRole(bot, GDEventConfigData::roleTimelyLevelsId))
						.setValueSetter((data, role) -> ImmutableGDEventConfigData.builder()
								.from(data)
								.roleTimelyLevelsId(Optional.ofNullable(role).map(Role::getId))
								.build())
						.setValidator(roleValidatorHasEnoughMembers(tr, bot)))
				.addEntry(GuildRoleConfigEntry.<GDEventConfigData>builder("role_gd_moderators")
						.setDisplayName(tr.translate("guildconfig_gd_events", "display_role_gd_moderators"))
						.setValueGetter(forOptionalGuildRole(bot, GDEventConfigData::roleGdModeratorsId))
						.setValueSetter((data, role) -> ImmutableGDEventConfigData.builder()
								.from(data)
								.roleGdModeratorsId(Optional.ofNullable(role).map(Role::getId))
								.build())
						.setValidator(roleValidatorHasEnoughMembers(tr, bot)))
				.build();
	}
	
	static Validator<GuildChannel> channelValidatorHasEnoughMembers(Translator tr, Bot bot) {
		return Validator.allowingWhen(channel -> channel.getGuild()
				.filter(guild -> guild.getMemberCount() >= MIN_MEMBERS_REQUIRED)
				.hasElement(), tr.translate("guildconfig_gd_events", "validate_enough_members", MIN_MEMBERS_REQUIRED));
	}
	
	static Validator<Role> roleValidatorHasEnoughMembers(Translator tr, Bot bot) {
		return Validator.allowingWhen(role -> role.getGuild()
				.filter(guild -> guild.getMemberCount() >= MIN_MEMBERS_REQUIRED)
				.hasElement(), tr.translate("guildconfig_gd_events", "validate_enough_members", MIN_MEMBERS_REQUIRED));
	}
}
