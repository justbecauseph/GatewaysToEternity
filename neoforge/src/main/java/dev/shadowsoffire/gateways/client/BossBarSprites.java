package dev.shadowsoffire.gateways.client;

import com.mojang.blaze3d.pipeline.RenderPipeline;

import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/**
 * The vanilla boss-bar sprites Gateways draws its own bars with.
 * <p>
 * These lived on {@code GatewaysClient}, which is the NeoForge client entrypoint and cannot leave the platform
 * side. Nothing about them is platform-specific -- an {@code Identifier} and a {@code RenderPipeline} -- and
 * they are read only by the bar renderers, so they sit beside those instead.
 */
public class BossBarSprites {

    public static final Identifier WHITE_PROGRESS = Identifier.withDefaultNamespace("boss_bar/white_progress");
    public static final Identifier WHITE_BACKGROUND = Identifier.withDefaultNamespace("boss_bar/white_background");
    public static final RenderPipeline BLIT_PIPELINE = RenderPipelines.GUI_TEXTURED;

}
