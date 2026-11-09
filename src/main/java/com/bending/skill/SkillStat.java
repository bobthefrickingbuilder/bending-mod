package com.bending.skill;

import java.util.Locale;

import com.bending.BendingMod;

import net.minecraft.network.chat.Component;

/**
 * What a skill node changes. Percent stats add up into a multiplier (+10% damage three times = x1.3);
 * flat stats add up as plain numbers.
 */
public enum SkillStat {
    // Per ability (or every ability, for a global node).
    DAMAGE(true),
    RANGE(true),
    SIZE(true),
    SPEED(true),
    /** Negative = charges faster. */
    CHARGE_TIME(true),
    /** Negative = cheaper. */
    CHI_COST(true),
    /** How long it lasts or can be held. */
    DURATION(true),
    /** Lightning: extra chain targets. */
    CHAIN(false),
    /** Lightning: extra stored bolts. */
    BOLTS(false),
    // Body.
    MAX_HEALTH(false),
    MAX_CHI(false),
    REGEN(true),
    /** Negative = chi starts coming back sooner after a cast. */
    REGEN_DELAY(true),
    RESISTANCE(true),
    MOVE_SPEED(true),
    BURN(true);

    private final boolean percent;

    SkillStat(boolean percent) {
        this.percent = percent;
    }

    public boolean percent() {
        return percent;
    }

    /** "+8% damage", "-12% charge time", "+15 max chi", "+1 heart". */
    public Component describe(float amount) {
        String sign = amount >= 0 ? "+" : "-";
        float abs = Math.abs(amount);
        String value;
        if (this == MAX_HEALTH) {
            float hearts = abs / 2f;
            value = sign + (hearts == Math.round(hearts) ? String.valueOf(Math.round(hearts)) : String.valueOf(hearts));
        } else if (percent) {
            value = sign + Math.round(abs * 100) + "%";
        } else {
            value = sign + (abs == Math.round(abs) ? String.valueOf(Math.round(abs)) : String.valueOf(abs));
        }
        return Component.translatable("skill_stat." + BendingMod.MODID + "." + name().toLowerCase(Locale.ROOT), value);
    }
}
