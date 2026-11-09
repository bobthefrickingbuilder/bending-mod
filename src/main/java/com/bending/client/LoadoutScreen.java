package com.bending.client;

import java.util.ArrayList;
import java.util.List;

import com.bending.ability.Abilities;
import com.bending.ability.Ability;
import com.bending.ability.CastType;
import com.bending.data.BendingData;
import com.bending.data.Element;
import com.bending.data.ModAttachments;
import com.bending.network.EquipAbilityPayload;
import com.bending.server.BendingService;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The bending menu (B): your four slots side by side, with every ability of your element that fits
 * each one. Click an ability to equip it; click the equipped one to empty the slot. Below are the
 * innate abilities, which are always ready in bending mode. Hover anything for what it does.
 *
 * Locked abilities are greyed out until the skill tree exists to unlock them; in creative mode
 * they can be equipped anyway, for testing.
 */
public class LoadoutScreen extends Screen {
    private static final int MAX_COLUMN_WIDTH = 124;
    private static final int GAP = 6;
    private static final int ROW = 20;
    private static final int ROW_GAP = 3;
    private static final int TOP = 34;
    private static final int HEADER = 24;
    private static final String[] SLOT_NAMES = {"projectile", "defense", "mobility", "ultimate"};

    private String signature = "";
    private int columnWidth;
    private int left;
    private int innateTop;
    private final List<Ability> innates = new ArrayList<>();

    public LoadoutScreen() {
        super(Component.translatable("screen.bending.loadout"));
    }

    private BendingData data() {
        return minecraft.player.getData(ModAttachments.BENDING);
    }

    /** Changes whenever the server sends a new loadout, so the buttons can be rebuilt. */
    private String signatureOf(BendingData data) {
        StringBuilder s = new StringBuilder(String.valueOf(minecraft.player.isCreative()));
        for (int slot = 0; slot < BendingData.SLOTS; slot++) {
            s.append('|').append(data.getLoadout(slot));
        }
        for (Ability a : Abilities.all()) {
            s.append(data.isUnlocked(a.id()) ? '1' : '0');
        }
        return s.toString();
    }

    @Override
    protected void init() {
        BendingData data = data();
        Element element = data.getElement();
        signature = signatureOf(data);
        int panel = Math.min(width - 20, MAX_COLUMN_WIDTH * 4 + GAP * 3);
        columnWidth = (panel - GAP * 3) / 4;
        left = (width - panel) / 2;
        boolean creative = BendingService.canUseLocked(minecraft.player);

        int tallest = 1;
        for (int slot = 0; slot < BendingData.SLOTS; slot++) {
            int x = left + slot * (columnWidth + GAP);
            int y = TOP + HEADER;
            int rows = 0;
            for (Ability ability : Abilities.all()) {
                if (ability.element() != element || ability.slot().ordinal() != slot) {
                    continue;
                }
                boolean equipped = ability.id().equals(data.getLoadout(slot));
                boolean unlocked = data.isUnlocked(ability.id());
                MutableComponent label = ability.displayName().copy();
                if (equipped) {
                    label = Component.literal("✔ ").append(label).withColor(element.color());
                } else if (!unlocked) {
                    label = label.withStyle(ChatFormatting.DARK_GRAY);
                }
                final int s = slot;
                Button button = addRenderableWidget(Button.builder(label, b ->
                        PacketDistributor.sendToServer(new EquipAbilityPayload(s, equipped ? "" : ability.id())))
                        .bounds(x, y, columnWidth, ROW)
                        .tooltip(Tooltip.create(tooltip(ability, equipped, unlocked, creative)))
                        .build());
                button.active = unlocked || creative;
                y += ROW + ROW_GAP;
                rows++;
            }
            tallest = Math.max(tallest, rows);
        }
        innateTop = TOP + HEADER + tallest * (ROW + ROW_GAP) + 14;

        innates.clear();
        for (Ability innate : new Ability[] {Abilities.innateAttack(element), Abilities.innateMobility(element), Abilities.innateSlam(element)}) {
            if (innate != null) {
                innates.add(innate);
            }
        }
    }

    private Component tooltip(Ability ability, boolean equipped, boolean unlocked, boolean creative) {
        MutableComponent text = ability.displayName().copy().withStyle(ChatFormatting.BOLD)
                .append(Component.literal("\n"))
                .append(castHint(ability).copy().withStyle(ChatFormatting.GRAY))
                .append(Component.literal("  "))
                .append(chiText(ability).copy().withStyle(ChatFormatting.AQUA))
                .append(Component.literal("\n"))
                .append(ability.description().copy().withStyle(ChatFormatting.WHITE));
        Component status = equipped ? Component.translatable("screen.bending.loadout.click_unequip")
                : unlocked ? Component.translatable("screen.bending.loadout.click_equip")
                : creative ? Component.translatable("screen.bending.loadout.locked_creative")
                : Component.translatable("screen.bending.loadout.locked");
        return text.append(Component.literal("\n")).append(status.copy().withStyle(
                unlocked ? ChatFormatting.YELLOW : ChatFormatting.RED));
    }

