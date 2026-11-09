package com.bending.client.render;

import com.bending.entity.LightningArcEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/**
 * Draws a {@link LightningArcEntity} the way vanilla draws lightning: several layered, glowing,
 * pale blue-white strips (the same lightning render type and colour), plus a bright white core.
 * The strips always face the camera, so the bolt reads from any angle.
 */
public class LightningArcRenderer extends EntityRenderer<LightningArcEntity> {
    /** {half width, red, green, blue, alpha}: white-hot core, then vanilla's 0.45/0.45/0.5 glow layers. */
    private static final float[][] LAYERS = {
            {0.05f, 1.0f, 1.0f, 1.0f, 0.9f},
            {0.13f, 0.45f, 0.45f, 0.5f, 0.3f},
            {0.25f, 0.45f, 0.45f, 0.5f, 0.3f},
            {0.4f, 0.45f, 0.45f, 0.5f, 0.3f},
    };

    public LightningArcRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(LightningArcEntity arc, float yaw, float partialTicks, PoseStack pose, MultiBufferSource buffers, int light) {
        float age = arc.tickCount + partialTicks;
        float life = arc.life();
        if (age >= life) {
            return;
        }
        // Full brightness, then a flickering fade over the last ticks.
        float fade = Math.min(1f, (life - age) / 2f) * (arc.tickCount % 2 == 0 ? 1f : 0.8f);
        float thick = arc.thickness();
        Vec3 camera = entityRenderDispatcher.camera.getPosition().subtract(arc.getPosition(partialTicks));
        VertexConsumer vc = buffers.getBuffer(RenderType.lightning());
        PoseStack.Pose p = pose.last();

        // Thin where it leaves your hands, full vanilla thickness further out.
        double taper = Math.min(3.0, arc.end().length() * 0.3);
        boolean mainLine = true;
        for (Vec3[] line : arc.shape(arc.tickCount / 2)) {
            float scale = thick * (mainLine ? 1f : 0.55f);
            for (int s = 1; s < line.length; s++) {
                for (float[] layer : LAYERS) {
                    strip(vc, p, line[s - 1], line[s], camera, layer[0] * scale, taper,
                            layer[1], layer[2], layer[3], layer[4] * fade);
                }
            }
            mainLine = false;
        }
    }

    /** A camera-facing strip from a to b, drawn from both sides. */
    private static void strip(VertexConsumer vc, PoseStack.Pose p, Vec3 a, Vec3 b, Vec3 camera,
                              float halfWidth, double taper, float r, float g, float bl, float alpha) {
        Vec3 along = b.subtract(a);
        Vec3 side = along.cross(camera.subtract(a));
        if (side.lengthSqr() < 1e-8) {
            return;
        }
        side = side.normalize().scale(halfWidth);
        Vec3 sideA = side.scale(taper(a, taper));
        Vec3 sideB = side.scale(taper(b, taper));
        Vec3[] corners = {a.subtract(sideA), a.add(sideA), b.add(sideB), b.subtract(sideB)};
        for (int pass = 0; pass < 2; pass++) {
            for (int i = 0; i < 4; i++) {
                Vec3 c = corners[pass == 0 ? i : 3 - i];
                vc.addVertex(p, (float) c.x, (float) c.y, (float) c.z).setColor(r, g, bl, alpha);
            }
        }
    }

    private static double taper(Vec3 point, double taper) {
        return taper <= 0 ? 1 : Math.max(0.12, Math.min(1, point.length() / taper));
    }

    @Override
    public boolean shouldRender(LightningArcEntity arc, Frustum frustum, double camX, double camY, double camZ) {
        return true; // long and thin: its bounding box doesn't cover it
    }

    @Override
    public ResourceLocation getTextureLocation(LightningArcEntity arc) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
