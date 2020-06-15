package com.github.alex1304.ultimategdbot.gdplugin.database;

import static com.github.alex1304.ultimategdbot.api.database.guildconfig.ValueGetters.forOptionalGuildChannel;
import static com.github.alex1304.ultimategdbot.api.database.guildconfig.ValueGetters.forOptionalGuildRole;

import java.util.Optional;

import org.immutables.value.Value;

import com.github.alex1304.ultimategdbot.api.Bot;
import com.github.alex1304.ultimategdbot.api.database.guildconfig.GuildChannelConfigEntry;
import com.github.alex1304.ultimategdbot.api.database.guildconfig.GuildConfigData;
import com.github.alex1304.ultimategdbot.api.database.guildconfig.GuildConfigurator;
import com.github.alex1304.ultimategdbot.api.database.guildconfig.GuildRoleConfigEntry;
import com.github.alex1304.ultimategdbot.api.database.guildconfig.Validator;

import discord4j.core.object.entity.Role;
import discord4j.core.object.entity.channel.Channel;
import discord4j.core.object.entity.channel.GuildChannel;
import discord4j.common.util.Snowflake;

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
	default GuildConfigurator<GDEventConfigData> configurator(Bot bot) {
		return GuildConfigurator.builder("Geometry Dash Notifications", this, GDEventConfigDao.class)
				.setDescription("Receive notifications in your server when new levels are being rated "
						+ "in Geometry Dash, when new Daily levels and Weekly demons are set, and when "
						+ "players are added or removed from the Geometry Dash Moderator team. Due to "
						+ "the expensive nature of this feature performance wise, this feature is only "
						+ "available to servers with more than 200 members.")
				.addEntry(GuildChannelConfigEntry.<GDEventConfigData>builder("channel_awarded_levels")
						.setDisplayName("channel for new awarded level notifications")
						.setValueGetter(forOptionalGuildChannel(bot, GDEventConfigData::channelAwardedLevelsId))
						.setValueSetter((data, channel) -> ImmutableGDEventConfigData.builder()
								.from(data)
								.channelAwardedLevelsId(Optional.ofNullable(channel).map(Channel::getId))
								.build())
						.setValidator(channelValidatorHasEnoughMembers(bot)))
				.addEntry(GuildChannelConfigEntry.<GDEventConfigData>builder("channel_timely_levels")
						.setDisplayName("channel for new Daily level/Weekly demon notifications")
						.setValueGetter(forOptionalGuildChannel(bot, GDEventConfigData::channelTimelyLevelsId))
						.setValueSetter((data, channel) -> ImmutableGDEventConfigData.builder()
								.from(data)
								.channelTimelyLevelsId(Optional.ofNullable(channel).map(Channel::getId))
								.build())
						.setValidator(channelValidatorHasEnoughMembers(bot)))
				.addEntry(GuildChannelConfigEntry.<GDEventConfigData>builder("channel_gd_moderators")
						.setDisplayName("channel for GD Moderators promotion/demotion notifications")
						.setValueGetter(forOptionalGuildChannel(bot, GDEventConfigData::channelGdModeratorsId))
						.setValueSetter((data, channel) -> ImmutableGDEventConfigData.builder()
								.from(data)
								.channelGdModeratorsId(Optional.ofNullable(channel).map(Channel::getId))
								.build())
						.setValidator(channelValidatorHasEnoughMembers(bot)))
				.addEntry(GuildRoleConfigEntry.<GDEventConfigData>builder("role_awarded_levels")
						.setDisplayName("role to tag for new awarded level notifications")
						.setValueGetter(forOptionalGuildRole(bot, GDEventConfigData::roleAwardedLevelsId))
						.setValueSetter((data, role) -> ImmutableGDEventConfigData.builder()
								.from(data)
								.roleAwardedLevelsId(Optional.ofNullable(role).map(Role::getId))
								.build())
						.setValidator(roleValidatorHasEnoughMembers(bot)))
				.addEntry(GuildRoleConfigEntry.<GDEventConfigData>builder("role_timely_levels")
						.setDisplayName("role to tag for new Daily level/Weekly demon notifications")
						.setValueGetter(forOptionalGuildRole(bot, GDEventConfigData::roleTimelyLevelsId))
						.setValueSetter((data, role) -> ImmutableGDEventConfigData.builder()
								.from(data)
								.roleTimelyLevelsId(Optional.ofNullable(role).map(Role::getId))
								.build())
						.setValidator(roleValidatorHasEnoughMembers(bot)))
				.addEntry(GuildRoleConfigEntry.<GDEventConfigData>builder("role_gd_moderators")
						.setDisplayName("role to tag for GD Moderators promotion/demotion notifications")
						.setValueGetter(forOptionalGuildRole(bot, GDEventConfigData::roleGdModeratorsId))
						.setValueSetter((data, role) -> ImmutableGDEventConfigData.builder()
								.from(data)
								.roleGdModeratorsId(Optional.ofNullable(role).map(Role::getId))
								.build())
						.setValidator(roleValidatorHasEnoughMembers(bot)))
				.build();
	}
	
	static Validator<GuildChannel> channelValidatorHasEnoughMembers(Bot bot) {
		return Validator.allowingWhen(channel -> channel.getGuild()
				.filter(guild -> guild.getMemberCount() >= MIN_MEMBERS_REQUIRED)
				.hasElement(), "Your server needs at least " + MIN_MEMBERS_REQUIRED + " members to configure this feature");
	}
	
	static Validator<Role> roleValidatorHasEnoughMembers(Bot bot) {
		return Validator.allowingWhen(role -> role.getGuild()
				.filter(guild -> guild.getMemberCount() >= MIN_MEMBERS_REQUIRED)
				.hasElement(), "Your server needs at least " + MIN_MEMBERS_REQUIRED + " members to configure this feature");
	}
}
