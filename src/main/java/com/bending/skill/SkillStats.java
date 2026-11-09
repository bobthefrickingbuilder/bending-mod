package com.bending.skill;

import java.util.Set;

import com.bending.BendingMod;
import com.bending.data.BendingData;

/**
 * Passive stat nodes from the skill tree. Each one is learned like any other node (stored in your
 * unlocks) and is always on. Gems will add to the same numbers later.
 */
public final class SkillStats {
    private static final String M = BendingMod.MODID + ":";

    /** Aggression: +15% damage. */
    public static final String SEARING_HEAT = M + "searing_heat";
    /** Aggression: +10% damage and burns last 50% longer. */
    public static final String WILDFIRE = M + "wildfire";
    /** Breath: +30 max chi. */
    public static final String DEEP_LUNGS = M + "deep_lungs";
    /** Breath: +30% chi regen. */
    public static final String STEADY_BREATH = M + "steady_breath";
    /** Discipline: abilities cost 15% less chi. */
    public static final String FOCUS = M + "focus";
    /** Discipline: a further 10% off chi costs, and chi starts coming back twice as soon after a cast. */
    public static final String INNER_CALM = M + "inner_calm";

    static final Set<String> ALL = Set.of(SEARING_HEAT, WILDFIRE, DEEP_LUNGS, STEADY_BREATH, FOCUS, INNER_CALM);

    private SkillStats() {}

    private static boolean has(BendingData data, String id) {
        return data.isUnlocked(id);
    }

    public static float damageMultiplier(BendingData data) {
        return 1f + (has(data, SEARING_HEAT) ? 0.15f : 0f) + (has(data, WILDFIRE) ? 0.10f : 0f);
    }

    public static float burnMultiplier(BendingData data) {
        return has(data, WILDFIRE) ? 1.5f : 1f;
    }

    public static float maxChiBonus(BendingData data) {
        return has(data, DEEP_LUNGS) ? 30f : 0f;
    }

    public static float regenMultiplier(BendingData data) {
        return has(data, STEADY_BREATH) ? 1.3f : 1f;
    }

    public static float chiCostMultiplier(BendingData data) {
        return (has(data, FOCUS) ? 0.85f : 1f) * (has(data, INNER_CALM) ? 0.9f : 1f);
    }

    /** Ticks after a cast before chi starts regenerating. */
    public static int regenDelay(BendingData data, int base) {
        return has(data, INNER_CALM) ? base / 2 : base;
    }
}
