package com.bending.server;

import com.bending.ability.Abilities;
import com.bending.ability.Ability;
import com.bending.ability.CastType;
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
    /** Safety limit so a lost "release" packet can't leave a charge active forever. */
    private static final int MAX_ACTIVE_TICKS = 20 * 30;
    /** Hunger exhaustion per point of chi spent: fire comes from the breath. */
    private static final float EXHAUSTION_PER_CHI = 0.03f;

    private CastHandler() {}

    public static void onInput(ServerPlayer player, int slot, boolean pressed) {
        if (slot < 0 || slot >= BendingData.SLOTS || player.isSpectator()) {
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
        if (data.activeSlot != -1) {
            return;
        }
        Ability ability = Abilities.get(data.getLoadout(slot));
        Element element = data.getElement();
        if (ability == null || ability.element() != element || !data.isUnlocked(ability.id())) {
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
            float cost = ability.chiCost(0);
            if (!spend(player, data, cost)) {
                return;
            }
            ability.onTap(player, data, BendingRules.power(player, element));
            BendingNetwork.sync(player);
            return;
        }

        // HOLD, CHARGE and TAP_OR_HOLD stay active until the key is released.
        float startCost = ability.startChiCost();
        float needed = startCost + switch (ability.castType()) {
            case CHARGE -> ability.chiCost(0);
            case TAP_OR_HOLD -> Math.min(ability.chiCost(0), ability.chiPerTick() * TAP_THRESHOLD);
            default -> ability.chiPerTick() * 5;
        };
        if (data.getChi() < needed) {
            noChi(player);
            return;
        }
        if (!spend(player, data, startCost)) {
            return;
        }
        ability.onStart(player, data, BendingRules.power(player, element));
        data.activeSlot = slot;
        data.activeTicks = 0;
        if (startCost > 0f) {
            BendingNetwork.sync(player);
        }
    }

    /** Called every server tick for each player. */
    public static void tick(ServerPlayer player, BendingData data) {
        if (data.activeSlot == -1) {
            return;
        }
        Ability ability = Abilities.get(data.getLoadout(data.activeSlot));
        Element element = data.getElement();
        if (ability == null || element == null || !BendingRules.canBend(player, element) || !player.isAlive()) {
            cancel(data);
            return;
        }
        data.activeTicks++;
        float power = BendingRules.power(player, element);

        switch (ability.castType()) {
            case HOLD -> holdTick(player, data, ability, data.activeTicks, power);
            case TAP_OR_HOLD -> {
                if (data.activeTicks >= TAP_THRESHOLD) {
                    holdTick(player, data, ability, data.activeTicks - TAP_THRESHOLD, power);
                }
            }
            case CHARGE -> {
                ability.onHoldTick(player, data, Math.min(data.activeTicks, ability.maxChargeTicks()), power);
                if (ability.autoReleaseAtMax() && data.activeTicks >= ability.maxChargeTicks()) {
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
        if (ticks > ability.maxHoldTicks() || !spend(player, data, ability.chiPerTick())) {
            finish(player, data);
            return;
        }
        ability.onHoldTick(player, data, ticks, power);
    }

    /** Ends the active cast: fires a charge, ends a hold, or resolves a tap. */
    private static void finish(ServerPlayer player, BendingData data) {
        Ability ability = Abilities.get(data.getLoadout(data.activeSlot));
        int ticks = data.activeTicks;
        cancel(data);
        if (ability == null || data.getElement() == null) {
            return;
        }
        float power = BendingRules.power(player, data.getElement());

        switch (ability.castType()) {
            case TAP_OR_HOLD -> {
                if (ticks < TAP_THRESHOLD) {
                    if (spend(player, data, ability.chiCost(0))) {
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
                int charge = Math.min(ticks, ability.maxChargeTicks());
                if (charge < ability.minChargeTicks()) {
                    ability.onChargeCancelled(player, data);
                    return;
                }
                if (spend(player, data, ability.chiCost(charge))) {
                    ability.onRelease(player, data, charge, power);
                    BendingNetwork.sync(player);
                }
            }
            default -> {}
        }
    }

    public static void cancel(BendingData data) {
        data.activeSlot = -1;
        data.activeTicks = 0;
    }

    private static boolean spend(ServerPlayer player, BendingData data, float cost) {
        if (cost <= 0f) {
            return true;
        }
        if (!data.trySpend(cost)) {
            noChi(player);
            return false;
        }
        player.causeFoodExhaustion(cost * EXHAUSTION_PER_CHI);
        data.lastCastTime = player.level().getGameTime();
        return true;
    }

    private static void noChi(ServerPlayer player) {
        player.displayClientMessage(Component.translatable("message.bending.no_chi"), true);
    }
}
