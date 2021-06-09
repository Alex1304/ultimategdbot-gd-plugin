import com.github.alex1304.ultimategdbot.api.Plugin;
import com.github.alex1304.ultimategdbot.api.service.ServiceDeclarator;
import com.github.alex1304.ultimategdbot.gdplugin.GDPlugin;
import com.github.alex1304.ultimategdbot.gdplugin.GDServices;

open module ultimategdbot.gd {
	requires com.github.benmanes.caffeine;
	requires io.netty.codec.http;
	requires java.compiler;
	requires java.desktop;
	requires jdash;
	requires jdash.events;
	requires jdk.unsupported;
    requires org.mongodb.driver.reactivestreams;
    requires org.immutables.criteria.common;
    requires org.immutables.criteria.mongo;
    requires org.immutables.criteria.reactor;
    requires org.mongodb.bson;
	requires reactor.extra;
	requires ultimategdbot.api;

	requires static com.google.errorprone.annotations;
	requires static org.immutables.value;
    requires com.fasterxml.jackson.databind;
    requires com.fasterxml.jackson.datatype.jdk8;
	requires org.jdbi.v3.core;
	requires discord4j.core;
	requires discord4j.rest;
	
	provides Plugin with GDPlugin;
	provides ServiceDeclarator with GDServices;
}