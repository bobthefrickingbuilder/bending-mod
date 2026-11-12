package com.bending.block;

import com.bending.menu.AltarMenu;
import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Where gems go into armor (and come back out, if they survive). */
public class ImbuingAltarBlock extends Block {
    public static final MapCodec<ImbuingAltarBlock> CODEC = simpleCodec(ImbuingAltarBlock::new);
    private static final VoxelShape SHAPE = Shapes.or(
            Block.box(0, 0, 0, 16, 4, 16),
            Block.box(3, 4, 3, 13, 10, 13),
            Block.box(1, 10, 1, 15, 13, 15));
    private static final Component TITLE = Component.translatable("container.bending.imbuing_altar");

    public ImbuingAltarBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide) {
            player.openMenu(state.getMenuProvider(level, pos));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected MenuProvider getMenuProvider(BlockState state, Level level, BlockPos pos) {
        return new SimpleMenuProvider((id, inventory, player) ->
                new AltarMenu(id, inventory, ContainerLevelAccess.create(level, pos)), TITLE);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        for (int i = 0; i < 2; i++) {
            level.addParticle(random.nextBoolean() ? ParticleTypes.END_ROD : ParticleTypes.SMALL_FLAME,
                    pos.getX() + 0.2 + random.nextDouble() * 0.6, pos.getY() + 0.9, pos.getZ() + 0.2 + random.nextDouble() * 0.6,
                    0, 0.02 + random.nextDouble() * 0.03, 0);
        }
    }
}
