package dev.shadowsoffire.gateways.client;

import dev.shadowsoffire.gateways.GatewayObjects;
import dev.shadowsoffire.gateways.Gateways;
import dev.shadowsoffire.gateways.mixin.client.ItemTintSourcesAccessor;
import dev.shadowsoffire.gateways.mixin.client.SelectItemModelPropertiesAccessor;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;

/** Fabric registration counterpart to NeoForge's {@code GatewaysClient} lifecycle handlers. */
public class GatewaysFabricClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        EntityRendererRegistry.register(GatewayObjects.NORMAL_GATEWAY.get(), GatewayRenderer::new);
        EntityRendererRegistry.register(GatewayObjects.ENDLESS_GATEWAY.get(), GatewayRenderer::new);
        ParticleProviderRegistry.getInstance().register(GatewayObjects.GLOW.get(), GatewayParticle.Provider::new);

        ItemTintSourcesAccessor.gateways$getIdMapper().put(Gateways.loc("gateway_color"), GatewayColorTintSource.MAP_CODEC);
        SelectItemModelPropertiesAccessor.gateways$getIdMapper().put(Gateways.loc("size"), GatewaySizeProperty.TYPE);
    }
}
