import com.github.alex1304.ultimategdbot.api.Plugin;
import com.github.alex1304.ultimategdbot.gdplugin.GDPlugin;

module ultimategdbot.gdplugin {
	requires discord4j.core;
	requires reactor.core;
	requires ultimategdbot.api;
	requires jdash;
	requires reactor.netty;
	requires io.netty.codec.http;
	requires java.desktop;
	
	provides Plugin with GDPlugin;
}