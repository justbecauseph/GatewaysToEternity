package dev.shadowsoffire.gateways.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.jetbrains.annotations.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;

import dev.shadowsoffire.gateways.GatewayObjects;
import dev.shadowsoffire.gateways.entity.GatewayEntity;
import dev.shadowsoffire.gateways.entity.NormalGatewayEntity;
import dev.shadowsoffire.gateways.event.GateEvents;
import dev.shadowsoffire.gateways.event.GateEvents.WaveEntitySpawnedCallback;
import dev.shadowsoffire.gateways.gate.BossEventSettings;
import dev.shadowsoffire.gateways.gate.GateRules;
import dev.shadowsoffire.gateways.gate.Gateway;
import dev.shadowsoffire.gateways.gate.StandardWaveEntity;
import dev.shadowsoffire.gateways.gate.Wave;
import dev.shadowsoffire.gateways.gate.WaveModifier;
import dev.shadowsoffire.gateways.gate.normal.NormalGateway;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Item.TooltipContext;
import net.minecraft.world.phys.Vec3;

/**
 * Covers the riders of wave entities: riders written in the wave entity's NBT and riders that the wave entity's or the
 * wave's modifiers attach sit on their vehicle when {@link GateEvents#WAVE_ENTITY_SPAWNED} fires, and join the level with it.
 */
public final class WavePassengerGameTests {

    /** How far a rider may sit from the wave entity's position. The chicken, the highest rider, sits about 2.1 blocks up. */
    private static final double RIDER_DISTANCE = 3;
    /** The wave entity's riders, in {@link Entity#getIndirectPassengers()} order. */
    private static final List<EntityType<?>> RIDERS = List.of(EntityTypes.SKELETON, EntityTypes.CHICKEN, EntityTypes.RABBIT, EntityTypes.PIG);

    @GameTest(maxTicks = 20)
    public void waveEntityRidersJoinLevelOnTheirVehicle(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Vec3 gatePos = helper.absoluteVec(new Vec3(1.5D, 1, 1.5D));
        Vec3 spawnPos = helper.absoluteVec(new Vec3(4.5D, 1, 1.5D));
        WaveFixture gate = new WaveFixture(level, gateway(spawnPos), gatePos);

        List<LivingEntity> hooked = new ArrayList<>();
        List<Entity> inLevelAtHook = new ArrayList<>();
        List<EntityType<?>> ridersAtHook = new ArrayList<>();
        List<Vec3> riderPositionsAtHook = new ArrayList<>();
        List<LivingEntity> spawned = new ArrayList<>();

        WaveEntitySpawnedCallback listener = (g, entity) -> {
            if (g != gate) return;
            hooked.add(entity);
            entity.getIndirectPassengers().forEach(rider -> {
                ridersAtHook.add(rider.getType());
                riderPositionsAtHook.add(rider.position());
            });
            entity.getPassengersAndSelf().filter(e -> level.getEntity(e.getUUID()) != null).forEach(inLevelAtHook::add);
        };

        GateEvents.WAVE_ENTITY_SPAWNED.register(listener);
        try {
            try {
                spawned.addAll(gate.spawnFirstWave());
            }
            finally {
                GateEvents.WAVE_ENTITY_SPAWNED.unregister(listener);
            }

            helper.assertValueEqual(spawned.size(), 1, "wave size");
            helper.assertValueEqual(hooked, spawned, "wave entities that fired the wave hook");
            helper.assertValueEqual(inLevelAtHook, List.of(), "wave entity or riders already in the level at the hook");
            // The skeleton and the chicken on it come from NBT, the rabbit from the wave entity's modifier, the pig from the wave's.
            helper.assertValueEqual(ridersAtHook, RIDERS, "riders on the wave entity at the hook");
            for (Vec3 pos : riderPositionsAtHook) {
                helper.assertTrue(pos.distanceTo(spawnPos) < RIDER_DISTANCE, "a rider was at " + pos + " at the hook, not on its wave entity at " + spawnPos);
            }

            LivingEntity entity = spawned.get(0);
            helper.assertValueEqual(entity.getType(), EntityTypes.SPIDER, "wave entity type");
            helper.assertTrue(level.getEntity(entity.getUUID()) == entity, "the wave entity is not in the level after the wave spawned");
            helper.assertTrue(entity.position().distanceTo(spawnPos) < 1.0E-6, "wave entity at " + entity.position() + ", expected " + spawnPos);
            List<Entity> riders = new ArrayList<>();
            entity.getIndirectPassengers().forEach(riders::add);
            helper.assertValueEqual(riders.stream().map(Entity::getType).toList(), RIDERS, "riders after the wave spawned");
            helper.assertValueEqual(riders.stream().map(r -> r.getVehicle().getType()).toList(),
                List.of(EntityTypes.SPIDER, EntityTypes.SKELETON, EntityTypes.SPIDER, EntityTypes.SPIDER), "vehicles of the riders after the wave spawned");
            for (Entity rider : riders) {
                helper.assertTrue(level.getEntity(rider.getUUID()) == rider, "a " + rider.getType() + " rider is not in the level after the wave spawned");
                helper.assertTrue(rider.position().distanceTo(spawnPos) < RIDER_DISTANCE, "a rider is at " + rider.position() + ", not on its wave entity at " + spawnPos);
            }
        }
        finally {
            // Also sweep the test area, in case a wave entity joined the level without firing the hook.
            List<Entity> toRemove = new ArrayList<>(spawned);
            toRemove.addAll(hooked);
            toRemove.addAll(level.getEntitiesOfClass(Mob.class, helper.getBounds()));
            toRemove.forEach(e -> e.getPassengersAndSelf().forEach(Entity::discard));
            // A failed spawn fails the gateway, which strikes it with real lightning.
            level.getEntitiesOfClass(LightningBolt.class, helper.getBounds()).forEach(Entity::discard);
        }
        helper.succeed();
    }

