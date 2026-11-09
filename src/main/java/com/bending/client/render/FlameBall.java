package com.bending.client.render;

import javax.annotation.Nullable;

import org.joml.Vector3f;

import com.bending.BendingMod;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/**
 * Draws a glowing, cel-shaded ball of fire: a white-hot core, a ring of flickering flame tongues
 * and (when it's moving) longer tongues streaming behind it. Everything faces the camera.
 * Used by the Fire Blast projectile and the Combustion Beam charge orb.
 */
public final class FlameBall {
    /** One texture: [core][flame frame 0..3], each 16x16. */
    public static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(BendingMod.MODID, "textures/entity/flame_ball.png");
    private static final int CELLS = 5;
    private static final int RING_TONGUES = 7;

    private FlameBall() {}

    /**
     * @param scale  1 = a ball about 1.6 blocks across
     * @param time   ticks, for flicker and spin
     * @param trail  direction the flames should stream (opposite of travel), or null for none
     */
    public static void render(PoseStack pose, MultiBufferSource buffers, float scale, float time, @Nullable Vec3 trail) {
        render(pose, buffers, scale, time, trail, false);
    }

    /** As above; {@code blue} = Blue Fire colours. */
    public static void render(PoseStack pose, MultiBufferSource buffers, float scale, float time, @Nullable Vec3 trail, boolean blue) {
        int[] tongue = blue ? new int[] {60, 125, 255} : new int[] {255, 110, 25};
        int[] ring = blue ? new int[] {75, 155, 255} : new int[] {255, 140, 30};
        int[] swirl = blue ? new int[] {125, 205, 255} : new int[] {255, 200, 70};
        int[] heart = blue ? new int[] {228, 246, 255} : new int[] {255, 250, 215};
        VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucentEmissive(TEXTURE));
        Minecraft mc = Minecraft.getInstance();
        pose.pushPose();
        pose.mulPose(mc.getEntityRenderDispatcher().cameraOrientation());
        float flicker = 1f + 0.06f * (float) Math.sin(time * 1.7f);

        // Tongues streaming behind (drawn first, so the core sits on top).
        if (trail != null) {
            Camera camera = mc.gameRenderer.getMainCamera();
            Vector3f left = camera.getLeftVector();
            Vector3f up = camera.getUpVector();
            double sx = -(trail.x * left.x() + trail.y * left.y() + trail.z * left.z());
            double sy = trail.x * up.x() + trail.y * up.y() + trail.z * up.z();
            double screenLen = Math.sqrt(sx * sx + sy * sy);
            if (screenLen > 0.2) {
                float angle = (float) Math.atan2(-sx, sy);
                float stretch = (float) Math.min(1.0, screenLen);
                for (int i = 0; i < 3; i++) {
                    pose.pushPose();
                    pose.mulPose(Axis.ZP.rotation(angle + (i - 1) * 0.35f + 0.08f * (float) Math.sin(time * 2.3f + i)));
                    pose.translate(0, (0.55f + 0.25f * stretch) * scale, 0);
                    quad(vc, pose, 0.32f * scale, (0.45f + 0.35f * stretch) * scale, frame(time, i + 1),
                            tongue[0], tongue[1], tongue[2], 230);
                    pose.popPose();
                }
            }
        }

        // A ring of flame tongues licking outward.
        for (int i = 0; i < RING_TONGUES; i++) {
            pose.pushPose();
            pose.mulPose(Axis.ZP.rotation((float) (i * Math.PI * 2 / RING_TONGUES) + time * 0.12f));
            pose.translate(0, 0.42f * scale, 0.001f * i);
            quad(vc, pose, 0.4f * scale * flicker, 0.4f * scale * flicker, frame(time, i), ring[0], ring[1], ring[2], 235);
            pose.popPose();
        }

        // Glowing swirl core: an orange-yellow spiral with a near-white heart, both spinning.
        pose.translate(0, 0, 0.02f);
        pose.pushPose();
        pose.mulPose(Axis.ZP.rotation(-time * 0.15f));
        quad(vc, pose, 0.6f * scale * flicker, 0.6f * scale * flicker, 0, swirl[0], swirl[1], swirl[2], 230);
        pose.popPose();
        pose.translate(0, 0, 0.01f);
        pose.mulPose(Axis.ZP.rotation(-time * 0.25f));
        quad(vc, pose, 0.4f * scale, 0.4f * scale, 0, heart[0], heart[1], heart[2], 255);
        pose.popPose();
    }

    private static int frame(float time, int offset) {
        return 1 + Math.floorMod((int) (time / 2) + offset, 4);
    }

    /** A camera-facing quad centred on the origin, drawn from both sides. */
    private static void quad(VertexConsumer vc, PoseStack pose, float halfW, float halfH, int cell,
                             int r, int g, int b, int a) {
        PoseStack.Pose p = pose.last();
        float u0 = cell / (float) CELLS;
        float u1 = (cell + 1) / (float) CELLS;
        float[][] corners = {{-halfW, -halfH, u0, 1}, {halfW, -halfH, u1, 1}, {halfW, halfH, u1, 0}, {-halfW, halfH, u0, 0}};
        for (int side = 0; side < 2; side++) {
            for (int i = 0; i < 4; i++) {
                float[] c = corners[side == 0 ? i : 3 - i];
                vc.addVertex(p, c[0], c[1], 0f)
                        .setColor(r, g, b, a)
                        .setUv(c[2], c[3])
                        .setOverlay(OverlayTexture.NO_OVERLAY)
                        .setLight(LightTexture.FULL_BRIGHT)
                        .setNormal(p, 0f, 1f, 0f);
            }
        }
    }
}
