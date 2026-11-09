package com.bending.ability;

import com.bending.BendingMod;
import com.bending.data.BendingData;
import com.bending.data.Element;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;

/**
 * Base class for every bending ability. Subclasses override the hooks that match their {@link CastType}.
 * All hooks run on the server. {@code power} is the element's current strength multiplier
 * (day/night, rain, Nether, and later skill tree and gem bonuses).
 */
public abstract class Ability {
    private final String path;
    private final String id;
    private final Element element;
    private final AbilitySlot slot;
    private final CastType castType;

    protected Ability(String path, Element element, AbilitySlot slot, CastType castType) {
        this.path = path;
        this.id = BendingMod.MODID + ":" + path;
        this.element = element;
        this.slot = slot;
        this.castType = castType;
    }

    public String id() {
        return id;
    }

    public Element element() {
        return element;
    }

    public AbilitySlot slot() {
        return slot;
    }

    public CastType castType() {
        return castType;
    }

    public Component displayName() {
        return Component.translatable("ability." + BendingMod.MODID + "." + path);
    }

    /** One or two sentences on what it does, for the loadout screen. */
    public Component description() {
        return Component.translatable("ability." + BendingMod.MODID + "." + path + ".desc");
    }

    /** Chi spent on a tap, or on release of a charge (scaled by how long it charged). */
    public abstract float chiCost(int chargeTicks);

    /** Chi drained each tick while a HOLD (or the hold part of TAP_OR_HOLD) is active. */
    public float chiPerTick() {
        return 0f;
    }

    /** Chi spent the moment a HOLD, CHARGE or TAP_OR_HOLD key is pressed (paid before {@link #onStart}). */
    public float startChiCost() {
        return 0f;
    }

    /** Longest a HOLD can be held before it ends on its own. */
    public int maxHoldTicks() {
        return Integer.MAX_VALUE;
    }


    /** Longest useful charge for CHARGE abilities. */
    public int maxChargeTicks() {
        return 0;
    }

    /** Releasing a CHARGE before this many ticks cancels it (no chi spent). */
    public int minChargeTicks() {
        return 0;
    }

    /** If true, a CHARGE fires by itself the moment it is fully charged. */
    public boolean autoReleaseAtMax() {
        return false;
    }

    /** Extra conditions for starting a cast (e.g. Lightning's calm-mind rule). */
    public boolean canStart(ServerPlayer player, BendingData data) {
        return true;
    }

    public void onTap(ServerPlayer player, BendingData data, float power) {}

    /** Runs once when a HOLD, CHARGE or TAP_OR_HOLD key is pressed (e.g. the dash's initial burst). */
    public void onStart(ServerPlayer player, BendingData data, float power) {}

    public void onHoldTick(ServerPlayer player, BendingData data, int ticks, float power) {}

    public void onRelease(ServerPlayer player, BendingData data, int ticks, float power) {}

    /** A CHARGE was let go before {@link #minChargeTicks()}. Nothing was spent. */
    public void onChargeCancelled(ServerPlayer player, BendingData data) {}

    /** If true, taking damage while this ability is active cancels it (Lightning's calm mind). */
    /** Movement speed while this is being cast (1 = normal; 0.3 = crouch speed). */
    public float castMoveSpeed() {
        return 1f;
    }

    public boolean cancelWhenHurt() {
        return false;
    }

    /** The bender took damage while this was active and {@link #cancelWhenHurt()} cancelled it. */
    public void onHurtWhileActive(ServerPlayer player, BendingData data) {}

    /**
     * Called when the bender is about to take damage while this ability is active (being held or charged).
     * Return the new amount; 0 or less blocks the hit completely. Used by shields and blocks.
     */
    public float modifyIncomingDamage(ServerPlayer player, BendingData data, DamageSource source, float amount) {
        return amount;
    }
}
