package com.bending.client;

import java.util.List;

import javax.annotation.Nullable;

import com.bending.data.BendingData;
import com.bending.data.Element;
import com.bending.data.ModAttachments;
import com.bending.network.UnlockSkillPayload;
import com.bending.server.BendingService;
import com.bending.skill.SkillBranch;
import com.bending.skill.SkillNode;
import com.bending.skill.SkillNode.Kind;
import com.bending.skill.SkillStat;
import com.bending.skill.SkillStats;
import com.bending.skill.SkillTree;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The skill tree (B, then the Skill Tree tab): a radial tree growing out from your Inner Fire.
 * Abilities and master techniques are the big nodes; small squares are ranked upgrades and body
 * passives (the two letters say what they raise); gold-framed nodes are exclusive choices, linked to
 * the partner they lock out. Click a glowing node to learn it (or its next rank). Drag to move
 * around, scroll to zoom.
 */
public class SkillTreeScreen extends Screen {
    /** World pixels between rings. Must match the layout spec the tree was generated from. */
    private static final int RING_PX = 46;
    private static final int TOP = 42;
    private static final int XP_BAR_WIDTH = 90;
    private static final double MIN_ZOOM = 0.4, MAX_ZOOM = 2.5;

    // Kept between openings so the tree stays where you left it.
    private static double zoom = -1;
    private static double panX, panY;

    private double pressX, pressY;
    private boolean dragged;

    public SkillTreeScreen() {
        super(Component.translatable("screen.bending.skill_tree"));
    }

    private BendingData data() {
        return minecraft.player.getData(ModAttachments.BENDING);
    }

    private boolean free() {
        return BendingService.canUseLocked(minecraft.player);
    }

    @Override
    protected void init() {
        BendingTabs.add(this, this::addRenderableWidget, BendingTabs.Tab.SKILLS, data());
        if (zoom < 0) {
            double fit = Math.min(width, height - TOP - 16) / 2.0 / (8.4 * RING_PX);
            zoom = Mth.clamp(fit, 0.55, 1.2);
        }
    }

    @Override
    public void tick() {
        if (minecraft.player == null || data().getElement() == null) {
            onClose();
        }
    }

    // ---------------- geometry ----------------

    private double cx() {
        return width / 2.0;
    }

    private double cy() {
        return TOP + (height - TOP - 14) / 2.0;
    }

    private static double wx(SkillNode node) {
        return Math.cos(Math.toRadians(node.angle())) * node.ring() * RING_PX;
    }

    private static double wy(SkillNode node) {
        return Math.sin(Math.toRadians(node.angle())) * node.ring() * RING_PX;
    }

    /** Node box size in world pixels: {width, height of the box itself}. */
    private static int[] size(SkillNode node) {
        return switch (node.kind()) {
            case ABILITY, TECHNIQUE -> new int[] {52, 22};
            case CORE -> new int[] {30, 30};
            case CHOICE -> new int[] {18, 18};
            default -> new int[] {16, 16};
        };
    }

    private double toWorldX(double mouseX) {
        return (mouseX - cx()) / zoom - panX;
    }

    private double toWorldY(double mouseY) {
        return (mouseY - cy()) / zoom - panY;
    }

    @Nullable
    private SkillNode nodeAt(double mouseX, double mouseY) {
        if (mouseY < TOP - 4) {
            return null;
        }
        double x = toWorldX(mouseX), y = toWorldY(mouseY);
        for (SkillNode node : SkillTree.of(data().getElement())) {
            int[] s = size(node);
            if (Math.abs(x - wx(node)) <= s[0] / 2.0 && Math.abs(y - wy(node)) <= s[1] / 2.0) {
                return node;
            }
        }
        return null;
    }

    // ---------------- drawing ----------------

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        BendingData data = data();
        Element element = data.getElement();
        if (element == null) {
            return;
        }
        boolean free = free();
        List<SkillNode> nodes = SkillTree.of(element);
        SkillNode hovered = nodeAt(mouseX, mouseY);

        g.enableScissor(0, TOP - 4, width, height - 12);
        g.pose().pushPose();
        g.pose().translate(cx(), cy(), 0);
        g.pose().scale((float) zoom, (float) zoom, 1f);
        g.pose().translate(panX, panY, 0);

