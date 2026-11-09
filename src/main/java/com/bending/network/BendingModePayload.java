package com.bending.network;

import com.bending.BendingMod;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server -> clients: a player entered or left bending mode. Sent to that player and to everyone
 * who can see them, so other players' hands render empty too.
 */
public record BendingModePayload(int entityId, boolean on) implements CustomPacketPayload {
    public static final Type<BendingModePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(BendingMod.MODID, "bending_mode"));
    public static final StreamCodec<ByteBuf, BendingModePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, BendingModePayload::entityId,
            ByteBufCodecs.BOOL, BendingModePayload::on,
            BendingModePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
