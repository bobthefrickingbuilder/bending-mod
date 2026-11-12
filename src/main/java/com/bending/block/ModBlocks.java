package com.bending.block;

import com.bending.BendingMod;

import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DropExperienceBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(BendingMod.MODID);

    public static final DeferredBlock<ImbuingAltarBlock> IMBUING_ALTAR = BLOCKS.register("imbuing_altar",
            () -> new ImbuingAltarBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.ENCHANTING_TABLE)
                    .lightLevel(s -> 7).noOcclusion()));

    public static final DeferredBlock<DropExperienceBlock> SPIRIT_CRYSTAL_ORE = BLOCKS.register("spirit_crystal_ore",
            () -> new DropExperienceBlock(UniformInt.of(3, 7), BlockBehaviour.Properties.ofFullCopy(Blocks.DIAMOND_ORE)
                    .lightLevel(s -> 3)));
    public static final DeferredBlock<DropExperienceBlock> DEEPSLATE_SPIRIT_CRYSTAL_ORE = BLOCKS.register("deepslate_spirit_crystal_ore",
            () -> new DropExperienceBlock(UniformInt.of(3, 7), BlockBehaviour.Properties.ofFullCopy(Blocks.DEEPSLATE_DIAMOND_ORE)
                    .lightLevel(s -> 3)));
    public static final DeferredBlock<DropExperienceBlock> EMBER_ORE = BLOCKS.register("ember_ore",
            () -> new DropExperienceBlock(UniformInt.of(3, 7), BlockBehaviour.Properties.ofFullCopy(Blocks.NETHER_GOLD_ORE)
                    .strength(3f, 3f).requiresCorrectToolForDrops().sound(SoundType.NETHER_ORE).lightLevel(s -> 6)));

    private ModBlocks() {}
}
