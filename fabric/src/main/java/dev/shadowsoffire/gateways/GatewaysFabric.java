package dev.shadowsoffire.gateways;

import dev.shadowsoffire.gateways.event.GatewayCommonEvents;
import dev.shadowsoffire.gateways.gate.Failure;
import dev.shadowsoffire.gateways.gate.GatewayRegistry;
import dev.shadowsoffire.gateways.gate.Reward;
import dev.shadowsoffire.gateways.gate.WaveEntity;
import dev.shadowsoffire.gateways.gate.WaveModifier;
import dev.shadowsoffire.gateways.gate.endless.ApplicationMode;
import dev.shadowsoffire.gateways.payloads.ParticlePayload;
import dev.shadowsoffire.placebo.dynreg.FabricDynReg;
import dev.shadowsoffire.placebo.network.PayloadHelper;
import dev.shadowsoffire.placebo.registry.DeferredHelper;
import dev.shadowsoffire.placebo.registry.FabricRegistryFactory;
import dev.shadowsoffire.placebo.tabs.TabFillingRegistry;
import net.fabricmc.api.ModInitializer;
import net.minecraft.stats.StatFormatter;
import net.minecraft.stats.Stats;

/**
 * Fabric entrypoint, the counterpart to {@code GatewaysNeoForge}.
 * <p>
 * Registers the loader-neutral content and services. Event and rendering hooks that have no Architectury
 * equivalent remain loader-specific.
 */
public class GatewaysFabric implements ModInitializer {

    @Override
    public void onInitialize() {
        DeferredHelper.setRegistryFactory(new FabricRegistryFactory());
        FabricDynReg.rebindRuntimeHooks();
        GatewayObjects.bootstrap();
        WaveModifier.initCodecs();
        Reward.initCodecs();
        WaveEntity.initCodecs();
        Failure.initCodecs();
        ApplicationMode.initCodecs();
        GatewayCommonEvents.register();
        GatewayIncomingDamageEvents.registerCommonHandlers();
        GatewayRegistry.INSTANCE.registerToBus();
        PayloadHelper.registerPayload(new ParticlePayload.Provider());
        TabFillingRegistry.register(GatewayObjects.TAB.getKey(), GatewayObjects.GATE_PEARL);
        Stats.CUSTOM.get(GatewayObjects.GATES_DEFEATED, StatFormatter.DEFAULT);
        Gateways.LOGGER.info("Gateways to Eternity (Fabric) initialized.");
    }

}
