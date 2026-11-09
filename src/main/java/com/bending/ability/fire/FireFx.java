package com.bending.ability.fire;

import javax.annotation.Nullable;

import com.bending.particle.ModParticles;

import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.world.entity.Entity;

/**
 * Picks the fire particle for a bender: the normal orange-red flame, or the blue one if they know
 * Blue Fire. Pass the bender (server side), or a synced flag (client-side entity effects).
 */
public final class FireFx {
    private FireFx() {}

    public static SimpleParticleType wisp(boolean blue) {
        return (blue ? ModParticles.BLUE_FIRE_WISP : ModParticles.FIRE_WISP).get();
    }

    public static SimpleParticleType core(boolean blue) {
        return (blue ? ModParticles.BLUE_FIRE_CORE : ModParticles.FIRE_CORE).get();
    }

    public static SimpleParticleType spark(boolean blue) {
        return (blue ? ModParticles.BLUE_FIRE_SPARK : ModParticles.FIRE_SPARK).get();
    }

    public static SimpleParticleType ember(boolean blue) {
        return (blue ? ModParticles.BLUE_EMBER : ModParticles.EMBER).get();
    }

    /** Big billowing fire puff (see {@link ModParticles#FIRE_CLOUD}). */
    public static SimpleParticleType cloud(boolean blue) {
        return (blue ? ModParticles.BLUE_FIRE_CLOUD : ModParticles.FIRE_CLOUD).get();
    }

    public static SimpleParticleType cloud(@Nullable Entity bender) {
        return cloud(FireTechniques.blue(bender));
    }

    public static SimpleParticleType wisp(@Nullable Entity bender) {
        return wisp(FireTechniques.blue(bender));
    }

    public static SimpleParticleType core(@Nullable Entity bender) {
        return core(FireTechniques.blue(bender));
    }

    public static SimpleParticleType spark(@Nullable Entity bender) {
        return spark(FireTechniques.blue(bender));
    }

    public static SimpleParticleType ember(@Nullable Entity bender) {
        return ember(FireTechniques.blue(bender));
    }
}
