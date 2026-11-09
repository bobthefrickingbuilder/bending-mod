package com.bending.skill;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import com.bending.BendingMod;
import com.bending.ability.Abilities;
import com.bending.ability.fire.FireTechniques;
import com.bending.data.BendingData;
import com.bending.data.Element;
import com.bending.skill.SkillNode.Kind;

/**
 * Each element's skill tree: a radial tree growing out from a centre node (Inner Fire, always known).
 * Three branches reach outward (Aggression up, Breath lower right, Discipline lower left) with
 * abilities, master techniques and ranked upgrades for each ability (damage, range, size, speed,
 * charge time, chi cost, duration). Between the branches sit body passives: health, resistance,
 * max chi, chi regen and move speed. Some nodes are exclusive choices: learning one locks out its
 * partner. Every bending level gives one skill point.
 *
 * <p>The node list below is generated from a layout spec (positions are checked so nothing overlaps).
 */
public final class SkillTree {
    private static final String M = BendingMod.MODID + ":";
    /** The centre of the tree. Always learned. */
    public static final String ROOT = M + "inner_fire";

    private static final List<SkillNode> FIRE = List.of(
            new SkillNode(M + "inner_fire", SkillBranch.CORE, 0, 0, 0, 1, List.of(), null, Kind.CORE, null, List.of()),
            new SkillNode(M + "vitality", SkillBranch.CORE, -30, 1.25, 1, 3, List.of(M + "inner_fire"), null, Kind.PASSIVE, null, List.of(fx(null, SkillStat.MAX_HEALTH, 2f))),
            new SkillNode(M + "iron_body", SkillBranch.CORE, -30, 2.3, 1, 3, List.of(M + "vitality"), null, Kind.PASSIVE, null, List.of(fx(null, SkillStat.RESISTANCE, 0.05f))),
            new SkillNode(M + "deep_lungs", SkillBranch.CORE, 90, 1.25, 1, 3, List.of(M + "inner_fire"), null, Kind.PASSIVE, null, List.of(fx(null, SkillStat.MAX_CHI, 15f))),
            new SkillNode(M + "steady_breath", SkillBranch.CORE, 90, 2.3, 1, 3, List.of(M + "deep_lungs"), null, Kind.PASSIVE, null, List.of(fx(null, SkillStat.REGEN, 0.12f))),
            new SkillNode(M + "fleet_foot", SkillBranch.CORE, -150, 1.25, 1, 3, List.of(M + "inner_fire"), null, Kind.PASSIVE, null, List.of(fx(null, SkillStat.MOVE_SPEED, 0.04f))),
            new SkillNode(M + "inner_calm", SkillBranch.CORE, -150, 2.3, 1, 2, List.of(M + "fleet_foot"), null, Kind.PASSIVE, null, List.of(fx(null, SkillStat.REGEN_DELAY, -0.25f))),
            new SkillNode(Abilities.FIRE_ARC.id(), SkillBranch.AGGRESSION, -90, 1.4, 1, 1, List.of(M + "inner_fire"), null, Kind.ABILITY, Abilities.FIRE_ARC, List.of()),
            new SkillNode(M + "blast_power", SkillBranch.AGGRESSION, -126, 1.6, 1, 3, List.of(M + "inner_fire"), null, Kind.UPGRADE, null, List.of(fx(Abilities.FIRE_BLAST.id(), SkillStat.DAMAGE, 0.08f))),
            new SkillNode(M + "blast_velocity", SkillBranch.AGGRESSION, -134, 2.25, 1, 3, List.of(M + "blast_power"), null, Kind.UPGRADE, null, List.of(fx(Abilities.FIRE_BLAST.id(), SkillStat.SPEED, 0.12f))),
            new SkillNode(M + "heavy_shot", SkillBranch.AGGRESSION, -142, 3.2, 2, 1, List.of(M + "blast_velocity"), "blast_style", Kind.CHOICE, null, List.of(fx(Abilities.FIRE_BLAST.id(), SkillStat.DAMAGE, 0.3f), fx(Abilities.FIRE_BLAST.id(), SkillStat.SPEED, -0.15f))),
            new SkillNode(M + "rapid_shot", SkillBranch.AGGRESSION, -130, 3.2, 2, 1, List.of(M + "blast_velocity"), "blast_style", Kind.CHOICE, null, List.of(fx(Abilities.FIRE_BLAST.id(), SkillStat.CHI_COST, -0.4f))),
            new SkillNode(M + "arc_reach", SkillBranch.AGGRESSION, -104, 2.3, 1, 3, List.of(Abilities.FIRE_ARC.id()), null, Kind.UPGRADE, null, List.of(fx(Abilities.FIRE_ARC.id(), SkillStat.RANGE, 0.15f))),
            new SkillNode(M + "arc_power", SkillBranch.AGGRESSION, -76, 2.3, 1, 3, List.of(Abilities.FIRE_ARC.id()), null, Kind.UPGRADE, null, List.of(fx(Abilities.FIRE_ARC.id(), SkillStat.DAMAGE, 0.1f))),
            new SkillNode(Abilities.COMBUSTION_BEAM.id(), SkillBranch.AGGRESSION, -112, 3.3, 2, 1, List.of(Abilities.FIRE_ARC.id()), null, Kind.ABILITY, Abilities.COMBUSTION_BEAM, List.of()),
            new SkillNode(Abilities.FIREBALL_CHARGE.id(), SkillBranch.AGGRESSION, -68, 3.3, 2, 1, List.of(Abilities.FIRE_ARC.id()), null, Kind.ABILITY, Abilities.FIREBALL_CHARGE, List.of()),
            new SkillNode(M + "beam_focus", SkillBranch.AGGRESSION, -122, 4.25, 1, 3, List.of(Abilities.COMBUSTION_BEAM.id()), null, Kind.UPGRADE, null, List.of(fx(Abilities.COMBUSTION_BEAM.id(), SkillStat.CHARGE_TIME, -0.12f))),
            new SkillNode(M + "beam_power", SkillBranch.AGGRESSION, -110, 4.3, 1, 3, List.of(Abilities.COMBUSTION_BEAM.id()), null, Kind.UPGRADE, null, List.of(fx(Abilities.COMBUSTION_BEAM.id(), SkillStat.DAMAGE, 0.1f))),
            new SkillNode(M + "beam_range", SkillBranch.AGGRESSION, -126, 5.2, 1, 2, List.of(M + "beam_focus"), null, Kind.UPGRADE, null, List.of(fx(Abilities.COMBUSTION_BEAM.id(), SkillStat.RANGE, 0.2f))),
            new SkillNode(M + "fireball_quick", SkillBranch.AGGRESSION, -70, 4.3, 1, 3, List.of(Abilities.FIREBALL_CHARGE.id()), null, Kind.UPGRADE, null, List.of(fx(Abilities.FIREBALL_CHARGE.id(), SkillStat.CHARGE_TIME, -0.12f))),
            new SkillNode(M + "fireball_size", SkillBranch.AGGRESSION, -58, 4.25, 1, 3, List.of(Abilities.FIREBALL_CHARGE.id()), null, Kind.UPGRADE, null, List.of(fx(Abilities.FIREBALL_CHARGE.id(), SkillStat.SIZE, 0.15f))),
            new SkillNode(M + "fireball_power", SkillBranch.AGGRESSION, -54, 5.2, 1, 3, List.of(M + "fireball_size"), null, Kind.UPGRADE, null, List.of(fx(Abilities.FIREBALL_CHARGE.id(), SkillStat.DAMAGE, 0.1f))),
            new SkillNode(Abilities.DRAGONS_BREATH.id(), SkillBranch.AGGRESSION, -99, 5.1, 3, 1, List.of(M + "beam_power"), null, Kind.ABILITY, Abilities.DRAGONS_BREATH, List.of()),
            new SkillNode(FireTechniques.BLUE_FIRE, SkillBranch.AGGRESSION, -81, 5.1, 4, 1, List.of(M + "fireball_quick", M + "beam_power"), null, Kind.TECHNIQUE, null, List.of()),
            new SkillNode(M + "breath_range", SkillBranch.AGGRESSION, -112, 6.1, 1, 3, List.of(Abilities.DRAGONS_BREATH.id()), null, Kind.UPGRADE, null, List.of(fx(Abilities.DRAGONS_BREATH.id(), SkillStat.RANGE, 0.15f))),
            new SkillNode(M + "breath_power", SkillBranch.AGGRESSION, -101, 6.2, 1, 3, List.of(Abilities.DRAGONS_BREATH.id()), null, Kind.UPGRADE, null, List.of(fx(Abilities.DRAGONS_BREATH.id(), SkillStat.DAMAGE, 0.1f))),
            new SkillNode(M + "breath_efficiency", SkillBranch.AGGRESSION, -122, 6.05, 1, 3, List.of(Abilities.DRAGONS_BREATH.id()), null, Kind.UPGRADE, null, List.of(fx(Abilities.DRAGONS_BREATH.id(), SkillStat.CHI_COST, -0.1f))),
            new SkillNode(M + "wide_maw", SkillBranch.AGGRESSION, -114, 7.05, 2, 1, List.of(M + "breath_range"), "breath_style", Kind.CHOICE, null, List.of(fx(Abilities.DRAGONS_BREATH.id(), SkillStat.SIZE, 0.35f))),
            new SkillNode(M + "long_breath", SkillBranch.AGGRESSION, -104, 7.1, 2, 1, List.of(M + "breath_range"), "breath_style", Kind.CHOICE, null, List.of(fx(Abilities.DRAGONS_BREATH.id(), SkillStat.RANGE, 0.4f))),
            new SkillNode(M + "searing_heat", SkillBranch.AGGRESSION, -85, 6.2, 1, 3, List.of(FireTechniques.BLUE_FIRE, Abilities.DRAGONS_BREATH.id()), null, Kind.UPGRADE, null, List.of(fx(null, SkillStat.DAMAGE, 0.05f))),
            new SkillNode(M + "wildfire", SkillBranch.AGGRESSION, -76, 6.15, 3, 1, List.of(FireTechniques.BLUE_FIRE), null, Kind.UPGRADE, null, List.of(fx(null, SkillStat.DAMAGE, 0.1f), fx(null, SkillStat.BURN, 0.5f))),
            new SkillNode(M + "dash_speed", SkillBranch.BREATH, 20, 1.35, 1, 3, List.of(M + "inner_fire"), null, Kind.UPGRADE, null, List.of(fx(Abilities.FIRE_JET_DASH.id(), SkillStat.SPEED, 0.1f))),
            new SkillNode(Abilities.JET_PROPULSION.id(), SkillBranch.BREATH, 54, 1.45, 1, 1, List.of(M + "inner_fire"), null, Kind.ABILITY, Abilities.JET_PROPULSION, List.of()),
            new SkillNode(M + "dash_efficiency", SkillBranch.BREATH, 10, 2.3, 1, 3, List.of(M + "dash_speed"), null, Kind.UPGRADE, null, List.of(fx(Abilities.FIRE_JET_DASH.id(), SkillStat.CHI_COST, -0.12f))),
            new SkillNode(M + "slam_impact", SkillBranch.BREATH, 27, 2.35, 1, 3, List.of(M + "dash_speed"), null, Kind.UPGRADE, null, List.of(fx(Abilities.METEOR_SLAM.id(), SkillStat.DAMAGE, 0.12f))),
            new SkillNode(M + "afterburner", SkillBranch.BREATH, -2, 3.2, 2, 1, List.of(M + "dash_efficiency"), "dash_style", Kind.CHOICE, null, List.of(fx(Abilities.FIRE_JET_DASH.id(), SkillStat.SPEED, 0.25f))),
            new SkillNode(M + "long_burn", SkillBranch.BREATH, 9, 3.25, 2, 1, List.of(M + "dash_efficiency"), "dash_style", Kind.CHOICE, null, List.of(fx(Abilities.FIRE_JET_DASH.id(), SkillStat.DURATION, 0.6f))),
            new SkillNode(M + "slam_crater", SkillBranch.BREATH, 24, 3.3, 1, 3, List.of(M + "slam_impact"), null, Kind.UPGRADE, null, List.of(fx(Abilities.METEOR_SLAM.id(), SkillStat.SIZE, 0.15f))),
            new SkillNode(M + "jet_speed", SkillBranch.BREATH, 68, 2.3, 1, 3, List.of(Abilities.JET_PROPULSION.id()), null, Kind.UPGRADE, null, List.of(fx(Abilities.JET_PROPULSION.id(), SkillStat.SPEED, 0.15f))),
            new SkillNode(Abilities.BLAZING_LEAP.id(), SkillBranch.BREATH, 48, 2.55, 2, 1, List.of(Abilities.JET_PROPULSION.id()), null, Kind.ABILITY, Abilities.BLAZING_LEAP, List.of()),
            new SkillNode(M + "jet_efficiency", SkillBranch.BREATH, 74, 3.25, 1, 3, List.of(M + "jet_speed"), null, Kind.UPGRADE, null, List.of(fx(Abilities.JET_PROPULSION.id(), SkillStat.CHI_COST, -0.12f))),
            new SkillNode(M + "leap_height", SkillBranch.BREATH, 46, 3.5, 1, 3, List.of(Abilities.BLAZING_LEAP.id()), null, Kind.UPGRADE, null, List.of(fx(Abilities.BLAZING_LEAP.id(), SkillStat.RANGE, 0.15f))),
            new SkillNode(M + "leap_quick", SkillBranch.BREATH, 60, 3.6, 1, 2, List.of(Abilities.BLAZING_LEAP.id()), null, Kind.UPGRADE, null, List.of(fx(Abilities.BLAZING_LEAP.id(), SkillStat.CHARGE_TIME, -0.15f))),
            new SkillNode(FireTechniques.BREATH_OF_FIRE, SkillBranch.BREATH, 33, 4.35, 3, 1, List.of(M + "slam_crater", Abilities.BLAZING_LEAP.id()), null, Kind.TECHNIQUE, null, List.of()),
            new SkillNode(FireTechniques.DANCING_DRAGON, SkillBranch.BREATH, 33, 5.45, 4, 1, List.of(FireTechniques.BREATH_OF_FIRE), null, Kind.TECHNIQUE, null, List.of()),
            new SkillNode(M + "storm_power", SkillBranch.BREATH, 52, 4.55, 1, 3, List.of(M + "leap_height"), null, Kind.UPGRADE, null, List.of(fx(Abilities.FIRESTORM.id(), SkillStat.DAMAGE, 0.1f))),
            new SkillNode(M + "storm_radius", SkillBranch.BREATH, 64, 4.6, 1, 3, List.of(M + "leap_quick"), null, Kind.UPGRADE, null, List.of(fx(Abilities.FIRESTORM.id(), SkillStat.SIZE, 0.12f))),
            new SkillNode(M + "storm_quick", SkillBranch.BREATH, 58, 5.5, 1, 2, List.of(M + "storm_power", M + "storm_radius"), null, Kind.UPGRADE, null, List.of(fx(Abilities.FIRESTORM.id(), SkillStat.CHARGE_TIME, -0.15f))),
            new SkillNode(Abilities.FLAME_PARRY.id(), SkillBranch.DISCIPLINE, 150, 1.4, 1, 1, List.of(M + "inner_fire"), null, Kind.ABILITY, Abilities.FLAME_PARRY, List.of()),
            new SkillNode(M + "shield_efficiency", SkillBranch.DISCIPLINE, 116, 1.4, 1, 3, List.of(M + "inner_fire"), null, Kind.UPGRADE, null, List.of(fx(Abilities.FIRE_SHIELD.id(), SkillStat.CHI_COST, -0.12f))),
            new SkillNode(M + "shield_endurance", SkillBranch.DISCIPLINE, 108, 2.3, 1, 2, List.of(M + "shield_efficiency"), null, Kind.UPGRADE, null, List.of(fx(Abilities.FIRE_SHIELD.id(), SkillStat.DURATION, 0.25f))),
            new SkillNode(M + "parry_counter", SkillBranch.DISCIPLINE, 134, 2.35, 1, 3, List.of(Abilities.FLAME_PARRY.id()), null, Kind.UPGRADE, null, List.of(fx(Abilities.FLAME_PARRY.id(), SkillStat.DAMAGE, 0.15f))),
            new SkillNode(M + "focus", SkillBranch.DISCIPLINE, 162, 2.3, 1, 3, List.of(Abilities.FLAME_PARRY.id()), null, Kind.UPGRADE, null, List.of(fx(null, SkillStat.CHI_COST, -0.05f))),
            new SkillNode(Abilities.WALL_OF_FIRE.id(), SkillBranch.DISCIPLINE, 126, 3.35, 2, 1, List.of(Abilities.FLAME_PARRY.id()), null, Kind.ABILITY, Abilities.WALL_OF_FIRE, List.of()),
            new SkillNode(Abilities.LIGHTNING.id(), SkillBranch.DISCIPLINE, 172, 3.35, 3, 1, List.of(Abilities.FLAME_PARRY.id()), null, Kind.ABILITY, Abilities.LIGHTNING, List.of()),
            new SkillNode(M + "wall_power", SkillBranch.DISCIPLINE, 130, 4.35, 1, 3, List.of(Abilities.WALL_OF_FIRE.id()), null, Kind.UPGRADE, null, List.of(fx(Abilities.WALL_OF_FIRE.id(), SkillStat.DAMAGE, 0.12f))),
            new SkillNode(M + "wall_duration", SkillBranch.DISCIPLINE, 116, 4.3, 1, 2, List.of(Abilities.WALL_OF_FIRE.id()), null, Kind.UPGRADE, null, List.of(fx(Abilities.WALL_OF_FIRE.id(), SkillStat.DURATION, 0.25f))),
            new SkillNode(M + "lightning_quick", SkillBranch.DISCIPLINE, 166, 4.35, 1, 3, List.of(Abilities.LIGHTNING.id()), null, Kind.UPGRADE, null, List.of(fx(Abilities.LIGHTNING.id(), SkillStat.CHARGE_TIME, -0.12f))),
            new SkillNode(M + "lightning_power", SkillBranch.DISCIPLINE, 180, 4.3, 1, 3, List.of(Abilities.LIGHTNING.id()), null, Kind.UPGRADE, null, List.of(fx(Abilities.LIGHTNING.id(), SkillStat.DAMAGE, 0.1f))),
            new SkillNode(FireTechniques.LIGHTNING_REDIRECTION, SkillBranch.DISCIPLINE, 172, 5.35, 4, 1, List.of(M + "lightning_quick"), null, Kind.TECHNIQUE, null, List.of()),
            new SkillNode(M + "forked", SkillBranch.DISCIPLINE, 182, 5.35, 2, 1, List.of(M + "lightning_power"), "lightning_style", Kind.CHOICE, null, List.of(fx(Abilities.LIGHTNING.id(), SkillStat.CHAIN, 2f))),
            new SkillNode(M + "overcharge", SkillBranch.DISCIPLINE, 192, 5.3, 2, 1, List.of(M + "lightning_power"), "lightning_style", Kind.CHOICE, null, List.of(fx(Abilities.LIGHTNING.id(), SkillStat.BOLTS, 2f))));

