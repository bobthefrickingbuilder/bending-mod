package com.bending.client;

import java.util.List;

import com.bending.item.GemType;
import com.bending.item.Gems;
import com.bending.item.ModItems;
import com.bending.menu.AltarMenu;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/** The altar: armor on the left, gem on the right, its sockets drawn in between. */
public class AltarScreen extends AbstractContainerScreen<AltarMenu> {
    private static final int PANEL = 0xF0201418, EDGE = 0xFFB86A2A, SLOT = 0xFF0E0A0C, SLOT_EDGE = 0xFF5A3A28;
    private Button imbue;
    private Button extract;

    public AltarScreen(AltarMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = 180;
        inventoryLabelY = 87;
    }

    @Override
    protected void init() {
        super.init();
        imbue = addRenderableWidget(Button.builder(Component.translatable("gui.bending.imbue"), b -> press(AltarMenu.BUTTON_IMBUE))
                .bounds(leftPos + 52, topPos + 66, 34, 14).build());
        extract = addRenderableWidget(Button.builder(Component.translatable("gui.bending.extract"), b -> press(AltarMenu.BUTTON_EXTRACT))
                .bounds(leftPos + 90, topPos + 66, 34, 14)
                .tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("gui.bending.extract_warning",
                        Math.round(AltarMenu.BREAK_CHANCE * 100))))
                .build());
    }

    private void press(int button) {
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, button);
        }
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        imbue.active = menu.canImbue();
        extract.active = menu.canExtract();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos, y = topPos;
        g.fill(x - 1, y - 1, x + imageWidth + 1, y + imageHeight + 1, EDGE);
        g.fill(x, y, x + imageWidth, y + imageHeight, PANEL);
        g.fill(x + 4, y + 84, x + imageWidth - 4, y + 85, 0xFF5A3A28);
        slot(g, x + AltarMenu.ARMOR_X, y + AltarMenu.ARMOR_Y);
        slot(g, x + AltarMenu.GEM_X, y + AltarMenu.GEM_Y);
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                slot(g, x + 8 + col * 18, y + 98 + row * 18);
            }
        }
        for (int col = 0; col < 9; col++) {
            slot(g, x + 8 + col * 18, y + 156);
        }
        // Sockets of the armor in the slot, centred between the two slots.
        ItemStack armor = menu.armor();
        int sockets = Gems.sockets(armor);
        List<GemType> gems = Gems.get(armor);
        int cx = x + imageWidth / 2;
        int sy = y + 44;
        int total = Math.max(sockets, 1);
        for (int i = 0; i < total; i++) {
            int sx = cx - total * 11 + i * 22 + 2;
            g.fill(sx - 1, sy - 1, sx + 19, sy + 19, sockets == 0 ? 0xFF2A2024 : 0xFF8A5A30);
            g.fill(sx, sy, sx + 18, sy + 18, 0xFF0A0608);
            if (i < gems.size()) {
                GemType gem = gems.get(i);
                g.fill(sx, sy, sx + 18, sy + 18, 0x40000000 | gem.color());
                g.renderItem(new ItemStack(ModItems.GEMS.get(gem).get()), sx + 1, sy + 1);
            }
        }
        // Arrows between the slots and the sockets.
        g.drawString(font, "▶", x + AltarMenu.ARMOR_X + 20, y + AltarMenu.ARMOR_Y + 4, 0xFFB86A2A, false);
        g.drawString(font, "◀", x + AltarMenu.GEM_X - 10, y + AltarMenu.GEM_Y + 4, 0xFFB86A2A, false);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, title, (imageWidth - font.width(title)) / 2, 8, 0xFFFFC070, false);
        g.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0xFFB0A090, false);
        Component hint = menu.armor().isEmpty()
                ? Component.translatable("gui.bending.altar_hint")
                : Component.translatable("tooltip.bending.sockets", Gems.get(menu.armor()).size(), Gems.sockets(menu.armor()));
        g.drawString(font, hint, (imageWidth - font.width(hint)) / 2, 24, 0xFFD0C0B0, false);
    }

    private static void slot(GuiGraphics g, int x, int y) {
        g.fill(x - 1, y - 1, x + 17, y + 17, SLOT_EDGE);
        g.fill(x, y, x + 16, y + 16, SLOT);
    }
}
