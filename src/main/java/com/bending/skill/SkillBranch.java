package com.bending.skill;

import com.bending.BendingMod;

import net.minecraft.network.chat.Component;

/**
 * The branches of an element's skill tree, growing out from the centre. CORE is the centre itself
 * and the body passives (health, chi, resistance, speed) that sit between the branches.
 */
public enum SkillBranch {
    AGGRESSION(0xFF4A2A, -90),
    BREATH(0xFFB43A, 30),
    DISCIPLINE(0x8FC8FF, 150),
    CORE(0xF2E2C0, 0);

    private final int color;
    private final double angle;

    SkillBranch(int color, double angle) {
        this.color = color;
        this.angle = angle;
    }

    public int color() {
        return color;
    }

    /** The direction the branch grows in, in degrees (-90 = straight up). */
    public double angle() {
        return angle;
    }

    public Component displayName() {
        return Component.translatable("skill_branch." + BendingMod.MODID + "." + name().toLowerCase());
    }

    /** A few words on what the branch is about. */
    public Component theme() {
        return Component.translatable("skill_branch." + BendingMod.MODID + "." + name().toLowerCase() + ".theme");
    }
}
