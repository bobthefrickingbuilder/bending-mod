package com.bending.mixin.client;

import java.util.List;
import java.util.Map;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.client.model.geom.ModelPart;

/** Lets {@link com.bending.client.LimbJoints} rebuild a limb as two hinged halves. */
@Mixin(ModelPart.class)
public interface ModelPartAccessor {
    @Mutable
    @Accessor("cubes")
    void bending$setCubes(List<ModelPart.Cube> cubes);

    @Mutable
    @Accessor("children")
    void bending$setChildren(Map<String, ModelPart> children);

    @Accessor("children")
    Map<String, ModelPart> bending$getChildren();

    @Accessor("cubes")
    List<ModelPart.Cube> bending$getCubes();
}
