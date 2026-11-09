package com.bending.client.render;

import com.bending.entity.ChargeOrbEntity;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/** The Combustion Beam charge: a ball of fire between the bender's hands that grows as it charges. */
public class ChargeOrbRenderer extends EntityRenderer<ChargeOrbEntity> {
    private static final float MIN_SCALE = 0.12f;
    private static final float MAX_SCALE = 0.55f;

    public ChargeOrbRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(ChargeOrbEntity orb, float yaw, float partialTicks, PoseStack pose, MultiBufferSource buffers, int light) {
        pose.pushPose();
        // Stick to the owner's hands using their smooth, client-side view (the server position lags a tick).
        Entity owner = orb.getOwner();
        if (owner != null) {
            Vec3 focus = ChargeOrbEntity.focus(owner.getEyePosition(partialTicks), owner.getViewVector(partialTicks));
            Vec3 at = new Vec3(Mth.lerp(partialTicks, orb.xo, orb.getX()), Mth.lerp(partialTicks, orb.yo, orb.getY()),
                    Mth.lerp(partialTicks, orb.zo, orb.getZ()));
            Vec3 offset = focus.subtract(at);
            pose.translate(offset.x, offset.y, offset.z);
        }
        float charge = orb.getCharge();
        float time = orb.tickCount + partialTicks;
        // Grows with the charge; at full charge it throbs, ready to fire.
        float scale = Mth.lerp(charge, MIN_SCALE, MAX_SCALE);
        if (charge >= 1f) {
            scale *= 1f + 0.08f * Mth.sin(time * 1.2f);
        }
        FlameBall.render(pose, buffers, scale, time * (1f + charge), null);
        pose.popPose();
        super.render(orb, yaw, partialTicks, pose, buffers, light);
    }

    @Override
    public boolean shouldRender(ChargeOrbEntity orb, net.minecraft.client.renderer.culling.Frustum frustum,
                                double camX, double camY, double camZ) {
        return true; // tiny and short-lived; always draw it
    }

    @Override
    public ResourceLocation getTextureLocation(ChargeOrbEntity orb) {
        return FlameBall.TEXTURE;
    }
}
