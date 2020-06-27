package com.github.alex1304.ultimategdbot.gdplugin.database;

import static com.github.alex1304.ultimategdbot.api.database.guildconfig.ValueGetters.forOptionalGuildChannel;
import static com.github.alex1304.ultimategdbot.api.database.guildconfig.ValueGetters.forOptionalGuildRole;

import java.util.Optional;
import java.util.function.Function;

import org.immutables.value.Value;

import com.github.alex1304.ultimategdbot.api.Bot;
import com.github.alex1304.ultimategdbot.api.Translator;
import com.github.alex1304.ultimategdbot.api.database.guildconfig.GuildChannelConfigEntry;
import com.github.alex1304.ultimategdbot.api.database.guildconfig.GuildConfigData;
import com.github.alex1304.ultimategdbot.api.database.guildconfig.GuildConfigurator;
import com.github.alex1304.ultimategdbot.api.database.guildconfig.GuildRoleConfigEntry;
import com.github.alex1304.ultimategdbot.api.database.guildconfig.Validator;
import com.github.alex1304.ultimategdbot.gdplugin.GDService;

import discord4j.common.util.Snowflake;
import discord4j.core.object.entity.Guild;
import discord4j.core.object.entity.Role;
import discord4j.core.object.entity.channel.Channel;
import discord4j.core.object.entity.channel.GuildChannel;
import reactor.core.publisher.Mono;

@Value.Immutable
public interface GDEventConfigData extends GuildConfigData<GDEventConfigData> {
	
	Optional<Snowflake> channelAwardedLevelsId();
	
	Optional<Snowflake> channelTimelyLevelsId();
	
	Optional<Snowflake> channelGdModeratorsId();
	
	Optional<Snowflake> channelChangelogId();
	
	Optional<Snowflake> roleAwardedLevelsId();
	
	Optional<Snowflake> roleTimelyLevelsId();
	
	Optional<Snowflake> roleGdModeratorsId();
	
	@Override
	default GuildConfigurator<GDEventConfigData> configurator(Translator tr, Bot bot) {
		return GuildConfigurator.builder(tr.translate("GDStrings", "gdevents_guildconfig_title"), this, GDEventConfigDao.class)
				.setDescription(tr.translate("GDStrings", "gdevents_guildconfig_desc"))
				.addEntry(GuildChannelConfigEntry.<GDEventConfigData>builder("channel_awarded_levels")
						.setDisplayName(tr.translate("GDStrings", "display_channel_awarded_levels"))
						.setValueGetter(forOptionalGuildChannel(bot, GDEventConfigData::channelAwardedLevelsId))
						.setValueSetter((data, channel) -> ImmutableGDEventConfigData.builder()
								.from(data)
								.channelAwardedLevelsId(Optional.ofNullable(channel).map(Channel::getId))
								.build())
						.setValidator(validatorHasEnoughMembers(tr, bot, GuildChannel::getGuild)))
				.addEntry(GuildChannelConfigEntry.<GDEventConfigData>builder("channel_timely_levels")
						.setDisplayName(tr.translate("GDStrings", "display_channel_timely_levels"))
						.setValueGetter(forOptionalGuildChannel(bot, GDEventConfigData::channelTimelyLevelsId))
						.setValueSetter((data, channel) -> ImmutableGDEventConfigData.builder()
								.from(data)
								.channelTimelyLevelsId(Optional.ofNullable(channel).map(Channel::getId))
								.build())
						.setValidator(validatorHasEnoughMembers(tr, bot, GuildChannel::getGuild)))
				.addEntry(GuildChannelConfigEntry.<GDEventConfigData>builder("channel_gd_moderators")
						.setDisplayName(tr.translate("GDStrings", "display_channel_gd_moderators"))
						.setValueGetter(forOptionalGuildChannel(bot, GDEventConfigData::channelGdModeratorsId))
						.setValueSetter((data, channel) -> ImmutableGDEventConfigData.builder()
								.from(data)
								.channelGdModeratorsId(Optional.ofNullable(channel).map(Channel::getId))
								.build())
						.setValidator(validatorHasEnoughMembers(tr, bot, GuildChannel::getGuild)))
				.addEntry(GuildRoleConfigEntry.<GDEventConfigData>builder("role_awarded_levels")
						.setDisplayName(tr.translate("GDStrings", "display_role_awarded_levels"))
						.setValueGetter(forOptionalGuildRole(bot, GDEventConfigData::roleAwardedLevelsId))
						.setValueSetter((data, role) -> ImmutableGDEventConfigData.builder()
								.from(data)
								.roleAwardedLevelsId(Optional.ofNullable(role).map(Role::getId))
								.build())
						.setValidator(validatorHasEnoughMembers(tr, bot, Role::getGuild)))
				.addEntry(GuildRoleConfigEntry.<GDEventConfigData>builder("role_timely_levels")
						.setDisplayName(tr.translate("GDStrings", "display_role_timely_levels"))
						.setValueGetter(forOptionalGuildRole(bot, GDEventConfigData::roleTimelyLevelsId))
						.setValueSetter((data, role) -> ImmutableGDEventConfigData.builder()
								.from(data)
								.roleTimelyLevelsId(Optional.ofNullable(role).map(Role::getId))
								.build())
						.setValidator(validatorHasEnoughMembers(tr, bot, Role::getGuild)))
				.addEntry(GuildRoleConfigEntry.<GDEventConfigData>builder("role_gd_moderators")
						.setDisplayName(tr.translate("GDStrings", "display_role_gd_moderators"))
						.setValueGetter(forOptionalGuildRole(bot, GDEventConfigData::roleGdModeratorsId))
						.setValueSetter((data, role) -> ImmutableGDEventConfigData.builder()
								.from(data)
								.roleGdModeratorsId(Optional.ofNullable(role).map(Role::getId))
								.build())
						.setValidator(validatorHasEnoughMembers(tr, bot, Role::getGuild)))
				.build();
	}
	
	static <T> Validator<T> validatorHasEnoughMembers(Translator tr, Bot bot, Function<T, Mono<Guild>> guildGetter) {
		var minMembers = bot.service(GDService.class).getGdEventsMinMembers();
		return Validator.allowingWhen(t -> guildGetter.apply(t)
				.filter(guild -> guild.getMemberCount() >= minMembers)
				.hasElement(), tr.translate("GDStrings", "validate_enough_members", minMembers));
	}
}
