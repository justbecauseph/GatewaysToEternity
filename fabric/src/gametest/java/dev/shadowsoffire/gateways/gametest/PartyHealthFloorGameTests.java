package dev.shadowsoffire.gateways.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import dev.shadowsoffire.gateways.GatewayObjects;
import dev.shadowsoffire.gateways.entity.EndlessGatewayEntity;
import dev.shadowsoffire.gateways.entity.NormalGatewayEntity;
import dev.shadowsoffire.gateways.gate.BossEventSettings;
import dev.shadowsoffire.gateways.gate.GateRules;
import dev.shadowsoffire.gateways.gate.Gateway;
import dev.shadowsoffire.gateways.gate.PartyHealthFloor;
import dev.shadowsoffire.gateways.gate.SpawnAlgorithms.SpawnAlgorithm;
import dev.shadowsoffire.gateways.gate.StandardWaveEntity;
import dev.shadowsoffire.gateways.gate.Wave;
import dev.shadowsoffire.gateways.gate.WaveModifier;
import dev.shadowsoffire.gateways.gate.endless.ApplicationMode;
import dev.shadowsoffire.gateways.gate.endless.EndlessGateway;
import dev.shadowsoffire.gateways.gate.endless.EndlessModifier;
import dev.shadowsoffire.gateways.gate.normal.NormalGateway;
import io.netty.channel.embedded.EmbeddedChannel;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.DefaultAttributes;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/**
 * Covers the party health floor on wave entities: max health × max(3, n) / 3, capped at 5 / 3, for n players near the gateway.
 * <p>
 * Every spawn scenario lives in one test, because Fabric runs the tests of a batch side by side in one level and a second
 * spawn test would count this test's players.
 */
public final class PartyHealthFloorGameTests {

    /** Max health the authored wave adds to each entity, on top of the entity type's default. */
    private static final float WAVE_BONUS = 10;
    /** Max health the endless modifier adds on wave 1, on top of the wave bonus. */
    private static final float ENDLESS_BONUS = 20;
    private static final double TOLERANCE = 1.0E-3;

    @GameTest
    public void factorFollowsFloorAndCap(GameTestHelper helper) {
        assertFactor(helper, 1, 1);
        assertFactor(helper, 2, 1);
        assertFactor(helper, 3, 1);
        assertFactor(helper, 4, 4D / 3D);
        assertFactor(helper, 5, 5D / 3D);
        assertFactor(helper, 8, 5D / 3D);
        helper.succeed();
    }

    @GameTest(maxTicks = 20)
    public void waveEntityMaxHealthScalesWithNearbyPlayers(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Vec3 gatePos = helper.absoluteVec(new Vec3(1.5D, 1, 1.5D));
        Vec3 spawnPos = helper.absoluteVec(new Vec3(4.5D, 1, 1.5D));
        double defaultHealth = DefaultAttributes.getSupplier(EntityTypes.ZOMBIE).getBaseValue(Attributes.MAX_HEALTH);
        double normalHealth = defaultHealth + WAVE_BONUS;
        double endlessHealth = defaultHealth + WAVE_BONUS + ENDLESS_BONUS;

        helper.assertValueEqual(PartyHealthFloor.countPlayers(level, gatePos), 0, "players from another test are near the gateway");

        List<ServerPlayer> players = new ArrayList<>();
        List<LivingEntity> spawned = new ArrayList<>();
        try {
            addPlayer(helper, players, gatePos.add(0, 0, 2), false);
            assertScenario(helper, spawnPos, gatePos, spawned, normalHealth, endlessHealth, 1, "one player");

            addPlayer(helper, players, gatePos.add(2, 0, 2), false);
            addPlayer(helper, players, gatePos.add(-2, 0, 2), false);
            assertScenario(helper, spawnPos, gatePos, spawned, normalHealth, endlessHealth, 3, "three players");

            addPlayer(helper, players, gatePos.add(0, 0, -2), false);
            // None of these count: a spectator, a dead player and a player just outside the 64 block radius.
            addPlayer(helper, players, gatePos.add(2, 0, -2), true);
            ServerPlayer dead = addPlayer(helper, players, gatePos.add(-2, 0, -2), false);
            dead.setHealth(0);
            addPlayer(helper, players, gatePos.add(64.5D, 0, 0), false);
            assertScenario(helper, spawnPos, gatePos, spawned, normalHealth, endlessHealth, 4, "four players and three excluded");

            addPlayer(helper, players, gatePos.add(63.5D, 0, 0), false);
            assertScenario(helper, spawnPos, gatePos, spawned, normalHealth, endlessHealth, 5, "five players");

            for (int i = 0; i < 3; i++) {
                addPlayer(helper, players, gatePos.add(i, 0, 4), false);
            }
            assertScenario(helper, spawnPos, gatePos, spawned, normalHealth, endlessHealth, 8, "eight players");
        }
        finally {
            spawned.forEach(e -> e.getPassengersAndSelf().forEach(Entity::discard));
            players.forEach(p -> level.getServer().getPlayerList().remove(p));
        }
        helper.succeed();
    }

