package com.bending.menu;

import java.util.ArrayList;
import java.util.List;

import com.bending.block.ModBlocks;
import com.bending.item.GemItem;
import com.bending.item.GemType;
import com.bending.item.Gems;
import com.bending.item.ModItems;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Imbuing Altar: an armor slot and a gem slot. "Imbue" sets the gem into the next free socket;
 * "Extract" pulls every gem back out, and each one has a chance to shatter.
 */
public class AltarMenu extends AbstractContainerMenu {
    public static final int BUTTON_IMBUE = 0;
    public static final int BUTTON_EXTRACT = 1;
    /** Chance each gem breaks when pulled out. */
    public static final float BREAK_CHANCE = 0.25f;
    public static final int ARMOR_X = 27, ARMOR_Y = 47, GEM_X = 133, GEM_Y = 47;

    private final Container input = new SimpleContainer(2) {
        @Override
        public void setChanged() {
            super.setChanged();
            AltarMenu.this.slotsChanged(this);
        }
    };
    private final ContainerLevelAccess access;

    public AltarMenu(int id, Inventory inventory) {
        this(id, inventory, ContainerLevelAccess.NULL);
    }

    public AltarMenu(int id, Inventory inventory, ContainerLevelAccess access) {
        super(ModMenus.IMBUING_ALTAR.get(), id);
        this.access = access;
        addSlot(new Slot(input, 0, ARMOR_X, ARMOR_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return Gems.sockets(stack) > 0;
            }

            @Override
            public int getMaxStackSize() {
                return 1;
            }
        });
        addSlot(new Slot(input, 1, GEM_X, GEM_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.getItem() instanceof GemItem;
            }
        });
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, 98 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inventory, col, 8 + col * 18, 156));
        }
    }

    public ItemStack armor() {
        return input.getItem(0);
    }

    public ItemStack gem() {
        return input.getItem(1);
    }

    public boolean canImbue() {
        ItemStack armor = armor();
        return !armor.isEmpty() && gem().getItem() instanceof GemItem && Gems.get(armor).size() < Gems.sockets(armor);
    }

    public boolean canExtract() {
        return !Gems.get(armor()).isEmpty();
    }

    @Override
    public boolean clickMenuButton(Player player, int button) {
        if (button == BUTTON_IMBUE && canImbue()) {
            ItemStack armor = armor();
            List<GemType> gems = new ArrayList<>(Gems.get(armor));
            gems.add(((GemItem) gem().getItem()).type());
            Gems.set(armor, gems);
            gem().shrink(1);
            input.setChanged();
            effects(player, false);
            return true;
        }
        if (button == BUTTON_EXTRACT && canExtract()) {
            ItemStack armor = armor();
            boolean broke = false;
            for (GemType gem : Gems.get(armor)) {
                if (player.getRandom().nextFloat() < BREAK_CHANCE) {
                    broke = true;
                    continue;
                }
                ItemStack back = new ItemStack(ModItems.GEMS.get(gem).get());
                ItemStack slot = gem();
                if (slot.isEmpty()) {
                    input.setItem(1, back);
                } else if (ItemStack.isSameItemSameComponents(slot, back) && slot.getCount() < slot.getMaxStackSize()) {
                    slot.grow(1);
                } else {
                    player.getInventory().placeItemBackInInventory(back);
                }
            }
            Gems.set(armor, List.of());
            input.setChanged();
            effects(player, broke);
            return true;
        }
        return false;
    }

    private void effects(Player player, boolean broke) {
        access.execute((level, pos) -> {
            level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1f, 1.2f);
            if (broke) {
                level.playSound(null, pos, SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.BLOCKS, 1f, 0.8f);
            } else {
                level.playSound(null, pos, SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.BLOCKS, 0.8f, 1.4f);
            }
            if (level instanceof ServerLevel server) {
                server.sendParticles(broke ? ParticleTypes.CRIT : ParticleTypes.END_ROD,
                        pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 30, 0.3, 0.3, 0.3, 0.08);
                server.sendParticles(ParticleTypes.FLAME, pos.getX() + 0.5, pos.getY() + 0.9, pos.getZ() + 0.5,
                        20, 0.25, 0.1, 0.25, 0.04);
            }
        });
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        access.execute((level, pos) -> clearContainer(player, input));
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(access, player, ModBlocks.IMBUING_ALTAR.get());
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        if (index < 2) {
            if (!moveItemStackTo(stack, 2, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (stack.getItem() instanceof GemItem) {
            if (!moveItemStackTo(stack, 1, 2, false)) {
                return ItemStack.EMPTY;
            }
        } else if (Gems.sockets(stack) > 0 && !slots.get(0).hasItem()) {
            ItemStack one = stack.split(1);
            slots.get(0).setByPlayer(one);
        } else {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return copy;
    }
}
