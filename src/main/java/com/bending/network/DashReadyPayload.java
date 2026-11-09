package com.bending.network;

import com.bending.BendingMod;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Client -> server: Ctrl went down or up in bending mode (the ready-to-dash crouch). */
public record DashReadyPayload(boolean ready) implements CustomPacketPayload {
    public static final Type<DashReadyPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(BendingMod.MODID, "dash_ready"));
    public static final StreamCodec<ByteBuf, DashReadyPayload> STREAM_CODEC =
            ByteBufCodecs.BOOL.map(DashReadyPayload::new, DashReadyPayload::ready);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
