package dev.shadowsoffire.gateways.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.jetbrains.annotations.Nullable;

import dev.shadowsoffire.gateways.GatewayObjects;
import dev.shadowsoffire.gateways.entity.GatewayEntity.FailureReason;
import dev.shadowsoffire.gateways.entity.NormalGatewayEntity;
import dev.shadowsoffire.gateways.event.GateEvents;
import dev.shadowsoffire.gateways.event.GateEvents.GateCallback;
import dev.shadowsoffire.gateways.event.GateEvents.WaveEntitySpawnedCallback;
import dev.shadowsoffire.gateways.gate.BossEventSettings;
import dev.shadowsoffire.gateways.gate.Failure.SummonFailure;
import dev.shadowsoffire.gateways.gate.GateRules;
import dev.shadowsoffire.gateways.gate.Gateway;
import dev.shadowsoffire.gateways.gate.StandardWaveEntity;
import dev.shadowsoffire.gateways.gate.Wave;
import dev.shadowsoffire.gateways.gate.normal.NormalGateway;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Covers summon failures: every entity a failing gateway summons fires {@link GateEvents#FAILURE_ENTITY_SPAWNED} while
 * neither it nor its passengers are in the level, with its passengers already placed on it, and then joins the level with
 * its passengers.
 */
public final class SummonFailureGameTests {

    /** Entities the summon failure summons. */
    private static final int COUNT = 2;
    /** How far a rider may sit from the summon's position: a spider plus a skeleton's riding height. */
    private static final double RIDER_DISTANCE = 3;

    @GameTest(maxTicks = 20)
    public void summonFailureFiresHookBeforeJoiningLevel(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Vec3 gatePos = helper.absoluteVec(new Vec3(1.5D, 1, 1.5D));
        Vec3 spawnPos = helper.absoluteVec(new Vec3(4.5D, 1, 1.5D));
        FailureFixture gate = new FailureFixture(level, gateway(spawnPos), gatePos);

        List<Boolean> failedBeforeHook = new ArrayList<>();
        List<Boolean> gateRemovedAtHook = new ArrayList<>();
        List<LivingEntity> hooked = new ArrayList<>();
        List<Entity> inLevelAtHook = new ArrayList<>();
        List<EntityType<?>> ridersAtHook = new ArrayList<>();
        List<Vec3> riderPositionsAtHook = new ArrayList<>();
        List<LivingEntity> waveHooked = new ArrayList<>();
        AtomicBoolean failed = new AtomicBoolean();

        GateCallback failedListener = g -> {
            if (g == gate) failed.set(true);
        };
        WaveEntitySpawnedCallback failureListener = (g, entity) -> {
            if (g != gate) return;
            hooked.add(entity);
            failedBeforeHook.add(failed.get());
            gateRemovedAtHook.add(g.isRemoved());
            entity.getIndirectPassengers().forEach(rider -> {
                ridersAtHook.add(rider.getType());
                riderPositionsAtHook.add(rider.position());
            });
            entity.getPassengersAndSelf().filter(e -> level.getEntity(e.getUUID()) != null).forEach(inLevelAtHook::add);
        };
        WaveEntitySpawnedCallback waveListener = (g, entity) -> {
            if (g == gate) waveHooked.add(entity);
        };

        GateEvents.FAILED.register(failedListener);
        GateEvents.FAILURE_ENTITY_SPAWNED.register(failureListener);
        GateEvents.WAVE_ENTITY_SPAWNED.register(waveListener);
        try {
            try {
                gate.onFailure(List.of(), FailureReason.TIMER_ELAPSED);
            }
            finally {
                GateEvents.FAILED.unregister(failedListener);
                GateEvents.FAILURE_ENTITY_SPAWNED.unregister(failureListener);
                GateEvents.WAVE_ENTITY_SPAWNED.unregister(waveListener);
                // The failure strikes the gateway with real lightning; remove it before it ticks.
                level.getEntitiesOfClass(LightningBolt.class, new AABB(gatePos, gatePos).inflate(1)).forEach(Entity::discard);
            }

            helper.assertValueEqual(hooked.size(), COUNT, "summons that fired the failure hook");
            helper.assertValueEqual(waveHooked.size(), 0, "summons that fired the wave hook");
            helper.assertValueEqual(failedBeforeHook, List.of(true, true), "FAILED fired before each summon's hook");
            helper.assertValueEqual(gateRemovedAtHook, List.of(false, false), "gateway removed at each summon's hook");
            helper.assertValueEqual(inLevelAtHook, List.of(), "summons or riders already in the level at the hook");
            helper.assertValueEqual(ridersAtHook, List.of(EntityTypes.SKELETON, EntityTypes.CHICKEN, EntityTypes.SKELETON, EntityTypes.CHICKEN),
                "riders on the summons at the hook");
            for (Vec3 pos : riderPositionsAtHook) {
                helper.assertTrue(pos.distanceTo(spawnPos) < RIDER_DISTANCE, "a rider was at " + pos + " at the hook, not on its summon at " + spawnPos);
            }

            for (LivingEntity entity : hooked) {
                helper.assertValueEqual(entity.getType(), EntityTypes.SPIDER, "summoned entity type");
                helper.assertTrue(level.getEntity(entity.getUUID()) == entity, "a summon is not in the level after the failure");
                helper.assertTrue(entity.position().distanceTo(spawnPos) < 1.0E-6, "summon at " + entity.position() + ", expected " + spawnPos);
                List<Entity> riders = new ArrayList<>();
                entity.getIndirectPassengers().forEach(riders::add);
                helper.assertValueEqual(riders.stream().map(Entity::getType).toList(), List.of(EntityTypes.SKELETON, EntityTypes.CHICKEN), "riders after the failure");
                for (Entity rider : riders) {
                    helper.assertTrue(level.getEntity(rider.getUUID()) == rider, "a " + rider.getType() + " rider is not in the level after the failure");
                    helper.assertTrue(rider.position().distanceTo(spawnPos) < RIDER_DISTANCE, "a rider is at " + rider.position() + ", not on its summon at " + spawnPos);
                }
            }
            helper.assertTrue(gate.isRemoved(), "the gateway was not removed after failing");
        }
        finally {
            // Also sweep the test area, in case a summon joined the level without firing the hook.
            List<Entity> summoned = new ArrayList<>(hooked);
            summoned.addAll(level.getEntitiesOfClass(Mob.class, helper.getBounds()));
            summoned.forEach(e -> e.getPassengersAndSelf().forEach(Entity::discard));
        }
        helper.succeed();
    }

    /** A spider carrying a skeleton carrying a chicken, with the riders written in NBT the way a datapack would. */
    private static StandardWaveEntity spiderJockey() {
        CompoundTag chicken = rider(EntityTypes.CHICKEN, null);
        CompoundTag skeleton = rider(EntityTypes.SKELETON, chicken);
        return new StandardWaveEntity.Builder(EntityTypes.SPIDER)
            .nbt(tag -> {
                ListTag passengers = new ListTag();
                passengers.add(skeleton);
                tag.put("Passengers", passengers);
                return tag;
            })
            .count(COUNT)
            .build();
    }

    private static CompoundTag rider(EntityType<?> type, @Nullable CompoundTag passenger) {
        CompoundTag tag = new CompoundTag();
        tag.putString("id", EntityType.getKey(type).toString());
        if (passenger != null) {
            ListTag passengers = new ListTag();
            passengers.add(passenger);
            tag.put("Passengers", passengers);
        }
        return tag;
    }

    private static NormalGateway gateway(Vec3 spawnPos) {
        Wave wave = Wave.builder().entity(spiderJockey()).maxWaveTime(200).setupTime(0).build();
        return new NormalGateway(Gateway.Size.SMALL, TextColor.fromRgb(0xFFFFFF), List.of(wave), List.of(), List.of(new SummonFailure(spiderJockey())),
            (level, pos, gate, toSpawn) -> spawnPos, GateRules.DEFAULT, BossEventSettings.DEFAULT);
    }

    /**
     * A normal gateway that serves a gateway definition without the registry. It is never added to the level, so it never ticks.
     */
    private static final class FailureFixture extends NormalGatewayEntity {

        private final NormalGateway gateway;

        private FailureFixture(ServerLevel level, NormalGateway gateway, Vec3 pos) {
            super(GatewayObjects.NORMAL_GATEWAY.get(), level);
            this.gateway = gateway;
            this.setPos(pos);
        }

        @Override
        public NormalGateway getGateway() {
            return this.gateway;
        }
    }

}
