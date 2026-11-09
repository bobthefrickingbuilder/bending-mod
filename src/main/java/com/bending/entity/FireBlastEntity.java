package com.bending.entity;

import com.bending.ability.fire.FireFx;
import com.bending.particle.ModParticles;

import javax.annotation.Nullable;

import com.bending.ability.BendingUtil;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
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
    /** Chance per tick of a cloud puff rolling off the back (kept low so the flames show through). */
    private static final float CLOUD_CHANCE = 0.3f;
    private static final EntityDataAccessor<Boolean> BLUE =
            SynchedEntityData.defineId(FireBlastEntity.class, EntityDataSerializers.BOOLEAN);
    /** Size of the ball (1 = a normal Fire Blast); charged fireballs are bigger. */
    private static final EntityDataAccessor<Float> BALL_SCALE =
            SynchedEntityData.defineId(FireBlastEntity.class, EntityDataSerializers.FLOAT);
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
    private double splashRadius = SPLASH_RADIUS;
    private float terrainPower = TERRAIN_POWER;
    private int lingeringFires = LINGERING_FIRES;
    private int maxLife = MAX_LIFE;

    public FireBlastEntity(EntityType<? extends FireBlastEntity> type, Level level) {
        super(type, level);
    }

    public FireBlastEntity(Level level, LivingEntity owner, float damage) {
        this(ModEntities.FIRE_BLAST.get(), level);
        setOwner(owner);
        this.damage = damage;
        entityData.set(BLUE, com.bending.ability.fire.FireTechniques.blue(owner));
    }

    /** A bigger blast (Fireball Charge). */
    public FireBlastEntity configure(float scale, double splashRadius, float terrainPower, int lingeringFires, int maxLife) {
        entityData.set(BALL_SCALE, scale);
        this.splashRadius = splashRadius;
        this.terrainPower = terrainPower;
        this.lingeringFires = lingeringFires;
        this.maxLife = maxLife;
        refreshDimensions();
        return this;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(BLUE, false);
        builder.define(BALL_SCALE, 1f);
    }

    public boolean isBlue() {
        return entityData.get(BLUE);
    }

    public float getBallScale() {
        return entityData.get(BALL_SCALE);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (BALL_SCALE.equals(key)) {
            refreshDimensions();
        }
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        return super.getDimensions(pose).scale(getBallScale());
    }

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
            if (tickCount > maxLife) {
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
                level.addParticle(FireFx.spark(isBlue()), getX(), getY(), getZ(),
                        (random.nextDouble() - 0.5) * 0.3, (random.nextDouble() - 0.5) * 0.3, (random.nextDouble() - 0.5) * 0.3);
            }
            // Comet tail: it moves 3 blocks a tick, so fill in flame tongues along the whole step.
            // They drift backwards, so they point back along the flight path.
            int steps = 10;
            for (int i = 0; i < steps; i++) {
                double t = i / (double) steps;
                double x = getX() - v.x * t;
                double y = getY() - v.y * t;
                double z = getZ() - v.z * t;
                if (tooCloseToCamera.test(new Vec3(x, y, z))) {
                    continue;
                }
                double spread = 0.45 * getBallScale();
                for (int k = 0; k < 2; k++) {
                    level.addParticle(FireFx.wisp(isBlue()),
                            x + (random.nextDouble() - 0.5) * spread,
                            y + (random.nextDouble() - 0.5) * spread,
                            z + (random.nextDouble() - 0.5) * spread,
                            -v.x * 0.03, -v.y * 0.03, -v.z * 0.03);
                }
                // Bright hot flashes through the tail.
                if (i % 3 == 0) {
                    level.addParticle(FireFx.core(isBlue()), x, y, z, -v.x * 0.02, -v.y * 0.02, -v.z * 0.02);
                }
            }
            if (tickCount % 3 == 0) {
                level.addParticle(ModParticles.FIRE_SMOKE.get(), getX() - v.x, getY() - v.y, getZ() - v.z, 0, 0.02, 0);
            }
            // Billowing clouds rolling off the back of the ball.
            Vec3 back = getPosition(0).subtract(v.scale(0.6 + random.nextDouble() * 0.4));
            if (random.nextFloat() < CLOUD_CHANCE && !tooCloseToCamera.test(back)) {
                level.addParticle(FireFx.cloud(isBlue()), back.x, back.y, back.z, -v.x * 0.04, 0.02, -v.z * 0.04);
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
            BendingUtil.fireBurst(bender, point, splashRadius, damage * SPLASH_FRACTION, 0.4 * getBallScale(), 2, directHit, terrainPower);
        } else {
            BendingUtil.terrainBlast(level, this, point, terrainPower);
        }
        // Melt first, so fire can land where the snow was.
        BendingUtil.meltSnowAndIce(level, BlockPos.containing(point), Math.round(MELT_RADIUS * getBallScale()));
        BendingUtil.scatterFire(level, firePos, lingeringFires);
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putFloat("damage", damage);
        tag.putFloat("scale", getBallScale());
        tag.putDouble("splash", splashRadius);
        tag.putFloat("terrain", terrainPower);
        tag.putInt("fires", lingeringFires);
        tag.putInt("life", maxLife);
        tag.putBoolean("blue", isBlue());
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        damage = tag.getFloat("damage");
        if (tag.contains("scale")) {
            configure(tag.getFloat("scale"), tag.getDouble("splash"), tag.getFloat("terrain"), tag.getInt("fires"), tag.getInt("life"));
        }
        entityData.set(BLUE, tag.getBoolean("blue"));
    }
}