        renderRings(g, element);
        for (SkillNode node : nodes) {
            for (String req : node.requires()) {
                SkillNode parent = SkillTree.get(req);
                if (parent != null) {
                    boolean on = SkillTree.rank(node, data) > 0;
                    boolean open = SkillTree.learned(parent.id(), data);
                    int color = on ? 0xFF000000 | node.branch().color() : open ? 0xFF8A8A8A : 0xFF3A3A3A;
                    line(g, wx(parent), wy(parent), wx(node), wy(node), color, on ? 2 : 1, false);
                }
            }
        }
        // Exclusive choices: a dashed red link between the partners.
        for (int i = 0; i < nodes.size(); i++) {
            SkillNode a = nodes.get(i);
            if (a.group() == null) {
                continue;
            }
            for (int j = i + 1; j < nodes.size(); j++) {
                SkillNode b = nodes.get(j);
                if (a.group().equals(b.group())) {
                    line(g, wx(a), wy(a), wx(b), wy(b), 0xFFD04030, 1, true);
                }
            }
        }
        for (SkillNode node : nodes) {
            renderNode(g, node, data, SkillTree.state(node, data, free), node == hovered, element);
        }
        g.pose().popPose();
        g.disableScissor();

        g.drawCenteredString(font, Component.translatable("screen.bending.skill_tree.title", element.displayName())
                .withColor(element.color()), width / 2, 12, 0xFFFFFF);
        renderProgress(g, data, free);
        Component hint = free ? Component.translatable("screen.bending.skill_tree.hint_creative")
                : Component.translatable("screen.bending.skill_tree.hint");
        g.drawCenteredString(font, hint, width / 2, height - 11, 0x808080);

