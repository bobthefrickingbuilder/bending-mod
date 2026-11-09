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
 * Slot 4 (Ultimate): hold to breathe a torrent of fire that follows where you look. It rolls out
 * as huge billowing clouds of flame, 50 blocks long and about 40 across at the far end.
 * Everything in it burns (5 damage every 0.2 s and set alight) and gets pushed back; the ground
 * it reaches catches fire. Up to 5 seconds per breath, and it eats chi fast.
 */
public class DragonsBreath extends Ability {
    private static final double RANGE = 50.0;
    /** Cone half-angle: cos(22 degrees), about 20 blocks either side at the far end. */
    private static final double CONE = 0.927;
    /** Spread of the thrown flames (standard deviation of the direction jitter). */
    private static final double SPREAD = 0.16;
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
        level.playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.DRAGONS_BREATH_START.get(), SoundSource.PLAYERS, 1.5f, 0.6f);
    }

    private static Vec3 jitter(Vec3 look, RandomSource random, double spread) {
        return look.add(random.nextGaussian() * spread, random.nextGaussian() * spread, random.nextGaussian() * spread).normalize();
    }

    @Override
    public void onHoldTick(ServerPlayer player, BendingData data, int ticks, float power) {
        ServerLevel level = player.serverLevel();
        RandomSource random = player.getRandom();
        Vec3 look = player.getLookAngle();
        Vec3 mouth = player.getEyePosition().add(look.scale(0.6)).add(0, -0.15, 0);
        // The big clouds start a little way out so they don't swallow your own view.
        Vec3 billow = mouth.add(look.scale(1.8));
        boolean blue = FireTechniques.blue(player);

        // The torrent: swelling clouds that roll the full 50 blocks...
        for (int i = 0; i < 12; i++) {
            Vec3 dir = jitter(look, random, SPREAD);
            level.sendParticles(FireFx.cloud(blue), billow.x, billow.y, billow.z, 0, dir.x, dir.y, dir.z, 2.1 + random.nextDouble() * 0.8);
        }
        // ...a dense core of flame tongues near the mouth...
        for (int i = 0; i < 14; i++) {
            Vec3 dir = jitter(look, random, SPREAD * 0.8);
            level.sendParticles(FireFx.wisp(blue), mouth.x, mouth.y, mouth.z, 0, dir.x, dir.y, dir.z, 1.4 + random.nextDouble() * 1.2);
        }
        // ...and sparks and embers thrown out through it.
        for (int i = 0; i < 4; i++) {
            Vec3 dir = jitter(look, random, SPREAD * 1.2);
            level.sendParticles(FireFx.spark(blue), mouth.x, mouth.y, mouth.z, 0, dir.x, dir.y, dir.z, 2.0);
            level.sendParticles(FireFx.ember(blue), mouth.x, mouth.y, mouth.z, 0, dir.x, dir.y + 0.1, dir.z, 1.6);
        }
        if (ticks % 3 == 0) {
            Vec3 far = mouth.add(look.scale(RANGE * (0.4 + random.nextDouble() * 0.5)));
            level.sendParticles(ModParticles.FIRE_SMOKE.get(), far.x, far.y + 2, far.z, 6, 4, 2, 4, 0.03);
        }

        if (ticks % DAMAGE_EVERY == 0) {
            for (LivingEntity target : BendingUtil.livingAround(player, mouth, RANGE)) {
                Vec3 at = target.position().add(0, target.getBbHeight() * 0.5, 0);
                Vec3 to = at.subtract(mouth);
                if (to.normalize().dot(look) < CONE || !canSee(level, player, mouth, at)) {
                    continue;
                }
                BendingUtil.damage(player, target, DAMAGE * power, 4);
                target.push(look.x * 0.25, 0.04, look.z * 0.25);
                target.hurtMarked = true;
            }
            for (double d = 3; d < RANGE; d += 6) {
                BendingUtil.destroyProjectiles(level, player, mouth.add(look.scale(d)), 1.5 + d * 0.35);
            }
        }

        // Wherever the torrent reaches the ground, it catches fire and snow melts.
        if (ticks % 3 == 0) {
            for (int i = 0; i < 4; i++) {
                Vec3 dir = jitter(look, random, SPREAD * 1.4);
                BlockHitResult hit = level.clip(new ClipContext(mouth, mouth.add(dir.scale(RANGE)),
                        ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
                if (hit.getType() != HitResult.Type.MISS) {
                    BlockPos pos = hit.getBlockPos().relative(hit.getDirection());
                    BendingUtil.meltSnowAndIce(level, pos, 2);
                    BendingUtil.scatterFire(level, pos, 2);
                }
            }
        }

        if (ticks % 4 == 0) {
            level.playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.DRAGONS_BREATH_LOOP.get(),
                    SoundSource.PLAYERS, 1.4f, 0.5f + random.nextFloat() * 0.2f);
        }
    }

    private static boolean canSee(ServerLevel level, ServerPlayer player, Vec3 from, Vec3 to) {
        return level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player))
                .getType() == HitResult.Type.MISS;
    }
}
