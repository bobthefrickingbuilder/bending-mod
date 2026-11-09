package com.bending.client;

import org.lwjgl.glfw.GLFW;

import com.bending.ability.AirJump;
import com.bending.data.BendingData;
import com.bending.data.ModAttachments;
import com.bending.network.AirJumpPayload;
import com.bending.network.CastAbilityPayload;
import com.bending.network.ToggleBendingModePayload;
import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Reads the bending keys each tick and tells the server about presses and releases. */
public final class ClientInput {
    public static final String CATEGORY = "key.categories.bending";

    public static final KeyMapping[] SLOT_KEYS = {
            new KeyMapping("key.bending.slot1", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_Z, CATEGORY),
            new KeyMapping("key.bending.slot2", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_X, CATEGORY),
            new KeyMapping("key.bending.slot3", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_C, CATEGORY),
            new KeyMapping("key.bending.slot4", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_V, CATEGORY),
    };
    public static final KeyMapping MENU_KEY =
            new KeyMapping("key.bending.menu", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_B, CATEGORY);
    public static final KeyMapping TOGGLE_KEY =
            new KeyMapping("key.bending.toggle", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_R, CATEGORY);

    private static final boolean[] wasDown = new boolean[BendingData.SLOTS];
    private static boolean prompted;
    private static boolean jumpWasDown;
    private static int airJumpsUsed;

    private ClientInput() {}

    public static boolean isHeld(int slot) {
        return wasDown[slot];
    }

    /** True if the local player is in bending mode. */
    public static boolean inBendingMode() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && mc.player.getData(ModAttachments.BENDING).bendingMode;
    }

    /**
     * In bending mode your hands are busy bending: left and right click don't swing,
     * mine or use items. Press the mode key again to go back to normal.
     */
    public static void onInteractionKey(InputEvent.InteractionKeyMappingTriggered event) {
        if (inBendingMode() && (event.isAttack() || event.isUseItem())) {
            event.setSwingHand(false);
            event.setCanceled(true);
        }
    }

    /** Press jump in mid-air for a small hop; limited uses until you land. */
    private static void tickAirJump(Minecraft mc, BendingData data) {
        LocalPlayer player = mc.player;
        boolean jumpDown = mc.options.keyJump.isDown();
        boolean pressed = jumpDown && !jumpWasDown;
        jumpWasDown = jumpDown;

        if (player.onGround() || player.isInWater() || player.onClimbable()) {
            airJumpsUsed = 0;
            return;
        }
        if (!pressed || mc.screen != null || airJumpsUsed >= AirJump.maxJumps(data.getElement())
                || player.getAbilities().flying || player.isFallFlying() || player.isPassenger()
                || player.isUnderWater()) {
            return;
        }
        Vec3 v = player.getDeltaMovement();
        if (v.y >= AirJump.HOP_VELOCITY) {
            return; // still rising fast from the last jump: don't waste a hop
        }
        airJumpsUsed++;
        player.setDeltaMovement(v.x, AirJump.HOP_VELOCITY, v.z);
        PacketDistributor.sendToServer(AirJumpPayload.INSTANCE);
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.getConnection() == null) {
            prompted = false;
            return;
        }
        BendingData data = mc.player.getData(ModAttachments.BENDING);

        // First join (or after a reset): ask the player to pick an element.
        if (data.getElement() != null) {
            prompted = false;
        } else if (data.synced && !prompted && mc.screen == null) {
            prompted = true;
            mc.setScreen(new ElementSelectScreen());
        }

        while (MENU_KEY.consumeClick()) {
            if (data.getElement() == null) {
                mc.setScreen(new ElementSelectScreen());
            } else {
                mc.player.displayClientMessage(Component.translatable("message.bending.menu_soon"), true);
            }
        }

        while (TOGGLE_KEY.consumeClick()) {
            if (data.getElement() == null) {
                mc.player.displayClientMessage(Component.translatable("message.bending.no_element"), true);
            } else {
                PacketDistributor.sendToServer(ToggleBendingModePayload.INSTANCE);
            }
        }

        if (data.bendingMode) {
            tickAirJump(mc, data);
        } else {
            jumpWasDown = mc.options.keyJump.isDown();
        }

        for (int slot = 0; slot < SLOT_KEYS.length; slot++) {
            KeyMapping key = SLOT_KEYS[slot];
            while (key.consumeClick()) {
                // Presses are tracked through isDown() below; just drain the click queue.
            }
            boolean down = key.isDown() && data.getElement() != null && data.bendingMode;
            if (down != wasDown[slot]) {
                wasDown[slot] = down;
                PacketDistributor.sendToServer(new CastAbilityPayload(slot, down));
            }
        }
    }
}
