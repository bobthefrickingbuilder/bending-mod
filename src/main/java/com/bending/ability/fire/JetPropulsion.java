package com.bending.ability.fire;

import com.bending.ability.Ability;
import com.bending.ability.AbilitySlot;
import com.bending.ability.CastType;
import com.bending.data.BendingData;
import com.bending.data.Element;
import com.bending.particle.ModParticles;
import com.bending.sound.ModSounds;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Slot 3 (Mobility): sustained flight on fire jets. Hold to fly where you look; sneak to hover in place.
 * Slower than an elytra but free to steer and stop. Drains chi fast, and you can't cast anything
 * else while flying (your hands are busy).
 */
public class JetPropulsion extends Ability {
    /** Cruising speed in blocks per tick (an elytra glides at about 1.5). */
    private static final double SPEED = 0.7;
    /** How quickly you turn toward where you're looking (0..1). */
    private static final double STEERING = 0.2;
    /** Cancels gravity, so letting the jets idle holds you in place. */
    private static final double LIFT = 0.08;
    private static final int NO_FALL_TICKS = 40;

    public JetPropulsion() {
        super("jet_propulsion", Element.FIRE, AbilitySlot.MOBILITY, CastType.HOLD);
    }

    @Override
    public float startChiCost() {
        return 5f;
    }

    @Override
    public float chiPerTick() {
        return 0.6f; // 12 chi per second
    }

    @Override
    public float chiCost(int chargeTicks) {
        return 0f;
    }

    @Override
    public void onStart(ServerPlayer player, BendingData data, float power) {
        ServerLevel level = player.serverLevel();
        level.sendParticles(ModParticles.FIRE_WISP.get(), player.getX(), player.getY(), player.getZ(), 14, 0.3, 0.05, 0.3, 0.08);
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                ModSounds.FIRE_JET_DASH_LAUNCH.get(), SoundSource.PLAYERS, 0.5f, 1.5f);
        if (player.onGround()) {
            player.setDeltaMovement(player.getDeltaMovement().add(0, 0.45, 0)); // lift off
            player.hurtMarked = true;
        }
    }

    @Override
    public void onHoldTick(ServerPlayer player, BendingData data, int ticks, float power) {
        boolean hover = player.isShiftKeyDown();
        Vec3 look = player.getLookAngle();
        Vec3 target = hover ? Vec3.ZERO : look.scale(SPEED);
        Vec3 velocity = player.getDeltaMovement().scale(1 - STEERING).add(target.scale(STEERING)).add(0, LIFT, 0);
        player.setDeltaMovement(velocity);
        player.hurtMarked = true;
        player.fallDistance = 0;
        data.noFallUntil = player.level().getGameTime() + NO_FALL_TICKS;

        // Jets from both feet, pushing opposite the way you're going (straight down when hovering).
        ServerLevel level = player.serverLevel();
        Vec3 thrust = hover ? new Vec3(0, -1, 0) : look.scale(-1).add(0, -0.4, 0).normalize();
        float yaw = player.getYRot() * Mth.DEG_TO_RAD;
        Vec3 side = new Vec3(-Mth.cos(yaw), 0, -Mth.sin(yaw)).scale(0.2);
        for (Vec3 foot : new Vec3[] {player.position().add(side), player.position().subtract(side)}) {
            level.sendParticles(ModParticles.FIRE_WISP.get(), foot.x, foot.y + 0.1, foot.z, 0, thrust.x, thrust.y, thrust.z, 0.3);
        }
        if (ticks % 3 == 0) {
            level.sendParticles(ModParticles.FIRE_SMOKE.get(), player.getX(), player.getY() - 0.3, player.getZ(), 1, 0.1, 0.1, 0.1, 0.01);
        }
        if (ticks % 6 == 0) {
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    ModSounds.JET_PROPULSION_THRUST.get(), SoundSource.PLAYERS, 0.35f, 0.8f + player.getRandom().nextFloat() * 0.2f);
        }
    }
}
