package com.github.alex1304.ultimategdbot.gdplugin.database;

import static java.util.Objects.requireNonNullElse;

import com.github.alex1304.ultimategdbot.api.database.GuildSettings;

public class GDSubscribedGuilds implements GuildSettings {
	
	private long guildId;
	private long channelAwardedLevelsId;
	private long channelTimelyLevelsId;
	private long channelGdModeratorsId;
	private long channelChangelogId;
	private long roleAwardedLevelsId;
	private long roleTimelyLevelsId;
	private long roleGdModeratorsId;
	
	@Override
	public long getGuildId() {
		return guildId;
	}
	
	@Override
	public void setGuildId(Long guildId) {
		this.guildId = requireNonNullElse(guildId, 0L);
	}
	
	public long getChannelAwardedLevelsId() {
		return channelAwardedLevelsId;
	}
	
	public void setChannelAwardedLevelsId(Long channelAwardedLevelsId) {
		this.channelAwardedLevelsId = requireNonNullElse(channelAwardedLevelsId, 0L);
	}
	
	public long getChannelTimelyLevelsId() {
		return channelTimelyLevelsId;
	}
	
	public void setChannelTimelyLevelsId(Long channelTimelyLevelsId) {
		this.channelTimelyLevelsId = requireNonNullElse(channelTimelyLevelsId, 0L);
	}
	
	public long getChannelGdModeratorsId() {
		return channelGdModeratorsId;
	}
	
	public void setChannelGdModeratorsId(Long channelGdModeratorsId) {
		this.channelGdModeratorsId = requireNonNullElse(channelGdModeratorsId, 0L);
	}
	
	public long getChannelChangelogId() {
		return channelChangelogId;
	}
	
	public void setChannelChangelogId(Long channelChangelogId) {
		this.channelChangelogId = requireNonNullElse(channelChangelogId, 0L);
	}
	
	public long getRoleAwardedLevelsId() {
		return roleAwardedLevelsId;
	}
	
	public void setRoleAwardedLevelsId(Long roleAwardedLevelsId) {
		this.roleAwardedLevelsId = requireNonNullElse(roleAwardedLevelsId, 0L);
	}
	
	public long getRoleTimelyLevelsId() {
		return roleTimelyLevelsId;
	}
	
	public void setRoleTimelyLevelsId(Long roleTimelyLevelsId) {
		this.roleTimelyLevelsId = requireNonNullElse(roleTimelyLevelsId, 0L);
	}
	
	public long getRoleGdModeratorsId() {
		return roleGdModeratorsId;
	}
	
	public void setRoleGdModeratorsId(Long roleGdModeratorsId) {
		this.roleGdModeratorsId = requireNonNullElse(roleGdModeratorsId, 0L);
	}
	
	@Override
	public boolean equals(Object obj) {
		return obj instanceof GDSubscribedGuilds && ((GDSubscribedGuilds) obj).guildId == guildId;
	}
	
	@Override
	public int hashCode() {
		return Long.hashCode(guildId);
	}
}