    private static void assertFactor(GameTestHelper helper, int players, double expected) {
        double actual = PartyHealthFloor.factor(players);
        helper.assertTrue(Math.abs(actual - expected) < 1.0E-9, "factor(" + players + ") was " + actual + ", expected " + expected);
    }

    /**
     * Spawns the first wave of a normal and an endless gateway with {@code players} counted players nearby, and checks each
     * spawned entity against its pre-floor max health × factor(players).
     */
    private static void assertScenario(GameTestHelper helper, Vec3 spawnPos, Vec3 gatePos, List<LivingEntity> spawned, double normalHealth, double endlessHealth,
        int players, String scenario) {
        helper.assertValueEqual(PartyHealthFloor.countPlayers(helper.getLevel(), gatePos), players, scenario + ": counted players");
        double factor = PartyHealthFloor.factor(players);

        NormalFixture normal = new NormalFixture(helper.getLevel(), normalGateway(spawnPos), gatePos);
        List<LivingEntity> normalWave = normal.spawnFirstWave();
        spawned.addAll(normalWave);
        helper.assertValueEqual(normalWave.size(), 1, scenario + ": normal wave size");
        for (LivingEntity entity : normalWave) {
            assertEntity(helper, entity, normalHealth * factor, factor, List.of((double) WAVE_BONUS), scenario + " (normal)");
        }

        EndlessFixture endless = new EndlessFixture(helper.getLevel(), endlessGateway(spawnPos), gatePos);
        List<LivingEntity> endlessWave = endless.spawnFirstWave();
        spawned.addAll(endlessWave);
        // One entity from the base wave and one from the endless modifier.
        helper.assertValueEqual(endlessWave.size(), 2, scenario + ": endless wave size");
        for (LivingEntity entity : endlessWave) {
            assertEntity(helper, entity, endlessHealth * factor, factor, List.of((double) WAVE_BONUS, (double) ENDLESS_BONUS), scenario + " (endless)");
        }
    }

    private static void assertEntity(GameTestHelper helper, LivingEntity entity, double expectedMaxHealth, double factor, List<Double> waveBonuses, String scenario) {
        helper.assertTrue(Math.abs(entity.getMaxHealth() - expectedMaxHealth) < TOLERANCE,
            scenario + ": max health was " + entity.getMaxHealth() + ", expected " + expectedMaxHealth);
        helper.assertTrue(Math.abs(entity.getHealth() - entity.getMaxHealth()) < TOLERANCE,
            scenario + ": health " + entity.getHealth() + " was not refilled to max health " + entity.getMaxHealth());

        AttributeInstance maxHealth = entity.getAttribute(Attributes.MAX_HEALTH);
        AttributeModifier floor = maxHealth.getModifier(PartyHealthFloor.MODIFIER_ID);
        if (factor > 1) {
            helper.assertTrue(floor != null, scenario + ": the party health floor modifier is missing");
            helper.assertValueEqual(floor.operation(), Operation.ADD_MULTIPLIED_TOTAL, scenario + ": floor modifier operation");
            helper.assertTrue(Math.abs(floor.amount() - (factor - 1)) < 1.0E-9, scenario + ": floor modifier amount was " + floor.amount());
        }
        else {
            helper.assertTrue(floor == null, scenario + ": a party of three or fewer received a floor modifier");
        }

        List<Double> others = maxHealth.getModifiers().stream()
            .filter(m -> !m.id().equals(PartyHealthFloor.MODIFIER_ID))
            .peek(m -> helper.assertValueEqual(m.operation(), Operation.ADD_VALUE, scenario + ": wave modifier operation"))
            .map(AttributeModifier::amount)
            .sorted()
            .toList();
        helper.assertValueEqual(others, waveBonuses, scenario + ": wave modifiers on max health");
    }

