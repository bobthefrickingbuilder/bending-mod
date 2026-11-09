package com.bending.network;

import com.bending.BendingMod;
import com.bending.data.BendingData;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Server -> client: the player's full bending data. */
public record SyncBendingDataPayload(BendingData data) implements CustomPacketPayload {
    public static final Type<SyncBendingDataPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(BendingMod.MODID, "sync_bending_data"));

    public static final StreamCodec<ByteBuf, SyncBendingDataPayload> STREAM_CODEC =
            ByteBufCodecs.fromCodec(BendingData.CODEC).map(SyncBendingDataPayload::new, SyncBendingDataPayload::data);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
