package com.bending.network;

import com.bending.BendingMod;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client -> server: Ctrl state in bending mode. {@code ready} = held on the ground and not dashing
 * (the ready-to-dash crouch); {@code ctrl} = held at all (in the air it turns Fire Blast into Air Kick).
 */
public record DashReadyPayload(boolean ready, boolean ctrl) implements CustomPacketPayload {
    public static final Type<DashReadyPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(BendingMod.MODID, "dash_ready"));
    public static final StreamCodec<ByteBuf, DashReadyPayload> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.BOOL, DashReadyPayload::ready, ByteBufCodecs.BOOL, DashReadyPayload::ctrl,
                    DashReadyPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
