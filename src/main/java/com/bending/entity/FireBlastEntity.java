package com.bending.entity;

import com.bending.particle.ModParticles;

import javax.annotation.Nullable;

import com.bending.ability.BendingUtil;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** A small, fast fireball. Flies straight (no gravity), burns what it hits, fizzles in water. */
public class FireBlastEntity extends Projectile {
    /**
     * Client hook: true if a point is too close to the camera to draw trail fire there (so it
     * doesn't flash in the viewer's face). Set by the client; always false on a server.
     */
    public static java.util.function.Predicate<Vec3> tooCloseToCamera = p -> false;
    private static final int MAX_LIFE = 14; // ~40 blocks at the default speed
    /** Snow and ice within this many blocks of the impact melt. */
    private static final int MELT_RADIUS = 2;
    private static final double SPLASH_RADIUS = 1.8;
    /** Splash damage as a share of the direct-hit damage. */
    private static final float SPLASH_FRACTION = 0.5f;
    /** Attempts at placing fire blocks around the impact (needs the mobGriefing game rule). */
    private static final int LINGERING_FIRES = 3;
    /** Explosion strength against terrain on impact (a creeper is 3, TNT is 4). */
    private static final float TERRAIN_POWER = 1.6f;

    private float damage = 4f;

    public FireBlastEntity(EntityType<? extends FireBlastEntity> type, Level level) {
        super(type, level);
    }

    public FireBlastEntity(Level level, LivingEntity owner, float damage) {
        this(ModEntities.FIRE_BLAST.get(), level);
        setOwner(owner);
        this.damage = damage;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {}

    @Override
    public void tick() {
        super.tick();
        Level level = level();

        if (!level.isClientSide) {
            if (isInWater()) {
                ((ServerLevel) level).sendParticles(ModParticles.FIRE_SMOKE.get(), getX(), getY(), getZ(), 8, 0.1, 0.1, 0.1, 0.02);
                discard();
                return;
            }
            if (tickCount > MAX_LIFE) {
                discard();
                return;
            }
            if (level.isRainingAt(blockPosition())) {
                damage *= 0.97f; // rain slowly douses the flame
            }
            HitResult hit = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
            if (hit.getType() != HitResult.Type.MISS) {
                onHit(hit);
                discard();
                return;
            }
        }

        Vec3 v = getDeltaMovement();
        setPos(getX() + v.x, getY() + v.y, getZ() + v.z);

        if (level.isClientSide) {
            // The glowing ball itself is drawn by FireBlastRenderer; sparks flick off it.
            if (random.nextInt(2) == 0 && !tooCloseToCamera.test(position())) {
                level.addParticle(ModParticles.FIRE_SPARK.get(), getX(), getY(), getZ(),
                        (random.nextDouble() - 0.5) * 0.3, (random.nextDouble() - 0.5) * 0.3, (random.nextDouble() - 0.5) * 0.3);
            }
            // Comet tail: it moves 3 blocks a tick, so fill in flame tongues along the whole step.
            // They drift backwards, so they point back along the flight path.
            int steps = 6;
            for (int i = 0; i < steps; i++) {
                double t = i / (double) steps;
                double x = getX() - v.x * t;
                double y = getY() - v.y * t;
                double z = getZ() - v.z * t;
                if (tooCloseToCamera.test(new Vec3(x, y, z))) {
                    continue;
                }
                level.addParticle(ModParticles.FIRE_WISP.get(),
                        x + (random.nextDouble() - 0.5) * 0.25,
                        y + (random.nextDouble() - 0.5) * 0.25,
                        z + (random.nextDouble() - 0.5) * 0.25,
                        -v.x * 0.03, -v.y * 0.03, -v.z * 0.03);
            }
            if (tickCount % 3 == 0) {
                level.addParticle(ModParticles.FIRE_SMOKE.get(), getX() - v.x, getY() - v.y, getZ() - v.z, 0, 0.02, 0);
            }
        }
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        super.onHitEntity(result);
        Entity target = result.getEntity();
        if (target instanceof LivingEntity living) {
            if (getOwner() instanceof ServerPlayer bender) {
                BendingUtil.damage(bender, living, damage, 3);
            } else {
                living.hurt(damageSources().onFire(), damage);
                living.igniteForSeconds(3);
            }
        }
        explode(result.getLocation(), target, target.blockPosition());
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        super.onHitBlock(result);
        // Pull the blast point slightly back out of the block face.
        Vec3 point = result.getLocation().add(Vec3.atLowerCornerOf(result.getDirection().getNormal()).scale(0.3));
        explode(point, null, result.getBlockPos().relative(result.getDirection()));
    }

    /** Small splash explosion that leaves a few flames burning behind. */
    private void explode(Vec3 point, @Nullable Entity directHit, BlockPos firePos) {
        if (!(level() instanceof ServerLevel level)) {
            return;
        }
        if (getOwner() instanceof ServerPlayer bender) {
            BendingUtil.fireBurst(bender, point, SPLASH_RADIUS, damage * SPLASH_FRACTION, 0.4, 2, directHit, TERRAIN_POWER);
        } else {
            BendingUtil.terrainBlast(level, this, point, TERRAIN_POWER);
        }
        // Melt first, so fire can land where the snow was.
        BendingUtil.meltSnowAndIce(level, BlockPos.containing(point), MELT_RADIUS);
        BendingUtil.scatterFire(level, firePos, LINGERING_FIRES);
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putFloat("damage", damage);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        damage = tag.getFloat("damage");
    }
}
