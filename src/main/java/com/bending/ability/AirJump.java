package com.bending.ability;

import com.bending.particle.ModParticles;
import com.bending.sound.ModSounds;

import javax.annotation.Nullable;

import com.bending.data.BendingData;
import com.bending.data.Element;
import com.bending.data.ModAttachments;
import com.bending.server.BendingRules;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/**
 * Mid-air hops: press jump while airborne for a small boost that breaks your fall
 * without gaining much height. Firebenders get 3 per jump ("Fire Hop").
 *
 * The client applies the motion immediately (player movement is client-side in Minecraft,
 * so this feels instant); the server counts uses, clears fall damage and shows the effects.
 */
public final class AirJump {
    /** Upward speed of each hop. A normal jump is 0.42, so this barely climbs. */
    public static final double HOP_VELOCITY = 0.3;

    private AirJump() {}

    public static int maxJumps(@Nullable Element element) {
        return element == Element.FIRE ? 3 : 0;
    }

    /** Server side: validate and apply effects for a hop the client just performed. */
    public static void perform(ServerPlayer player) {
        BendingData data = player.getData(ModAttachments.BENDING);
        Element element = data.getElement();
        if (element == null || !data.bendingMode || data.airJumpsUsed >= maxJumps(element)
                || player.isInWater() || player.isPassenger() || player.getAbilities().flying
                || !BendingRules.canBend(player, element)) {
            return;
        }
        data.airJumpsUsed++;
        player.fallDistance = 0;

        ServerLevel level = player.serverLevel();
        level.sendParticles(ModParticles.FIRE_WISP.get(), player.getX(), player.getY(), player.getZ(), 12, 0.25, 0.05, 0.25, 0.04);
        level.sendParticles(ModParticles.FIRE_SMOKE.get(), player.getX(), player.getY(), player.getZ(), 4, 0.2, 0.05, 0.2, 0.02);
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                ModSounds.FIRE_HOP.get(), SoundSource.PLAYERS, 0.4f, 1.8f);
    }
}
