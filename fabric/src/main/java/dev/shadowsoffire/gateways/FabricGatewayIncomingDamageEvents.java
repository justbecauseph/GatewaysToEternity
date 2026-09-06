package dev.shadowsoffire.gateways;

import dev.architectury.event.EventResult;
import dev.architectury.event.EventPriority;
import dev.shadowsoffire.placebo.events.FabricDamageDispatcher;
import dev.shadowsoffire.placebo.events.PlaceboEvents;
import dev.shadowsoffire.placebo.events.PlaceboEvents.IncomingDamage;
import dev.shadowsoffire.placebo.events.PlaceboEvents.IncomingDamageContext;

/** Fabric-owned gateway incoming-damage slot and its public-event compatibility listener. */
public final class FabricGatewayIncomingDamageEvents {

    private static boolean registered;

    private static final IncomingDamage FALLBACK = FabricGatewayIncomingDamageEvents::fallback;

    private FabricGatewayIncomingDamageEvents() {}

    /** Installs the NORMAL gateway slot and fallback listener once. */
    public static synchronized void register() {
        if (registered) return;

        FabricDamageDispatcher.registerGateways((entity, source, amount) ->
            FabricDamageDispatcher.pack(amount, GatewayIncomingDamageEvents.hurt(entity, source)));
        PlaceboEvents.registerIncomingDamageInternal(EventPriority.NORMAL, FALLBACK);
        registered = true;
    }

    private static EventResult fallback(IncomingDamageContext ctx) {
        return GatewayIncomingDamageEvents.hurt(ctx.getEntity(), ctx.getSource())
            ? EventResult.interruptFalse() : EventResult.pass();
    }

}
