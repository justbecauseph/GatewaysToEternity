package dev.shadowsoffire.gateways;

import java.util.List;

import dev.architectury.hooks.level.entity.PlayerHooks;
import dev.shadowsoffire.gateways.command.GatewayCommand;
import dev.shadowsoffire.gateways.entity.GatewayEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.EntityTeleportEvent;
import net.neoforged.neoforge.event.entity.living.LivingConversionEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

public class GatewayEvents {

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

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void death(LivingDeathEvent e) {
        if (e.getEntity() instanceof Player player) {
            List<GatewayEntity> gateways = player.level().getEntitiesOfClass(GatewayEntity.class, player.getBoundingBox().inflate(100));
            for (GatewayEntity gate : gateways) {
                gate.playerDied(player);
            }
        }
    }

}
