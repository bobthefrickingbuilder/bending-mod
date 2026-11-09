package com.bending.client;

import com.bending.data.ModAttachments;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;

/**
 * Bending mode visuals: both hands show empty.
 * First person: both bare arms are drawn, whatever you're holding.
 * Third person (you and other players): held items are hidden for that frame and the arms hang empty.
 * Items aren't touched for real; they're only hidden while the player is drawn.
 *
 * Hand animations (Phase 8) will plug in here: the arm transforms below are the place to pose them.
 */
public final class BendingHands {
    private BendingHands() {}

    public static void register(IEventBus bus) {
        bus.addListener(BendingHands::onRenderHand);
        // Lowest priority so we hide items only if no other mod cancelled the render.
        bus.addListener(EventPriority.LOWEST, BendingHands::onRenderPlayerPre);
        bus.addListener(BendingHands::onRenderPlayerPost);
    }

    private static boolean inBendingMode(Player player) {
        return player.getData(ModAttachments.BENDING).bendingMode;
    }

    // ---- First person ----

    private static void onRenderHand(RenderHandEvent event) {
        Minecraft mc = Minecraft.getInstance();
        AbstractClientPlayer player = mc.player;
        if (player == null || !inBendingMode(player)) {
            return;
        }
        event.setCanceled(true); // don't draw the held item
        if (player.isInvisible()) {
            return;
        }
        // The game asks for each hand in turn; draw the bare arm on that hand's side.
        HumanoidArm side = event.getHand() == InteractionHand.MAIN_HAND ? player.getMainArm() : player.getMainArm().getOpposite();
        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        renderArm(mc, player, pose, event.getMultiBufferSource(), event.getPackedLight(), event.getSwingProgress(), side);
        pose.popPose();
    }

    /** Vanilla's empty-hand arm (from ItemInHandRenderer), held fully raised. */
    private static void renderArm(Minecraft mc, AbstractClientPlayer player, PoseStack pose, MultiBufferSource buffer,
                                  int light, float swing, HumanoidArm side) {
        boolean right = side != HumanoidArm.LEFT;
        float f = right ? 1.0F : -1.0F;
        float sqrtSwing = Mth.sqrt(swing);
        float x = -0.3F * Mth.sin(sqrtSwing * (float) Math.PI);
        float y = 0.4F * Mth.sin(sqrtSwing * (float) (Math.PI * 2));
        float z = -0.4F * Mth.sin(swing * (float) Math.PI);
        pose.translate(f * (x + 0.64000005F), y - 0.6F, z - 0.71999997F);
        pose.mulPose(Axis.YP.rotationDegrees(f * 45.0F));
        float swingSq = Mth.sin(swing * swing * (float) Math.PI);
        float swingRoot = Mth.sin(sqrtSwing * (float) Math.PI);
        pose.mulPose(Axis.YP.rotationDegrees(f * swingRoot * 70.0F));
        pose.mulPose(Axis.ZP.rotationDegrees(f * swingSq * -20.0F));
        pose.translate(f * -1.0F, 3.6F, 3.5F);
        pose.mulPose(Axis.ZP.rotationDegrees(f * 120.0F));
        pose.mulPose(Axis.XP.rotationDegrees(200.0F));
        pose.mulPose(Axis.YP.rotationDegrees(f * -135.0F));
        pose.translate(f * 5.6F, 0.0F, 0.0F);

        PlayerRenderer renderer = (PlayerRenderer) mc.getEntityRenderDispatcher().getRenderer(player);
        if (right) {
            renderer.renderRightHand(pose, buffer, light, player);
        } else {
            renderer.renderLeftHand(pose, buffer, light, player);
        }
    }

    // ---- Third person ----

    // Rendering is single-threaded, so one stash is enough (Pre and Post always pair up per player).
    private static Player stashedFor;
    private static ItemStack stashedMain = ItemStack.EMPTY;
    private static ItemStack stashedOff = ItemStack.EMPTY;

    private static void onRenderPlayerPre(RenderPlayerEvent.Pre event) {
        Player player = event.getEntity();
        if (event.isCanceled() || !inBendingMode(player)) {
            return;
        }
        Inventory inv = player.getInventory();
        if (!Inventory.isHotbarSlot(inv.selected)) {
            return;
        }
        // Write the inventory lists directly so no equip sounds or events fire.
        stashedFor = player;
        stashedMain = inv.items.get(inv.selected);
        stashedOff = inv.offhand.get(0);
        inv.items.set(inv.selected, ItemStack.EMPTY);
        inv.offhand.set(0, ItemStack.EMPTY);

        // Arm poses were worked out before this event, from the real items: reset them.
        PlayerModel<AbstractClientPlayer> model = event.getRenderer().getModel();
        model.rightArmPose = HumanoidModel.ArmPose.EMPTY;
        model.leftArmPose = HumanoidModel.ArmPose.EMPTY;
    }

    private static void onRenderPlayerPost(RenderPlayerEvent.Post event) {
        Player player = event.getEntity();
        if (stashedFor != player) {
            return;
        }
        Inventory inv = player.getInventory();
        inv.items.set(inv.selected, stashedMain);
        inv.offhand.set(0, stashedOff);
        stashedFor = null;
        stashedMain = ItemStack.EMPTY;
        stashedOff = ItemStack.EMPTY;
    }
}
