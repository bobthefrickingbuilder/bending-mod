package com.bending.client.render;

import com.bending.entity.FireBlastEntity;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** Fire Blast: a glowing ball of flame with tongues streaming back along its path. */
public class FireBlastRenderer extends EntityRenderer<FireBlastEntity> {
    /** About 70% bigger than the old particle head. */
    private static final float SCALE = 1.4f;
    /** Closer than this to the camera the ball isn't drawn; it reaches full size at FADE_END. */
    public static final double FADE_START = 1.8;
    public static final double FADE_END = 2.8;

    public FireBlastRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(FireBlastEntity entity, float yaw, float partialTicks, PoseStack pose, MultiBufferSource buffers, int light) {
        // Don't flash in the viewer's face: hidden right at the camera, growing in from ~2 blocks out.
        Vec3 at = entity.getPosition(partialTicks).add(0, entity.getBbHeight() / 2, 0);
        double dist = at.distanceTo(entityRenderDispatcher.camera.getPosition());
        float fade = (float) Mth.clamp((dist - FADE_START) / (FADE_END - FADE_START), 0, 1);
        if (fade > 0f) {
            pose.pushPose();
            pose.translate(0, entity.getBbHeight() / 2, 0);
            Vec3 v = entity.getDeltaMovement();
            Vec3 trail = v.lengthSqr() > 1e-4 ? v.normalize().scale(-1) : null;
            FlameBall.render(pose, buffers, SCALE * entity.getBallScale() * fade, entity.tickCount + partialTicks, trail, entity.isBlue());
            pose.popPose();
        }
        super.render(entity, yaw, partialTicks, pose, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(FireBlastEntity entity) {
        return FlameBall.TEXTURE;
    }
}