        if (hovered != null) {
            g.renderTooltip(font, font.split(tooltip(hovered, data, SkillTree.state(hovered, data, free)), 240), mouseX, mouseY);
        }
    }

    /** Faint guide rings, and each branch's name out past its last node. */
    private void renderRings(GuiGraphics g, Element element) {
        for (int ring = 1; ring <= 7; ring++) {
            double r = ring * RING_PX;
            int dots = (int) (r * 0.9);
            for (int i = 0; i < dots; i++) {
                double a = i * Math.PI * 2 / dots;
                int x = (int) Math.round(Math.cos(a) * r), y = (int) Math.round(Math.sin(a) * r);
                g.fill(x, y, x + 1, y + 1, 0x30FFFFFF);
            }
        }
        for (SkillBranch branch : SkillBranch.values()) {
            if (branch == SkillBranch.CORE) {
                continue;
            }
            double a = Math.toRadians(branch.angle());
            double r = 8.1 * RING_PX;
            int x = (int) (Math.cos(a) * r), y = (int) (Math.sin(a) * r);
            g.pose().pushPose();
            g.pose().translate(x, y, 0);
            g.pose().scale(1.4f, 1.4f, 1f);
            g.drawCenteredString(font, branch.displayName(), 0, -4, branch.color());
            g.pose().popPose();
            g.pose().pushPose();
            g.pose().translate(x, y + 9, 0);
            g.pose().scale(0.8f, 0.8f, 1f);
            g.drawCenteredString(font, branch.theme(), 0, 0, 0x909090);
            g.pose().popPose();
        }
    }

    private static void line(GuiGraphics g, double x1, double y1, double x2, double y2, int color, int thick, boolean dashed) {
        double dx = x2 - x1, dy = y2 - y1;
        int steps = (int) Math.max(Math.abs(dx), Math.abs(dy));
        for (int i = 0; i <= steps; i++) {
            if (dashed && (i / 3) % 2 == 1) {
                continue;
            }
            double t = steps == 0 ? 0 : i / (double) steps;
            int x = (int) Math.round(x1 + dx * t), y = (int) Math.round(y1 + dy * t);
            g.fill(x, y, x + thick, y + thick, color);
        }
    }

    private void renderNode(GuiGraphics g, SkillNode node, BendingData data, SkillTree.State state, boolean over, Element element) {
        int[] s = size(node);
        int x = (int) Math.round(wx(node) - s[0] / 2.0), y = (int) Math.round(wy(node) - s[1] / 2.0);
        int w = s[0], h = s[1];
        int rgb = node.kind() == Kind.CORE ? element.color() & 0xFFFFFF : node.branch().color();
        int rank = SkillTree.rank(node, data);
        int fill, border, text;
        switch (state) {
            case LEARNED -> {
                fill = 0x66000000 | rgb;
                border = 0xFF000000 | rgb;
                text = 0xFFFFFF;
            }
            case AVAILABLE -> {
                float pulse = 0.55f + 0.45f * (float) Math.sin(System.currentTimeMillis() / 250.0);
                fill = rank > 0 ? 0x44000000 | rgb : 0xEE1A1A1A;
                border = ((int) (pulse * 255) << 24) | rgb;
                text = 0xFFFFFF;
            }
            case NEED_POINTS -> {
                fill = rank > 0 ? 0x44000000 | rgb : 0xEE161616;
                border = 0xFF777777;
                text = 0xBBBBBB;
            }
            case LOCKED_OUT -> {
                fill = 0xEE200C0C;
                border = 0xFF6A2A2A;
                text = 0x6A4A4A;
            }
            default -> {
                fill = 0xEE121212;
                border = 0xFF404040;
                text = 0x6A6A6A;
            }
        }
        g.fill(x, y, x + w, y + h, fill);
        g.renderOutline(x, y, w, h, over ? 0xFFFFFFFF : border);
        if (node.kind() == Kind.TECHNIQUE) {
            g.renderOutline(x + 2, y + 2, w - 4, h - 4, (border & 0x00FFFFFF) | 0x88000000);
        } else if (node.kind() == Kind.CHOICE) {
            g.renderOutline(x - 2, y - 2, w + 4, h + 4, state == SkillTree.State.LOCKED_OUT ? 0xFF5A3A20 : 0xFFE0B040);
        }

        switch (node.kind()) {
            case ABILITY, TECHNIQUE -> {
                float scale = 0.62f;
                List<FormattedCharSequence> lines = font.split(node.name(), (int) ((w - 4) / scale));
                int n = Math.min(2, lines.size());
                float lineH = 9 * scale;
                float top = y + h / 2f - n * lineH / 2f + 0.5f;
                for (int i = 0; i < n; i++) {
                    drawScaled(g, lines.get(i), x + w / 2f, top + i * lineH, scale, text);
                }
            }
            case CORE -> {
                drawScaled(g, Component.literal("✹").getVisualOrderText(), x + w / 2f, y + 9, 1.4f, 0xFFFFFF);
                drawScaled(g, node.name().getVisualOrderText(), x + w / 2f, y + h + 2, 0.7f, rgb);
            }
            default -> {
                SkillStat stat = node.effects().isEmpty() ? null : node.effects().get(0).stat();
                drawScaled(g, Component.literal(code(stat)).getVisualOrderText(), x + w / 2f, y + h / 2f - 3, 0.75f,
                        state == SkillTree.State.LOCKED_OUT ? text : statColor(stat));
                if (state == SkillTree.State.LOCKED_OUT) {
                    drawScaled(g, Component.literal("✖").getVisualOrderText(), x + w / 2f, y + h / 2f - 4, 1f, 0xFFB03030);
                }
                drawScaled(g, Component.literal(rank + "/" + node.maxRank()).getVisualOrderText(), x + w / 2f, y + h + 1.5f, 0.55f,
                        rank > 0 ? rgb : 0x8A8A8A);
            }
        }
    }

    private void drawScaled(GuiGraphics g, FormattedCharSequence text, float centerX, float y, float scale, int color) {
        g.pose().pushPose();
        g.pose().translate(centerX, y, 0);
        g.pose().scale(scale, scale, 1f);
        g.drawString(font, text, -font.width(text) / 2, 0, color, false);
        g.pose().popPose();
    }

    /** Two letters for what a small node raises. */
    private static String code(@Nullable SkillStat stat) {
        if (stat == null) {
            return "?";
        }
        return switch (stat) {
            case DAMAGE -> "DM";
            case RANGE -> "RG";
            case SIZE -> "SZ";
            case SPEED -> "SP";
            case CHARGE_TIME -> "CT";
            case CHI_COST -> "CC";
            case DURATION -> "DU";
            case CHAIN -> "FK";
            case BOLTS -> "BO";
            case MAX_HEALTH -> "HP";
            case MAX_CHI -> "MC";
            case REGEN -> "RE";
            case REGEN_DELAY -> "CA";
            case RESISTANCE -> "AR";
            case MOVE_SPEED -> "MS";
            case BURN -> "BU";
        };
    }

    private static int statColor(@Nullable SkillStat stat) {
        if (stat == null) {
            return 0xFFFFFF;
        }
        return switch (stat) {
            case DAMAGE, BURN -> 0xFF6A4A;
            case RANGE -> 0x7FE07F;
            case SIZE -> 0xFFB040;
            case SPEED, MOVE_SPEED -> 0x6AE0E0;
            case CHARGE_TIME -> 0xC08AFF;
            case CHI_COST, MAX_CHI, REGEN, REGEN_DELAY -> 0x6AA8FF;
            case DURATION -> 0xFFE070;
            case CHAIN, BOLTS -> 0xA0D8FF;
            case MAX_HEALTH -> 0xFF6A8A;
            case RESISTANCE -> 0xC8C8C8;
        };
    }

    /** Level, XP toward the next level, and unspent skill points. */
    private void renderProgress(GuiGraphics g, BendingData data, boolean free) {
        int points = data.getSkillPoints();
        Component level = Component.translatable("screen.bending.skill_tree.level", data.getLevel());
        Component pointsText = free ? Component.translatable("screen.bending.skill_tree.points_free")
                : Component.translatable(points == 1 ? "screen.bending.skill_tree.point" : "screen.bending.skill_tree.points", points);
        int total = font.width(level) + 8 + XP_BAR_WIDTH + 8 + font.width(pointsText);
        int x = width / 2 - total / 2;
        int y = 26;
        g.drawString(font, level, x, y, 0xFFFFFF);
        x += font.width(level) + 8;
        float progress = Math.min(1f, data.getXp() / (float) BendingData.xpForNextLevel(data.getLevel()));
        g.fill(x, y + 2, x + XP_BAR_WIDTH, y + 7, 0xFF202020);
        g.fill(x, y + 2, x + Math.round(XP_BAR_WIDTH * progress), y + 7, 0xFF7FD34A);
        g.renderOutline(x - 1, y + 1, XP_BAR_WIDTH + 2, 7, 0xFF000000);
        x += XP_BAR_WIDTH + 8;
        g.drawString(font, pointsText, x, y, points > 0 || free ? 0xFFD94A : 0x909090);
    }

    private static Component costText(SkillNode node) {
        return Component.translatable(node.cost() == 1 ? "screen.bending.skill_tree.cost_one" : "screen.bending.skill_tree.cost", node.cost());
    }

    private Component tooltip(SkillNode node, BendingData data, SkillTree.State state) {
        int rank = SkillTree.rank(node, data);
        MutableComponent kind = switch (node.kind()) {
            case CORE -> Component.translatable("screen.bending.skill_tree.core");
            case TECHNIQUE -> Component.translatable("screen.bending.skill_tree.technique");
            case ABILITY -> Component.translatable("screen.bending.skill_tree.ability",
                    Component.translatable("screen.bending.slot." + slotName(node)));
            case CHOICE -> Component.translatable("screen.bending.skill_tree.choice");
            case PASSIVE -> Component.translatable("screen.bending.skill_tree.passive");
            case UPGRADE -> Component.translatable("screen.bending.skill_tree.upgrade");
        };
        MutableComponent text = node.name().copy().withColor(node.branch().color()).withStyle(ChatFormatting.BOLD)
                .append(Component.literal("\n")).append(kind.withStyle(ChatFormatting.GRAY));
        if (node.kind() != Kind.CORE) {
            text.append(Component.literal("  ")).append(costText(node).copy().withStyle(ChatFormatting.YELLOW));
        }
        if (node.maxRank() > 1) {
            text.append(Component.literal("  ")).append(Component.translatable("screen.bending.skill_tree.rank", rank, node.maxRank())
                    .withStyle(ChatFormatting.AQUA));
        }
        if (!node.requires().isEmpty() && rank == 0 && node.kind() != Kind.CORE) {
            MutableComponent names = Component.empty();
            for (int i = 0; i < node.requires().size(); i++) {
                SkillNode parent = SkillTree.get(node.requires().get(i));
                if (i > 0) {
                    names.append(Component.translatable("screen.bending.skill_tree.or"));
                }
                names.append(parent != null ? parent.name() : Component.literal(node.requires().get(i)));
            }
            text.append(Component.literal("\n")).append(Component.translatable("screen.bending.skill_tree.requires", names)
                    .withStyle(state == SkillTree.State.NEED_PREVIOUS ? ChatFormatting.RED : ChatFormatting.DARK_GRAY));
        }
        // What it does, per rank, and what you have from it now.
        for (SkillNode.Effect effect : node.effects()) {
            MutableComponent line = Component.literal("\n").append(effect.stat().describe(effect.perRank()).copy().withStyle(ChatFormatting.GREEN));
            if (node.maxRank() > 1) {
                line.append(Component.translatable("screen.bending.skill_tree.per_rank").withStyle(ChatFormatting.DARK_GREEN));
                if (rank > 0) {
                    line.append(Component.literal("  ")).append(Component.translatable("screen.bending.skill_tree.now",
                            effect.stat().describe(effect.perRank() * rank)).withStyle(ChatFormatting.GRAY));
                }
            }
            text.append(line);
        }
        Component desc = node.description();
        if (!desc.getString().isEmpty()) {
            text.append(Component.literal("\n")).append(desc.copy().withStyle(ChatFormatting.WHITE));
        }
        @Nullable SkillNode rival = SkillTree.lockedBy(node, data);
        Component status = switch (state) {
            case LEARNED -> Component.translatable(node.kind() == Kind.CORE ? "screen.bending.skill_tree.core_learned"
                    : node.ability() != null ? "screen.bending.skill_tree.learned_ability"
                    : "screen.bending.skill_tree.learned_technique").withStyle(ChatFormatting.GREEN);
            case AVAILABLE -> Component.translatable(rank > 0 ? "screen.bending.skill_tree.click_rank" : "screen.bending.skill_tree.click_learn")
                    .withStyle(ChatFormatting.YELLOW);
            case NEED_POINTS -> Component.translatable("screen.bending.skill_tree.need_points").withStyle(ChatFormatting.RED);
            case NEED_PREVIOUS -> Component.translatable("screen.bending.skill_tree.need_previous").withStyle(ChatFormatting.RED);
            case LOCKED_OUT -> Component.translatable("screen.bending.skill_tree.locked_out",
                    rival != null ? rival.name() : Component.empty()).withStyle(ChatFormatting.DARK_RED);
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

    // ---------------- input ----------------

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        pressX = mouseX;
        pressY = mouseY;
        dragged = false;
        return button == 0;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button == 0 && mouseY > TOP - 4) {
            if (Math.abs(mouseX - pressX) + Math.abs(mouseY - pressY) > 4) {
                dragged = true;
            }
            if (dragged) {
                panX += dragX / zoom;
                panY += dragY / zoom;
            }
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && !dragged) {
            SkillNode node = nodeAt(mouseX, mouseY);
            if (node != null && SkillTree.state(node, data(), free()) == SkillTree.State.AVAILABLE) {
                PacketDistributor.sendToServer(new UnlockSkillPayload(node.id()));
                minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1f));
            }
        }
        dragged = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY == 0) {
            return false;
        }
        // Zoom around the cursor: the point under it stays put.
        double beforeX = toWorldX(mouseX), beforeY = toWorldY(mouseY);
        zoom = Mth.clamp(zoom * (scrollY > 0 ? 1.15 : 1 / 1.15), MIN_ZOOM, MAX_ZOOM);
        panX += toWorldX(mouseX) - beforeX;
        panY += toWorldY(mouseY) - beforeY;
        return true;
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
