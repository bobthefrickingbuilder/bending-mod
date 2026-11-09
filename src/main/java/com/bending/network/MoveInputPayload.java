package com.bending.network;

import com.bending.BendingMod;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client -> server: which movement keys are held, so abilities like Fire Jet Dash can go the way
 * you're pressing. {@code forward}: 1 = W, -1 = S. {@code left}: 1 = A, -1 = D. 0 = neither.
 * The server doesn't otherwise know your keys (movement is client-side).
 */
public record MoveInputPayload(byte forward, byte left) implements CustomPacketPayload {
    public static final Type<MoveInputPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(BendingMod.MODID, "move_input"));
    public static final StreamCodec<ByteBuf, MoveInputPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BYTE, MoveInputPayload::forward,
            ByteBufCodecs.BYTE, MoveInputPayload::left,
            MoveInputPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
