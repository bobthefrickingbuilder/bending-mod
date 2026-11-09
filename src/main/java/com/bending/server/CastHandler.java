package com.bending.server;

import com.bending.ability.Abilities;
import com.bending.ability.Ability;
import com.bending.ability.CastType;
import com.bending.ability.fire.FireTechniques;
import com.bending.skill.SkillStat;
import com.bending.skill.SkillStats;
import com.bending.data.BendingData;
import com.bending.data.Element;
import com.bending.data.ModAttachments;
import com.bending.network.BendingNetwork;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Turns key presses from the client into ability casts, enforcing chi and element rules.
 * There are no cooldowns: chi cost and chi regen are the only limits.
 */
public final class CastHandler {
    /** Releasing a TAP_OR_HOLD key before this many ticks counts as a tap. */
    public static final int TAP_THRESHOLD = 5;
    /** Input slots after the 4 loadout slots: the innate attack (left click) and movement (Ctrl+Space). */
    public static final int INNATE_ATTACK_SLOT = BendingData.SLOTS;
    public static final int INNATE_MOBILITY_SLOT = BendingData.SLOTS + 1;
    /** Innate aerial slam (Shift while airborne). */
    public static final int INNATE_SLAM_SLOT = BendingData.SLOTS + 2;

    /** The ability an input slot casts: a loadout slot, or one of the innate abilities. */
    @javax.annotation.Nullable
    public static Ability abilityIn(BendingData data, int slot) {
        if (slot == INNATE_ATTACK_SLOT) {
            return Abilities.innateAttack(data.getElement());
        }
        if (slot == INNATE_MOBILITY_SLOT) {
            return Abilities.innateMobility(data.getElement());
        }
        if (slot == INNATE_SLAM_SLOT) {
            return Abilities.innateSlam(data.getElement());
        }
        return slot >= 0 && slot < BendingData.SLOTS ? Abilities.get(data.getLoadout(slot)) : null;
    }
    /** Safety limit so a lost "release" packet can't leave a charge active forever. */
    private static final int MAX_ACTIVE_TICKS = 20 * 30;
    /** Hunger exhaustion per point of chi spent: fire comes from the breath. */
    private static final float EXHAUSTION_PER_CHI = 0.03f;

    private CastHandler() {}

    public static void onInput(ServerPlayer player, int slot, boolean pressed) {
        if (slot < 0 || slot > INNATE_SLAM_SLOT || player.isSpectator()) {
            return;
        }
        BendingData data = player.getData(ModAttachments.BENDING);
        if (data.getElement() == null) {
            return;
        }
        if (pressed) {
            if (!data.bendingMode) {
                return; // abilities only work in bending mode
            }
            start(player, data, slot);
        } else if (data.activeSlot == slot) {
            finish(player, data);
        }
    }

    private static void start(ServerPlayer player, BendingData data, int slot) {
        if (slot == INNATE_SLAM_SLOT && data.activeSlot == INNATE_MOBILITY_SLOT) {
            finish(player, data); // slam straight out of a dash
        }
        if (data.activeSlot != -1) {
            return;
        }
        Element element = data.getElement();
        boolean innate = slot >= INNATE_ATTACK_SLOT;
        Ability ability = abilityIn(data, slot);
        if (ability == null || ability.element() != element || (!innate && !data.isUnlocked(ability.id()))) {
            return;
        }
        if (!BendingRules.canBend(player, element)) {
            player.displayClientMessage(BendingRules.cannotBendMessage(element), true);
            return;
        }
        if (!ability.canStart(player, data)) {
            return;
        }

        if (ability.castType() == CastType.TAP) {
            float cost = cost(player, ability, ability.chiCost(0));
            if (!spend(player, data, cost)) {
                return;
            }
            ability.onTap(player, data, power(player, element, ability));
            BendingNetwork.sync(player);
            return;
        }

        // HOLD, CHARGE and TAP_OR_HOLD stay active until the key is released.
        float startCost = cost(player, ability, ability.startChiCost());
        float needed = startCost + cost(player, ability, switch (ability.castType()) {
            case CHARGE -> ability.chiCost(0);
            case TAP_OR_HOLD -> Math.min(ability.chiCost(0), ability.chiPerTick() * TAP_THRESHOLD);
            default -> ability.chiPerTick() * 5;
        });
        if (!canAfford(player, data, needed)) {
            noChi(player);
            return;
        }
        if (!spend(player, data, startCost)) {
            return;
        }
        ability.onStart(player, data, power(player, element, ability));
        data.activeSlot = slot;
        data.activeTicks = 0;
        if (startCost > 0f) {
            BendingNetwork.sync(player);
        }
    }

