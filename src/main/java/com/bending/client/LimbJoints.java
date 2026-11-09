package com.bending.client;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.bending.mixin.client.ModelPartAccessor;
import com.zigythebird.playeranim.api.PlayerAnimationAccess;
import com.zigythebird.playeranimcore.animation.layered.IAnimation;
import com.zigythebird.playeranimcore.bones.PlayerAnimBone;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;

/**
 * Real knees and elbows. The vanilla player limb is one 12-pixel block, so the animation library
 * can only swing it from the hip or shoulder. Here each limb (and its pants/sleeve overlay) is
 * rebuilt as an upper half plus a hinged lower half child, textured from the same skin regions, and
 * the lower half is turned by the animation's "bend" value every frame. With no bend the limb looks
 * exactly like the vanilla one.
 */
public final class LimbJoints {
    private static final String LOWER = "bending_lower";
    private static final float TEX = 64f;
    /** Each half is 6 of the limb's 12 pixels. */
    private static final float HALF = 6f;
    private static final float OVERLAY_GROW = 0.25f;

    private LimbJoints() {}

    /** Called once when a player model is built. */
    public static void split(PlayerModel<?> model, boolean slim) {
        float armW = slim ? 3f : 4f;
        // Legs: 0..12 below the hip, knee at the front so the shin folds back.
        split(model.rightLeg, 0, 16, -2f, 4f, 0f, 0f, true);
        split(model.leftLeg, 16, 48, -2f, 4f, 0f, 0f, true);
        split(model.rightPants, 0, 32, -2f, 4f, 0f, OVERLAY_GROW, true);
        split(model.leftPants, 0, 48, -2f, 4f, 0f, OVERLAY_GROW, true);
        // Arms: -2..10 around the shoulder, elbow at the back so the forearm folds forward.
        split(model.rightArm, 40, 16, -3f, armW, -2f, 0f, false);
        split(model.leftArm, 32, 48, -1f, armW, -2f, 0f, false);
        split(model.rightSleeve, 40, 32, -3f, armW, -2f, OVERLAY_GROW, false);
        split(model.leftSleeve, 48, 48, -1f, armW, -2f, OVERLAY_GROW, false);
    }

    private static void split(ModelPart part, int u, int v, float x0, float w, float y0, float grow, boolean knee) {
        Set<Direction> noUp = EnumSet.complementOf(EnumSet.of(Direction.UP));
        Set<Direction> upOnly = EnumSet.of(Direction.UP);
        // Upper half: the top 6 rows of the skin's side strips, plus its own top face. Its cut face
        // at the joint borrows a strip of the back texture so a bent joint never shows the sole.
        List<ModelPart.Cube> upper = List.of(
                cube(u, v, x0, y0, -2f, w, grow, noUp),
                cube(u + 4, v + 8, x0, y0, -2f, w, grow, upOnly));
        // Lower half pivots on the joint's outer edge: front of the knee, back of the elbow.
        float hingeZ = knee ? -2f : 2f;
        float z0 = -2f - hingeZ;
        ModelPart lower = new ModelPart(List.of(
                cube(u, v + 6, x0, 0f, z0, w, grow, noUp),
                // The end face (sole / hand) keeps the original bottom texture.
                cube(u, v, x0, 0f, z0, w, grow, upOnly)), new HashMap<>());
        PartPose pose = PartPose.offset(0f, y0 + HALF, hingeZ);
        lower.loadPose(pose);
        lower.setInitialPose(pose);

        ModelPartAccessor access = (ModelPartAccessor) (Object) part;
        Map<String, ModelPart> children = new HashMap<>(access.bending$getChildren());
        children.put(LOWER, lower);
        access.bending$setChildren(children);
        access.bending$setCubes(upper);
    }

    private static ModelPart.Cube cube(int u, int v, float x, float y, float z, float w, float grow, Set<Direction> faces) {
        return new ModelPart.Cube(u, v, x, y, z, w, HALF, 4f, grow, grow, grow, false, TEX, TEX, faces);
    }

    /** Called after every pose of a player model: turns the knees and elbows to the animation's bend. */
    public static void pose(PlayerModel<?> model, LivingEntity entity, float ageInTicks) {
        float rightKnee = 0, leftKnee = 0, rightElbow = 0, leftElbow = 0;
        // ageInTicks is exactly 0 only for the first-person hand, which stays straight.
        if (ageInTicks != 0f && entity instanceof AbstractClientPlayer player) {
            IAnimation layer = PlayerAnimationAccess.getPlayerAnimationLayer(player, MoveAnimations.LAYER);
            if (layer != null && layer.isActive()) {
                rightKnee = bend(layer, "right_leg");
                leftKnee = bend(layer, "left_leg");
                rightElbow = bend(layer, "right_arm");
                leftElbow = bend(layer, "left_arm");
            }
        }
        // Joints only fold one way: the shin back, the forearm forward.
        set(model.rightLeg, rightKnee);
        set(model.rightPants, rightKnee);
        set(model.leftLeg, leftKnee);
        set(model.leftPants, leftKnee);
        set(model.rightArm, -rightElbow);
        set(model.rightSleeve, -rightElbow);
        set(model.leftArm, -leftElbow);
        set(model.leftSleeve, -leftElbow);
    }

    private static float bend(IAnimation layer, String bone) {
        float b = layer.get3DTransform(new PlayerAnimBone(bone)).getBend();
        // Radians; clamp so a bad value can't fold a limb through itself.
        return Math.min(Math.abs(b), 2.6f);
    }

    private static void set(ModelPart part, float angle) {
        if (part.hasChild(LOWER)) {
            part.getChild(LOWER).xRot = angle;
        }
    }
}
