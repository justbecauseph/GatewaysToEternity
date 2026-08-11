package dev.shadowsoffire.gateways.event;

import dev.architectury.event.EventPriority;
import dev.shadowsoffire.gateways.entity.GatewayEntity;
import dev.shadowsoffire.placebo.events.PlaceboEvents;
import dev.shadowsoffire.placebo.events.PlaceboEvents.DespawnResult;
import dev.shadowsoffire.placebo.events.PlaceboEvents.FinalizeSpawnContext;
import dev.shadowsoffire.placebo.events.PlaceboEvents.MobDespawnContext;

/** Loader-neutral handlers backed by Placebo's cross-loader event surface. */
public final class GatewayCommonEvents {

    private GatewayCommonEvents() {}

    public static void register() {
        // Last-writer-wins: gateway-owned mobs must remain non-despawning after other handlers run.
        PlaceboEvents.MOB_DESPAWN.register(EventPriority.LOWEST, GatewayCommonEvents::despawn);
        // This deliberately undoes spawn cancellation, so retain the original LOWEST priority.
        PlaceboEvents.FINALIZE_SPAWN.register(EventPriority.LOWEST, GatewayCommonEvents::spawn);
    }

    private static void despawn(MobDespawnContext event) {
        if (GatewayEntity.getOwner(event.getEntity()) != null) {
            event.setResult(DespawnResult.DENY);
        }
    }

    private static void spawn(FinalizeSpawnContext event) {
        if (GatewayEntity.getOwner(event.getEntity()) != null && event.isSpawnCancelled()) {
            event.setSpawnCancelled(false);
        }
    }
}
