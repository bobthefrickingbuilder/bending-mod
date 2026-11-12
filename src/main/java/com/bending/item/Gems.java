package com.bending.item;

import java.util.ArrayList;
import java.util.List;

import com.bending.data.BendingData;
import com.bending.data.ModAttachments;
import com.bending.skill.SkillStat;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ArmorMaterials;
import net.minecraft.world.item.ItemStack;

/** Armor sockets and the stats the gems in your worn armor add up to. */
public final class Gems {
    private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    private Gems() {}

    /** Leather to iron: 1 socket. Diamond: 2. Netherite: 3. Anything that is not armor: 0. */
    public static int sockets(ItemStack stack) {
        if (!(stack.getItem() instanceof ArmorItem armor)) {
            return 0;
        }
        Holder<ArmorMaterial> material = armor.getMaterial();
        if (material.equals(ArmorMaterials.NETHERITE)) {
            return 3;
        }
        if (material.equals(ArmorMaterials.DIAMOND)) {
            return 2;
        }
        return 1;
    }

    public static List<GemType> get(ItemStack stack) {
        List<String> ids = stack.get(ModComponents.GEMS.get());
        List<GemType> out = new ArrayList<>();
        if (ids != null) {
            for (String id : ids) {
                GemType type = GemType.byId(id);
                if (type != null) {
                    out.add(type);
                }
            }
        }
        return out;
    }

    public static void set(ItemStack stack, List<GemType> gems) {
        if (gems.isEmpty()) {
            stack.remove(ModComponents.GEMS.get());
        } else {
            stack.set(ModComponents.GEMS.get(), gems.stream().map(GemType::id).toList());
        }
    }

    /** Recomputes the cached gem totals from what the player is wearing. Runs on both sides. */
    public static void update(Player player) {
        BendingData data = player.getData(ModAttachments.BENDING);
        float[] totals = new float[SkillStat.values().length];
        for (EquipmentSlot slot : ARMOR) {
            for (GemType gem : get(player.getItemBySlot(slot))) {
                if (!gem.worksFor(data.getElement())) {
                    continue;
                }
                for (GemType.Bonus bonus : gem.bonuses()) {
                    totals[bonus.stat().ordinal()] += bonus.amount();
                }
            }
        }
        data.gemStats = totals;
    }

    /** Socket lines for an armor tooltip. */
    public static void appendTooltip(ItemStack stack, List<Component> tooltip) {
        int sockets = sockets(stack);
        if (sockets == 0) {
            return;
        }
        List<GemType> gems = get(stack);
        tooltip.add(Component.translatable("tooltip.bending.sockets", gems.size(), sockets).withStyle(ChatFormatting.GOLD));
        for (GemType gem : gems) {
            Component name = Component.translatable("item.bending." + gem.id());
            tooltip.add(Component.literal(" ◆ ").withColor(gem.color()).append(name.copy().withStyle(ChatFormatting.GRAY)));
        }
    }
}
