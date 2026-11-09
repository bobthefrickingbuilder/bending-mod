package com.bending.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.bending.client.LimbJoints;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.LivingEntity;

/** Gives the player model real knees and elbows (see {@link LimbJoints}). */
@Mixin(PlayerModel.class)
public abstract class PlayerModelMixin {
    @Inject(method = "<init>", at = @At("TAIL"))
    private void bending$splitLimbs(ModelPart root, boolean slim, CallbackInfo ci) {
        LimbJoints.split((PlayerModel<?>) (Object) this, slim);
    }

    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
    private void bending$poseJoints(LivingEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks,
                                    float netHeadYaw, float headPitch, CallbackInfo ci) {
        LimbJoints.pose((PlayerModel<?>) (Object) this, entity, ageInTicks);
    }
}
