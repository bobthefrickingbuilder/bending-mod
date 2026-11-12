package com.bending.client.render;

import com.bending.BendingMod;
import com.bending.entity.FireWarlordEntity;
import com.bending.entity.FirebenderEntity;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;

/** Firebender mobs and the Warlord: player-shaped, in a fighting stance while they have a target. */
public class FirebenderRenderer extends HumanoidMobRenderer<FirebenderEntity, FirebenderRenderer.Model> {
    private static final ResourceLocation SOLDIER = ResourceLocation.fromNamespaceAndPath(BendingMod.MODID, "textures/entity/firebender.png");
    private static final ResourceLocation WARLORD = ResourceLocation.fromNamespaceAndPath(BendingMod.MODID, "textures/entity/fire_warlord.png");
    private final float scale;

    public FirebenderRenderer(EntityRendererProvider.Context context, float scale) {
        super(context, new Model(context.bakeLayer(ModelLayers.PLAYER)), 0.5f * scale);
        this.scale = scale;
    }

    public static FirebenderRenderer soldier(EntityRendererProvider.Context context) {
        return new FirebenderRenderer(context, 1f);
    }

    public static FirebenderRenderer warlord(EntityRendererProvider.Context context) {
        return new FirebenderRenderer(context, 1.3f);
    }

    @Override
    protected void scale(FirebenderEntity entity, PoseStack pose, float partialTick) {
        pose.scale(scale, scale, scale);
    }

    @Override
    public ResourceLocation getTextureLocation(FirebenderEntity entity) {
        return entity instanceof FireWarlordEntity ? WARLORD : SOLDIER;
    }

    public static class Model extends PlayerModel<FirebenderEntity> {
        public Model(ModelPart root) {
            super(root, false);
        }

        @Override
        public void setupAnim(FirebenderEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
            super.setupAnim(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
            if (!entity.isAggressive() || attackTime > 0f) {
                return;
            }
            // Bending stance: lead fist forward, rear hand guarding the chest.
            float bob = (float) Math.sin(ageInTicks * 0.15f) * 0.06f;
            rightArm.xRot = -1.25f + bob;
            rightArm.yRot = -0.15f;
            rightArm.zRot = 0.05f;
            leftArm.xRot = -0.85f - bob;
            leftArm.yRot = 0.55f;
            leftArm.zRot = -0.1f;
            rightSleeve.copyFrom(rightArm);
            leftSleeve.copyFrom(leftArm);
        }
    }
}
