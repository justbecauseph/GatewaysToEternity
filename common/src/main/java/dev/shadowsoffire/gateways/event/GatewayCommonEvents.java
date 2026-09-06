package dev.shadowsoffire.gateways.event;

import dev.architectury.event.EventPriority;
import dev.architectury.event.EventResult;
import dev.shadowsoffire.gateways.entity.GatewayEntity;
import dev.shadowsoffire.placebo.events.PlaceboEvents;
import dev.shadowsoffire.placebo.events.PlaceboEvents.DespawnResult;
import dev.shadowsoffire.placebo.events.PlaceboEvents.FinalizeSpawnContext;
import dev.shadowsoffire.placebo.events.PlaceboEvents.MobDespawnContext;
import dev.shadowsoffire.placebo.events.PlaceboEvents.EntityTeleportContext;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ServerLevelAccessor;

/** Loader-neutral handlers backed by Placebo's cross-loader event surface. */
public final class GatewayCommonEvents {

    private GatewayCommonEvents() {}

    public static void register() {
        // Last-writer-wins: gateway-owned mobs must remain non-despawning after other handlers run.
        PlaceboEvents.registerMobDespawnInternal(EventPriority.LOWEST, GatewayCommonEvents::despawn);
        // This deliberately undoes spawn cancellation, so retain the original LOWEST priority.
        PlaceboEvents.FINALIZE_SPAWN.register(EventPriority.LOWEST, GatewayCommonEvents::spawn);
        PlaceboEvents.ENTITY_TELEPORT.register(GatewayCommonEvents::teleport);
    }

    private static void despawn(MobDespawnContext event) {
        event.setResult(despawn(event.getEntity(), event.getLevel(), event.getResult()));
    }

    /** Direct Fabric despawn handler; preserve the carried result for last-writer-wins priority ordering. */
    public static DespawnResult despawn(Mob mob, ServerLevelAccessor level, DespawnResult current) {
        return GatewayEntity.getOwner(mob) != null ? DespawnResult.DENY : current;
    }

    private static void spawn(FinalizeSpawnContext event) {
        if (GatewayEntity.getOwner(event.getEntity()) != null && event.isSpawnCancelled()) {
            event.setSpawnCancelled(false);
        }
    }

    private static EventResult teleport(EntityTeleportContext event) {
        GatewayEntity gate = GatewayEntity.getOwner(event.getEntity());
        if (gate != null && gate.getGateway().rules().failOnOutOfBounds()
            && gate.distanceToSqr(event.getTargetX(), event.getTargetY(), event.getTargetZ()) >= gate.getGateway().getLeashRangeSq()) {
            event.setTargetX(gate.getX() + 0.5 * gate.getBbWidth());
            event.setTargetY(gate.getY() + 0.5 * gate.getBbHeight());
            event.setTargetZ(gate.getZ() + 0.5 * gate.getBbWidth());
        }
        return EventResult.pass();
    }
}
