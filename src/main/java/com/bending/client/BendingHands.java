package com.bending.client;

import com.bending.data.ModAttachments;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.world.entity.player.PlayerModelPart;
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
 * During a move, {@link FirstPersonArms} poses the two first-person arms.
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
        float now = player.tickCount + event.getPartialTick();
        float swing = event.getSwingProgress();
        if (FirstPersonArms.active(now)) {
            // A move is posing the arms: apply its pose around the shoulder, no vanilla punch swing.
            float[][] arms = {new float[6], new float[6]};
            FirstPersonArms.sample(now, arms);
            applyMovePose(pose, arms[side == HumanoidArm.RIGHT ? 0 : 1], side == HumanoidArm.RIGHT ? 1f : -1f);
            swing = 0f;
        }
        renderArm(mc, player, pose, event.getMultiBufferSource(), event.getPackedLight(), swing, side);
        pose.popPose();
        // Kicks: the leg comes up into view (drawn once per frame, with the main hand).
        float[] leg = new float[4];
        if (event.getHand() == InteractionHand.MAIN_HAND && FirstPersonArms.sampleLeg(now, leg)) {
            pose.pushPose();
            renderLeg(mc, player, pose, event.getMultiBufferSource(), event.getPackedLight(), leg, FirstPersonArms.legRight);
            pose.popPose();
        }
    }

    /** Hip of the kicking leg in view space (right leg; x mirrored for the left). Higher than real
     *  hips so the kick reads on screen. */
    private static final float HIP_X = 0.24f, HIP_Y = -0.62f, HIP_Z = 0.05f, LEG_SCALE = 1.15f;

    private static void renderLeg(Minecraft mc, AbstractClientPlayer player, PoseStack pose, MultiBufferSource buffer,
                                  int light, float[] a, boolean right) {
        float f = right ? 1f : -1f;
        PlayerRenderer renderer = (PlayerRenderer) mc.getEntityRenderDispatcher().getRenderer(player);
        PlayerModel<AbstractClientPlayer> model = renderer.getModel();
        ModelPart leg = right ? model.rightLeg : model.leftLeg;
        ModelPart pants = right ? model.rightPants : model.leftPants;
        // Slide up from below the screen as it comes into view.
        pose.translate(f * HIP_X, HIP_Y - (1 - a[FirstPersonArms.SHOW]) * 0.9f, HIP_Z);
        pose.mulPose(Axis.YP.rotationDegrees(f * a[FirstPersonArms.LEG_YAW]));
        pose.mulPose(Axis.XP.rotationDegrees(a[FirstPersonArms.LEG_PITCH]));
        // Model space hangs the leg down +y with the front at -z; flip it into view space.
        pose.scale(-LEG_SCALE, -LEG_SCALE, LEG_SCALE);
        VertexConsumer vc = buffer.getBuffer(RenderType.entityTranslucent(player.getSkin().texture()));
        float knee = a[FirstPersonArms.KNEE] * Mth.DEG_TO_RAD;
        for (ModelPart part : new ModelPart[] {leg, pants}) {
            part.x = 0f;
            part.y = 0f;
            part.z = 0f;
            part.xRot = 0f;
            part.yRot = 0f;
            part.zRot = 0f;
            if (part.hasChild("bending_lower")) {
                part.getChild("bending_lower").xRot = knee;
            }
        }
        leg.visible = true;
        pants.visible = player.isModelPartShown(right ? PlayerModelPart.RIGHT_PANTS_LEG : PlayerModelPart.LEFT_PANTS_LEG);
        leg.render(pose, vc, light, OverlayTexture.NO_OVERLAY);
        if (pants.visible) {
            pants.render(pose, vc, light, OverlayTexture.NO_OVERLAY);
        }
    }

    /** Shoulder the move poses rotate around, in view space (right arm; x is mirrored for the left). */
    private static final float PIVOT_X = 0.5f, PIVOT_Y = -1.2f, PIVOT_Z = -0.2f;

    /** A {@link FirstPersonArms} pose: x/y/z offset, then pitch/yaw/roll around the shoulder. */
    private static void applyMovePose(PoseStack pose, float[] a, float f) {
        pose.translate(f * a[FirstPersonArms.X], a[FirstPersonArms.Y], -a[FirstPersonArms.Z]);
        pose.translate(f * PIVOT_X, PIVOT_Y, PIVOT_Z);
        pose.mulPose(Axis.XP.rotationDegrees(a[FirstPersonArms.PITCH]));
        pose.mulPose(Axis.YP.rotationDegrees(f * a[FirstPersonArms.YAW]));
        pose.mulPose(Axis.ZP.rotationDegrees(f * a[FirstPersonArms.ROLL]));
        pose.translate(-f * PIVOT_X, -PIVOT_Y, -PIVOT_Z);
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