    /** Called every server tick for each player. */
    public static void tick(ServerPlayer player, BendingData data) {
        com.bending.ability.BendingAnimations.tick(player, data);
        com.bending.ability.fire.Lightning.tickStored(player, data);
        com.bending.ability.fire.MeteorSlam.tickRooted(player, data);
        com.bending.ability.fire.FireSweep.tick(player, data);
        Ability casting = data.activeSlot == -1 ? null : abilityIn(data, data.activeSlot);
        updateCastSlow(player, casting == null ? 1f : casting.castMoveSpeed());
        if (data.activeSlot == -1) {
            return;
        }
        Ability ability = abilityIn(data, data.activeSlot);
        Element element = data.getElement();
        if (ability == null || element == null || !BendingRules.canBend(player, element) || !player.isAlive()) {
            cancel(data);
            return;
        }
        data.activeTicks++;
        float power = power(player, element, ability);

        switch (ability.castType()) {
            case HOLD -> holdTick(player, data, ability, data.activeTicks, power);
            case TAP_OR_HOLD -> {
                if (data.activeTicks >= TAP_THRESHOLD) {
                    holdTick(player, data, ability, data.activeTicks - TAP_THRESHOLD, power);
                }
            }
            case CHARGE -> {
                int charge = chargeTicks(player, ability, data.activeTicks);
                ability.onHoldTick(player, data, Math.min(charge, ability.maxChargeTicks()), power);
                if (ability.autoReleaseAtMax() && charge >= ability.maxChargeTicks()) {
                    finish(player, data);
                    return;
                }
            }
            default -> {}
        }

        if (data.activeSlot != -1 && data.activeTicks > MAX_ACTIVE_TICKS) {
            finish(player, data);
        }
    }

    private static void holdTick(ServerPlayer player, BendingData data, Ability ability, int ticks, float power) {
        int maxHold = Math.round(ability.maxHoldTicks() * SkillStats.ability(player, ability.id(), SkillStat.DURATION));
        if (ticks > maxHold || !spend(player, data, cost(player, ability, ability.chiPerTick()))) {
            finish(player, data);
            return;
        }
        ability.onHoldTick(player, data, ticks, power);
    }

    /** Ends the active cast: fires a charge, ends a hold, or resolves a tap. */
    private static void finish(ServerPlayer player, BendingData data) {
        Ability ability = abilityIn(data, data.activeSlot);
        int ticks = data.activeTicks;
        cancel(data);
        if (ability == null || data.getElement() == null) {
            return;
        }
        float power = power(player, data.getElement(), ability);

        switch (ability.castType()) {
            case TAP_OR_HOLD -> {
                if (ticks < TAP_THRESHOLD) {
                    if (spend(player, data, cost(player, ability, ability.chiCost(0)))) {
                        ability.onTap(player, data, power);
                        BendingNetwork.sync(player);
                    }
                } else {
                    ability.onRelease(player, data, ticks - TAP_THRESHOLD, power);
                    BendingNetwork.sync(player);
                }
            }
            case HOLD -> {
                ability.onRelease(player, data, ticks, power);
                BendingNetwork.sync(player);
            }
            case CHARGE -> {
                int charge = Math.min(chargeTicks(player, ability, ticks), ability.maxChargeTicks());
                if (charge < ability.minChargeTicks()) {
                    ability.onChargeCancelled(player, data);
                    return;
                }
                if (spend(player, data, cost(player, ability, ability.chiCost(charge)))) {
                    ability.onRelease(player, data, charge, power);
                    BendingNetwork.sync(player);
                }
            }
            default -> {}
        }
    }

