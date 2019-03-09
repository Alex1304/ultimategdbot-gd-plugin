import com.github.alex1304.ultimategdbot.api.Plugin;
import com.github.alex1304.ultimategdbot.gdplugin.GDPlugin;

module ultimategdbot.gdplugin {
	requires discord4j.core;
	requires reactor.core;
	requires transitive ultimategdbot.api;
	requires transitive jdash;
	requires reactor.netty;
	requires io.netty.codec.http;
	requires java.desktop;
	
	exports com.github.alex1304.ultimategdbot.gdplugin;
	
	provides Plugin with GDPlugin;
}