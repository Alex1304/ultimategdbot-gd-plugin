package com.github.alex1304.ultimategdbot.gdplugin;

import com.github.alex1304.ultimategdbot.api.guildsettings.GuildSettings;

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
	public void setGuildId(long guildId) {
		this.guildId = guildId;
	}
	
	public long getChannelAwardedLevelsId() {
		return channelAwardedLevelsId;
	}
	
	public void setChannelAwardedLevelsId(long channelAwardedLevelsId) {
		this.channelAwardedLevelsId = channelAwardedLevelsId;
	}
	
	public long getChannelTimelyLevelsId() {
		return channelTimelyLevelsId;
	}
	
	public void setChannelTimelyLevelsId(long channelTimelyLevelsId) {
		this.channelTimelyLevelsId = channelTimelyLevelsId;
	}
	
	public long getChannelGdModeratorsId() {
		return channelGdModeratorsId;
	}
	
	public void setChannelGdModeratorsId(long channelGdModeratorsId) {
		this.channelGdModeratorsId = channelGdModeratorsId;
	}
	
	public long getChannelChangelogId() {
		return channelChangelogId;
	}
	
	public void setChannelChangelogId(long channelChangelogId) {
		this.channelChangelogId = channelChangelogId;
	}
	
	public long getRoleAwardedLevelsId() {
		return roleAwardedLevelsId;
	}
	
	public void setRoleAwardedLevelsId(long roleAwardedLevelsId) {
		this.roleAwardedLevelsId = roleAwardedLevelsId;
	}
	
	public long getRoleTimelyLevelsId() {
		return roleTimelyLevelsId;
	}
	
	public void setRoleTimelyLevelsId(long roleTimelyLevelsId) {
		this.roleTimelyLevelsId = roleTimelyLevelsId;
	}
	
	public long getRoleGdModeratorsId() {
		return roleGdModeratorsId;
	}
	
	public void setRoleGdModeratorsId(long roleGdModeratorsId) {
		this.roleGdModeratorsId = roleGdModeratorsId;
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
