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
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Slot 4 (Ultimate): hold to breathe a roaring cone of fire, 10 blocks long, that follows where
 * you look. Everything in the cone burns (5 damage every 0.2 s and set alight) and gets pushed
 * back; the ground it reaches catches fire. Up to 5 seconds per breath, and it eats chi fast.
 */
public class DragonsBreath extends Ability {
    private static final double RANGE = 10.0;
    /** Cone half-angle: cos(22 degrees). */
    private static final double CONE = 0.927;
    private static final float DAMAGE = 5f;
    private static final int DAMAGE_EVERY = 4;
    private static final int MAX_TICKS = 100;

    public DragonsBreath() {
        super("dragons_breath", Element.FIRE, AbilitySlot.ULTIMATE, CastType.HOLD);
    }

    @Override
    public float chiCost(int chargeTicks) {
        return 0f;
    }

    @Override
    public float startChiCost() {
        return 10f;
    }

    @Override
    public float chiPerTick() {
        return 1.1f; // 22 chi per second
    }

    @Override
    public int maxHoldTicks() {
        return MAX_TICKS;
    }

    @Override
    public void onStart(ServerPlayer player, BendingData data, float power) {
        ServerLevel level = player.serverLevel();
        // A sharp inhale.
        level.playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.DRAGONS_BREATH_START.get(), SoundSource.PLAYERS, 0.9f, 0.7f);
    }

    @Override
    public void onHoldTick(ServerPlayer player, BendingData data, int ticks, float power) {
        ServerLevel level = player.serverLevel();
        RandomSource random = player.getRandom();
        Vec3 look = player.getLookAngle();
        Vec3 mouth = player.getEyePosition().add(look.scale(0.6)).add(0, -0.15, 0);
        boolean blue = FireTechniques.blue(player);

        // The stream: flames thrown forward with a widening spread, so it fans out into a cone.
        for (int i = 0; i < 7; i++) {
            Vec3 dir = look.add(random.nextGaussian() * 0.12, random.nextGaussian() * 0.12, random.nextGaussian() * 0.12).normalize();
            double speed = 0.9 + random.nextDouble() * 0.5;
            level.sendParticles(FireFx.wisp(blue), mouth.x, mouth.y, mouth.z, 0, dir.x, dir.y, dir.z, speed);
        }
        if (ticks % 2 == 0) {
            Vec3 dir = look.add(random.nextGaussian() * 0.2, random.nextGaussian() * 0.2, random.nextGaussian() * 0.2).normalize();
            level.sendParticles(FireFx.spark(blue), mouth.x, mouth.y, mouth.z, 0, dir.x, dir.y, dir.z, 1.0);
            level.sendParticles(FireFx.ember(blue), mouth.x, mouth.y, mouth.z, 0, dir.x, dir.y + 0.1, dir.z, 0.6);
        }
        if (ticks % 5 == 0) {
            Vec3 tip = mouth.add(look.scale(RANGE * 0.8));
            level.sendParticles(ModParticles.FIRE_SMOKE.get(), tip.x, tip.y, tip.z, 2, 0.6, 0.4, 0.6, 0.02);
        }

        if (ticks % DAMAGE_EVERY == 0) {
            for (LivingEntity target : BendingUtil.livingAround(player, mouth, RANGE)) {
                Vec3 at = target.position().add(0, target.getBbHeight() * 0.5, 0);
                Vec3 to = at.subtract(mouth);
                if (to.normalize().dot(look) < CONE || !canSee(level, player, mouth, at)) {
                    continue;
                }
                BendingUtil.damage(player, target, DAMAGE * power, 4);
                target.push(look.x * 0.15, 0.03, look.z * 0.15);
                target.hurtMarked = true;
            }
            BendingUtil.destroyProjectiles(level, player, mouth.add(look.scale(2.5)), 2.0);
        }

        // Where the breath reaches the ground, it catches fire and snow melts.
        if (ticks % 6 == 0) {
            BlockHitResult hit = level.clip(new ClipContext(mouth, mouth.add(look.scale(RANGE)),
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
            if (hit.getType() != HitResult.Type.MISS) {
                BlockPos pos = hit.getBlockPos().relative(hit.getDirection());
                BendingUtil.meltSnowAndIce(level, pos, 1);
                BendingUtil.scatterFire(level, pos, 1);
            }
        }

        if (ticks % 4 == 0) {
            level.playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.DRAGONS_BREATH_LOOP.get(),
                    SoundSource.PLAYERS, 0.6f, 0.6f + random.nextFloat() * 0.2f);
        }
    }

    private static boolean canSee(ServerLevel level, ServerPlayer player, Vec3 from, Vec3 to) {
        return level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player))
                .getType() == HitResult.Type.MISS;
    }
}
