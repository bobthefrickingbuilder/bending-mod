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
    /** The hot, round head of a fireball or beam: short-lived and nearly white. */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> FIRE_CORE = register("fire_core");
    /** Small flickers: charge-ups, focus points. */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> FIRE_SPARK = register("fire_spark");
    /** Popping molten embers. */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> EMBER = register("ember");
    /** Smoke from fire: trails, fizzles, burned projectiles. */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> FIRE_SMOKE = register("fire_smoke");
    /** Crackling blue-white sparks: lightning bolts and their charge-up. */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> LIGHTNING_ARC = register("lightning_arc");
    /** Big billowing fireball puffs that swell as they drift: breath streams, walls, eruptions. */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> FIRE_CLOUD = register("fire_cloud");
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> BLUE_FIRE_CLOUD = register("blue_fire_cloud");
    /** Blue Fire versions of the four fire particles (master technique). */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> BLUE_FIRE_WISP = register("blue_fire_wisp");
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> BLUE_FIRE_CORE = register("blue_fire_core");
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> BLUE_FIRE_SPARK = register("blue_fire_spark");
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> BLUE_EMBER = register("blue_ember");
    /** Tiny glowing motes that drift up off a firebender's hands while in bending mode. */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> HAND_EMBER = register("hand_ember");
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> BLUE_HAND_EMBER = register("blue_hand_ember");
    /** Steam from melting snow and ice. */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> STEAM = register("steam");
    /** Faint heat shimmer rising off strong flames. */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> HEAT_HAZE = register("heat_haze");

    private ModParticles() {}

    private static DeferredHolder<ParticleType<?>, SimpleParticleType> register(String name) {
        // false = obeys the player's particle setting (not forced to render at long range).
        return PARTICLES.register(name, () -> new SimpleParticleType(false));
    }
}
