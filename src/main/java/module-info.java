import com.github.alex1304.ultimategdbot.api.Plugin;
import com.github.alex1304.ultimategdbot.gdplugin.GDPlugin;

module ultimategdbot.gd {
	opens com.github.alex1304.ultimategdbot.gdplugin;
	opens com.github.alex1304.ultimategdbot.gdplugin.command;
	opens com.github.alex1304.ultimategdbot.gdplugin.database;
	
	requires com.github.benmanes.caffeine;
	requires io.netty.codec.http;
	requires java.compiler;
	requires java.desktop;
	requires jdash;
	requires jdash.events;
	requires jdk.unsupported;
	requires reactor.extra;
	requires ultimategdbot.api;

	requires static com.google.errorprone.annotations;
	requires static org.immutables.value;
	
	provides Plugin with GDPlugin;
}