    private static final Map<String, SkillNode> BY_ID = FIRE.stream()
            .collect(Collectors.toMap(SkillNode::id, Function.identity()));

    private SkillTree() {}

    private static SkillNode.Effect fx(@Nullable String ability, SkillStat stat, float perRank) {
        return new SkillNode.Effect(ability, stat, perRank);
    }

    /** The element's nodes. Empty if its tree isn't built. */
    public static List<SkillNode> of(@Nullable Element element) {
        return element == Element.FIRE ? FIRE : List.of();
    }

    @Nullable
    public static SkillNode get(String id) {
        return BY_ID.get(id);
    }

    /** How many ranks of the node this bender has (the centre always counts as learned). */
    public static int rank(SkillNode node, BendingData data) {
        if (node.kind() == Kind.CORE) {
            return 1;
        }
        int rank = 0;
        while (rank < node.maxRank() && data.isUnlocked(node.rankId(rank + 1))) {
            rank++;
        }
        return rank;
    }

    public static boolean learned(String id, BendingData data) {
        SkillNode node = BY_ID.get(id);
        return node != null ? rank(node, data) > 0 : data.isUnlocked(id);
    }

    /** The node that locks this one out (its exclusive partner, already learned), or null. */
    @Nullable
    public static SkillNode lockedBy(SkillNode node, BendingData data) {
        if (node.group() == null) {
            return null;
        }
        for (SkillNode other : of(data.getElement())) {
            if (other != node && node.group().equals(other.group()) && rank(other, data) > 0) {
                return other;
            }
        }
        return null;
    }

    public enum State { LEARNED, AVAILABLE, NEED_POINTS, NEED_PREVIOUS, LOCKED_OUT, COMING_SOON }

    /**
     * Where a node stands for this bender. LEARNED means every rank is learned; a node with ranks left
     * is AVAILABLE (or short on points) again. {@code free} = creative: points don't matter.
     */
    public static State state(SkillNode node, BendingData data, boolean free) {
        int rank = rank(node, data);
        if (rank >= node.maxRank()) {
            return State.LEARNED;
        }
        if (rank == 0 && lockedBy(node, data) != null) {
            return State.LOCKED_OUT;
        }
        if (rank == 0 && !node.requires().isEmpty() && node.requires().stream().noneMatch(r -> learned(r, data))) {
            return State.NEED_PREVIOUS;
        }
        return free || data.getSkillPoints() >= node.cost() ? State.AVAILABLE : State.NEED_POINTS;
    }
}
