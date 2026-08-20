package dev.shadowsoffire.gateways.client;

import javax.annotation.Nullable;

import dev.shadowsoffire.gateways.GatewayObjects;
import dev.shadowsoffire.gateways.Gateways;
import dev.shadowsoffire.gateways.entity.GatewayEntity;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.world.BossEvent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.CustomizeGuiOverlayEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent.RegisterRenderers;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.neoforged.neoforge.client.event.RegisterSelectItemModelPropertyEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.common.NeoForge;

@EventBusSubscriber(value = Dist.CLIENT, modid = Gateways.MODID)
public class GatewaysClient {

    @Nullable
    public static Rect2i bossBarRect = null;

    @SubscribeEvent
    public static void setup(FMLClientSetupEvent e) {
        NeoForge.EVENT_BUS.addListener(GatewaysClient::bossRenderPre);
        NeoForge.EVENT_BUS.addListener(GatewaysClient::renderPre);
    }

    @SubscribeEvent
    public static void registerTintSources(RegisterColorHandlersEvent.ItemTintSources e) {
        e.register(Gateways.loc("gateway_color"), GatewayColorTintSource.MAP_CODEC);
    }

    @SubscribeEvent
    public static void registerSelectProperties(RegisterSelectItemModelPropertyEvent e) {
        e.register(Gateways.loc("size"), GatewaySizeProperty.TYPE);
    }

    @SubscribeEvent
    public static void eRenders(RegisterRenderers e) {
        e.registerEntityRenderer(GatewayObjects.NORMAL_GATEWAY.get(), GatewayRenderer::new);
        e.registerEntityRenderer(GatewayObjects.ENDLESS_GATEWAY.get(), GatewayRenderer::new);
    }

    @SubscribeEvent
    public static void factories(RegisterParticleProvidersEvent e) {
        e.registerSpriteSet(GatewayObjects.GLOW.get(), GatewayParticle.Provider::new);
    }

    public static void renderPre(RenderFrameEvent.Pre event) {
        bossBarRect = null;
        GatewayBossBarState.reset();
    }

    public static void bossRenderPre(CustomizeGuiOverlayEvent.BossEventProgress event) {
        BossEvent boss = event.getBossEvent();
        if (GatewayBossBarState.isGateway(boss)) {
            event.setCanceled(true);
            GatewayEntity gate = GatewayBossBarState.resolve(boss);
            if (gate != null && gate.isValid()) {
                gate.getGateway().renderBossBar(gate, event.getGuiGraphics(), event.getX(), event.getY(), false);
                event.setIncrement(event.getIncrement() * 2);
            }
        }
        bossBarRect = new Rect2i(event.getX(), 0, 200, event.getY() + event.getIncrement());
        GatewayBossBarState.recordBounds(event.getX(), event.getY() + event.getIncrement());
    }


}
