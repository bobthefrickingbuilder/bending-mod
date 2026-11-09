package com.bending.network;

import com.bending.BendingMod;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Client -> server: the player pressed the bending mode key. */
public record ToggleBendingModePayload() implements CustomPacketPayload {
    public static final ToggleBendingModePayload INSTANCE = new ToggleBendingModePayload();
    public static final Type<ToggleBendingModePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(BendingMod.MODID, "toggle_bending_mode"));
    public static final StreamCodec<ByteBuf, ToggleBendingModePayload> STREAM_CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
