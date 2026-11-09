package com.bending.ability.fire;

import javax.annotation.Nullable;

import com.bending.BendingMod;
import com.bending.data.BendingData;
import com.bending.data.ModAttachments;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/**
 * Firebending master techniques, learned from the skill tree. They don't take a slot; they change
 * how your bending works. Each one is stored in your unlocks under its id.
 */
public final class FireTechniques {
    /** All your fire burns blue: +25% damage and longer burns. */
    public static final String BLUE_FIRE = BendingMod.MODID + ":blue_fire";
    /** Out of chi? Keep bending by burning hunger instead (1 hunger per 10 chi). */
    public static final String BREATH_OF_FIRE = BendingMod.MODID + ":breath_of_fire";
    /** +50% chi regen by day, and back-to-back hits build a damage combo. */
    public static final String DANCING_DRAGON = BendingMod.MODID + ":dancing_dragon";
    /** Holding Fire Shield catches incoming lightning and fires it back where you look. */
    public static final String LIGHTNING_REDIRECTION = BendingMod.MODID + ":lightning_redirection";

    /** Blue Fire's damage bonus. */
    public static final float BLUE_FIRE_POWER = 1.25f;
    /** Blue Fire's burn-time bonus. */
    public static final float BLUE_FIRE_BURN = 1.5f;

    private FireTechniques() {}

    public static boolean has(BendingData data, String technique) {
        return data.isUnlocked(technique);
    }

    /** Server side: whether this entity is a player who knows the technique. */
    public static boolean has(@Nullable Entity entity, String technique) {
        return entity instanceof Player player && player.getData(ModAttachments.BENDING).isUnlocked(technique);
    }

    public static boolean blue(@Nullable Entity bender) {
        return has(bender, BLUE_FIRE);
    }
}
