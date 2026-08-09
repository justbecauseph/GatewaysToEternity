package dev.shadowsoffire.gateways;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;

/**
 * Loader-neutral identity for Gateways to Eternity.
 * <p>
 * The platform entrypoint ({@code GatewaysNeoForge}, and a Fabric initializer in Phase 2b) lives in its own
 * subproject and drives registration and events.
 */
public class Gateways {

    public static final String MODID = "gateways";
    public static final Logger LOGGER = LoggerFactory.getLogger("Gateways to Eternity");

    protected Gateways() {}

    public static Identifier loc(String path) {
        return Identifier.fromNamespaceAndPath(MODID, path);
    }

    /**
     * Constructs a translatable component with a lang key of the form {@code type.modid.path}.
     */
    public static MutableComponent lang(String type, String path, Object... args) {
        return Component.translatable(langKey(type, path), args);
    }

    public static String langKey(String type, String path) {
        return type + "." + MODID + "." + path;
    }

}
