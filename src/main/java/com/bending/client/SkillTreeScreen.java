package com.bending.client;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import com.bending.data.BendingData;
import com.bending.data.Element;
import com.bending.data.ModAttachments;
import com.bending.network.UnlockSkillPayload;
import com.bending.server.BendingService;
import com.bending.skill.SkillBranch;
import com.bending.skill.SkillNode;
import com.bending.skill.SkillTree;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The skill tree (B, then the Skill Tree tab): three branches of nodes, each unlocking an ability for
 * your loadout or a master technique. Click a glowing node to spend skill points on it. Lines show
 * what each node needs first. Not-yet-built nodes are shown as "coming soon" so the full tree is visible.
 */
public class SkillTreeScreen extends Screen {
    private static final int MAX_COLUMN_WIDTH = 170;
    private static final int GAP = 10;
    private static final int BRANCH_TOP = 46;
    private static final int NODES_TOP = BRANCH_TOP + 28;
    private static final int TIER_SPACING = 44;
    private static final int NODE_HEIGHT = 30;
    private static final int XP_BAR_WIDTH = 90;

    private final Map<SkillNode, int[]> rects = new LinkedHashMap<>();
    private String signature = "";
    private int columnWidth;
    private int left;
    private int bottom;

    public SkillTreeScreen() {
        super(Component.translatable("screen.bending.skill_tree"));
    }

    private BendingData data() {
        return minecraft.player.getData(ModAttachments.BENDING);
    }

    private boolean free() {
        return BendingService.canUseLocked(minecraft.player);
    }

    private String signatureOf(BendingData data) {
        StringBuilder s = new StringBuilder().append(free()).append('|').append(data.getSkillPoints());
        for (SkillNode node : SkillTree.of(data.getElement())) {
            s.append(data.isUnlocked(node.id()) ? '1' : '0');
        }
        return s.toString();
    }

    @Override
    protected void init() {
        BendingData data = data();
        signature = signatureOf(data);
        BendingTabs.add(this, this::addRenderableWidget, BendingTabs.Tab.SKILLS, data);

        int panel = Math.min(width - 20, MAX_COLUMN_WIDTH * 3 + GAP * 2);
        columnWidth = (panel - GAP * 2) / 3;
        left = (width - panel) / 2;

        // Lay out each branch: nodes on the same tier sit side by side.
        rects.clear();
        List<SkillNode> nodes = SkillTree.of(data.getElement());
        int deepest = 0;
        for (SkillBranch branch : SkillBranch.values()) {
            int columnX = left + branch.ordinal() * (columnWidth + GAP);
            for (int tier = 0; tier < 8; tier++) {
                List<SkillNode> row = new ArrayList<>();
                for (SkillNode node : nodes) {
                    if (node.branch() == branch && node.tier() == tier) {
                        row.add(node);
                    }
                }
                if (row.isEmpty()) {
                    continue;
                }
                deepest = Math.max(deepest, tier);
                int nodeWidth = Math.min(96, (columnWidth - 6 * (row.size() - 1)) / Math.max(2, row.size()));
                int rowWidth = row.size() * nodeWidth + (row.size() - 1) * 6;
                int x = columnX + (columnWidth - rowWidth) / 2;
                for (SkillNode node : row) {
                    rects.put(node, new int[] {x, NODES_TOP + tier * TIER_SPACING, nodeWidth, NODE_HEIGHT});
                    x += nodeWidth + 6;
                }
            }
        }
        bottom = NODES_TOP + deepest * TIER_SPACING + NODE_HEIGHT + 8;
    }

