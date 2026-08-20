package dev.shadowsoffire.gateways.client;

import org.jetbrains.annotations.Nullable;

import dev.shadowsoffire.gateways.entity.GatewayEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.world.BossEvent;
import net.minecraft.world.level.Level;

/** Shared gateway boss-bar identification and last-rendered HUD bounds. */
public final class GatewayBossBarState {

    private static final String GATEWAY_ID_PREFIX = "GATEWAY_ID";

    @Nullable
    private static Rect2i bossBarRect;

    private GatewayBossBarState() {}

    public static boolean isGateway(BossEvent event) {
        return event.getName().getString().startsWith(GATEWAY_ID_PREFIX);
    }

    @Nullable
    public static GatewayEntity resolve(BossEvent event) {
        String name = event.getName().getString();
        Level level = Minecraft.getInstance().level;
        if (level == null || !name.startsWith(GATEWAY_ID_PREFIX)) {
            return null;
        }
        try {
            if (level.getEntity(Integer.parseInt(name.substring(GATEWAY_ID_PREFIX.length()))) instanceof GatewayEntity gateway) {
                return gateway;
            }
        }
        catch (NumberFormatException ex) {
            // Malformed synthetic boss events are hidden just like unresolved gateway entities.
        }
        return null;
    }

    public static void reset() {
        bossBarRect = null;
    }

    public static void recordBounds(int x, int bottom) {
        bossBarRect = new Rect2i(x, 0, 200, bottom);
    }

    @Nullable
    public static Rect2i bossBarRect() {
        return bossBarRect;
    }
}
