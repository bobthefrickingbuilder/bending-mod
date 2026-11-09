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
 * Fire Arc: a wide crescent of fire that sweeps forward, passing through everything it hits.
 * Drawn entirely with particles (server side), so it has no model.
 */
public class FireArcEntity extends Entity {
    public static final double SPEED = 1.2;
    public static final double RANGE = 12.0;
    /** Half the crescent's width (4 blocks across). */
    private static final double HALF_WIDTH = 2.0;
    /** How far the crescent's tips trail behind its middle. */
    private static final double BEND = 0.9;
    private static final int POINTS = 11;
    private static final double HIT_RADIUS = 0.9;

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

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {}

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel level)) {
            return;
        }
        if (owner == null || owner.level() != level || tickCount * SPEED > RANGE) {
            discard();
            return;
        }
        Vec3 next = position().add(dir.scale(SPEED));
        // The middle of the crescent stops at walls (with a small burst); it passes through creatures.
        BlockPos blockAt = BlockPos.containing(next);
        if (!level.getBlockState(blockAt).getCollisionShape(level, blockAt).isEmpty() || isInWater()) {
            level.sendParticles(FireFx.wisp(owner), getX(), getY(), getZ(), 12, 0.6, 0.3, 0.6, 0.05);
            level.sendParticles(ModParticles.FIRE_SMOKE.get(), getX(), getY(), getZ(), 4, 0.5, 0.2, 0.5, 0.02);
            discard();
            return;
        }
        setPos(next.x, next.y, next.z);

        for (int i = 0; i < POINTS; i++) {
            double u = (i / (double) (POINTS - 1)) * 2 - 1;                 // -1 .. 1 across the crescent
            Vec3 p = position().add(side.scale(u * HALF_WIDTH)).subtract(dir.scale(BEND * u * u));
            // Flames drift back along the path, so their tips trail behind the sweep.
            level.sendParticles(FireFx.wisp(owner), p.x, p.y, p.z, 0, -dir.x, -dir.y + 0.15, -dir.z, 0.15);
            if (i % 3 == 0) {
                level.sendParticles(FireFx.spark(owner), p.x, p.y, p.z, 1, 0.1, 0.1, 0.1, 0.05);
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