    private static final net.minecraft.resources.ResourceLocation CAST_SLOW =
            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(com.bending.BendingMod.MODID, "cast_slow");

    /** Slows you while casting something heavy (Dragon's Breath), and lifts it when you stop. */
    private static void updateCastSlow(ServerPlayer player, float speed) {
        var attribute = player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);
        if (attribute == null) {
            return;
        }
        boolean has = attribute.hasModifier(CAST_SLOW);
        if (speed < 1f && !has) {
            attribute.addTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(CAST_SLOW, speed - 1f,
                    net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        } else if (speed >= 1f && has) {
            attribute.removeModifier(CAST_SLOW);
        }
    }

    /** Bending power for a cast: your element's power times the ability's damage upgrades. */
    private static float power(ServerPlayer player, Element element, Ability ability) {
        return BendingRules.power(player, element) * SkillStats.ability(player, ability.id(), SkillStat.DAMAGE);
    }

    /** An ability's chi cost after its own upgrades (global discounts are applied when spending). */
    private static float cost(ServerPlayer player, Ability ability, float raw) {
        return raw * SkillStats.ability(player, ability.id(), SkillStat.CHI_COST);
    }

    /** How far along a charge is: charge-time upgrades make it fill faster. */
    private static int chargeTicks(ServerPlayer player, Ability ability, int heldTicks) {
        return (int) (heldTicks / SkillStats.ability(player, ability.id(), SkillStat.CHARGE_TIME));
    }

    public static void cancel(BendingData data) {
        data.activeSlot = -1;
        data.activeTicks = 0;
    }

    private static boolean spend(ServerPlayer player, BendingData data, float cost) {
        cost *= SkillStats.chiCostMultiplier(data) * com.bending.BendingConfig.CHI_COST_MULTIPLIER.get().floatValue();
        if (cost <= 0f) {
            return true;
        }
        if (!data.trySpend(cost) && !burnHunger(player, data, cost)) {
            noChi(player);
            return false;
        }
        player.causeFoodExhaustion(cost * EXHAUSTION_PER_CHI);
        data.lastCastTime = player.level().getGameTime();
        return true;
    }

    /** Breath of Fire: chi you lack can be paid with hunger, down to the last hunger point. */
    private static final float CHI_PER_HUNGER = 10f;
    private static final int MIN_FOOD = 1;

    private static float hungerChi(ServerPlayer player, BendingData data) {
        if (!FireTechniques.has(data, FireTechniques.BREATH_OF_FIRE)) {
            return 0f;
        }
        return Math.max(0f, (player.getFoodData().getFoodLevel() - MIN_FOOD) * CHI_PER_HUNGER - data.breathDebt);
    }

    private static boolean canAfford(ServerPlayer player, BendingData data, float amount) {
        return data.hasInfiniteChi() || data.getChi() + hungerChi(player, data) >= amount * SkillStats.chiCostMultiplier(data);
    }

    /** Pays what chi can't cover out of hunger. False (nothing spent) if that's not enough either. */
    private static boolean burnHunger(ServerPlayer player, BendingData data, float cost) {
        float deficit = cost - data.getChi();
        if (deficit > hungerChi(player, data)) {
            return false;
        }
        data.setChi(0f);
        data.breathDebt += deficit;
        var food = player.getFoodData();
        while (data.breathDebt >= CHI_PER_HUNGER) {
            data.breathDebt -= CHI_PER_HUNGER;
            food.setFoodLevel(Math.max(MIN_FOOD, food.getFoodLevel() - 1));
            // A deep, smoky breath.
            player.serverLevel().sendParticles(com.bending.particle.ModParticles.FIRE_SMOKE.get(),
                    player.getX(), player.getEyeY() - 0.2, player.getZ(), 4, 0.2, 0.1, 0.2, 0.02);
        }
        return true;
    }

    private static void noChi(ServerPlayer player) {
        player.displayClientMessage(Component.translatable("message.bending.no_chi"), true);
    }
}
