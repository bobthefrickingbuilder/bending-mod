package com.bending.item;

import java.util.List;
import java.util.function.Supplier;

import com.bending.BendingMod;
import com.mojang.serialization.Codec;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModComponents {
    public static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, BendingMod.MODID);

    /** Gem ids socketed into a piece of armor. */
    public static final Supplier<DataComponentType<List<String>>> GEMS = COMPONENTS.registerComponentType("gems",
            b -> b.persistent(Codec.STRING.listOf())
                    .networkSynchronized(ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list(3))));

    private ModComponents() {}
}
