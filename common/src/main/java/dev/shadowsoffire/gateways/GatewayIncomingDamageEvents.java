package dev.shadowsoffire.gateways;

import dev.architectury.event.EventResult;
import dev.architectury.hooks.level.entity.PlayerHooks;
import dev.shadowsoffire.gateways.entity.GatewayEntity;
import dev.shadowsoffire.placebo.events.PlaceboEvents;
import dev.shadowsoffire.placebo.events.PlaceboEvents.IncomingDamageContext;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/** Cancels non-player damage against wave entities when the gateway rule requires it. */
public final class GatewayIncomingDamageEvents {

    private GatewayIncomingDamageEvents() {}

    public static void registerCommonHandlers() {
        PlaceboEvents.LIVING_INCOMING_DAMAGE.register(GatewayIncomingDamageEvents::hurt);
    }

    private static EventResult hurt(IncomingDamageContext ctx) {
        return hurt(ctx.getEntity(), ctx.getSource()) ? EventResult.interruptFalse() : EventResult.pass();
    }

    /** Returns whether a gateway-owned target rejects this hit without allocating an event context. */
    public static boolean hurt(LivingEntity target, DamageSource source) {
        GatewayEntity gate = GatewayEntity.getOwner(target);
        if (gate != null) {
            boolean playerDamage = source.getEntity() instanceof Player player && !PlayerHooks.isFake(player);
            if (!playerDamage && gate.getGateway().rules().playerDamageOnly()) {
                return true;
            }
        }
        return false;
    }

}
