package com.bending.entity;

import com.bending.ability.fire.FireFx;
import com.bending.particle.ModParticles;

import java.util.HashSet;
import java.util.Set;

import javax.annotation.Nullable;

import com.bending.ability.BendingUtil;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
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
 * Invisible effect entity for Firestorm. Follows its caster:
 * first an expanding ring of fire (7 blocks), then a 4-second vortex that burns nearby enemies
 * and burns up incoming projectiles.
 */
public class FirestormEntity extends Entity {
    private static final int RING_TICKS = 7;
    private static final int VORTEX_TICKS = 80;
    private static final double VORTEX_RADIUS = 4.5;
    /** The ring of fire rolls out to this radius over {@link #RING_TICKS}. */
    private static final double RING_RADIUS = 10.0;
    /** Terrain blast under the caster when Firestorm goes off (a creeper is 3, TNT is 4). */
    private static final float CENTER_TERRAIN_POWER = 4.0f;
    /** Circle of explosions halfway out (at 4 blocks). */
    private static final int MID_RING_TICK = 4;
    private static final int MID_BLASTS = 6;
    private static final float MID_TERRAIN_POWER = 2.0f;
    /** Circle of explosions at the ring's edge (7 blocks). */
    private static final int EDGE_BLASTS = 12;
    private static final float EDGE_TERRAIN_POWER = 2.5f;

    @Nullable
    private ServerPlayer owner;
    private float power = 1f;
    private final Set<Integer> ringHits = new HashSet<>();

    public FirestormEntity(EntityType<? extends FirestormEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
    }

    public FirestormEntity(Level level, ServerPlayer owner, float power) {
        this(ModEntities.FIRESTORM.get(), level);
        this.owner = owner;
        this.power = power;
        setPos(owner.getX(), owner.getY(), owner.getZ());
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {}

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel level)) {
            return;
        }
        if (owner == null || !owner.isAlive() || owner.level() != level || tickCount > RING_TICKS + VORTEX_TICKS) {
            discard();
            return;
        }
        setPos(owner.getX(), owner.getY(), owner.getZ());
        Vec3 center = position();

        if (tickCount == 1) {
            BendingUtil.terrainBlast(level, owner, center, CENTER_TERRAIN_POWER);
        }
        if (tickCount <= RING_TICKS) {
            double radius = tickCount * RING_RADIUS / RING_TICKS;
            // The ring scorches the ground as it passes...
            for (int i = 0; i < (int) radius; i++) {
                double angle = level.getRandom().nextDouble() * Math.PI * 2;
                BendingUtil.scatterFire(level, BlockPos.containing(
                        center.x + Math.cos(angle) * radius, center.y, center.z + Math.sin(angle) * radius), 1);
            }
            // ...erupts in a circle of explosions halfway out, then a bigger one at its edge.
            if (tickCount == MID_RING_TICK) {
                blastCircle(level, center, radius, MID_BLASTS, MID_TERRAIN_POWER);
            }
            if (tickCount == RING_TICKS) {
                blastCircle(level, center, radius, EDGE_BLASTS, EDGE_TERRAIN_POWER);
            }
            BendingUtil.ring(level, FireFx.wisp(owner), center.add(0, 0.4, 0), radius, (int) (radius * 22));
            BendingUtil.ring(level, FireFx.wisp(owner), center.add(0, 1.4, 0), radius * 0.97, (int) (radius * 10));
            BendingUtil.ring(level, FireFx.cloud(owner), center.add(0, 0.8, 0), radius, (int) (radius * 3));
            BendingUtil.ring(level, FireFx.ember(owner), center.add(0, 0.4, 0), radius, (int) (radius * 2));
            for (LivingEntity target : BendingUtil.livingAround(owner, center, radius + 0.5)) {
                if (ringHits.add(target.getId())) {
                    BendingUtil.damage(owner, target, 24f * power, 4); // ring: one-shots normal mobs
                    BendingUtil.knockAway(target, center, 1.4);
                }
            }
            return;
        }

        // Vortex: five spiralling strands of flame climbing 6 blocks, with clouds of fire rolling up the middle.
        for (int strand = 0; strand < 5; strand++) {
            double angle = tickCount * 0.45 + strand * (Math.PI * 2 / 5);
            for (int layer = 0; layer < 3; layer++) {
                double height = ((tickCount + layer * 10) % 30) / 5.0;
                double r = VORTEX_RADIUS * (0.6 + height / 15);
                level.sendParticles(FireFx.wisp(owner),
                        center.x + Math.cos(angle + layer) * r, center.y + height, center.z + Math.sin(angle + layer) * r,
                        3, 0.15, 0.15, 0.15, 0.02);
            }
        }
        level.sendParticles(FireFx.cloud(owner), center.x, center.y + 0.5, center.z, 3, VORTEX_RADIUS * 0.4, 0.3, VORTEX_RADIUS * 0.4, 0.05);
        if (tickCount % 2 == 0) {
            level.sendParticles(FireFx.ember(owner), center.x, center.y + 1, center.z, 3, VORTEX_RADIUS * 0.5, 1.0, VORTEX_RADIUS * 0.5, 0.1);
        }
        BendingUtil.destroyProjectiles(level, owner, center.add(0, 1, 0), VORTEX_RADIUS + 0.5);
        if (tickCount % 20 == 0) {
            for (LivingEntity target : BendingUtil.livingAround(owner, center.add(0, 1, 0), VORTEX_RADIUS)) {
                BendingUtil.damage(owner, target, 6f * power, 2); // vortex, once per second
            }
        }
    }

    private void blastCircle(ServerLevel level, Vec3 center, double radius, int count, float power) {
        double offset = level.getRandom().nextDouble() * Math.PI * 2;
        for (int i = 0; i < count; i++) {
            double angle = offset + (Math.PI * 2 * i) / count;
            BendingUtil.terrainBlast(level, owner,
                    center.add(Math.cos(angle) * radius, 0.5, Math.sin(angle) * radius), power);
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
