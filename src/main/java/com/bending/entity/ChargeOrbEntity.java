package com.bending.entity;

import javax.annotation.Nullable;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * A ball of fire gathering in front of a bender while they charge (Combustion Beam).
 * The server keeps it alive and sets its charge each tick; it vanishes on its own once
 * the charge stops (released, cancelled, or the bender leaves bending mode).
 * Clients draw it at the owner's hands so it stays put while they look around.
 */
public class ChargeOrbEntity extends Entity {
    private static final EntityDataAccessor<Integer> OWNER =
            SynchedEntityData.defineId(ChargeOrbEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> CHARGE =
            SynchedEntityData.defineId(ChargeOrbEntity.class, EntityDataSerializers.FLOAT);
    /** How far in front of the eyes, and how far below them, the orb sits. */
    public static final double FORWARD = 1.0;
    public static final double DROP = 0.45;

    private int lastKeptAlive;

    public ChargeOrbEntity(EntityType<? extends ChargeOrbEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
    }

    public ChargeOrbEntity(Level level, ServerPlayer owner) {
        this(ModEntities.CHARGE_ORB.get(), level);
        entityData.set(OWNER, owner.getId());
        follow(owner);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(OWNER, -1);
        builder.define(CHARGE, 0f);
    }

    /** Server: called every tick while charging. */
    public void update(ServerPlayer owner, float charge) {
        entityData.set(CHARGE, charge);
        lastKeptAlive = tickCount;
        follow(owner);
    }

    private void follow(ServerPlayer owner) {
        Vec3 p = focus(owner.getEyePosition(), owner.getLookAngle());
        setPos(p.x, p.y, p.z);
    }

    /** Where the orb sits for a bender with these eyes and look direction. */
    public static Vec3 focus(Vec3 eyes, Vec3 look) {
        return eyes.add(look.scale(FORWARD)).add(0, -DROP, 0);
    }

    public float getCharge() {
        return entityData.get(CHARGE);
    }

    @Nullable
    public Entity getOwner() {
        return level().getEntity(entityData.get(OWNER));
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide && tickCount - lastKeptAlive > 2) {
            discard();
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
