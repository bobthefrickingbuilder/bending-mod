package com.bending.network;

import com.bending.BendingMod;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Client -> server: an ability key was pressed or released. */
public record CastAbilityPayload(int slot, boolean pressed) implements CustomPacketPayload {
    public static final Type<CastAbilityPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(BendingMod.MODID, "cast_ability"));

    public static final StreamCodec<ByteBuf, CastAbilityPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, CastAbilityPayload::slot,
            ByteBufCodecs.BOOL, CastAbilityPayload::pressed,
            CastAbilityPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
