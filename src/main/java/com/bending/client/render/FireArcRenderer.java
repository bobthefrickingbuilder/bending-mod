package com.bending.client.render;

import org.joml.Vector3f;

import com.bending.BendingMod;
import com.bending.entity.FireArcEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Fire Arc: a solid, glowing crescent of flame (the particles stream off it). Two layered ribbons
 * follow the same curve the damage uses: a broad orange (or blue) blaze and a thin white-hot core.
 */
public class FireArcRenderer extends EntityRenderer<FireArcEntity> {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(BendingMod.MODID, "textures/entity/fire_arc.png");
    private static final int SEGMENTS = 24;
    private static final int FRAMES = 4;

    public FireArcRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(FireArcEntity entity, float yaw, float partialTicks, PoseStack pose, MultiBufferSource buffers, int light) {
        Vec3 dir = entity.syncedDir();
        Vec3 side = entity.syncedSide();
        if (dir.lengthSqr() < 1e-4 || side.lengthSqr() < 1e-4) {
            return;
        }
        float time = entity.tickCount + partialTicks;
        // Swell in over the first ticks, burn out near the end of its reach.
        float grow = Mth.clamp(time / 3f, 0f, 1f) * entity.fade(partialTicks);
        if (grow <= 0.01f) {
            return;
        }
        Vec3 up = side.cross(dir).normalize();
        if (up.y < 0) {
            up = up.scale(-1);
        }
        boolean blue = entity.isBlue();
        VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucentEmissive(TEXTURE));
        int frame = ((int) (time / 1.5f)) % FRAMES;
        float scroll = time * 0.08f;
        // Outer blaze, then the hot core on top.
        ribbon(vc, pose, dir, side, up, 0.85f * grow, frame, scroll, blue ? 70 : 255, blue ? 140 : 120, blue ? 255 : 30, 225);
        ribbon(vc, pose, dir, side, up, 0.32f * grow, (frame + 2) % FRAMES, -scroll * 1.3f,
                blue ? 215 : 255, blue ? 240 : 238, 255, 255);
        super.render(entity, yaw, partialTicks, pose, buffers, light);
    }

    /** One band along the crescent, thickest in the middle, tapering to the tips; both faces drawn. */
    private static void ribbon(VertexConsumer vc, PoseStack pose, Vec3 dir, Vec3 side, Vec3 up, float thickness,
                               int frame, float scroll, int r, int g, int b, int a) {
        PoseStack.Pose p = pose.last();
        float v0 = frame / (float) FRAMES, v1 = (frame + 1) / (float) FRAMES;
        for (int i = 0; i < SEGMENTS; i++) {
            float ua = i / (float) SEGMENTS * 2 - 1, ub = (i + 1) / (float) SEGMENTS * 2 - 1;
            Vec3 pa = FireArcEntity.crescentPoint(dir, side, ua), pb = FireArcEntity.crescentPoint(dir, side, ub);
            float ta = thickness * taper(ua), tb = thickness * taper(ub);
            // Texture clamps at its edges, so map the strip once across the arc (the frames animate it).
            float sa = Mth.clamp((ua + 1) / 2 + scroll * 0f, 0.002f, 0.998f), sb = Mth.clamp((ub + 1) / 2, 0.002f, 0.998f);
            Vector3f[] quad = {
                    vec(pa.add(up.scale(-ta))), vec(pb.add(up.scale(-tb))),
                    vec(pb.add(up.scale(tb))), vec(pa.add(up.scale(ta)))};
            float[][] uv = {{sa, v1}, {sb, v1}, {sb, v0}, {sa, v0}};
            for (int face = 0; face < 2; face++) {
                for (int k = 0; k < 4; k++) {
                    int idx = face == 0 ? k : 3 - k;
                    Vector3f q = quad[idx];
                    vc.addVertex(p, q.x, q.y, q.z).setColor(r, g, b, a).setUv(uv[idx][0], uv[idx][1])
                            .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT)
                            .setNormal(p, 0f, 1f, 0f);
                }
            }
        }
    }

    private static float taper(float u) {
        return 0.25f + 0.75f * (1 - u * u);
    }

    private static Vector3f vec(Vec3 v) {
        return new Vector3f((float) v.x, (float) v.y, (float) v.z);
    }

    @Override
    public ResourceLocation getTextureLocation(FireArcEntity entity) {
        return TEXTURE;
    }
}
