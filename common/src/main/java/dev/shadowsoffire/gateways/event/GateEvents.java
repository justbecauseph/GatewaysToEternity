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
 * changed -- nothing in this stack subscribed to the originals, so the shape was free to pick.
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
     * Fired for each entity spawned as part of a wave, just before it and its passengers are added to the level.
     * <p>
     * By then the entity is positioned (its passengers are not), owned by the gate, carries its wave's modifiers, the party
     * health floor and the gate rules, and has been finalized if its wave entity asks for it. An endless gateway applies the
     * wave modifiers of its endless modifiers later, after the whole wave has joined the level. Entity-add processing runs
     * after the event, so listeners can record state that it reads. Fired once per spawned entity, not per passenger;
     * passengers are reachable through {@link net.minecraft.world.entity.Entity#getPassengersAndSelf()}.
     */
    public static final Event<WaveEntitySpawnedCallback> WAVE_ENTITY_SPAWNED = EventFactory.createLoop();

    /**
     * Fired for each entity a {@link dev.shadowsoffire.gateways.gate.Failure.SummonFailure} summons, just before it and its
     * passengers are added to the level.
     * <p>
     * Failure summons are not wave members, so they do not fire {@link #WAVE_ENTITY_SPAWNED}: they are summoned while the gate
     * fails (after {@link #FAILED}, before the gate is removed), are not owned or tracked by the gate, and get none of a
     * wave's modifiers, no party health floor and no finalization; only the wave entity's own modifiers apply. By then the
     * entity is positioned, and its passengers sit on it.
     * Entity-add processing runs after the event, so listeners can record state that it reads. Fired once per summoned
     * entity, not per passenger.
     */
    public static final Event<WaveEntitySpawnedCallback> FAILURE_ENTITY_SPAWNED = EventFactory.createLoop();

    @FunctionalInterface
    public interface GateCallback {
        void accept(GatewayEntity gate);
    }

    /**
     * Receives an entity built from a {@link dev.shadowsoffire.gateways.gate.WaveEntity}, by a wave or by a summon failure.
     */
    @FunctionalInterface
    public interface WaveEntitySpawnedCallback {
        void accept(GatewayEntity gate, LivingEntity waveEntity);
    }

}
