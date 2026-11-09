package com.bending.ability.fire;

import com.bending.particle.ModParticles;
import com.bending.sound.ModSounds;

import com.bending.ability.Ability;
import com.bending.ability.AbilitySlot;
import com.bending.ability.BendingUtil;
import com.bending.ability.CastType;
import com.bending.data.BendingData;
import com.bending.data.Element;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Innate (Ctrl+Space in bending mode): an explosive burst that launches you the way you're pressing
 * (WASD) tilted by where you're looking, or straight where you look with no keys held.
 * Keep holding Ctrl to ride the fire jets and steer (keys and look), for up to 2 seconds.
 */
public class FireJetDash extends Ability {
    /** Speed of the initial burst (blocks per tick). */
    private static final double LAUNCH_SPEED = 1.6;
    /** Speed the jets settle at while holding. */
    private static final double CRUISE_SPEED = 1.0;
    /** How quickly holding steers you toward where you look (0..1). */
    private static final double STEERING = 0.3;
    /** Small upward push while holding, so the jets fight gravity a little. */
    private static final double LIFT = 0.05;
    private static final int MAX_HOLD_TICKS = 40;
    private static final int TRAIL_TICKS = 8;
    private static final int NO_FALL_TICKS = 40;
    private static final double LAUNCH_BLAST_RADIUS = 2.5;
    /** Explosion strength against terrain at the launch point (a creeper is 3). */
    private static final float LAUNCH_TERRAIN_POWER = 1.5f;

    public FireJetDash() {
        super("fire_jet_dash", Element.FIRE, AbilitySlot.INNATE, CastType.HOLD);
    }

    @Override
    public float startChiCost() {
        return 15f;
    }

    @Override
    public float chiPerTick() {
        return 0.6f; // 12 chi per second while holding
    }

    @Override
    public float chiCost(int chargeTicks) {
        return 0f;
    }

    @Override
    public int maxHoldTicks() {
        return MAX_HOLD_TICKS;
    }

    @Override
    public void onStart(ServerPlayer player, BendingData data, float power) {
        ServerLevel level = player.serverLevel();

        // The explosion at the launch point hits anything standing next to you.
        // ...and leaves a small crater behind you.
        BendingUtil.fireBurst(player, player.position(), LAUNCH_BLAST_RADIUS, 8f * power, 0.8, 2, null, LAUNCH_TERRAIN_POWER);
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                ModSounds.FIRE_JET_DASH_LAUNCH.get(), SoundSource.PLAYERS, 0.8f, 1.2f);

        Vec3 velocity = direction(player, data).scale(LAUNCH_SPEED);
        if (player.onGround() && velocity.y < 0.25) {
            velocity = new Vec3(velocity.x, 0.25, velocity.z); // lift off the ground so friction doesn't eat the dash
        }
        player.setDeltaMovement(velocity);
        player.hurtMarked = true; // sends the new velocity to the client
        keepAirborneSafe(player, data);
        data.dashTrailHits.clear();
    }

    @Override
    public void onHoldTick(ServerPlayer player, BendingData data, int ticks, float power) {
        Vec3 look = direction(player, data);
        Vec3 velocity = player.getDeltaMovement().scale(1 - STEERING)
                .add(look.scale(CRUISE_SPEED * STEERING))
                .add(0, LIFT, 0);
        player.setDeltaMovement(velocity);
        player.hurtMarked = true;
        keepAirborneSafe(player, data);

        // Jets of flame shooting out behind you.
        ServerLevel level = player.serverLevel();
        for (int i = 0; i < 3; i++) {
            level.sendParticles(ModParticles.FIRE_WISP.get(), player.getX(), player.getY() + 0.4, player.getZ(),
                    0, -look.x, -look.y, -look.z, 0.35);
        }
        if (ticks % 6 == 0) {
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    ModSounds.FIRE_JET_DASH_THRUST.get(), SoundSource.PLAYERS, 0.3f, 0.6f);
        }
    }

    /**
     * Which way the dash goes: your movement keys turned to match where you face, tilted up or down by
     * where you look. W (or no keys) = exactly where you look; A/D = sideways; S = backwards; W+A = diagonal.
     * The up/down part always comes from your look, so looking up while strafing sends you up and sideways.
     */
    static Vec3 direction(ServerPlayer player, BendingData data) {
        Vec3 look = player.getLookAngle();
        if (data.moveForward == 0 && data.moveLeft == 0) {
            return look;
        }
        float yaw = player.getYRot() * ((float) Math.PI / 180f);
        Vec3 forward = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
        Vec3 left = new Vec3(Math.cos(yaw), 0, Math.sin(yaw));
        Vec3 keys = forward.scale(data.moveForward).add(left.scale(data.moveLeft)).normalize();
        double flat = Math.sqrt(look.x * look.x + look.z * look.z);
        return keys.scale(flat).add(0, look.y, 0).normalize();
    }

    private static void keepAirborneSafe(ServerPlayer player, BendingData data) {
        player.fallDistance = 0;
        data.noFallUntil = player.level().getGameTime() + NO_FALL_TICKS;
        data.dashTrailTicks = TRAIL_TICKS;
    }

    /** Flame trail behind the dash that burns anything you pass through (once per dash). */
    public static void tickTrail(ServerPlayer player, BendingData data) {
        data.dashTrailTicks--;
        ServerLevel level = player.serverLevel();
        level.sendParticles(ModParticles.FIRE_WISP.get(), player.getX(), player.getY() + 0.3, player.getZ(), 6, 0.2, 0.2, 0.2, 0.01);
        level.sendParticles(ModParticles.FIRE_SMOKE.get(), player.getX(), player.getY() + 0.3, player.getZ(), 2, 0.2, 0.2, 0.2, 0.01);
        for (LivingEntity target : BendingUtil.livingAround(player, player.position().add(0, 0.9, 0), 1.6)) {
            if (data.dashTrailHits.add(target.getId())) {
                BendingUtil.damage(player, target, 5f * FireConditions.power(player), 2);
            }
        }
    }
}
