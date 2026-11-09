package com.bending.client;

import com.bending.BendingConfig;
import com.bending.network.ImpactPayload;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

/** Client side of {@link com.bending.ability.ImpactFx}: camera shake ("trauma") and a brief tinted flash. */
public final class ImpactEffects {
    /** 0..1; the shake grows with its square, so small hits barely nudge and big ones rattle. */
    private static float trauma;
    private static float flash, flashO;
    private static int flashColor = 0xFF9A3C;

    private static final float MAX_YAW = 3.5f, MAX_PITCH = 3.0f, MAX_ROLL = 4.0f;

    private ImpactEffects() {}

    public static void init() {
        ImpactPayload.clientHandler = ImpactEffects::handle;
    }

    private static void handle(ImpactPayload p) {
        trauma = Math.min(1f, trauma + p.shake());
        if (p.flash() > flash) {
            flash = Math.min(1f, p.flash());
            flashColor = p.color();
        }
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        trauma = Math.max(0f, trauma - 0.045f);
        flashO = flash;
        flash = flash < 0.01f ? 0f : flash * 0.72f;
    }

    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        double scale = BendingConfig.SCREEN_SHAKE.get();
        if (trauma <= 0f || scale <= 0) {
            return;
        }
        float shake = (float) (trauma * trauma * scale);
        double t = (Minecraft.getInstance().level != null ? Minecraft.getInstance().level.getGameTime() : 0) + event.getPartialTick();
        event.setYaw(event.getYaw() + MAX_YAW * shake * noise(t, 0.0));
        event.setPitch(event.getPitch() + MAX_PITCH * shake * noise(t, 17.3));
        event.setRoll(event.getRoll() + MAX_ROLL * shake * noise(t, 41.9));
    }

    /** Smooth-ish pseudo noise in -1..1. */
    private static float noise(double t, double seed) {
        double x = t * 1.9 + seed;
        return (float) ((Math.sin(x * 2.3) + Math.sin(x * 3.7 + 1.3) * 0.6 + Math.sin(x * 7.1 + 2.1) * 0.3) / 1.9);
    }

    /** GUI layer: a warm (or electric) wash over the screen that fades in a few ticks. */
    public static void renderFlash(GuiGraphics graphics, DeltaTracker delta) {
        if (!BendingConfig.IMPACT_FLASHES.get()) {
            return;
        }
        float f = Mth.lerp(delta.getGameTimeDeltaPartialTick(false), flashO, flash);
        if (f < 0.01f) {
            return;
        }
        int alpha = (int) (Math.min(0.45f, f * 0.45f) * 255);
        graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(), (alpha << 24) | (flashColor & 0xFFFFFF));
    }
}
