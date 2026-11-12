package com.bending.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.bending.client.LimbJoints;

import net.minecraft.client.model.HumanoidModel;

/** When a model hands its pose to an armor model, the knee and elbow angles go along. */
@Mixin(HumanoidModel.class)
public abstract class HumanoidModelMixin {
    @Inject(method = "copyPropertiesTo", at = @At("TAIL"))
    private void bending$copyJoints(HumanoidModel<?> target, CallbackInfo ci) {
        LimbJoints.copyJoints((HumanoidModel<?>) (Object) this, target);
    }
}
