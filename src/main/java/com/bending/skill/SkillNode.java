package com.bending.skill;

import javax.annotation.Nullable;

import com.bending.BendingMod;
import com.bending.ability.Ability;

import net.minecraft.network.chat.Component;

/**
 * One node of a skill tree. It either unlocks an ability (which you can then equip in the loadout
 * screen) or a master technique. Nodes that aren't built yet are shown as "coming soon".
 *
 * @param id       what gets stored in your unlocks: the ability's id, or {@code bending:<technique>}
 * @param tier     row in its branch, 0 at the top
 * @param cost     skill points to learn it
 * @param requires the node you must learn first, or null for a branch's first node
 * @param ability  the ability it unlocks, or null for a technique or an unbuilt ability
 * @param ready    false = coming soon, can't be learned yet
 */
public record SkillNode(String id, SkillBranch branch, int tier, int cost, @Nullable String requires,
                        @Nullable Ability ability, boolean ready) {

    private String path() {
        return id.substring(id.indexOf(':') + 1);
    }

    public Component name() {
        return ability != null ? ability.displayName() : Component.translatable("skill." + BendingMod.MODID + "." + path());
    }

    public Component description() {
        return ability != null ? ability.description()
                : Component.translatable("skill." + BendingMod.MODID + "." + path() + ".desc");
    }

    /** A master technique rather than an ability. */
    public boolean technique() {
        return SkillTree.TECHNIQUES.contains(id);
    }
}
