package com.github.alex1304.ultimategdbot.gdplugin.database;

import com.github.alex1304.ultimategdbot.api.database.GuildSettings;

public class GDSubscribedGuilds implements GuildSettings {
	
	private Long guildId;
	private Long channelAwardedLevelsId;
	private Long channelTimelyLevelsId;
	private Long channelGdModeratorsId;
	private Long channelChangelogId;
	private Long roleAwardedLevelsId;
	private Long roleTimelyLevelsId;
	private Long roleGdModeratorsId;
	
	@Override
	public Long getGuildId() {
		return guildId;
	}
	
	@Override
	public void setGuildId(Long guildId) {
		this.guildId = guildId;
	}
	
	public Long getChannelAwardedLevelsId() {
		return channelAwardedLevelsId;
	}
	
	public void setChannelAwardedLevelsId(Long channelAwardedLevelsId) {
		this.channelAwardedLevelsId = channelAwardedLevelsId;
	}
	
	public Long getChannelTimelyLevelsId() {
		return channelTimelyLevelsId;
	}
	
	public void setChannelTimelyLevelsId(Long channelTimelyLevelsId) {
		this.channelTimelyLevelsId = channelTimelyLevelsId;
	}
	
	public Long getChannelGdModeratorsId() {
		return channelGdModeratorsId;
	}
	
	public void setChannelGdModeratorsId(Long channelGdModeratorsId) {
		this.channelGdModeratorsId = channelGdModeratorsId;
	}
	
	public Long getChannelChangelogId() {
		return channelChangelogId;
	}
	
	public void setChannelChangelogId(Long channelChangelogId) {
		this.channelChangelogId = channelChangelogId;
	}
	
	public Long getRoleAwardedLevelsId() {
		return roleAwardedLevelsId;
	}
	
	public void setRoleAwardedLevelsId(Long roleAwardedLevelsId) {
		this.roleAwardedLevelsId = roleAwardedLevelsId;
	}
	
	public Long getRoleTimelyLevelsId() {
		return roleTimelyLevelsId;
	}
	
	public void setRoleTimelyLevelsId(Long roleTimelyLevelsId) {
		this.roleTimelyLevelsId = roleTimelyLevelsId;
	}
	
	public Long getRoleGdModeratorsId() {
		return roleGdModeratorsId;
	}
	
	public void setRoleGdModeratorsId(Long roleGdModeratorsId) {
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
