package com.bending.client;

import com.bending.ability.Abilities;
import com.bending.data.Element;
import com.bending.network.ChooseElementPayload;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/** Shown on first join: pick one of the four bending arts. */
public class ElementSelectScreen extends Screen {
    private static final int BUTTON_WIDTH = 160;
    private static final int BUTTON_HEIGHT = 20;

    public ElementSelectScreen() {
        super(Component.translatable("screen.bending.choose"));
    }

    @Override
    protected void init() {
        int x = width / 2 - BUTTON_WIDTH / 2;
        int y = height / 2 - 45;
        for (Element element : Element.values()) {
            boolean ready = Abilities.isImplemented(element);
            Component label = element.displayName().copy().withColor(ready ? element.color() : 0x808080);
            if (!ready) {
                label = label.copy().append(" ").append(
                        Component.translatable("screen.bending.coming_soon").withStyle(ChatFormatting.GRAY));
            }
            Button button = addRenderableWidget(Button.builder(label, b -> {
                PacketDistributor.sendToServer(new ChooseElementPayload(element));
                onClose();
            }).bounds(x, y, BUTTON_WIDTH, BUTTON_HEIGHT).build());
            button.active = ready;
            y += BUTTON_HEIGHT + 4;
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, height / 2 - 70, 0xFFFFFF);
        graphics.drawCenteredString(font, Component.translatable("screen.bending.choose_hint"),
                width / 2, height / 2 - 58, 0xA0A0A0);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
