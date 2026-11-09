package com.bending.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.client.CameraType;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/**
 * Over-the-shoulder third person: the back view sits off to one side of the player instead of
 * straight behind, so you see past your own body and can judge distances. The swap key flips it
 * to the other shoulder, and the camera slides across smoothly. The actual camera move happens in
 * {@code CameraMixin}; this holds the settings and draws a crosshair, which vanilla hides in third person.
 */
public final class ShoulderCamera {
    /** Blocks to the side of the player's head (positive = right shoulder). */
    public static final float SIDE = 0.85f;
    /** Blocks above the head, so the player doesn't block the middle of the screen. */
    public static final float UP = 0.2f;

    private static final ResourceLocation CROSSHAIR = ResourceLocation.withDefaultNamespace("hud/crosshair");

    private static boolean rightShoulder = true;
    /** The side offset actually in use, sliding toward the target after a swap. */
    private static float currentSide = SIDE;

    private ShoulderCamera() {}

    public static void swapShoulder() {
        rightShoulder = !rightShoulder;
    }

    /** Eases toward the chosen shoulder; called once per frame from the camera. */
    public static float side() {
        float target = rightShoulder ? SIDE : -SIDE;
        currentSide += (target - currentSide) * 0.2f;
        if (Math.abs(target - currentSide) < 0.002f) {
            currentSide = target;
        }
        return currentSide;
    }

    /** The vanilla crosshair, drawn in the back view too, since that's the view you aim from now. */
    public static void renderCrosshair(GuiGraphics g, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.player == null || mc.options.getCameraType() != CameraType.THIRD_PERSON_BACK
                || mc.player.isSpectator()) {
            return;
        }
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.ONE_MINUS_DST_COLOR, GlStateManager.DestFactor.ONE_MINUS_SRC_COLOR,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
        g.blitSprite(CROSSHAIR, (g.guiWidth() - 15) / 2, (g.guiHeight() - 15) / 2, 15, 15);
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
    }
}
