package com.bending.entity;

import com.bending.ability.fire.FireFx;
import java.util.HashSet;
import java.util.Set;

import javax.annotation.Nullable;

import com.bending.ability.BendingUtil;
import com.bending.particle.ModParticles;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Fire Arc: a wide crescent of fire (7 blocks across) that sweeps 18 blocks forward, passing through everything it hits.
 * Drawn entirely with particles (server side), so it has no model.
 */
public class FireArcEntity extends Entity {
    public static final double SPEED = 1.2;
    public static final double RANGE = 18.0;
    /** Half the crescent's width (7 blocks across). */
    private static final double HALF_WIDTH = 3.5;
    /** How far the crescent's tips trail behind its middle. */
    private static final double BEND = 1.5;
    private static final int POINTS = 17;
    private static final double HIT_RADIUS = 1.2;

    @Nullable
    private ServerPlayer owner;
    private Vec3 dir = Vec3.ZERO;
    private Vec3 side = Vec3.ZERO;
    private float damage;
    private final Set<Integer> hits = new HashSet<>();

    public FireArcEntity(EntityType<? extends FireArcEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
    }

    public FireArcEntity(Level level, ServerPlayer owner, Vec3 start, Vec3 dir, Vec3 side, float damage) {
        this(ModEntities.FIRE_ARC.get(), level);
        this.owner = owner;
        this.dir = dir.normalize();
        this.side = side.normalize();
        this.damage = damage;
        setPos(start.x, start.y, start.z);
    }

    /** How far the crescent sweeps (Fire Arc: Reach). */
    private double range = RANGE;

    public FireArcEntity withRange(double range) {
        this.range = range;
        return this;
    }

    /** Air Kick: when the crescent hits the ground it bursts into a splash of flame. */
    private boolean groundBurst;

    public FireArcEntity withGroundBurst() {
        this.groundBurst = true;
        return this;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {}

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel level)) {
            return;
        }
        if (owner == null || owner.level() != level || tickCount * SPEED > range) {
            discard();
            return;
        }
        Vec3 next = position().add(dir.scale(SPEED));
        // The middle of the crescent stops at walls (with a small burst); it passes through creatures.
        BlockPos blockAt = BlockPos.containing(next);
        if (!level.getBlockState(blockAt).getCollisionShape(level, blockAt).isEmpty() || isInWater()) {
            level.sendParticles(FireFx.wisp(owner), getX(), getY(), getZ(), 12, 0.6, 0.3, 0.6, 0.05);
            level.sendParticles(ModParticles.FIRE_SMOKE.get(), getX(), getY(), getZ(), 4, 0.5, 0.2, 0.5, 0.02);
            if (groundBurst) {
                Vec3 at = position().add(0, 0.3, 0);
                com.bending.ability.BendingUtil.fireBurst(owner, at, 3.0, damage * 0.6f, 0.9, 3, null, 0f);
                com.bending.ability.BendingUtil.ring(level, FireFx.wisp(owner), at, 2.2, 36);
                com.bending.ability.BendingUtil.scatterFire(level, blockAt.above(), 2);
            }
            discard();
            return;
        }
        setPos(next.x, next.y, next.z);

        for (int i = 0; i < POINTS; i++) {
            double u = (i / (double) (POINTS - 1)) * 2 - 1;                 // -1 .. 1 across the crescent
            Vec3 p = position().add(side.scale(u * HALF_WIDTH)).subtract(dir.scale(BEND * u * u));
            // Flames drift back along the path, so their tips trail behind the sweep.
            for (int k = 0; k < 2; k++) {
                level.sendParticles(FireFx.wisp(owner), p.x, p.y + (k - 0.5) * 0.35, p.z, 0, -dir.x, -dir.y + 0.15, -dir.z, 0.15 + k * 0.1);
            }
            if (i % 2 == 0) {
                level.sendParticles(FireFx.cloud(owner), p.x, p.y, p.z, 0, -dir.x, -dir.y + 0.2, -dir.z, 0.12);
            }
            if (i % 3 == 0) {
                level.sendParticles(FireFx.spark(owner), p.x, p.y, p.z, 2, 0.15, 0.15, 0.15, 0.08);
            }
            for (LivingEntity target : BendingUtil.livingAround(owner, p, HIT_RADIUS)) {
                if (hits.add(target.getId())) {
                    BendingUtil.damage(owner, target, damage, 3);
                    target.knockback(0.5, -dir.x, -dir.z);
                    target.hurtMarked = true;
                }
            }
        }
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {}

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {}
}
