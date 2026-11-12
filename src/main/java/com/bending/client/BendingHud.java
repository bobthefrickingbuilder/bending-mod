package com.bending.client;

import com.bending.ability.Abilities;
import com.bending.ability.Ability;
import com.bending.ability.CastType;
import com.bending.data.BendingData;
import com.bending.data.Element;
import com.bending.data.ModAttachments;
import com.bending.skill.SkillStat;
import com.bending.skill.SkillStats;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * Chi bar and the four ability slots, drawn to the right of the hotbar. Each slot shows its key,
 * dims red when you can't afford it, and while held shows a charge (or hold) bar. Pressing a slot
 * pops its name above the bar for a moment.
 */
public final class BendingHud {
    private static final int SLOT_SIZE = 20;
    private static final int GAP = 2;
    /** Ticks the ability name stays up after a press (it fades over the last third). */
    private static final int NAME_TICKS = 30;

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
        Font font = mc.font;
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
        g.drawString(font, chiText, x0 + width + 3, barY - 2, data.hasInfiniteChi() ? 0xFFD700 : 0xFFFFFF);

        long now = mc.level.getGameTime();
        int newest = -1;
        for (int slot = 0; slot < BendingData.SLOTS; slot++) {
            int x = x0 + slot * (SLOT_SIZE + GAP);
            Ability ability = Abilities.get(data.getLoadout(slot));
            boolean held = ClientInput.isHeld(slot);
            boolean broke = ability != null && !data.hasInfiniteChi() && data.getChi() < startCost(data, ability);
            g.fill(x, y0, x + SLOT_SIZE, y0 + SLOT_SIZE, broke ? 0xB0500808 : 0xB0000000);
            if (ability != null) {
                g.drawCenteredString(font, initials(ability.displayName().getString()), x + SLOT_SIZE / 2, y0 + 6,
                        broke ? 0xFF8A7070 : 0xFFFFFF);
                if (held) {
                    renderHoldBar(g, data, ability, ClientInput.heldTicks(slot), x, y0, color);
                }
                if (now - ClientInput.lastPress(slot) < NAME_TICKS
                        && (newest < 0 || ClientInput.lastPress(slot) > ClientInput.lastPress(newest))) {
                    newest = slot;
                }
            }
            g.renderOutline(x, y0, SLOT_SIZE, SLOT_SIZE, held ? 0xFFFFFFFF : color);
            // Key label in the corner, small.
            String key = keyLabel(slot);
            g.pose().pushPose();
            g.pose().translate(x + 2, y0 + 2, 0);
            g.pose().scale(0.5f, 0.5f, 1f);
            g.drawString(font, key, 0, 0, 0xFFC8C8C8);
            g.pose().popPose();
        }

        // Name of the last ability pressed, floating above the chi bar.
        if (newest >= 0) {
            Ability ability = Abilities.get(data.getLoadout(newest));
            float age = (now - ClientInput.lastPress(newest)) / (float) NAME_TICKS;
            int alpha = (int) (255 * Mth.clamp((1f - age) * 3f, 0f, 1f));
            if (ability != null && alpha > 8) {
                Component name = ability.displayName();
                g.drawCenteredString(font, name, x0 + width / 2, barY - 11, (alpha << 24) | 0xFFFFFF);
            }
        }
    }

    /** The bar over a held slot: charge progress for charges, chi drain for holds. */
    private static void renderHoldBar(GuiGraphics g, BendingData data, Ability ability, int ticks, int x, int y0, int color) {
        float progress;
        int full;
        CastType type = ability.castType();
        if (type == CastType.CHARGE || ability.maxChargeTicks() > 0 && type != CastType.HOLD) {
            float speed = SkillStats.ability(data, ability.id(), SkillStat.CHARGE_TIME);
            int max = Math.max(1, ability.maxChargeTicks());
            progress = Mth.clamp(ticks / speed / max, 0f, 1f);
            full = progress >= 1f ? 0xFFFFFFFF : color;
        } else if (ability.maxHoldTicks() > 0 && ability.maxHoldTicks() < 100000) {
            progress = 1f - Mth.clamp(ticks / (float) ability.maxHoldTicks(), 0f, 1f);
            full = color;
        } else {
            return;
        }
        int top = y0 - 3;
        g.fill(x, top, x + SLOT_SIZE, top + 2, 0xC0000000);
        g.fill(x, top, x + Math.round(SLOT_SIZE * progress), top + 2, full);
    }

    private static float startCost(BendingData data, Ability ability) {
        float raw = switch (ability.castType()) {
            case TAP, CHARGE -> ability.chiCost(0);
            case TAP_OR_HOLD -> Math.min(ability.chiCost(0), Math.max(ability.startChiCost(), ability.chiPerTick() * 5));
            default -> Math.max(ability.startChiCost(), ability.chiPerTick());
        };
        return raw * SkillStats.ability(data, ability.id(), SkillStat.CHI_COST) * SkillStats.chiCostMultiplier(data);
    }

    private static String keyLabel(int slot) {
        String s = ClientInput.SLOT_KEYS[slot].getTranslatedKeyMessage().getString();
        return s.length() > 3 ? s.substring(0, 3) : s;
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