    /**
     * A spider carrying a skeleton carrying a chicken, with the riders written in NBT the way a datapack would, and a rabbit
     * that its own modifier attaches, the way Apotheosis's tiered summit gateway attaches its rider.
     */
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
            .modifier(riderModifier(EntityTypes.RABBIT))
            .finalizeSpawn(false)
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
        Wave wave = Wave.builder()
            .entity(spiderJockey())
            .modifier(riderModifier(EntityTypes.PIG))
            .maxWaveTime(200)
            .setupTime(0)
            .build();
        return new NormalGateway(Gateway.Size.SMALL, TextColor.fromRgb(0xFFFFFF), List.of(wave), List.of(), List.of(), (level, pos, gate, toSpawn) -> spawnPos,
            GateRules.DEFAULT, BossEventSettings.DEFAULT);
    }

    private static RiderModifier riderModifier(EntityType<?> type) {
        return new RiderModifier(new StandardWaveEntity.Builder(type).build());
    }

    /**
     * Attaches a rider the way Apotheosis's {@code apotheosis:passenger} wave modifier does, but only to the wave entity
     * itself, not to the riders it already carries.
     */
    private record RiderModifier(StandardWaveEntity rider) implements WaveModifier {

        @Override
        public void apply(LivingEntity entity, GatewayEntity gate) {
            if (entity.getVehicle() != null) return;
            LivingEntity passenger = this.rider.createEntity((ServerLevel) gate.level(), gate);
            if (passenger != null) {
                passenger.startRiding(entity, true, true);
            }
        }

        @Override
        public void appendHoverText(TooltipContext ctx, Consumer<MutableComponent> list) {}

        @Override
        public Codec<? extends WaveModifier> getCodec() {
            return MapCodec.unitCodec(this);
        }
    }

    /**
     * A normal gateway that serves a gateway definition without the registry. It is never added to the level, so it never ticks.
     */
    private static final class WaveFixture extends NormalGatewayEntity {

        private final NormalGateway gateway;

        private WaveFixture(ServerLevel level, NormalGateway gateway, Vec3 pos) {
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

}
