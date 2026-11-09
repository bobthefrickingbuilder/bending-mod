package com.bending.client;

import com.bending.ability.fire.FireTechniques;
import com.bending.data.Element;
import com.bending.data.ModAttachments;
import com.bending.particle.ModParticles;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Client only: while a firebender is in bending mode (their stance), tiny embers drift up off
 * their hands. Runs for every player you can see; nothing is sent over the network.
 */
public final class HandEmbers {
    /** Chance per hand per tick of an ember. */
    private static final float RATE = 0.45f;

    private HandEmbers() {}

    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.isPaused()) {
            return;
        }
        RandomSource random = mc.level.getRandom();
        for (Player player : mc.level.players()) {
            var data = player.getData(ModAttachments.BENDING);
            if (!data.bendingMode || player.isInvisible() || player.isUnderWater()) {
                continue;
            }
            // Other players' element isn't synced to us; only firebending exists so far.
            if (player == mc.player && data.getElement() != Element.FIRE) {
                continue;
            }
            boolean blue = FireTechniques.blue(player);
            float yaw = player.yBodyRot * Mth.DEG_TO_RAD;
            Vec3 forward = new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
            Vec3 side = new Vec3(-forward.z, 0, forward.x);
            Vec3 hip = player.position().add(0, player.isCrouching() ? 0.55 : 0.72, 0).add(forward.scale(0.08));
            for (int s = -1; s <= 1; s += 2) {
                if (random.nextFloat() > RATE) {
                    continue;
                }
                Vec3 hand = hip.add(side.scale(0.37 * s));
                // Radiate outward from the hand, rising.
                double vx = side.x * s * 0.015 + (random.nextDouble() - 0.5) * 0.03;
                double vz = side.z * s * 0.015 + (random.nextDouble() - 0.5) * 0.03;
                double vy = 0.01 + random.nextDouble() * 0.03;
                mc.level.addParticle((blue ? ModParticles.BLUE_HAND_EMBER : ModParticles.HAND_EMBER).get(),
                        hand.x + (random.nextDouble() - 0.5) * 0.12,
                        hand.y + (random.nextDouble() - 0.5) * 0.12,
                        hand.z + (random.nextDouble() - 0.5) * 0.12, vx, vy, vz);
            }
        }
    }
}
