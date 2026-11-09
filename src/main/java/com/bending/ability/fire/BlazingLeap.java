package com.bending.ability.fire;

import com.bending.ability.Ability;
import com.bending.ability.AbilitySlot;
import com.bending.ability.BendingUtil;
import com.bending.ability.CastType;
import com.bending.data.BendingData;
import com.bending.data.Element;
import com.bending.particle.ModParticles;
import com.bending.sound.ModSounds;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Slot 3 (Mobility): crouch and hold to build up, release to blast off the ground on fire jets.
 * A tap hops you about 6 blocks up; a full 1 s charge launches you about 20 blocks up and well
 * forward. The take-off leaves a ring of flame that burns and knocks back anyone next to you.
 * No fall damage from the leap, and you can Meteor Slam out of it.
 */
public class BlazingLeap extends Ability {
    private static final int FULL_CHARGE = 20;
    private static final int MIN_CHARGE = 2;
    private static final double MIN_HEIGHT = 6;
    private static final double FULL_HEIGHT = 20;
    /** Forward speed at take-off (blocks per tick); about 4 to 10 blocks of travel. */
    private static final double MIN_FORWARD = 0.35;
    private static final double FULL_FORWARD = 0.9;
    private static final double BURST_RADIUS = 3.0;
    private static final int NO_FALL_TICKS = 120;

    public BlazingLeap() {
        super("blazing_leap", Element.FIRE, AbilitySlot.MOBILITY, CastType.CHARGE);
    }

    private static float fraction(int ticks) {
        return Mth.clamp(ticks / (float) FULL_CHARGE, 0f, 1f);
    }

    @Override
    public float chiCost(int chargeTicks) {
        return 10f + 15f * fraction(chargeTicks); // 10 to 25
    }

    @Override
    public int maxChargeTicks() {
        return FULL_CHARGE;
    }

    @Override
    public int minChargeTicks() {
        return MIN_CHARGE;
    }

    @Override
    public boolean canStart(ServerPlayer player, BendingData data) {
        return player.onGround(); // you need something to push off
    }

    @Override
    public void onHoldTick(ServerPlayer player, BendingData data, int ticks, float power) {
        if (ticks == 1) {
            com.bending.ability.BendingAnimations.playForCast(player, data, com.bending.ability.BendingAnimations.LAUNCH_CROUCH);
        }
        ServerLevel level = player.serverLevel();
        float progress = fraction(ticks);
        // Fire pools around your feet as you crouch into it.
        BendingUtil.ring(level, FireFx.wisp(player), player.position().add(0, 0.1, 0), 0.6 + 0.4 * progress, 4 + (int) (progress * 6));
        if (ticks == FULL_CHARGE) {
            level.playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.FIREBALL_CHARGE_READY.get(), SoundSource.PLAYERS, 0.5f, 1.6f);
        }
        BendingUtil.chargeBar(player, progress);
    }

    @Override
    public void onChargeCancelled(ServerPlayer player, BendingData data) {
        BendingUtil.fizzle(player);
    }

    @Override
    public void onRelease(ServerPlayer player, BendingData data, int ticks, float power) {
        ServerLevel level = player.serverLevel();
        float f = fraction(ticks);
        Vec3 feet = player.position();
        Vec3 look = player.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0, look.z);
        flat = flat.lengthSqr() > 1e-4 ? flat.normalize() : Vec3.ZERO;
        double forward = Mth.lerp(f, MIN_FORWARD, FULL_FORWARD);
        double up = launchSpeed(Mth.lerp(f, MIN_HEIGHT, FULL_HEIGHT));
        player.setDeltaMovement(flat.x * forward, up, flat.z * forward);
        player.hurtMarked = true;
        player.fallDistance = 0;
        data.noFallUntil = level.getGameTime() + NO_FALL_TICKS;
        com.bending.ability.BendingAnimations.playUntilLanded(player, data, com.bending.ability.BendingAnimations.LEAP_AIR);

        // Take-off: a burst of flame where you stood.
        BendingUtil.fireBurst(player, feet.add(0, 0.3, 0), BURST_RADIUS, (6f + 6f * f) * power, 0.8, 2, null, 0f);
        BendingUtil.ring(level, FireFx.wisp(player), feet.add(0, 0.2, 0), BURST_RADIUS * 0.8, 28);
        for (int i = 0; i < 16; i++) {
            level.sendParticles(FireFx.wisp(player), feet.x, feet.y + 0.2, feet.z, 0,
                    (player.getRandom().nextDouble() - 0.5) * 0.4, -0.6 - player.getRandom().nextDouble() * 0.3,
                    (player.getRandom().nextDouble() - 0.5) * 0.4, 0.6);
        }
        level.sendParticles(ModParticles.FIRE_SMOKE.get(), feet.x, feet.y + 0.3, feet.z, 10, 0.6, 0.2, 0.6, 0.03);
        BendingUtil.scatterFire(level, BlockPos.containing(feet), 2 + Math.round(2 * f));
        level.playSound(null, feet.x, feet.y, feet.z, ModSounds.BLAZING_LEAP_LAUNCH.get(), SoundSource.PLAYERS, 1.0f, 1.1f - 0.3f * f);
        player.displayClientMessage(net.minecraft.network.chat.Component.empty(), true);
    }

    /**
     * Upward speed that peaks at {@code height} blocks, with Minecraft's gravity (0.08 per tick)
     * and air drag (x0.98 per tick).
     */
    static double launchSpeed(double height) {
        double lo = 0, hi = 4;
        for (int i = 0; i < 30; i++) {
            double mid = (lo + hi) / 2;
            if (peak(mid) < height) {
                lo = mid;
            } else {
                hi = mid;
            }
        }
        return (lo + hi) / 2;
    }

    private static double peak(double v) {
        double y = 0;
        while (v > 0) {
            y += v;
            v = (v - 0.08) * 0.98;
        }
        return y;
    }
}
