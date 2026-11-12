package com.bending.item;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Supplier;

import com.bending.BendingMod;
import com.bending.block.ModBlocks;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(BendingMod.MODID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, BendingMod.MODID);

    public static final Map<GemType, DeferredItem<GemItem>> GEMS = new EnumMap<>(GemType.class);

    static {
        for (GemType type : GemType.values()) {
            GEMS.put(type, ITEMS.register(type.id(), () -> new GemItem(type,
                    new Item.Properties().rarity(type.element() == null ? Rarity.UNCOMMON : Rarity.RARE))));
        }
    }

    public static final DeferredItem<BlockItem> IMBUING_ALTAR = ITEMS.registerSimpleBlockItem(ModBlocks.IMBUING_ALTAR);
    public static final DeferredItem<BlockItem> SPIRIT_CRYSTAL_ORE = ITEMS.registerSimpleBlockItem(ModBlocks.SPIRIT_CRYSTAL_ORE);
    public static final DeferredItem<BlockItem> DEEPSLATE_SPIRIT_CRYSTAL_ORE = ITEMS.registerSimpleBlockItem(ModBlocks.DEEPSLATE_SPIRIT_CRYSTAL_ORE);
    public static final DeferredItem<BlockItem> EMBER_ORE = ITEMS.registerSimpleBlockItem(ModBlocks.EMBER_ORE);

    public static final DeferredItem<Item> FIREBENDER_SPAWN_EGG = ITEMS.register("firebender_spawn_egg",
            () -> new net.neoforged.neoforge.common.DeferredSpawnEggItem(com.bending.entity.ModEntities.FIREBENDER,
                    0x7A1010, 0xFFB020, new Item.Properties()));
    public static final DeferredItem<Item> FIRE_WARLORD_SPAWN_EGG = ITEMS.register("fire_warlord_spawn_egg",
            () -> new net.neoforged.neoforge.common.DeferredSpawnEggItem(com.bending.entity.ModEntities.FIRE_WARLORD,
                    0x1A0A0A, 0x3A8DFF, new Item.Properties()));
    public static final DeferredItem<WarlordEmberItem> WARLORD_EMBER = ITEMS.register("warlord_ember",
            () -> new WarlordEmberItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant()));

    public static final Supplier<CreativeModeTab> TAB = TABS.register("bending", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.bending"))
            .icon(() -> new ItemStack(GEMS.get(GemType.SUNSTONE).get()))
            .displayItems((params, out) -> {
                out.accept(IMBUING_ALTAR.get());
                out.accept(SPIRIT_CRYSTAL_ORE.get());
                out.accept(DEEPSLATE_SPIRIT_CRYSTAL_ORE.get());
                out.accept(EMBER_ORE.get());
                for (DeferredItem<GemItem> gem : GEMS.values()) {
                    out.accept(gem.get());
                }
                out.accept(WARLORD_EMBER.get());
                out.accept(FIREBENDER_SPAWN_EGG.get());
                out.accept(FIRE_WARLORD_SPAWN_EGG.get());
            })
            .build());

    private ModItems() {}
}
