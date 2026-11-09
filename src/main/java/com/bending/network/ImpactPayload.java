package com.bending.network;

import java.util.function.Consumer;

import com.bending.BendingMod;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Server -> client: a big impact near you. Shakes the camera and flashes the screen (scaled already by distance). */
public record ImpactPayload(float shake, float flash, int color) implements CustomPacketPayload {
    public static final Type<ImpactPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(BendingMod.MODID, "impact"));
    public static final StreamCodec<ByteBuf, ImpactPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.FLOAT, ImpactPayload::shake,
            ByteBufCodecs.FLOAT, ImpactPayload::flash,
            ByteBufCodecs.INT, ImpactPayload::color,
            ImpactPayload::new);

    /** Set by the client entry point. */
    public static Consumer<ImpactPayload> clientHandler = p -> {};

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
