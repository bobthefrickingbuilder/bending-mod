package com.bending.ability.fire;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

/** The show's firebending rules: the Sun empowers, water douses, the Nether is home. */
public final class FireConditions {
    private FireConditions() {}

    /** Fire comes from the breath: no firebending with your head underwater. */
    public static boolean canBend(ServerPlayer player) {
        return !player.isUnderWater();
    }

    public static float power(ServerPlayer player) {
        Level level = player.level();
        float m = 1f;
        if (level.dimension() == Level.NETHER) {
            m *= 1.3f;
        } else if (!level.dimensionType().hasFixedTime()) {
            m *= level.isDay() ? 1.2f : 0.85f;
        }
        if (isInRain(player)) {
            m *= 0.75f;
        }
        return m;
    }

    public static float regen(ServerPlayer player) {
        Level level = player.level();
        float m = 1f;
        if (level.dimension() == Level.NETHER) {
            m *= 1.5f;
        } else if (!level.dimensionType().hasFixedTime() && level.isDay()) {
            m *= 1.25f;
        }
        if (isInRain(player)) {
            m *= 0.8f;
        }
        return m;
    }

    private static boolean isInRain(ServerPlayer player) {
        return player.level().isRainingAt(player.blockPosition().above());
    }
}
