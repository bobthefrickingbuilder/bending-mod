package com.bending.sound;

import com.bending.BendingMod;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Named sound hooks. Code plays these; what each one actually sounds like is decided in
 * assets/bending/sounds.json. They currently point at vanilla sounds. To use custom audio,
 * drop .ogg files into assets/bending/sounds/ and edit sounds.json; no code changes needed.
 */
public final class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, BendingMod.MODID);

    // ---- Shared fire sounds ----
    public static final DeferredHolder<SoundEvent, SoundEvent> FIRE_EXPLOSION = register("fire.explosion");
    public static final DeferredHolder<SoundEvent, SoundEvent> FIRE_MELT = register("fire.melt");
    public static final DeferredHolder<SoundEvent, SoundEvent> FIRE_FIZZLE = register("fire.fizzle");
    public static final DeferredHolder<SoundEvent, SoundEvent> FIRE_HOP = register("fire_hop");

    // ---- Abilities ----
    public static final DeferredHolder<SoundEvent, SoundEvent> FIRE_BLAST_CAST = register("fire_blast.cast");

    public static final DeferredHolder<SoundEvent, SoundEvent> FIRE_SHIELD_LOOP = register("fire_shield.loop");
    public static final DeferredHolder<SoundEvent, SoundEvent> FIRE_SHIELD_BLOCK = register("fire_shield.block");
    public static final DeferredHolder<SoundEvent, SoundEvent> FIRE_SHIELD_DEFLECT = register("fire_shield.deflect");
    public static final DeferredHolder<SoundEvent, SoundEvent> FLAME_BURST = register("flame_burst");

    public static final DeferredHolder<SoundEvent, SoundEvent> FIRE_JET_DASH_LAUNCH = register("fire_jet_dash.launch");
    public static final DeferredHolder<SoundEvent, SoundEvent> FIRE_JET_DASH_THRUST = register("fire_jet_dash.thrust");

    public static final DeferredHolder<SoundEvent, SoundEvent> FIRESTORM_CHARGE = register("firestorm.charge");
    public static final DeferredHolder<SoundEvent, SoundEvent> FIRESTORM_ERUPT = register("firestorm.erupt");

    public static final DeferredHolder<SoundEvent, SoundEvent> COMBUSTION_BEAM_CHARGE = register("combustion_beam.charge");
    public static final DeferredHolder<SoundEvent, SoundEvent> COMBUSTION_BEAM_READY = register("combustion_beam.ready");
    public static final DeferredHolder<SoundEvent, SoundEvent> COMBUSTION_BEAM_FIRE = register("combustion_beam.fire");

    // ---- Phase 3 abilities ----
    public static final DeferredHolder<SoundEvent, SoundEvent> FIRE_ARC_CAST = register("fire_arc.cast");
    public static final DeferredHolder<SoundEvent, SoundEvent> FLAME_PARRY_READY = register("flame_parry.ready");
    public static final DeferredHolder<SoundEvent, SoundEvent> FLAME_PARRY_COUNTER = register("flame_parry.counter");
    public static final DeferredHolder<SoundEvent, SoundEvent> JET_PROPULSION_THRUST = register("jet_propulsion.thrust");
    public static final DeferredHolder<SoundEvent, SoundEvent> LIGHTNING_CHARGE = register("lightning.charge");
    public static final DeferredHolder<SoundEvent, SoundEvent> LIGHTNING_READY = register("lightning.ready");
    public static final DeferredHolder<SoundEvent, SoundEvent> LIGHTNING_STRIKE = register("lightning.strike");

    // ---- Bending mode ----
    public static final DeferredHolder<SoundEvent, SoundEvent> BENDING_MODE_ON = register("bending_mode.on");
    public static final DeferredHolder<SoundEvent, SoundEvent> BENDING_MODE_OFF = register("bending_mode.off");

    private ModSounds() {}

    private static DeferredHolder<SoundEvent, SoundEvent> register(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(
                ResourceLocation.fromNamespaceAndPath(BendingMod.MODID, name)));
    }
}
