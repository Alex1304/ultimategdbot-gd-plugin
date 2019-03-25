import com.github.alex1304.ultimategdbot.api.Plugin;
import com.github.alex1304.ultimategdbot.gdplugin.GDPlugin;

module ultimategdbot.gdplugin {
	requires discord4j.core;
	requires reactor.core;
	requires transitive ultimategdbot.api;
	requires transitive jdash;
	requires transitive jdash.events;
	requires reactor.netty;
	requires io.netty.codec.http;
	requires java.desktop;
	requires transitive org.reactivestreams;
	requires transitive java.sql;
	
	exports com.github.alex1304.ultimategdbot.gdplugin;
	exports com.github.alex1304.ultimategdbot.gdplugin.gdevents;
	
	provides Plugin with GDPlugin;
}