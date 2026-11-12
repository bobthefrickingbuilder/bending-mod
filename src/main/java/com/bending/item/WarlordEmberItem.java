package com.bending.item;

import java.util.List;

import com.bending.entity.FireWarlordEntity;
import com.bending.entity.ModEntities;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;

/** Used on the ground, calls down the Fire Warlord. */
public class WarlordEmberItem extends Item {
    public WarlordEmberItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (!(context.getLevel() instanceof ServerLevel server)) {
            return InteractionResult.SUCCESS;
        }
        BlockPos pos = context.getClickedPos().relative(context.getClickedFace());
        FireWarlordEntity boss = ModEntities.FIRE_WARLORD.get().create(server);
        if (boss == null) {
            return InteractionResult.FAIL;
        }
        boss.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0, 0);
        boss.finalizeSpawn(server, server.getCurrentDifficultyAt(pos), MobSpawnType.SPAWN_EGG, null);
        server.addFreshEntity(boss);
        boss.announce(server);
        if (context.getPlayer() != null) {
            boss.setTarget(context.getPlayer().isCreative() ? null : context.getPlayer());
            if (!context.getPlayer().isCreative()) {
                context.getItemInHand().shrink(1);
            }
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.bending.warlord_ember").withStyle(ChatFormatting.GRAY));
    }
}
