package com.bending.client;

import com.bending.ability.Abilities;
import com.bending.ability.Ability;
import com.bending.data.BendingData;
import com.bending.data.Element;
import com.bending.data.ModAttachments;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/** Chi bar and the four ability slots, drawn to the right of the hotbar. */
public final class BendingHud {
    private static final int SLOT_SIZE = 20;
    private static final int GAP = 2;

    private BendingHud() {}

    public static void render(GuiGraphics g, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.player == null || mc.level == null || mc.player.isSpectator()) {
            return;
        }
        BendingData data = mc.player.getData(ModAttachments.BENDING);
        Element element = data.getElement();
        if (element == null || !data.bendingMode) {
            return; // the HUD only shows in bending mode
        }
        int color = 0xFF000000 | element.color();
        int x0 = g.guiWidth() / 2 + 91 + 8;
        int y0 = g.guiHeight() - SLOT_SIZE - 1;
        int width = BendingData.SLOTS * SLOT_SIZE + (BendingData.SLOTS - 1) * GAP;

        // Chi bar
        int barY = y0 - 7;
        g.fill(x0, barY, x0 + width, barY + 5, 0xB0000000);
        int filled = Math.round(width * (data.getChi() / data.getMaxChi()));
        g.fill(x0, barY, x0 + filled, barY + 5, color);
        String chiText = data.hasInfiniteChi() ? "∞" : String.valueOf(Math.round(data.getChi()));
        g.drawString(mc.font, chiText, x0 + width + 3, barY - 2, data.hasInfiniteChi() ? 0xFFD700 : 0xFFFFFF);

        // Ability slots
        for (int slot = 0; slot < BendingData.SLOTS; slot++) {
            int x = x0 + slot * (SLOT_SIZE + GAP);
            g.fill(x, y0, x + SLOT_SIZE, y0 + SLOT_SIZE, 0xB0000000);
            Ability ability = Abilities.get(data.getLoadout(slot));
            if (ability != null) {
                g.drawCenteredString(mc.font, initials(ability.displayName().getString()), x + SLOT_SIZE / 2, y0 + 6, 0xFFFFFF);
            }
            g.renderOutline(x, y0, SLOT_SIZE, SLOT_SIZE, ClientInput.isHeld(slot) ? 0xFFFFFFFF : color);
        }
    }

    /** "Fire Jet Dash" -> "FJ" (max two letters so it fits the slot). */
    private static String initials(String name) {
        StringBuilder sb = new StringBuilder();
        for (String word : name.split(" ")) {
            if (!word.isEmpty() && sb.length() < 2) {
                sb.append(Character.toUpperCase(word.charAt(0)));
            }
        }
        return sb.toString();
    }
}
