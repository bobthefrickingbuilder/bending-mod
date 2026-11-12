package com.bending.ability.fire;

import com.bending.particle.ModParticles;
import com.bending.sound.ModSounds;

import com.bending.ability.Ability;
import com.bending.ability.AbilitySlot;
import com.bending.ability.BendingUtil;
import com.bending.ability.CastType;
import com.bending.data.BendingData;
import com.bending.data.Element;
import com.bending.entity.FirestormEntity;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

/**
 * Slot 4 (area). Hold to gather fire for 1 second (it goes off by itself when full),
 * then erupt: a cratering blast, an expanding ring of fire and explosions, and a flaming vortex.
 * Letting go early cancels it for free.
 */
public class Firestorm extends Ability {
    private static final int CHARGE_TICKS = 20;
    private static final double RING_RADIUS = 7.0;
    private static final int NO_FALL_TICKS = 60;

    public Firestorm() {
        super("firestorm", Element.FIRE, AbilitySlot.ULTIMATE, CastType.CHARGE);
    }

    @Override
    public float chiCost(int chargeTicks) {
        return 45f;
    }

    @Override
    public int maxChargeTicks() {
        return CHARGE_TICKS;
    }

    @Override
    public int minChargeTicks() {
        return CHARGE_TICKS; // must charge fully
    }

    @Override
    public boolean autoReleaseAtMax() {
        return true;
    }

    @Override
    public void onHoldTick(ServerPlayer player, BendingData data, int ticks, float power) {
        if (data.activeTicks == 1) {
            com.bending.ability.BendingAnimations.playForCast(player, data, com.bending.ability.BendingAnimations.FIRESTORM_STANCE);
        }
        ServerLevel level = player.serverLevel();
        float progress = ticks / (float) CHARGE_TICKS;
        Vec3 center = player.position();

        // Flames get pulled in from the edge of the blast toward you.
        double radius = RING_RADIUS * (1 - progress) + 1;
        for (int i = 0; i < 12; i++) {
            double angle = player.getRandom().nextDouble() * Math.PI * 2;
            Vec3 p = center.add(Math.cos(angle) * radius, 0.2 + player.getRandom().nextDouble() * 1.6, Math.sin(angle) * radius);
            Vec3 toCenter = center.add(0, 1, 0).subtract(p).scale(0.08);
            level.sendParticles(FireFx.wisp(player), p.x, p.y, p.z, 0, toCenter.x, toCenter.y, toCenter.z, 1.0);
        }
        // Mark where the ring will reach.
        if (ticks % 4 == 0) {
            BendingUtil.ring(level, FireFx.spark(player), center.add(0, 0.1, 0), RING_RADIUS, 36);
            level.playSound(null, center.x, center.y, center.z, ModSounds.FIRESTORM_CHARGE.get(), SoundSource.PLAYERS, 0.8f, 0.5f + progress);
        }
        BendingUtil.chargeBar(player, progress);
    }

    @Override
    public void onChargeCancelled(ServerPlayer player, BendingData data) {
        BendingUtil.fizzle(player);
    }

    @Override
    public void onRelease(ServerPlayer player, BendingData data, int ticks, float power) {
        com.bending.ability.BendingAnimations.play(player, com.bending.ability.BendingAnimations.FIRESTORM_STOMP);
        player.level().addFreshEntity(new FirestormEntity(player.level(), player, power, com.bending.skill.SkillStats.ability(player, id(), com.bending.skill.SkillStat.SIZE)));
        data.noFallUntil = player.level().getGameTime() + NO_FALL_TICKS; // you'll drop into your own crater
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                ModSounds.FIRESTORM_ERUPT.get(), SoundSource.PLAYERS, 1.2f, 0.5f);
    }
}
