package com.bending.entity;

import java.util.List;

import javax.annotation.Nullable;

import com.bending.ability.BendingUtil;
import com.bending.ability.fire.FireFx;
import com.bending.ability.fire.FireTechniques;
import com.bending.particle.ModParticles;
import com.bending.sound.ModSounds;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Wall of Fire: a flat sheet of flame standing on the ground. The server burns projectiles that
 * reach it and scorches creatures touching it; clients fill it with rising flames.
 * The position is the middle of its base; it runs sideways along {@link #along()}.
 */
public class WallOfFireEntity extends Entity {
    public static final double HALF_WIDTH = 3.5;
    public static final double HEIGHT = 3.5;
    public static final int LIFE = 120;
    private static final int RISE_TICKS = 6;
    /** How close to the sheet a creature has to be to get burned. */
    private static final double TOUCH = 1.1;
    /** Projectiles this close to the sheet burn up (generous, so fast arrows can't skip past it). */
    private static final double CATCH = 2.2;
    private static final float DAMAGE = 4f;

    private static final EntityDataAccessor<Float> YAW =
            SynchedEntityData.defineId(WallOfFireEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> BLUE =
            SynchedEntityData.defineId(WallOfFireEntity.class, EntityDataSerializers.BOOLEAN);

    @Nullable
    private ServerPlayer owner;
    private float power = 1f;

    public WallOfFireEntity(EntityType<? extends WallOfFireEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
    }

    public WallOfFireEntity(Level level, ServerPlayer owner, Vec3 base, float yaw, float power) {
        this(ModEntities.WALL_OF_FIRE.get(), level);
        this.owner = owner;
        this.power = power;
        entityData.set(YAW, yaw);
        entityData.set(BLUE, FireTechniques.blue(owner));
        setPos(base.x, base.y, base.z);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(YAW, 0f);
        builder.define(BLUE, false);
    }

    /** The direction the wall faces (away from its caster). */
    public Vec3 normal() {
        float yaw = entityData.get(YAW) * Mth.DEG_TO_RAD;
        return new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
    }

    /** The direction it runs, side to side. */
    public Vec3 along() {
        Vec3 n = normal();
        return new Vec3(-n.z, 0, n.x);
    }

    /** 0 while rising, up to 1 once fully risen; shrinks again near the end. */
    private float strength(float partial) {
        float t = tickCount + partial;
        float rise = Mth.clamp(t / RISE_TICKS, 0f, 1f);
        float fade = Mth.clamp((LIFE - t) / 15f, 0f, 1f);
        return Math.min(rise, fade);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            spawnFlames();
            return;
        }
        ServerLevel level = (ServerLevel) level();
        if (owner == null || owner.level() != level || tickCount > LIFE) {
            discard();
            return;
        }
        Vec3 base = position();
        Vec3 normal = normal();
        Vec3 along = along();
        double height = HEIGHT * strength(0);

        // Burn up projectiles that reach the sheet, wherever along it they cross.
        AABB box = new AABB(base, base).inflate(HALF_WIDTH + CATCH, 0, HALF_WIDTH + CATCH).expandTowards(0, height + 1, 0);
        List<Projectile> projectiles = level.getEntitiesOfClass(Projectile.class, box, p -> p.getOwner() != owner);
        for (Projectile p : projectiles) {
            Vec3 rel = p.position().subtract(base);
            if (Math.abs(rel.dot(normal)) <= CATCH && Math.abs(rel.dot(along)) <= HALF_WIDTH + 0.5
                    && rel.y >= -0.5 && rel.y <= height + 0.5) {
                level.sendParticles(ModParticles.FIRE_SMOKE.get(), p.getX(), p.getY(), p.getZ(), 6, 0.1, 0.1, 0.1, 0.02);
                level.sendParticles(FireFx.spark(isBlue()), p.getX(), p.getY(), p.getZ(), 6, 0.1, 0.1, 0.1, 0.2);
                level.playSound(null, p.getX(), p.getY(), p.getZ(), ModSounds.FIRE_SHIELD_DEFLECT.get(), SoundSource.PLAYERS, 0.5f, 1.5f);
                p.discard();
            }
        }

        // Scorch and shove back anything touching it (from either side).
        if (tickCount % 10 == 0 && height > 1) {
            for (LivingEntity target : BendingUtil.livingAround(owner, base.add(0, height / 2, 0), HALF_WIDTH + 1.5)) {
                Vec3 rel = target.position().subtract(base);
                double side = rel.dot(normal);
                if (Math.abs(side) <= TOUCH && Math.abs(rel.dot(along)) <= HALF_WIDTH + 0.4 && rel.y > -1.5 && rel.y < height) {
                    BendingUtil.damage(owner, target, DAMAGE * power, 3);
                    Vec3 push = normal.scale(side >= 0 ? 1 : -1);
                    target.push(push.x * 0.7, 0.15, push.z * 0.7);
                    target.hurtMarked = true;
                }
            }
        }
        if (tickCount % 20 == 0) {
            level.playSound(null, base.x, base.y + 1, base.z, ModSounds.WALL_OF_FIRE_LOOP.get(), SoundSource.PLAYERS, 0.7f, 0.9f);
        }
    }

    private void spawnFlames() {
        float s = strength(0);
        if (s <= 0f) {
            return;
        }
        Vec3 base = position();
        Vec3 along = along();
        Vec3 normal = normal();
        boolean blue = isBlue();
        int count = (int) (26 * s);
        for (int i = 0; i < count; i++) {
            double u = (random.nextDouble() * 2 - 1) * HALF_WIDTH;
            // More flame low down, licking upward; the top edge flickers.
            double v = HEIGHT * s * Math.pow(random.nextDouble(), 1.4);
            double n = (random.nextDouble() - 0.5) * 0.4;
            Vec3 p = base.add(along.scale(u)).add(normal.scale(n)).add(0, v, 0);
            level().addParticle(FireFx.wisp(blue), p.x, p.y, p.z, 0, 0.01 + random.nextDouble() * 0.02, 0);
        }
        if (random.nextInt(3) == 0) {
            double u = (random.nextDouble() * 2 - 1) * HALF_WIDTH;
            Vec3 p = base.add(along.scale(u)).add(0, 0.2, 0);
            level().addParticle(FireFx.ember(blue), p.x, p.y, p.z, (random.nextDouble() - 0.5) * 0.1, 0.15, (random.nextDouble() - 0.5) * 0.1);
        }
        if (random.nextInt(4) == 0) {
            double u = (random.nextDouble() * 2 - 1) * HALF_WIDTH;
            Vec3 p = base.add(along.scale(u)).add(0, HEIGHT * s, 0);
            level().addParticle(ModParticles.FIRE_SMOKE.get(), p.x, p.y, p.z, 0, 0.03, 0);
        }
    }

    public boolean isBlue() {
        return entityData.get(BLUE);
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 128 * 128;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {}

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {}
}
