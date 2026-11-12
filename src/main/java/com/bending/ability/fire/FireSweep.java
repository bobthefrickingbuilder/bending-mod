package com.bending.ability.fire;

import com.bending.ability.BendingAnimations;
import com.bending.ability.BendingUtil;
import com.bending.data.BendingData;
import com.bending.particle.ModParticles;
import com.bending.sound.ModSounds;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Fire Blast's low variant (Ctrl held on the ground + attack): you drop and spin a leg sweep along
 * the ground. The sweeping foot leaves a ring of fire around you, which then rolls outward as a
 * low wave, burning and knocking back everything it passes.
 */
public final class FireSweep {
    /** Chi on top of Fire Blast's own cost. */
    public static final float EXTRA_CHI = 8f;
    private static final float DAMAGE = 9f;
    /** Ticks the spin takes to draw the full ring (matches the animation's spin). */
    private static final int SPIN_START = 2, SPIN_TICKS = 8;
    /** After the spin the ring rolls outward this long. */
    private static final int WAVE_TICKS = 9;
    private static final double RING_START = 1.3;
    private static final double RING_GROWTH = 0.5;
    /** You stay planted while you spin. */
    private static final int ROOT_TICKS = 10;
    private static final double KNOCKBACK = 1.1;
    private static final int FIRE_SECONDS = 4;

    private FireSweep() {}

    public static boolean sweeping(BendingData data) {
        return data.sweepTick >= 0;
    }

    public static void start(ServerPlayer player, BendingData data, float power) {
        data.sweepTick = 0;
        data.sweepCenter = player.position();
        data.sweepYaw = player.getYRot();
        data.sweepDamage = DAMAGE * power;
        data.sweepHit.clear();
        data.rootedUntil = Math.max(data.rootedUntil, player.level().getGameTime() + ROOT_TICKS);
        player.setDeltaMovement(0, Math.min(0, player.getDeltaMovement().y), 0);
        player.hurtMarked = true;
        BendingAnimations.play(player, BendingAnimations.FIRE_SWEEP);
        Vec3 c = data.sweepCenter;
        player.level().playSound(null, c.x, c.y, c.z, ModSounds.FIRE_BLAST_CAST.get(), SoundSource.PLAYERS, 0.9f, 0.7f);
        player.level().playSound(null, c.x, c.y, c.z, SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 0.6f, 0.8f);
    }

    /** Every tick while a sweep is running. */
    public static void tick(ServerPlayer player, BendingData data) {
        if (data.sweepTick < 0) {
            return;
        }
        int t = data.sweepTick++;
        if (t > SPIN_START + SPIN_TICKS + WAVE_TICKS || !(player.level() instanceof ServerLevel level)) {
            data.sweepTick = -1;
            return;
        }
        if (t < SPIN_START) {
            return;
        }
        Vec3 c = data.sweepCenter;
        // How much of the circle the foot has drawn so far, and how far the ring has rolled out.
        double arc = Math.min(1.0, (t - SPIN_START + 1) / (double) SPIN_TICKS);
        int waveT = Math.max(0, t - SPIN_START - SPIN_TICKS + 2);
        double radius = RING_START + waveT * RING_GROWTH;
        boolean rolling = waveT > 0;
        // Starts at your front-right, where the sweeping foot begins, and runs round to your right.
        double start = Math.toRadians(data.sweepYaw + 135);

        // Dense flames along the ring, thrown outward and a little up; brighter where the foot is now.
        int points = (int) (Math.PI * 2 * radius * arc * 7) + 6;
        for (int i = 0; i < points; i++) {
            double a = start + (i + level.random.nextDouble()) / points * arc * Math.PI * 2;
            double dx = Math.cos(a), dz = Math.sin(a);
            double r = radius + level.random.nextGaussian() * 0.15;
            double x = c.x + dx * r, z = c.z + dz * r, y = c.y + 0.15 + level.random.nextDouble() * 0.35;
            double out = rolling ? 0.32 : 0.14;
            level.sendParticles(FireFx.wisp(player), x, y, z, 0, dx * out, 0.08 + level.random.nextDouble() * 0.08, dz * out, 1.0);
            if (i % 3 == 0) {
                level.sendParticles(FireFx.core(player), x, y, z, 0, dx * out * 0.7, 0.05, dz * out * 0.7, 1.0);
            }
            if (i % 5 == 0) {
                level.sendParticles(FireFx.cloud(player), x, y + 0.2, z, 0, dx * out * 0.5, 0.06, dz * out * 0.5, 1.0);
            }
            if (i % 4 == 0) {
                level.sendParticles(FireFx.spark(player), x, y, z, 0, dx * 0.4, 0.25, dz * 0.4, 1.0);
            }
        }
        if (!rolling) {
            // The leading edge: a burst at the foot as it sweeps round.
            double a = start + arc * Math.PI * 2;
            double x = c.x + Math.cos(a) * radius, z = c.z + Math.sin(a) * radius;
            level.sendParticles(FireFx.core(player), x, c.y + 0.3, z, 8, 0.2, 0.15, 0.2, 0.06);
            level.sendParticles(FireFx.ember(player), x, c.y + 0.3, z, 6, 0.2, 0.1, 0.2, 0.15);
        } else if (waveT == 1) {
            com.bending.ability.ImpactFx.impact(level, c, 0.3f, 14, com.bending.ability.ImpactFx.fireColor(player));
            level.sendParticles(ModParticles.FIRE_SMOKE.get(), c.x, c.y + 0.2, c.z, 10, radius * 0.4, 0.1, radius * 0.4, 0.02);
            level.playSound(null, c.x, c.y, c.z, ModSounds.FIRE_EXPLOSION.get(), SoundSource.PLAYERS, 0.5f, 1.3f);
        }

        // Hits: anything at the ring's edge, low to the ground, that the drawn part of the ring has reached.
        for (LivingEntity target : BendingUtil.livingAround(player, c, radius + 0.8)) {
            if (data.sweepHit.contains(target.getId()) || target.getY() > c.y + 1.6 || target.getY() < c.y - 1.5) {
                continue;
            }
            double ddx = target.getX() - c.x, ddz = target.getZ() - c.z;
            double dist = Math.sqrt(ddx * ddx + ddz * ddz);
            if (dist < radius - 1.4) {
                continue;
            }
            double angle = Mth.positiveModulo(Math.atan2(ddz, ddx) - start, Math.PI * 2);
            if (angle > arc * Math.PI * 2 + 0.3) {
                continue;
            }
            data.sweepHit.add(target.getId());
            BendingUtil.damage(player, target, data.sweepDamage, FIRE_SECONDS);
            BendingUtil.knockAway(target, c, KNOCKBACK);
            // Swept off their feet.
            target.setDeltaMovement(target.getDeltaMovement().add(0, 0.35, 0));
        }
    }
}
