package dev.shadowsoffire.gateways;

import net.fabricmc.api.ModInitializer;

/**
 * Fabric entrypoint, the counterpart to {@code GatewaysNeoForge}.
 * <p>
 * The 2a split landed at 5 common / 39 platform, the lowest ratio in the stack. That is honest rather than
 * lazy: Gateways is almost entirely event-driven -- wave spawning, entity lifecycle, boss bars and rewards
 * all hang off NeoForge events -- so the event abstraction layer is the single thing this mod's Fabric port
 * waits on. See {@code porting/03-fabric-completion.md}.
 * <p>
 * Also unported: {@code GatewayObjects} registration, the client renderer, and the Apotheosis tiered-gate
 * compat.
 */
public class GatewaysFabric implements ModInitializer {

    @Override
    public void onInitialize() {
        Gateways.LOGGER.info("Gateways to Eternity (Fabric) initialized -- common only; see the class javadoc.");
    }

}
