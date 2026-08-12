package dev.shadowsoffire.gateways;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.shadowsoffire.gateways.data.GatewayProvider;
import dev.shadowsoffire.gateways.data.GatewayRecipeProvider;
import dev.shadowsoffire.gateways.data.GearSetProvider;
import dev.shadowsoffire.gateways.entity.GatewayEntity;
import dev.shadowsoffire.gateways.event.GatewayCommonEvents;
import dev.shadowsoffire.gateways.gate.Failure;
import dev.shadowsoffire.gateways.gate.GatewayRegistry;
import dev.shadowsoffire.gateways.gate.Reward;
import dev.shadowsoffire.gateways.gate.WaveEntity;
import dev.shadowsoffire.gateways.gate.WaveModifier;
import dev.shadowsoffire.gateways.gate.endless.ApplicationMode;
import dev.shadowsoffire.gateways.payloads.ParticlePayload;
import dev.shadowsoffire.placebo.datagen.DataGenBuilder;
import dev.shadowsoffire.placebo.datagen.FilteredOrderingFactory;
import dev.shadowsoffire.placebo.datagen.RegisterFieldOrderingsEvent;
import dev.shadowsoffire.placebo.network.PayloadHelper;
import dev.shadowsoffire.placebo.tabs.TabFillingRegistry;
import net.minecraft.data.DataProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.stats.StatFormatter;
import net.minecraft.stats.Stats;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.data.event.GatherDataEvent;

@Mod(Gateways.MODID)
public class GatewaysNeoForge {


    public GatewaysNeoForge(IEventBus bus) {
        bus.register(this);
        PayloadHelper.registerPayload(new ParticlePayload.Provider());
        NeoForge.EVENT_BUS.register(new GatewayEvents());
        GatewayCommonEvents.register();
        GatewayIncomingDamageEvents.registerCommonHandlers();
        GatewayObjects.bootstrap();
        WaveModifier.initCodecs();
        Reward.initCodecs();
        WaveEntity.initCodecs();
        Failure.initCodecs();
        ApplicationMode.initCodecs();
    }

    @SubscribeEvent
    public void setup(FMLCommonSetupEvent e) {
        GatewayRegistry.INSTANCE.registerToBus();
        e.enqueueWork(() -> {
            TabFillingRegistry.register(GatewayObjects.TAB.getKey(), GatewayObjects.GATE_PEARL);
            Stats.CUSTOM.get(GatewayObjects.GATES_DEFEATED, StatFormatter.DEFAULT);
        });
    }

    @SubscribeEvent
    public void data(GatherDataEvent.Client e) {
        DataProvider.INDENT_WIDTH.set(4);
        DataGenBuilder.create(Gateways.MODID)
            .provider(GatewayProvider::new)
            .provider(GearSetProvider::new)
            .provider(GatewayRecipeProvider::new)
            .build(e);
    }

    @SubscribeEvent
    public void fieldOrdering(RegisterFieldOrderingsEvent e) {
        e.register(FilteredOrderingFactory.builder()
            .registries(GatewayRegistry.INSTANCE.getId())
            .orderMap(map -> {
                // Normal Gateway Fields
                map.put("size", 10);
                map.put("color", 20);
                map.put("waves", 30);
                map.put("rewards", 40);
                map.put("failures", 50);
                map.put("spawn_algorithm", 60);
                map.put("rules", 70);
                map.put("boss_event", 80);

                // Endless Gateway Fields
                map.put("base_wave", 30);
                map.put("modifiers", 35);

                // Wave Fields. Waves reuse a couple field names from normal gateways so we interleave the wave keys within those indicies.
                map.put("max_wave_time", 5);
                map.put("setup_time", 8);
                map.put("entities", 10);
            })
            .build());
    }

}