    private static Component castHint(Ability ability) {
        return Component.translatable("screen.bending.cast." + ability.castType().name().toLowerCase());
    }

    /** How much chi it costs, in the shape that fits how it's cast. */
    private static Component chiText(Ability ability) {
        CastType type = ability.castType();
        return switch (type) {
            case TAP -> Component.translatable("screen.bending.chi", num(ability.chiCost(0)));
            case HOLD -> ability.startChiCost() > 0
                    ? Component.translatable("screen.bending.chi_start_per_second", num(ability.startChiCost()), num(ability.chiPerTick() * 20))
                    : Component.translatable("screen.bending.chi_per_second", num(ability.chiPerTick() * 20));
            case CHARGE -> {
                float full = ability.chiCost(ability.maxChargeTicks());
                float least = ability.chiCost(Math.max(1, ability.minChargeTicks()));
                yield Math.abs(full - least) < 0.5f
                        ? Component.translatable("screen.bending.chi", num(full))
                        : Component.translatable("screen.bending.chi_range", num(least), num(full));
            }
            case TAP_OR_HOLD -> Component.translatable("screen.bending.chi_tap_hold", num(ability.chiCost(0)), num(ability.chiPerTick() * 20));
        };
    }

    private static String num(float value) {
        return Math.abs(value - Math.round(value)) < 0.05f ? String.valueOf(Math.round(value)) : String.format("%.1f", value);
    }

    @Override
    public void tick() {
        if (minecraft.player == null || data().getElement() == null) {
            onClose();
            return;
        }
        if (!signatureOf(data()).equals(signature)) {
            rebuildWidgets(); // the server confirmed a change
        }
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(g, mouseX, mouseY, partialTick);
        int bottom = innateTop - 8;
        for (int slot = 0; slot < BendingData.SLOTS; slot++) {
            int x = left + slot * (columnWidth + GAP);
            g.fill(x - 3, TOP - 4, x + columnWidth + 3, bottom, 0x66000000);
        }
        int right = left + 4 * columnWidth + 3 * GAP;
        g.fill(left - 3, innateTop - 4, right + 3, innateTop + 40, 0x66000000);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        BendingData data = data();
        Element element = data.getElement();
        if (element == null) {
            return;
        }
        g.drawCenteredString(font, Component.translatable("screen.bending.loadout.title", element.displayName())
                .withColor(element.color()), width / 2, 12, 0xFFFFFF);

        for (int slot = 0; slot < BendingData.SLOTS; slot++) {
            int x = left + slot * (columnWidth + GAP);
            Component name = Component.literal((slot + 1) + "  ")
                    .append(Component.translatable("screen.bending.slot." + SLOT_NAMES[slot]));
            g.drawString(font, name, x, TOP, 0xFFFFFF);
            Component key = Component.literal("[").append(ClientInput.SLOT_KEYS[slot].getTranslatedKeyMessage()).append("]");
            g.drawString(font, key, x + columnWidth - font.width(key), TOP, 0xA0A0A0);
            Component equipped = data.getLoadout(slot).isEmpty()
                    ? Component.translatable("screen.bending.loadout.empty").withStyle(ChatFormatting.DARK_GRAY)
                    : Component.translatable("screen.bending.loadout.equipped").withStyle(ChatFormatting.GRAY);
            g.drawString(font, equipped, x, TOP + 11, 0xFFFFFF);
        }

        // Innate abilities: always ready in bending mode, no slot needed.
        g.drawString(font, Component.translatable("screen.bending.loadout.innate"), left, innateTop, 0xFFFFFF);
        int innateWidth = (4 * columnWidth + 3 * GAP) / Math.max(1, innates.size());
        Ability hovered = null;
        for (int i = 0; i < innates.size(); i++) {
            Ability innate = innates.get(i);
            int x = left + i * innateWidth;
            int y = innateTop + 13;
            g.drawString(font, innate.displayName(), x, y, element.color());
            g.drawString(font, Component.translatable("screen.bending.innate_control." + innate.id().substring(innate.id().indexOf(':') + 1)), x, y + 11, 0xA0A0A0);
            if (mouseX >= x && mouseX < x + innateWidth - 4 && mouseY >= y - 2 && mouseY < y + 21) {
                hovered = innate;
            }
        }

        Component hint = BendingService.canUseLocked(minecraft.player)
                ? Component.translatable("screen.bending.loadout.hint_creative")
                : Component.translatable("screen.bending.loadout.hint");
        g.drawCenteredString(font, hint, width / 2, Math.min(height - 12, innateTop + 48), 0x808080);

        if (hovered != null) {
            MutableComponent text = hovered.displayName().copy().withStyle(ChatFormatting.BOLD)
                    .append(Component.literal("\n"))
                    .append(chiText(hovered).copy().withStyle(ChatFormatting.AQUA))
                    .append(Component.literal("\n"))
                    .append(hovered.description());
            g.renderTooltip(font, font.split(text, 200), mouseX, mouseY);
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (ClientInput.MENU_KEY.matches(keyCode, scanCode)) {
            onClose(); // B toggles the menu
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
