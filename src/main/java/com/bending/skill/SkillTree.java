package com.bending.skill;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import com.bending.BendingMod;
import com.bending.ability.Abilities;
import com.bending.ability.Ability;
import com.bending.ability.fire.FireTechniques;
import com.bending.data.BendingData;
import com.bending.data.Element;

/**
 * Each element's skill tree. The tree decides what you can do: every node unlocks an ability for
 * your loadout or a master technique (stats will come from gems). Bending XP comes from landing
 * abilities, and every level gives one skill point.
 *
 * Firebending:
 * <pre>
 *  AGGRESSION                 BREATH                       DISCIPLINE
 *  Fire Arc (1)               Jet Propulsion (1)           Flame Parry (1)
 *   ├ Combustion Beam (2)      ├ Blazing Leap (2)           ├ Wall of Fire (2)
 *   └ Fireball Charge (2)      └ Breath of Fire (3)*        └ Lightning (3)
 *  Combustion Beam:           Breath of Fire:              Lightning:
 *   ├ Dragon's Breath (3)      └ Dancing Dragon (4)*        └ Lightning Redirection (4)*
 *   └ Blue Fire (4)*
 * </pre>
 * (* = master technique.) The starting kit (Fire Shield, Firestorm and the innate abilities) is always known.
 */
public final class SkillTree {
    private static final String M = BendingMod.MODID + ":";

    /** Master techniques: they change abilities you already have instead of taking a slot. */
    static final Set<String> TECHNIQUES = Set.of(FireTechniques.BLUE_FIRE, FireTechniques.BREATH_OF_FIRE,
            FireTechniques.DANCING_DRAGON, FireTechniques.LIGHTNING_REDIRECTION);

    private static final List<SkillNode> FIRE = List.of(
            // Aggression: raw destructive power.
            node(Abilities.FIRE_ARC, SkillBranch.AGGRESSION, 0, 1, null),
            node(Abilities.COMBUSTION_BEAM, SkillBranch.AGGRESSION, 1, 2, Abilities.FIRE_ARC.id()),
            node(Abilities.FIREBALL_CHARGE, SkillBranch.AGGRESSION, 1, 2, Abilities.FIRE_ARC.id()),
            node(Abilities.DRAGONS_BREATH, SkillBranch.AGGRESSION, 2, 3, Abilities.COMBUSTION_BEAM.id()),
            technique(FireTechniques.BLUE_FIRE, SkillBranch.AGGRESSION, 2, 4, Abilities.COMBUSTION_BEAM.id()),
            // Breath: endurance and movement.
            node(Abilities.JET_PROPULSION, SkillBranch.BREATH, 0, 1, null),
            node(Abilities.BLAZING_LEAP, SkillBranch.BREATH, 1, 2, Abilities.JET_PROPULSION.id()),
            technique(FireTechniques.BREATH_OF_FIRE, SkillBranch.BREATH, 1, 3, Abilities.JET_PROPULSION.id()),
            technique(FireTechniques.DANCING_DRAGON, SkillBranch.BREATH, 2, 4, FireTechniques.BREATH_OF_FIRE),
            // Discipline: precision and control.
            node(Abilities.FLAME_PARRY, SkillBranch.DISCIPLINE, 0, 1, null),
            node(Abilities.WALL_OF_FIRE, SkillBranch.DISCIPLINE, 1, 2, Abilities.FLAME_PARRY.id()),
            node(Abilities.LIGHTNING, SkillBranch.DISCIPLINE, 1, 3, Abilities.FLAME_PARRY.id()),
            technique(FireTechniques.LIGHTNING_REDIRECTION, SkillBranch.DISCIPLINE, 2, 4, Abilities.LIGHTNING.id()));

    private static final Map<String, SkillNode> BY_ID = FIRE.stream()
            .collect(Collectors.toMap(SkillNode::id, Function.identity()));

    private SkillTree() {}

    private static SkillNode node(Ability ability, SkillBranch branch, int tier, int cost, @Nullable String requires) {
        return new SkillNode(ability.id(), branch, tier, cost, requires, ability, true);
    }

    /** A master technique: changes how your bending works instead of taking a slot. */
    private static SkillNode technique(String id, SkillBranch branch, int tier, int cost, @Nullable String requires) {
        return new SkillNode(id, branch, tier, cost, requires, null, true);
    }

    /** A node that isn't built yet: shown, but it can't be learned. */
    @SuppressWarnings("unused")
    private static SkillNode soon(String path, SkillBranch branch, int tier, int cost, @Nullable String requires) {
        return new SkillNode(M + path, branch, tier, cost, requires, null, false);
    }

    /** The element's nodes, branch by branch, top to bottom. Empty if its tree isn't built. */
    public static List<SkillNode> of(@Nullable Element element) {
        return element == Element.FIRE ? FIRE : List.of();
    }

    @Nullable
    public static SkillNode get(String id) {
        return BY_ID.get(id);
    }

    public enum State { LEARNED, AVAILABLE, NEED_POINTS, NEED_PREVIOUS, COMING_SOON }

    /** Where a node stands for this bender. {@code free} = creative: points don't matter. */
    public static State state(SkillNode node, BendingData data, boolean free) {
        if (data.isUnlocked(node.id())) {
            return State.LEARNED;
        }
        if (!node.ready()) {
            return State.COMING_SOON;
        }
        if (node.requires() != null && !data.isUnlocked(node.requires())) {
            return State.NEED_PREVIOUS;
        }
        return free || data.getSkillPoints() >= node.cost() ? State.AVAILABLE : State.NEED_POINTS;
    }
}