    /**
     * Logs in a player at {@code pos} and records it in {@code players} before any assertion, so the caller always removes it.
     */
    @SuppressWarnings("removal")
    private static ServerPlayer addPlayer(GameTestHelper helper, List<ServerPlayer> players, Vec3 pos, boolean spectator) {
        ServerPlayer player;
        if (spectator) {
            MinecraftServer server = helper.getLevel().getServer();
            GameProfile profile = new GameProfile(UUID.randomUUID(), "test-spectator");
            player = new ServerPlayer(server, helper.getLevel(), profile, ClientInformation.createDefault()) {
                @Override
                public GameType gameMode() {
                    return GameType.SPECTATOR;
                }
            };
            Connection connection = new Connection(PacketFlow.SERVERBOUND);
            new EmbeddedChannel(connection);
            server.getPlayerList().placeNewPlayer(connection, player, CommonListenerCookie.createInitial(profile, false));
        }
        else {
            player = helper.makeMockServerPlayerInLevel();
        }
        players.add(player);
        player.setPos(pos);
        helper.assertTrue(player.level() == helper.getLevel(), "a test player was placed outside the test level");
        return player;
    }

    private static SpawnAlgorithm fixedSpawn(Vec3 spawnPos) {
        return (level, pos, gate, toSpawn) -> spawnPos;
    }

    private static StandardWaveEntity zombie() {
        return new StandardWaveEntity.Builder(EntityTypes.ZOMBIE).finalizeSpawn(false).build();
    }

    private static Wave wave() {
        return Wave.builder()
            .entity(zombie())
            .attribute(Attributes.MAX_HEALTH, Operation.ADD_VALUE, WAVE_BONUS)
            .maxWaveTime(200)
            .setupTime(0)
            .build();
    }

    private static NormalGateway normalGateway(Vec3 spawnPos) {
        return new NormalGateway(Gateway.Size.SMALL, TextColor.fromRgb(0xFFFFFF), List.of(wave()), List.of(), List.of(), fixedSpawn(spawnPos), GateRules.DEFAULT,
            BossEventSettings.DEFAULT);
    }

    private static EndlessGateway endlessGateway(Vec3 spawnPos) {
        EndlessModifier modifier = EndlessModifier.builder()
            .applicationMode(new ApplicationMode.OnlyOnWave(1))
            .entity(zombie())
            .attribute(Attributes.MAX_HEALTH, Operation.ADD_VALUE, ENDLESS_BONUS)
            .build();
        return new EndlessGateway(Gateway.Size.SMALL, TextColor.fromRgb(0xFFFFFF), wave(), List.of(modifier), List.of(), fixedSpawn(spawnPos), GateRules.DEFAULT,
            BossEventSettings.DEFAULT);
    }

    /**
     * A normal gateway that serves a gateway definition without the registry. It is never added to the level, so it never ticks.
     */
    private static final class NormalFixture extends NormalGatewayEntity {

        private final NormalGateway gateway;

        private NormalFixture(ServerLevel level, NormalGateway gateway, Vec3 pos) {
            super(GatewayObjects.NORMAL_GATEWAY.get(), level);
            this.gateway = gateway;
            this.setPos(pos);
        }

        @Override
        public NormalGateway getGateway() {
            return this.gateway;
        }

        private List<LivingEntity> spawnFirstWave() {
            this.startNextWave();
            return List.copyOf(this.currentWaveEntities);
        }
    }

    /**
     * An endless gateway that serves a gateway definition without the registry. It is never added to the level, so it never ticks.
     */
    private static final class EndlessFixture extends EndlessGatewayEntity {

        private final EndlessGateway gateway;

        private EndlessFixture(ServerLevel level, EndlessGateway gateway, Vec3 pos) {
            super(GatewayObjects.ENDLESS_GATEWAY.get(), level);
            this.gateway = gateway;
            this.setPos(pos);
        }

        @Override
        public EndlessGateway getGateway() {
            return this.gateway;
        }

        private List<LivingEntity> spawnFirstWave() {
            this.startNextWave();
            return List.copyOf(this.currentWaveEntities);
        }
    }

}
