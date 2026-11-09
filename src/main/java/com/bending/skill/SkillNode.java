package com.bending.skill;

import java.util.List;

import javax.annotation.Nullable;

import com.bending.BendingMod;
import com.bending.ability.Ability;

import net.minecraft.network.chat.Component;

/**
 * One node of the radial skill tree.
 *
 * @param id       what gets stored in your unlocks: the ability's id, or {@code bending:<node>}.
 *                 Extra ranks are stored as {@code id#2}, {@code id#3}...
 * @param branch   which branch it belongs to (CORE for the centre and the body passives)
 * @param angle    where it sits around the centre, in degrees (-90 = straight up)
 * @param ring     how far out from the centre (0 = the centre)
 * @param cost     skill points per rank
 * @param maxRank  how many times it can be learned
 * @param requires learn any one of these first (empty = only the centre)
 * @param group    exclusive choice: learning one node of a group locks out the others (null = none)
 * @param ability  the ability it unlocks, for an ability node
 * @param effects  what each rank changes
 */
public record SkillNode(String id, SkillBranch branch, double angle, double ring, int cost, int maxRank,
                        List<String> requires, @Nullable String group, Kind kind, @Nullable Ability ability,
                        List<Effect> effects) {

    public enum Kind { CORE, ABILITY, TECHNIQUE, UPGRADE, PASSIVE, CHOICE }

    /**
     * One change a rank makes.
     * @param ability the ability it applies to, or null for all your bending / your body
     */
    public record Effect(@Nullable String ability, SkillStat stat, float perRank) {}

    private String path() {
        return id.substring(id.indexOf(':') + 1);
    }

    public Component name() {
        return ability != null ? ability.displayName() : Component.translatable("skill." + BendingMod.MODID + "." + path());
    }

    /** Flavour / explanation text; empty for plain upgrades (their effects say it all). */
    public Component description() {
        return ability != null ? ability.description()
                : Component.translatableWithFallback("skill." + BendingMod.MODID + "." + path() + ".desc", "");
    }

    /** A master technique rather than an ability. */
    public boolean technique() {
        return kind == Kind.TECHNIQUE;
    }

    /** A passive bonus of some kind (upgrade, body passive or choice). */
    public boolean stat() {
        return kind == Kind.UPGRADE || kind == Kind.PASSIVE || kind == Kind.CHOICE;
    }

    /** The unlock id for a rank (1-based). */
    public String rankId(int rank) {
        return rank <= 1 ? id : id + "#" + rank;
    }

    /** Kept for older callers: nodes are always buildable now. */
    public boolean ready() {
        return true;
    }
}
