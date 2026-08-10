package dev.shadowsoffire.gateways;

import java.util.List;

import dev.architectury.hooks.level.entity.PlayerHooks;
import dev.shadowsoffire.gateways.command.GatewayCommand;
import dev.shadowsoffire.gateways.entity.GatewayEntity;
import dev.shadowsoffire.placebo.events.PlaceboEvents.DespawnResult;
import dev.shadowsoffire.placebo.events.PlaceboEvents.MobDespawnContext;
import dev.shadowsoffire.placebo.events.PlaceboEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.EntityTeleportEvent;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;
import net.neoforged.neoforge.event.entity.living.LivingConversionEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

public class GatewayEvents {

    /**
     * Registers the handlers in this class that live on {@link PlaceboEvents} rather than on NeoForge's bus.
     * <p>
     * The despawn handler registers at {@code LOWEST} because that is where it sat on NeoForge's bus, and the
     * reason still holds: the result is last-writer-wins, so denying despawns for gateway-owned mobs has to run
     * after anyone who might allow them.
     */
    public static void registerCommonHandlers() {
        PlaceboEvents.MOB_DESPAWN.register(dev.architectury.event.EventPriority.LOWEST, GatewayEvents::despawn);
    }

    @SubscribeEvent
    public void commands(RegisterCommandsEvent e) {
        GatewayCommand.register(e.getDispatcher());
    }

    @SubscribeEvent
    public void teleport(EntityTeleportEvent e) {
        GatewayEntity gate = GatewayEntity.getOwner(e.getEntity());
        if (gate != null && gate.getGateway().rules().failOnOutOfBounds()) {
            if (gate.distanceToSqr(e.getTargetX(), e.getTargetY(), e.getTargetZ()) >= gate.getGateway().getLeashRangeSq()) {
                e.setTargetX(gate.getX() + 0.5 * gate.getBbWidth());
                e.setTargetY(gate.getY() + 0.5 * gate.getBbHeight());
                e.setTargetZ(gate.getZ() + 0.5 * gate.getBbWidth());
            }
        }
    }

    @SubscribeEvent
    public void convert(LivingConversionEvent.Post e) {
        Entity entity = e.getEntity();
        GatewayEntity gate = GatewayEntity.getOwner(entity);
        if (gate != null) {
            gate.handleConversion(entity, e.getOutcome());
        }
    }

    @SubscribeEvent
    public void hurt(LivingIncomingDamageEvent e) {
        GatewayEntity gate = GatewayEntity.getOwner(e.getEntity());
        if (gate != null) {
            boolean isPlayerDamage = e.getSource().getEntity() instanceof Player p && !PlayerHooks.isFake(p);
            if (!isPlayerDamage && gate.getGateway().rules().playerDamageOnly()) e.setCanceled(true);
        }
    }

    private static void despawn(MobDespawnContext e) {
        if (GatewayEntity.getOwner(e.getEntity()) != null) {
            e.setResult(DespawnResult.DENY);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void death(LivingDeathEvent e) {
        if (e.getEntity() instanceof Player player) {
            List<GatewayEntity> gateways = player.level().getEntitiesOfClass(GatewayEntity.class, player.getBoundingBox().inflate(100));
            for (GatewayEntity gate : gateways) {
                gate.playerDied(player);
            }
        }
    }

    /**
     * Ensure that entities spawned by gateways are not cancelled by spawn rules or other mods that are attempting to cancel spawns.
     * <p>
     * This might have unintended side effects if other mods are cancelling spawns for important reasons, but there is no good way to track this otherwise.
     * The effect of a spawn truly being cancelled is that the gateway implodes, which is terrible player experience.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void spawn(FinalizeSpawnEvent e) {
        Entity entity = e.getEntity();
        GatewayEntity gate = GatewayEntity.getOwner(entity);
        if (gate != null && e.isSpawnCancelled()) {
            e.setSpawnCancelled(false);
        }
    }
}
