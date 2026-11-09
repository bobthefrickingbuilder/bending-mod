package com.bending.skill;

import com.bending.BendingMod;

import net.minecraft.network.chat.Component;

/** The three branches of an element's skill tree. Names and colors are per element. */
public enum SkillBranch {
    AGGRESSION(0xFF4A2A),
    BREATH(0xFFB43A),
    DISCIPLINE(0x8FC8FF);

    private final int color;

    SkillBranch(int color) {
        this.color = color;
    }

    public int color() {
        return color;
    }

    public Component displayName() {
        return Component.translatable("skill_branch." + BendingMod.MODID + "." + name().toLowerCase());
    }

    /** A few words on what the branch is about. */
    public Component theme() {
        return Component.translatable("skill_branch." + BendingMod.MODID + "." + name().toLowerCase() + ".theme");
    }
}
