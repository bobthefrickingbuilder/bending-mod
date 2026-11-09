package com.bending.particle;

import com.bending.BendingMod;

import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Named particle hooks. Abilities spawn these instead of vanilla particles.
 * Their textures are set in assets/bending/particles/<name>.json (vanilla textures for now),
 * and their behaviour (movement, lifetime, glow) by the provider registered in BendingModClient.
 * Swapping in custom art means new textures plus, at most, a new provider class.
 */
public final class ModParticles {
    public static final DeferredRegister<ParticleType<?>> PARTICLES = DeferredRegister.create(Registries.PARTICLE_TYPE, BendingMod.MODID);

    /** The main body of fire: blasts, jets, rings. */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> FIRE_WISP = register("fire_wisp");
    /** Small flickers: charge-ups, focus points. */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> FIRE_SPARK = register("fire_spark");
    /** Popping molten embers. */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> EMBER = register("ember");
    /** Smoke from fire: trails, fizzles, burned projectiles. */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> FIRE_SMOKE = register("fire_smoke");
    /** Steam from melting snow and ice. */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> STEAM = register("steam");

    private ModParticles() {}

    private static DeferredHolder<ParticleType<?>, SimpleParticleType> register(String name) {
        // false = obeys the player's particle setting (not forced to render at long range).
        return PARTICLES.register(name, () -> new SimpleParticleType(false));
    }
}
