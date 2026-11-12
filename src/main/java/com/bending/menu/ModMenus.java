package com.bending.menu;

import java.util.function.Supplier;

import com.bending.BendingMod;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModMenus {
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, BendingMod.MODID);

    public static final Supplier<MenuType<AltarMenu>> IMBUING_ALTAR = MENUS.register("imbuing_altar",
            () -> new MenuType<>(AltarMenu::new, FeatureFlags.DEFAULT_FLAGS));

    private ModMenus() {}
}
