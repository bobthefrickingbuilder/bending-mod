package com.bending.ability;

import com.bending.BendingMod;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;

/**
 * The mod's own damage types (data/bending/damage_type), so kills read like
 * "Zombie was burned to a crisp by Steve's firebending".
 */
public final class BendingDamage {
    public static final ResourceKey<DamageType> FIREBENDING = key("firebending");
    public static final ResourceKey<DamageType> LIGHTNING = key("lightning");

    private BendingDamage() {}

    private static ResourceKey<DamageType> key(String name) {
        return ResourceKey.create(Registries.DAMAGE_TYPE, ResourceLocation.fromNamespaceAndPath(BendingMod.MODID, name));
    }

    public static DamageSource source(ServerPlayer bender, ResourceKey<DamageType> type) {
        return new DamageSource(bender.level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(type), bender);
    }
}
