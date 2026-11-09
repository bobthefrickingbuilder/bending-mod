package com.bending.client;

import java.util.function.Consumer;

import com.bending.data.BendingData;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** The tab bar shared by the bending menu screens: Loadout and Skill Tree. */
final class BendingTabs {
    enum Tab { LOADOUT, SKILLS }

    private static final int WIDTH = 74;
    private static final int HEIGHT = 16;

    private BendingTabs() {}

    /** Adds the tabs to the top-left of {@code screen}; the current one is shown pressed (inactive). */
    static void add(Screen screen, Consumer<Button> addWidget, Tab current, BendingData data) {
        Button loadout = Button.builder(Component.translatable("screen.bending.tab.loadout"),
                b -> Minecraft.getInstance().setScreen(new LoadoutScreen()))
                .bounds(8, 6, WIDTH, HEIGHT).build();
        Component skillsLabel = data.getSkillPoints() > 0
                ? Component.translatable("screen.bending.tab.skills_points", data.getSkillPoints())
                : Component.translatable("screen.bending.tab.skills");
        Button skills = Button.builder(skillsLabel, b -> Minecraft.getInstance().setScreen(new SkillTreeScreen()))
                .bounds(8 + WIDTH + 4, 6, WIDTH, HEIGHT).build();
        loadout.active = current != Tab.LOADOUT;
        skills.active = current != Tab.SKILLS;
        addWidget.accept(loadout);
        addWidget.accept(skills);
    }
}
