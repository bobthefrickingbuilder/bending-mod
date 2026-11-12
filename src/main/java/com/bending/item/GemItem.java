package com.bending.item;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/** A gem you socket into armor at an Imbuing Altar. */
public class GemItem extends Item {
    private final GemType type;

    public GemItem(GemType type, Properties properties) {
        super(properties);
        this.type = type;
    }

    public GemType type() {
        return type;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        for (GemType.Bonus bonus : type.bonuses()) {
            tooltip.add(bonus.stat().describe(bonus.amount()).copy().withStyle(ChatFormatting.BLUE));
        }
        tooltip.add(Component.translatable(type.element() == null ? "tooltip.bending.gem_universal"
                : "tooltip.bending.gem_" + type.element().getSerializedName()).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.bending.gem_hint").withStyle(ChatFormatting.DARK_GRAY));
    }
}
