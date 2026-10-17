package dev.shadowsoffire.gateways.gate;

import org.jetbrains.annotations.Nullable;

import com.mojang.serialization.Codec;

import dev.shadowsoffire.gateways.Gateways;
import dev.shadowsoffire.gateways.entity.GatewayEntity;
import dev.shadowsoffire.placebo.codec.CodecMap;
import dev.shadowsoffire.placebo.codec.CodecProvider;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;

public interface WaveEntity extends CodecProvider<WaveEntity> {

    public static final CodecMap<WaveEntity> CODEC = new CodecMap<>("Wave Entity");

    public static void initCodecs() {
        register("standard", StandardWaveEntity.CODEC);
        CODEC.setDefaultCodec(StandardWaveEntity.CODEC);
    }

    private static void register(String id, Codec<? extends WaveEntity> codec) {
        CODEC.register(Gateways.loc(id), codec);
    }

    /**
     * Logs why a wave entity could not be spawned.
     * <p>
     * Lives here rather than on the NeoForge entrypoint, where it used to: it is plain logging over
     * {@link GatewayEntity} and {@link WaveEntity}, so keeping it beside them means it travels with them
     * rather than pinning both of its callers to the platform side.
     */
    public static void logSpawnDebug(GatewayEntity gate, WaveEntity entity, String failureReason) {
        Gateways.LOGGER.debug("Failed to spawn entity '{}' for gateway '{}' at position {}. Reason: {}",
            entity.getDescription().getString(),
            GatewayRegistry.INSTANCE.holder(gate.getGateway()).getId(),
            gate.blockPosition(),
            failureReason);
    }

    /**
     * Places every direct and indirect passenger of {@code entity} on its vehicle. Call it once {@code entity} is positioned
     * and has all of its riders, before it joins the level.
     * <p>
     * Passengers stay where they were loaded or created (usually 0, 0, 0) until they ride-tick, and a passenger only
     * ride-ticks while its own chunk ticks, so one added at its load position may never reach its vehicle.
     */
    public static void positionPassengers(Entity entity) {
        entity.getIndirectPassengers().forEach(p -> p.getVehicle().positionRider(p));
    }

    /**
     * Creates the entity to be spawned in the current wave.
     *
     * @param level The level.
     * @return The entity, or null if an error occured. Null will end the gate.
     */
    @Nullable
    default LivingEntity createEntity(ServerLevel level, GatewayEntity gate) {
        return createEntity(level);
    }

    /**
     * Gets the tooltip form of this wave entity for display in the Gate Pearl's "Waves" section.
     */
    public MutableComponent getDescription();

    /**
     * If the spawned wave entity should have {@link Mob#finalizeSpawn} called.
     */
    public boolean shouldFinalizeSpawn();

    /**
     * The number of times this wave entity should be spawned.
     */
    public int getCount();

    /**
     * @deprecated Use {@link #createEntity(ServerLevel, GatewayEntity)}.
     */
    @Deprecated(forRemoval = true)
    default LivingEntity createEntity(Level level) {
        return null;
    }

}
