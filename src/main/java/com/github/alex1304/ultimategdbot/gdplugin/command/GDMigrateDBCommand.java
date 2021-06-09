package com.github.alex1304.ultimategdbot.gdplugin.command;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.github.alex1304.ultimategdbot.api.command.Context;
import com.github.alex1304.ultimategdbot.api.command.PermissionLevel;
import com.github.alex1304.ultimategdbot.api.command.annotated.CommandAction;
import com.github.alex1304.ultimategdbot.api.command.annotated.CommandDescriptor;
import com.github.alex1304.ultimategdbot.api.command.annotated.CommandPermission;
import com.github.alex1304.ultimategdbot.api.service.Root;
import com.github.alex1304.ultimategdbot.gdplugin.GDService;
import com.github.alex1304.ultimategdbot.gdplugin.database.*;
import com.github.alex1304.ultimategdbot.gdplugin.database.mongo.*;
import com.mongodb.reactivestreams.client.MongoClients;
import discord4j.common.jackson.UnknownPropertyHandler;
import org.immutables.criteria.mongo.MongoBackend;
import org.immutables.criteria.mongo.MongoSetup;
import org.immutables.criteria.mongo.bson4jackson.BsonModule;
import org.immutables.criteria.mongo.bson4jackson.IdAnnotationModule;
import org.immutables.criteria.mongo.bson4jackson.JacksonCodecs;
import reactor.core.publisher.Mono;

import java.util.stream.Collectors;

import static reactor.function.TupleUtils.function;

@CommandDescriptor(
        aliases = "gdmigratedb"
)
@CommandPermission(level = PermissionLevel.BOT_OWNER)
public final class GDMigrateDBCommand {

    @Root
    private GDService gd;

    @CommandAction
    public Mono<Void> runAdd(Context ctx, String dbName) {
        return Mono.defer(() -> {
            final var mapper = new ObjectMapper()
                    .registerModule(new BsonModule())
                    .registerModule(new Jdk8Module())
                    .registerModule(new IdAnnotationModule())
                    .addHandler(new UnknownPropertyHandler(true));
            @SuppressWarnings("UnstableApiUsage") final var registry = JacksonCodecs.registryFromMapper(mapper);
            final var mongoClient = MongoClients.create();
            final var db = mongoClient.getDatabase(dbName).withCodecRegistry(registry);
            final var backend = new MongoBackend(MongoSetup.of(db));
            final var awardedLevelRepo = new GdAwardedLevelRepository(backend);
            final var leaderboardBanRepo = new GdLeaderboardBanRepository(backend);
            final var leaderboardRepo = new GdLeaderboardRepository(backend);
            final var linkedUserRepo = new GdLinkedUserRepository(backend);
            final var modRepo = new GdModRepository(backend);
            return Mono.zip(
                    gd.bot().database().withExtension(GDAwardedLevelDao.class, GDAwardedLevelDao::getAll),
                    gd.bot().database().withExtension(GDLeaderboardBanDao.class, GDLeaderboardBanDao::getAll),
                    gd.bot().database().withExtension(GDLeaderboardDao.class, GDLeaderboardDao::getAll),
                    gd.bot().database().withExtension(GDLinkedUserDao.class, GDLinkedUserDao::getAll),
                    gd.bot().database().withExtension(GDModDao.class, GDModDao::getAll))
                    .flatMap(function((awardedLevel, leaderboardBan, leaderboard, linkedUser, mod) -> Mono.when(
                            awardedLevel.isEmpty() ? Mono.empty() : awardedLevelRepo.upsertAll(awardedLevel.stream()
                                    .map(data -> ImmutableGdAwardedLevel.builder()
                                            .levelId(data.levelId())
                                            .insertDate(data.insertDate())
                                            .downloads(data.downloads())
                                            .likes(data.likes())
                                            .build())
                                    .collect(Collectors.toList())),
                            leaderboardBan.isEmpty() ? Mono.empty() : leaderboardBanRepo.upsertAll(leaderboardBan.stream()
                                    .map(data -> ImmutableGdLeaderboardBan.builder()
                                            .accountId(data.accountId())
                                            .bannedBy(data.bannedBy().asLong())
                                            .build())
                                    .collect(Collectors.toList())),
                            leaderboard.isEmpty() ? Mono.empty() : leaderboardRepo.upsertAll(leaderboard.stream()
                                    .map(data -> ImmutableGdLeaderboard.builder()
                                            .accountId(data.accountId())
                                            .creatorPoints(data.creatorPoints())
                                            .demons(data.demons())
                                            .diamonds(data.diamonds())
                                            .lastRefreshed(data.lastRefreshed())
                                            .name(data.name())
                                            .secretCoins(data.secretCoins())
                                            .stars(data.stars())
                                            .userCoins(data.userCoins())
                                            .build())
                                    .collect(Collectors.toList())),
                            linkedUser.isEmpty() ? Mono.empty() : linkedUserRepo.upsertAll(linkedUser.stream()
                                    .map(data -> ImmutableGdLinkedUser.builder()
                                            .confirmationToken(data.confirmationToken())
                                            .discordUserId(data.discordUserId().asLong())
                                            .gdUserId(data.gdUserId())
                                            .isLinkActivated(data.isLinkActivated())
                                            .build())
                                    .collect(Collectors.toList())),
                            mod.isEmpty() ? Mono.empty() : modRepo.upsertAll(mod.stream()
                                    .map(data -> ImmutableGdMod.builder()
                                            .accountId(data.accountId())
                                            .elder(data.isElder() ? 1 : 0)
                                            .name(data.name())
                                            .build())
                                    .collect(Collectors.toList()))
                    )))
                    .doFinally(signal -> mongoClient.close())
                    .then(ctx.reply("Success!"))
                    .then();
        });
    }
}
