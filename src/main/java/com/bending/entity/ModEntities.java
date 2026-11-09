package com.bending.entity;

import java.util.function.Supplier;

import com.bending.BendingMod;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, BendingMod.MODID);

    public static final Supplier<EntityType<FireBlastEntity>> FIRE_BLAST = ENTITY_TYPES.register("fire_blast",
            () -> EntityType.Builder.<FireBlastEntity>of(FireBlastEntity::new, MobCategory.MISC)
                    .sized(0.4f, 0.4f)
                    .clientTrackingRange(4)
                    .updateInterval(1)
                    .fireImmune()
                    .build("fire_blast"));

    public static final Supplier<EntityType<FirestormEntity>> FIRESTORM = ENTITY_TYPES.register("firestorm",
            () -> EntityType.Builder.<FirestormEntity>of(FirestormEntity::new, MobCategory.MISC)
                    .sized(0.5f, 0.5f)
                    .clientTrackingRange(4)
                    .noSave()
                    .fireImmune()
                    .build("firestorm"));

    public static final Supplier<EntityType<ChargeOrbEntity>> CHARGE_ORB = ENTITY_TYPES.register("charge_orb",
            () -> EntityType.Builder.<ChargeOrbEntity>of(ChargeOrbEntity::new, MobCategory.MISC)
                    .sized(0.3f, 0.3f)
                    .clientTrackingRange(4)
                    .updateInterval(1)
                    .noSave()
                    .fireImmune()
                    .build("charge_orb"));

    public static final Supplier<EntityType<FireArcEntity>> FIRE_ARC = ENTITY_TYPES.register("fire_arc",
            () -> EntityType.Builder.<FireArcEntity>of(FireArcEntity::new, MobCategory.MISC)
                    .sized(0.5f, 0.5f)
                    .clientTrackingRange(4)
                    .noSave()
                    .fireImmune()
                    .build("fire_arc"));

    public static final Supplier<EntityType<LightningArcEntity>> LIGHTNING_ARC = ENTITY_TYPES.register("lightning_arc",
            () -> EntityType.Builder.<LightningArcEntity>of(LightningArcEntity::new, MobCategory.MISC)
                    .sized(0.2f, 0.2f)
                    .clientTrackingRange(8)
                    .updateInterval(20)
                    .noSave()
                    .fireImmune()
                    .build("lightning_arc"));

    private ModEntities() {}
}