    @Override
    public void tick() {
        if (minecraft.player == null || data().getElement() == null) {
            onClose();
            return;
        }
        if (!signatureOf(data()).equals(signature)) {
            rebuildWidgets();
        }
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(g, mouseX, mouseY, partialTick);
        for (SkillBranch branch : SkillBranch.values()) {
            int x = left + branch.ordinal() * (columnWidth + GAP);
            g.fill(x - 4, BRANCH_TOP - 5, x + columnWidth + 4, bottom, 0x66000000);
            g.fill(x - 4, BRANCH_TOP - 5, x + columnWidth + 4, BRANCH_TOP - 4, 0xFF000000 | branch.color());
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        BendingData data = data();
        Element element = data.getElement();
        if (element == null) {
            return;
        }
        boolean free = free();
        g.drawCenteredString(font, Component.translatable("screen.bending.skill_tree.title", element.displayName())
                .withColor(element.color()), width / 2, 12, 0xFFFFFF);
        renderProgress(g, data, free);

        for (SkillBranch branch : SkillBranch.values()) {
            int x = left + branch.ordinal() * (columnWidth + GAP);
            g.drawCenteredString(font, branch.displayName(), x + columnWidth / 2, BRANCH_TOP, branch.color());
            List<FormattedCharSequence> theme = font.split(branch.theme(), columnWidth);
            if (!theme.isEmpty()) {
                g.drawCenteredString(font, theme.get(0), x + columnWidth / 2, BRANCH_TOP + 11, 0x909090);
            }
        }

        // Connections first, so the nodes draw over them.
        for (Map.Entry<SkillNode, int[]> entry : rects.entrySet()) {
            SkillNode node = entry.getKey();
            SkillNode parent = node.requires() == null ? null : SkillTree.get(node.requires());
            int[] to = entry.getValue();
            int[] from = parent == null ? null : rects.get(parent);
            if (from == null) {
                continue;
            }
            int color = data.isUnlocked(node.id()) ? 0xFF000000 | node.branch().color()
                    : data.isUnlocked(parent.id()) ? 0xFF8A8A8A : 0xFF4A4A4A;
            int fx = from[0] + from[2] / 2, fy = from[1] + from[3];
            int tx = to[0] + to[2] / 2, ty = to[1];
            int midY = (fy + ty) / 2;
            g.fill(fx, fy, fx + 1, midY + 1, color);
            g.fill(Math.min(fx, tx), midY, Math.max(fx, tx) + 1, midY + 1, color);
            g.fill(tx, midY, tx + 1, ty, color);
        }

        SkillNode hovered = null;
        for (Map.Entry<SkillNode, int[]> entry : rects.entrySet()) {
            SkillNode node = entry.getKey();
            int[] r = entry.getValue();
            boolean over = mouseX >= r[0] && mouseX < r[0] + r[2] && mouseY >= r[1] && mouseY < r[1] + r[3];
            if (over) {
                hovered = node;
            }
            renderNode(g, node, r, SkillTree.state(node, data, free), over);
        }

        Component hint = free ? Component.translatable("screen.bending.skill_tree.hint_creative")
                : Component.translatable("screen.bending.skill_tree.hint");
        g.drawCenteredString(font, hint, width / 2, Math.min(height - 12, bottom + 8), 0x808080);

        if (hovered != null) {
            g.renderTooltip(font, font.split(tooltip(hovered, SkillTree.state(hovered, data, free)), 220), mouseX, mouseY);
        }
    }

    /** Level, XP toward the next level, and unspent skill points. */
    private void renderProgress(GuiGraphics g, BendingData data, boolean free) {
        int points = data.getSkillPoints();
        Component level = Component.translatable("screen.bending.skill_tree.level", data.getLevel());
        Component pointsText = free ? Component.translatable("screen.bending.skill_tree.points_free")
                : Component.translatable(points == 1 ? "screen.bending.skill_tree.point" : "screen.bending.skill_tree.points", points);
        int total = font.width(level) + 8 + XP_BAR_WIDTH + 8 + font.width(pointsText);
        int x = width / 2 - total / 2;
        int y = 28;
        g.drawString(font, level, x, y, 0xFFFFFF);
        x += font.width(level) + 8;
        float progress = Math.min(1f, data.getXp() / (float) BendingData.xpForNextLevel(data.getLevel()));
        g.fill(x, y + 2, x + XP_BAR_WIDTH, y + 7, 0xFF202020);
        g.fill(x, y + 2, x + Math.round(XP_BAR_WIDTH * progress), y + 7, 0xFF7FD34A);
        g.renderOutline(x - 1, y + 1, XP_BAR_WIDTH + 2, 7, 0xFF000000);
        x += XP_BAR_WIDTH + 8;
        g.drawString(font, pointsText, x, y, points > 0 || free ? 0xFFD94A : 0x909090);
    }

    private void renderNode(GuiGraphics g, SkillNode node, int[] r, SkillTree.State state, boolean over) {
        int x = r[0], y = r[1], w = r[2], h = r[3];
        int branchColor = 0xFF000000 | node.branch().color();
        int fill, border, text;
        Component status;
        switch (state) {
            case LEARNED -> {
                fill = 0x55000000 | node.branch().color() & 0xFFFFFF;
                border = branchColor;
                text = 0xFFFFFF;
                status = Component.literal("✔");
            }
            case AVAILABLE -> {
                // Gently pulsing so you can see what you can learn right now.
                float pulse = 0.55f + 0.45f * (float) Math.sin(System.currentTimeMillis() / 250.0);
                fill = 0xDD1A1A1A;
                border = ((int) (pulse * 255) << 24) | node.branch().color();
                text = 0xFFFFFF;
                status = costText(node).copy().withStyle(ChatFormatting.YELLOW);
            }
            case NEED_POINTS -> {
                fill = 0xDD161616;
                border = 0xFF777777;
                text = 0xBBBBBB;
                status = costText(node);
            }
            case NEED_PREVIOUS -> {
                fill = 0xDD121212;
                border = 0xFF444444;
                text = 0x777777;
                status = costText(node);
            }
            default -> {
                fill = 0x99101010;
                border = 0xFF333333;
                text = 0x5A5A5A;
                status = Component.translatable("screen.bending.skill_tree.soon");
            }
        }
        g.fill(x, y, x + w, y + h, fill);
        g.renderOutline(x, y, w, h, over && state != SkillTree.State.COMING_SOON ? 0xFFFFFFFF : border);
        if (node.technique()) {
            // Master techniques get a double border.
            g.renderOutline(x + 2, y + 2, w - 4, h - 4, (border & 0x00FFFFFF) | 0x88000000);
        }
        List<FormattedCharSequence> lines = font.split(node.name(), w - 6);
        int lineY = y + (lines.size() > 1 ? 3 : 6);
        for (int i = 0; i < Math.min(2, lines.size()); i++) {
            g.drawCenteredString(font, lines.get(i), x + w / 2, lineY + i * 9, text);
        }
        g.drawCenteredString(font, status, x + w / 2, y + h - 10, state == SkillTree.State.LEARNED ? branchColor : text);
    }

    private static Component costText(SkillNode node) {
        return Component.translatable(node.cost() == 1 ? "screen.bending.skill_tree.cost_one" : "screen.bending.skill_tree.cost", node.cost());
    }

    private Component tooltip(SkillNode node, SkillTree.State state) {
        MutableComponent kind = node.technique() ? Component.translatable("screen.bending.skill_tree.technique")
                : node.stat() ? Component.translatable("screen.bending.skill_tree.stat")
                : node.ability() != null ? Component.translatable("screen.bending.skill_tree.ability",
                        Component.translatable("screen.bending.slot." + slotName(node)))
                : Component.translatable("screen.bending.skill_tree.ability_soon");
        MutableComponent text = node.name().copy().withColor(node.branch().color()).withStyle(ChatFormatting.BOLD)
                .append(Component.literal("\n"))
                .append(kind.withStyle(ChatFormatting.GRAY))
                .append(Component.literal("  "))
                .append(costText(node).copy().withStyle(ChatFormatting.YELLOW));
        @Nullable SkillNode parent = node.requires() == null ? null : SkillTree.get(node.requires());
        if (parent != null) {
            text.append(Component.literal("\n")).append(Component.translatable("screen.bending.skill_tree.requires", parent.name())
                    .withStyle(state == SkillTree.State.NEED_PREVIOUS ? ChatFormatting.RED : ChatFormatting.DARK_GRAY));
        }
        text.append(Component.literal("\n")).append(node.description().copy().withStyle(ChatFormatting.WHITE));
        Component status = switch (state) {
            case LEARNED -> Component.translatable(node.ability() != null
                    ? "screen.bending.skill_tree.learned_ability"
                    : node.technique() || node.stat() ? "screen.bending.skill_tree.learned_technique"
                    : "screen.bending.skill_tree.learned").withStyle(ChatFormatting.GREEN);
            case AVAILABLE -> Component.translatable("screen.bending.skill_tree.click_learn").withStyle(ChatFormatting.YELLOW);
            case NEED_POINTS -> Component.translatable("screen.bending.skill_tree.need_points").withStyle(ChatFormatting.RED);
            case NEED_PREVIOUS -> Component.translatable("screen.bending.skill_tree.need_previous").withStyle(ChatFormatting.RED);
            case COMING_SOON -> Component.translatable("screen.bending.skill_tree.coming_soon").withStyle(ChatFormatting.DARK_GRAY);
        };
        return text.append(Component.literal("\n")).append(status);
    }

    private static String slotName(SkillNode node) {
        return switch (node.ability().slot()) {
            case PROJECTILE -> "projectile";
            case DEFENSE -> "defense";
            case MOBILITY -> "mobility";
            case ULTIMATE -> "ultimate";
            case INNATE -> "innate";
        };
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (button != 0) {
            return false;
        }
        for (Map.Entry<SkillNode, int[]> entry : rects.entrySet()) {
            int[] r = entry.getValue();
            if (mouseX >= r[0] && mouseX < r[0] + r[2] && mouseY >= r[1] && mouseY < r[1] + r[3]) {
                SkillNode node = entry.getKey();
                if (SkillTree.state(node, data(), free()) == SkillTree.State.AVAILABLE) {
                    PacketDistributor.sendToServer(new UnlockSkillPayload(node.id()));
                    minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1f));
                }
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (ClientInput.MENU_KEY.matches(keyCode, scanCode)) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
