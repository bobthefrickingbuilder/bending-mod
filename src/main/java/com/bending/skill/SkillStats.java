package com.bending.skill;

import javax.annotation.Nullable;

import com.bending.BendingMod;
import com.bending.data.BendingData;
import com.bending.data.ModAttachments;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * Adds up what your learned skill nodes give you. Each node's effects count once per rank. An
 * effect for a named ability only changes that ability; one with no ability is global (all your
 * bending, or your body). Gems will add to the same numbers later.
 */
public final class SkillStats {
    private static final ResourceLocation HEALTH_ID = ResourceLocation.fromNamespaceAndPath(BendingMod.MODID, "skill_health");
    private static final ResourceLocation SPEED_ID = ResourceLocation.fromNamespaceAndPath(BendingMod.MODID, "skill_speed");

    private SkillStats() {}

    /** Sum of an effect over your learned ranks: for one ability, or the global part when ability is null. */
    public static float sum(BendingData data, @Nullable String ability, SkillStat stat) {
        float total = 0f;
        for (SkillNode node : SkillTree.of(data.getElement())) {
            if (node.effects().isEmpty()) {
                continue;
            }
            int rank = -1;
            for (SkillNode.Effect effect : node.effects()) {
                if (effect.stat() != stat || !java.util.Objects.equals(effect.ability(), ability)) {
                    continue;
                }
                if (rank < 0) {
                    rank = SkillTree.rank(node, data);
                }
                total += effect.perRank() * rank;
            }
        }
        return total;
    }

    /** Multiplier for one ability's stat (1 = unchanged). Costs and charge times can't drop below 25%. */
    public static float ability(BendingData data, String ability, SkillStat stat) {
        float m = 1f + sum(data, ability, stat);
        return stat == SkillStat.CHI_COST || stat == SkillStat.CHARGE_TIME ? Math.max(0.25f, m) : Math.max(0.1f, m);
    }

    public static float ability(ServerPlayer player, String ability, SkillStat stat) {
        return ability(player.getData(ModAttachments.BENDING), ability, stat);
    }

    /** Flat bonus for one ability (extra chain targets, extra bolts). */
    public static int abilityFlat(ServerPlayer player, String ability, SkillStat stat) {
        return Math.round(sum(player.getData(ModAttachments.BENDING), ability, stat));
    }

    // ---- global numbers ----

    public static float damageMultiplier(BendingData data) {
        return 1f + sum(data, null, SkillStat.DAMAGE);
    }

    public static float burnMultiplier(BendingData data) {
        return 1f + sum(data, null, SkillStat.BURN);
    }

    public static float maxChiBonus(BendingData data) {
        return sum(data, null, SkillStat.MAX_CHI);
    }

    public static float regenMultiplier(BendingData data) {
        return 1f + sum(data, null, SkillStat.REGEN);
    }

    public static float chiCostMultiplier(BendingData data) {
        return Math.max(0.3f, 1f + sum(data, null, SkillStat.CHI_COST));
    }

    /** Ticks after a cast before chi starts regenerating. */
    public static int regenDelay(BendingData data, int base) {
        return Math.round(base * Math.max(0.2f, 1f + sum(data, null, SkillStat.REGEN_DELAY)));
    }

    /** Fraction of incoming damage you shrug off. */
    public static float resistance(BendingData data) {
        return Math.min(0.6f, sum(data, null, SkillStat.RESISTANCE));
    }

    /** Keeps the health and move speed bonuses on the player in step with their tree. */
    public static void applyAttributes(ServerPlayer player) {
        BendingData data = player.getData(ModAttachments.BENDING);
        set(player.getAttribute(Attributes.MAX_HEALTH), HEALTH_ID, sum(data, null, SkillStat.MAX_HEALTH),
                AttributeModifier.Operation.ADD_VALUE);
        set(player.getAttribute(Attributes.MOVEMENT_SPEED), SPEED_ID, sum(data, null, SkillStat.MOVE_SPEED),
                AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        if (player.getHealth() > player.getMaxHealth()) {
            player.setHealth(player.getMaxHealth());
        }
    }

    private static void set(@Nullable net.minecraft.world.entity.ai.attributes.AttributeInstance attribute,
                            ResourceLocation id, double amount, AttributeModifier.Operation op) {
        if (attribute == null) {
            return;
        }
        AttributeModifier current = attribute.getModifier(id);
        if (current != null && current.amount() == amount) {
            return;
        }
        attribute.removeModifier(id);
        if (amount != 0) {
            attribute.addTransientModifier(new AttributeModifier(id, amount, op));
        }
    }
}
