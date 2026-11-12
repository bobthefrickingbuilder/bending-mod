package com.bending.ability.fire;

import com.bending.particle.ModParticles;
import com.bending.sound.ModSounds;

import javax.annotation.Nullable;

import com.bending.ability.Ability;
import com.bending.ability.AbilitySlot;
import com.bending.ability.BendingUtil;
import com.bending.ability.CastType;
import com.bending.data.BendingData;
import com.bending.data.Element;
import com.bending.entity.ChargeOrbEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Slot 4 (single target). Hold to focus fire into one point, release to fire an instant,
 * explosive beam that strikes the first thing in its path. More charge = more damage and a bigger blast.
 */
public class CombustionBeam extends Ability {
    private static final double RANGE = 40.0;
    private static final int FULL_CHARGE = 30;
    private static final int MIN_CHARGE = 10;
    private static final float MIN_DAMAGE = 16f;
    private static final float BONUS_DAMAGE = 24f; // added at full charge (40 total)
    private static final double SPLASH_RADIUS = 2.5;
    /** Explosion power against terrain at minimum and full charge (a creeper is 3, TNT is 4). */
    private static final float MIN_TERRAIN_POWER = 2f;
    private static final float FULL_TERRAIN_POWER = 7f;

    /**
     * Grows with the square of the charge, so partial charges stay modest and a full charge
     * pays off: ~3.3 at half, 7 at full (about 4x the area destroyed at 3.5).
     */
    private static float terrainPower(float charge) {
        return MIN_TERRAIN_POWER + (FULL_TERRAIN_POWER - MIN_TERRAIN_POWER) * charge * charge;
    }

    public CombustionBeam() {
        super("combustion_beam", Element.FIRE, AbilitySlot.ULTIMATE, CastType.CHARGE);
    }

    @Override
    public float chiCost(int chargeTicks) {
        return 40f;
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
    public void onHoldTick(ServerPlayer player, BendingData data, int ticks, float power) {
        if (data.activeTicks == 1) {
            com.bending.ability.BendingAnimations.playForCast(player, data, com.bending.ability.BendingAnimations.COMBUSTION_FOCUS);
        }
        ServerLevel level = player.serverLevel();
        float progress = Math.min(1f, ticks / (float) FULL_CHARGE);

        // A ball of fire gathers between your hands and grows with the charge.
        ChargeOrbEntity orb = data.chargeOrb instanceof ChargeOrbEntity existing && !existing.isRemoved() ? existing : null;
        if (orb == null) {
            orb = new ChargeOrbEntity(level, player);
            level.addFreshEntity(orb);
            data.chargeOrb = orb;
        }
        orb.update(player, progress);
        Vec3 focus = orb.position();

        // Fire is drawn into it from all around.
        for (int i = 0; i < 4; i++) {
            Vec3 dir = new Vec3(player.getRandom().nextGaussian(), player.getRandom().nextGaussian(), player.getRandom().nextGaussian())
                    .normalize().scale(1.2);
            Vec3 p = focus.add(dir);
            Vec3 inward = dir.scale(-0.12);
            level.sendParticles(FireFx.wisp(player), p.x, p.y, p.z, 0, inward.x, inward.y, inward.z, 1.0);
        }
        level.sendParticles(FireFx.spark(player), focus.x, focus.y, focus.z, 1 + (int) (progress * 4), 0.05, 0.05, 0.05, 0.005);

        if (ticks == FULL_CHARGE) {
            level.playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.COMBUSTION_BEAM_READY.get(), SoundSource.PLAYERS, 0.7f, 1.8f);
        } else if (ticks < FULL_CHARGE && ticks % 5 == 0) {
            level.playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.COMBUSTION_BEAM_CHARGE.get(), SoundSource.PLAYERS, 0.7f, 0.6f + progress);
        }
        BendingUtil.chargeBar(player, progress);
    }

    @Override
    public void onChargeCancelled(ServerPlayer player, BendingData data) {
        removeOrb(data);
        BendingUtil.fizzle(player);
    }

    @Override
    public void onRelease(ServerPlayer player, BendingData data, int ticks, float power) {
        com.bending.ability.BendingAnimations.play(player, com.bending.ability.BendingAnimations.COMBUSTION_RELEASE);
        removeOrb(data);
        ServerLevel level = player.serverLevel();
        float charge = ticks / (float) FULL_CHARGE;
        Vec3 look = player.getLookAngle();
        Vec3 start = player.getEyePosition();
        double range = RANGE * com.bending.skill.SkillStats.ability(player, id(), com.bending.skill.SkillStat.RANGE);
        Vec3 end = start.add(look.scale(range));

        // First block in the way, then the first creature before that block.
        BlockHitResult blockHit = level.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        Vec3 stop = blockHit.getType() == HitResult.Type.MISS ? end : blockHit.getLocation();
        EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(level, player, start, stop,
                player.getBoundingBox().expandTowards(look.scale(range)).inflate(1.0),
                e -> e instanceof LivingEntity && e != player && !e.isSpectator() && e.isPickable());
        @Nullable Entity target = entityHit != null ? entityHit.getEntity() : null;
        Vec3 impact = entityHit != null ? entityHit.getLocation() : stop;

        // The beam itself.
        Vec3 from = start.add(look.scale(0.8)).add(0, -0.1, 0);
        double length = impact.distanceTo(from);
        for (double d = 0; d < length; d += 0.4) {
            Vec3 p = from.add(look.scale(d));
            level.sendParticles(FireFx.wisp(player), p.x, p.y, p.z, 2, 0.04 + 0.06 * charge, 0.04 + 0.06 * charge, 0.04 + 0.06 * charge, 0.0);
            if (d % 2.0 < 0.4) {
                level.sendParticles(FireFx.ember(player), p.x, p.y, p.z, 1, 0, 0, 0, 0);
            }
        }

        float damage = (MIN_DAMAGE + BONUS_DAMAGE * charge) * power;
        if (target instanceof LivingEntity living) {
            BendingUtil.damage(player, living, damage, 5);
        }
        if (target != null || blockHit.getType() != HitResult.Type.MISS) {
            BendingUtil.fireBurst(player, impact, SPLASH_RADIUS, damage * 0.3f, 1.0, 3, target, terrainPower(charge));
            BendingUtil.meltSnowAndIce(level, BlockPos.containing(impact), 2);
            BendingUtil.scatterFire(level, BlockPos.containing(impact), 4);
        }

        // Recoil.
        player.setDeltaMovement(player.getDeltaMovement().add(look.scale(-0.5 * charge)));
        player.hurtMarked = true;

        level.playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.COMBUSTION_BEAM_FIRE.get(), SoundSource.PLAYERS, 1.5f, 0.5f);
    }

    private static void removeOrb(BendingData data) {
        if (data.chargeOrb != null) {
            data.chargeOrb.discard();
            data.chargeOrb = null;
        }
    }
}
