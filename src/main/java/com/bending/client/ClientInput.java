package com.bending.client;

import org.lwjgl.glfw.GLFW;

import com.bending.ability.AirJump;
import com.bending.server.CastHandler;
import com.bending.data.BendingData;
import com.bending.data.ModAttachments;
import com.bending.network.AirJumpPayload;
import com.bending.network.CastAbilityPayload;
import com.bending.network.MoveInputPayload;
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
    public static final KeyMapping SHOULDER_KEY =
            new KeyMapping("key.bending.swap_shoulder", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_O, CATEGORY);

    private static final boolean[] wasDown = new boolean[BendingData.SLOTS];
    private static boolean prompted;
    private static boolean jumpWasDown;
    private static boolean attackWasDown;
    private static boolean sneakWasDown;
    /** True while the innate dash is being channelled (Ctrl held). */
    private static boolean dashing;
    private static int dashTicks;
    private static int airJumpsUsed;
    private static byte sentForward;
    private static byte sentLeft;

    private ClientInput() {}

    public static boolean isHeld(int slot) {
        return wasDown[slot];
    }

    private static boolean anySlotHeld() {
        for (boolean down : wasDown) {
            if (down) {
                return true;
            }
        }
        return false;
    }

    /** True if the local player is in bending mode. */
    public static boolean inBendingMode() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && mc.player.getData(ModAttachments.BENDING).bendingMode;
    }

    /**
     * In bending mode your hands are busy bending: left click casts your innate attack instead
     * of swinging or mining, and right click doesn't use items. Press the mode key to go back to normal.
     */
    public static void onInteractionKey(InputEvent.InteractionKeyMappingTriggered event) {
        if (inBendingMode() && (event.isAttack() || event.isUseItem())) {
            event.setSwingHand(false);
            event.setCanceled(true);
        }
    }

    /**
     * True if the key bound to {@code key} is physically held. Used for the sprint key (Ctrl),
     * because with "toggle sprint" on, isDown() reports the toggle, not the key.
     */
    private static boolean physicallyDown(Minecraft mc, KeyMapping key) {
        InputConstants.Key bound = key.getKey();
        long window = mc.getWindow().getWindow();
        return switch (bound.getType()) {
            case KEYSYM -> InputConstants.isKeyDown(window, bound.getValue());
            case MOUSE -> GLFW.glfwGetMouseButton(window, bound.getValue()) == GLFW.GLFW_PRESS;
            default -> false;
        };
    }

    /**
     * Innate dash: Sprint + Jump (Ctrl+Space) launches it; keep holding Ctrl to channel it
     * through the air. Letting go of Ctrl, or landing, ends it.
     * @return true if this jump press started a dash (so it isn't also a fire hop)
     */
    private static boolean tickDash(Minecraft mc, BendingData data, boolean jumpPressed) {
        boolean ctrl = data.bendingMode && mc.screen == null && physicallyDown(mc, mc.options.keySprint);
        boolean started = false;
        if (!dashing && ctrl && jumpPressed) {
            sendMoveInput(mc, true); // before the press, so the launch already knows your keys
            PacketDistributor.sendToServer(new CastAbilityPayload(CastHandler.INNATE_MOBILITY_SLOT, true));
            dashing = true;
            dashTicks = 0;
            started = true;
        } else if (dashing) {
            dashTicks++;
            sendMoveInput(mc, false); // steer with the keys while channelling
            boolean landed = dashTicks > 5 && (mc.player.onGround() || mc.player.isInWater());
            if (!ctrl || landed) {
                PacketDistributor.sendToServer(new CastAbilityPayload(CastHandler.INNATE_MOBILITY_SLOT, false));
                dashing = false;
            }
        }
        return started;
    }

    /** Tells the server which movement keys are held, when they change (or always if {@code force}). */
    private static void sendMoveInput(Minecraft mc, boolean force) {
        byte forward = (byte) Math.signum(mc.player.input.forwardImpulse);
        byte left = (byte) Math.signum(mc.player.input.leftImpulse);
        if (force || forward != sentForward || left != sentLeft) {
            sentForward = forward;
            sentLeft = left;
            PacketDistributor.sendToServer(new MoveInputPayload(forward, left));
        }
    }

    /** Press jump in mid-air for a small hop; limited uses until you land. */
    private static void tickAirJump(Minecraft mc, BendingData data, boolean pressed) {
        LocalPlayer player = mc.player;

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
            } else if (mc.screen == null) {
                mc.setScreen(new LoadoutScreen());
            }
        }

        while (SHOULDER_KEY.consumeClick()) {
            ShoulderCamera.swapShoulder();
        }

        while (TOGGLE_KEY.consumeClick()) {
            if (data.getElement() == null) {
                mc.player.displayClientMessage(Component.translatable("message.bending.no_element"), true);
            } else {
                PacketDistributor.sendToServer(ToggleBendingModePayload.INSTANCE);
            }
        }

        boolean jumpDown = mc.options.keyJump.isDown();
        boolean jumpPressed = jumpDown && !jumpWasDown;
        jumpWasDown = jumpDown;
        boolean dashStarted = tickDash(mc, data, jumpPressed);
        if (data.bendingMode) {
            tickAirJump(mc, data, jumpPressed && !dashStarted && !dashing);
        }

        // Tap Shift in mid-air: the innate slam. Not while holding an ability (Jet Propulsion uses Shift to hover).
        boolean sneakDown = mc.options.keyShift.isDown() && mc.screen == null;
        if (sneakDown && !sneakWasDown && data.bendingMode && !anySlotHeld()
                && !mc.player.onGround() && !mc.player.isInWater() && !mc.player.onClimbable()
                && !mc.player.getAbilities().flying && !mc.player.isFallFlying()) {
            PacketDistributor.sendToServer(new CastAbilityPayload(CastHandler.INNATE_SLAM_SLOT, true));
            PacketDistributor.sendToServer(new CastAbilityPayload(CastHandler.INNATE_SLAM_SLOT, false));
        }
        sneakWasDown = sneakDown;

        // Left click in bending mode: the element's innate attack (Fire Blast), once per click.
        boolean attackDown = mc.options.keyAttack.isDown() && mc.screen == null;
        if (attackDown && !attackWasDown && data.bendingMode) {
            PacketDistributor.sendToServer(new CastAbilityPayload(CastHandler.INNATE_ATTACK_SLOT, true));
            PacketDistributor.sendToServer(new CastAbilityPayload(CastHandler.INNATE_ATTACK_SLOT, false));
        }
        attackWasDown = attackDown;

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
