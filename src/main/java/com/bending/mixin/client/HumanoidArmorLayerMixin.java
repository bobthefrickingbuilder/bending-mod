package com.bending.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.bending.client.LimbJoints;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.resources.model.ModelManager;

/** Armor gets knees and elbows too, so leggings and sleeves bend with the limb underneath. */
@Mixin(HumanoidArmorLayer.class)
public abstract class HumanoidArmorLayerMixin {
    @Inject(method = "<init>", at = @At("TAIL"))
    private void bending$splitArmor(RenderLayerParent<?, ?> renderer, HumanoidModel<?> inner, HumanoidModel<?> outer,
                                    ModelManager models, CallbackInfo ci) {
        // Vanilla's inflation: inner layer (leggings) 0.5, outer 1.0; legs are 0.1 slimmer.
        LimbJoints.splitArmor(inner, 0.4f, 0.5f);
        LimbJoints.splitArmor(outer, 0.9f, 1.0f);
    }
}
