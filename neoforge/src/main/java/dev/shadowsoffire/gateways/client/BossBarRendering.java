package dev.shadowsoffire.gateways.client;

import org.joml.Matrix3x2fStack;

import com.mojang.blaze3d.pipeline.RenderPipeline;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * What Gateways' two boss-bar renderers share: the vanilla sprites they draw with, and the reversed
 * drop-shadow text helper.
 * <p>
 * All of it lived on {@code GatewaysClient}, which is the NeoForge client entrypoint and cannot leave the
 * platform side. None of it is platform-specific, and {@code GatewaysClient} never used any of it itself.
 */
public class BossBarRendering {

    public static final Identifier WHITE_PROGRESS = Identifier.withDefaultNamespace("boss_bar/white_progress");
    public static final Identifier WHITE_BACKGROUND = Identifier.withDefaultNamespace("boss_bar/white_background");
    public static final RenderPipeline BLIT_PIPELINE = RenderPipelines.GUI_TEXTURED;

    /**
     * Draws {@code comp} with its drop shadow offset down-right and darkened, rather than vanilla's
     * up-left shadow -- which is what makes the gateway bars' labels read against the bar itself.
     */
    public static void drawReversedDropShadow(GuiGraphicsExtractor gfx, Font font, Component comp, int x, int y) {
        gfx.text(font, comp, x, y, 0xFF000000, false);
        Matrix3x2fStack pose = gfx.pose();
        pose.pushMatrix();
        pose.translate(1, 1);
        int color = comp.getStyle().getColor().getValue();
        int r = ((color >> 16) & 0xFF) / 4;
        int g = ((color >> 8) & 0xFF) / 4;
        int b = ((color) & 0xFF) / 4;
        color = 0xFF << 24 | r << 16 | g << 8 | b;
        gfx.text(font, comp.getString(), x, y, color, false);
        pose.popMatrix();
    }

}
