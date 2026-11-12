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
                    .updateInterval(1)
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

    public static final Supplier<EntityType<WallOfFireEntity>> WALL_OF_FIRE = ENTITY_TYPES.register("wall_of_fire",
            () -> EntityType.Builder.<WallOfFireEntity>of(WallOfFireEntity::new, MobCategory.MISC)
                    .sized(0.5f, 0.5f)
                    .clientTrackingRange(8)
                    .updateInterval(20)
                    .noSave()
                    .fireImmune()
                    .build("wall_of_fire"));

    public static final Supplier<EntityType<FirebenderEntity>> FIREBENDER = ENTITY_TYPES.register("firebender",
            () -> EntityType.Builder.<FirebenderEntity>of(FirebenderEntity::new, MobCategory.MONSTER)
                    .sized(0.6f, 1.8f)
                    .eyeHeight(1.62f)
                    .clientTrackingRange(8)
                    .fireImmune()
                    .build("firebender"));

    public static final Supplier<EntityType<FireWarlordEntity>> FIRE_WARLORD = ENTITY_TYPES.register("fire_warlord",
            () -> EntityType.Builder.<FireWarlordEntity>of(FireWarlordEntity::new, MobCategory.MONSTER)
                    .sized(0.8f, 2.35f)
                    .eyeHeight(2.1f)
                    .clientTrackingRange(10)
                    .fireImmune()
                    .build("fire_warlord"));

    /** Mod bus: mob attributes. */
    public static void registerAttributes(net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent event) {
        event.put(FIREBENDER.get(), FirebenderEntity.createAttributes().build());
        event.put(FIRE_WARLORD.get(), FireWarlordEntity.createAttributes().build());
    }

    /** Mod bus: where firebenders may naturally spawn (biomes come from the biome modifier). */
    public static void registerSpawnPlacements(net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent event) {
        event.register(FIREBENDER.get(), net.minecraft.world.entity.SpawnPlacementTypes.ON_GROUND,
                net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                net.minecraft.world.entity.monster.Monster::checkMonsterSpawnRules,
                net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent.Operation.REPLACE);
    }

    private ModEntities() {}
}
