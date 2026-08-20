package dev.shadowsoffire.gateways.mixin.client;

import java.util.Map;
import java.util.UUID;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.shadowsoffire.gateways.client.GatewayBossBarState;
import dev.shadowsoffire.gateways.entity.GatewayEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.BossHealthOverlay;
import net.minecraft.client.gui.components.LerpingBossEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.world.BossEvent;

/** Replaces only gateway-tagged vanilla boss bars with Gateways' existing renderer. */
@Mixin(value = BossHealthOverlay.class, remap = false)
public abstract class BossHealthOverlayMixin {

    @Shadow
    @Final
    private Minecraft minecraft;

    @Shadow
    @Final
    private Map<UUID, LerpingBossEvent> events;

    @Shadow
    private void extractBar(GuiGraphicsExtractor graphics, int x, int y, BossEvent event) {}

    @Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true)
    private void gateways$extractGatewayBars(GuiGraphicsExtractor graphics, CallbackInfo callback) {
        GatewayBossBarState.reset();
        recordBounds(graphics);
        if (this.events.values().stream().noneMatch(GatewayBossBarState::isGateway)) {
            return;
        }

        graphics.nextStratum();
        var profiler = Profiler.get();
        profiler.push("bossHealth");
        try {
            int width = graphics.guiWidth();
            int y = 12;
            for (LerpingBossEvent event : this.events.values()) {
                int x = width / 2 - 91;
                int increment = 10 + this.minecraft.font.lineHeight;
                if (GatewayBossBarState.isGateway(event)) {
                    GatewayEntity gateway = GatewayBossBarState.resolve(event);
                    if (gateway != null && gateway.isValid()) {
                        gateway.getGateway().renderBossBar(gateway, graphics, x, y, false);
                        increment *= 2;
                    }
                }
                else {
                    this.extractBar(graphics, x, y, event);
                    Component name = event.getName();
                    graphics.text(this.minecraft.font, name, width / 2 - this.minecraft.font.width(name) / 2, y - 9, -1);
                }

                y += increment;
                if (y >= graphics.guiHeight() / 3) {
                    break;
                }
            }
        }
        finally {
            profiler.pop();
        }
        callback.cancel();
    }

    private void recordBounds(GuiGraphicsExtractor graphics) {
        int x = graphics.guiWidth() / 2 - 91;
        int y = 12;
        for (LerpingBossEvent event : this.events.values()) {
            int increment = 10 + this.minecraft.font.lineHeight;
            GatewayEntity gateway = GatewayBossBarState.resolve(event);
            if (gateway != null && gateway.isValid()) {
                increment *= 2;
            }
            y += increment;
            GatewayBossBarState.recordBounds(x, y);
            if (y >= graphics.guiHeight() / 3) {
                break;
            }
        }
    }
}
