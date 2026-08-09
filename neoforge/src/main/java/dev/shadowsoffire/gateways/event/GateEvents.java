package dev.shadowsoffire.gateways.event;

import dev.architectury.event.Event;
import dev.architectury.event.EventFactory;
import dev.shadowsoffire.gateways.entity.GatewayEntity;
import net.minecraft.world.entity.LivingEntity;

/**
 * Gateways' own events, for other mods to listen to.
 * <p>
 * Replaces the {@code GateEvent} class hierarchy, which extended NeoForge's {@code EntityEvent} and so was
 * posted to NeoForge's bus. Architectury's {@link Event} works on both loaders, which is the only reason this
 * changed -- nothing in this stack ever subscribed to these, so the shape was free to pick.
 * <p>
 * None of them are cancellable, matching the originals.
 */
public class GateEvents {

    /**
     * Fired when a gateway is opened.
     */
    public static final Event<GateCallback> OPENED = EventFactory.createLoop();

    /**
     * Fired when a gateway is successfully completed.
     */
    public static final Event<GateCallback> COMPLETED = EventFactory.createLoop();

    /**
     * Fired when a wave is started.
     */
    public static final Event<GateCallback> WAVE_STARTED = EventFactory.createLoop();

    /**
     * Fired when a wave is completed, but before the current wave counter is incremented.
     */
    public static final Event<GateCallback> WAVE_END = EventFactory.createLoop();

    /**
     * Fired when a gateway is failed, for any reason.
     */
    public static final Event<GateCallback> FAILED = EventFactory.createLoop();

    /**
     * Fired for each entity spawned as part of a wave, after it has been added to the level.
     */
    public static final Event<WaveEntitySpawnedCallback> WAVE_ENTITY_SPAWNED = EventFactory.createLoop();

    @FunctionalInterface
    public interface GateCallback {
        void accept(GatewayEntity gate);
    }

    @FunctionalInterface
    public interface WaveEntitySpawnedCallback {
        void accept(GatewayEntity gate, LivingEntity waveEntity);
    }

}
