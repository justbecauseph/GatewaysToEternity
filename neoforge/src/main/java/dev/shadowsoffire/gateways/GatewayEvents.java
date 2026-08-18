package dev.shadowsoffire.gateways;

import java.util.List;

import dev.shadowsoffire.gateways.command.GatewayCommand;
import dev.shadowsoffire.gateways.entity.GatewayEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.living.LivingConversionEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

public class GatewayEvents {

    @SubscribeEvent
    public void commands(RegisterCommandsEvent e) {
        GatewayCommand.register(e.getDispatcher());
    }

    @SubscribeEvent
    public void convert(LivingConversionEvent.Post e) {
        Entity entity = e.getEntity();
        GatewayEntity gate = GatewayEntity.getOwner(entity);
        if (gate != null) {
            gate.handleConversion(entity, e.getOutcome());
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
