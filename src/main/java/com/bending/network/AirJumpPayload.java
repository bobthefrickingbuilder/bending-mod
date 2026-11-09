package com.bending.network;

import com.bending.BendingMod;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Client -> server: the player did a mid-air hop. */
public record AirJumpPayload() implements CustomPacketPayload {
    public static final AirJumpPayload INSTANCE = new AirJumpPayload();
    public static final Type<AirJumpPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(BendingMod.MODID, "air_jump"));
    public static final StreamCodec<ByteBuf, AirJumpPayload> STREAM_CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
