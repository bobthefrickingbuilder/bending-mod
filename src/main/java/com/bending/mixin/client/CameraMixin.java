package com.bending.mixin.client;

import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.bending.client.ShoulderCamera;

import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Shifts the third-person back view over the shoulder (see {@link ShoulderCamera}). */
@Mixin(Camera.class)
public abstract class CameraMixin {
    /** Gap kept between the camera and a wall it slides toward, so it never sees through it. */
    private static final double WALL_MARGIN = 0.25;

    @Shadow private Vec3 position;
    @Shadow @Final private Quaternionf rotation;

    @Shadow protected abstract void move(float zoom, float dy, float dx);

    @Inject(method = "setup", at = @At("TAIL"))
    private void bending$overTheShoulder(BlockGetter level, Entity entity, boolean detached, boolean thirdPersonReverse,
            float partialTick, CallbackInfo ci) {
        if (!detached || thirdPersonReverse) {
            return; // only the back view; first person and the front view stay vanilla
        }
        float scale = entity instanceof LivingEntity living ? living.getScale() : 1.0f;
        float side = ShoulderCamera.side() * scale;
        float up = ShoulderCamera.UP * scale;
        Vector3f offset = new Vector3f(side, up, 0).rotate(rotation);
        double length = offset.length();
        if (length < 1.0e-4) {
            return;
        }
        // Slide sideways only as far as the space allows.
        Vec3 dir = new Vec3(offset.x, offset.y, offset.z).scale(1 / length);
        Vec3 end = position.add(dir.scale(length + WALL_MARGIN));
        HitResult hit = level.clip(new ClipContext(position, end, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, entity));
        double allowed = length;
        if (hit.getType() != HitResult.Type.MISS) {
            allowed = Math.max(0, hit.getLocation().distanceTo(position) - WALL_MARGIN);
        }
        float fraction = (float) Math.min(1, allowed / length);
        move(0, up * fraction, side * fraction);
    }
}
