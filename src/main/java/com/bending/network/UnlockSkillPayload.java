package com.bending.network;

import com.bending.BendingMod;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Client -> server (skill tree screen): spend skill points to learn a node. */
public record UnlockSkillPayload(String nodeId) implements CustomPacketPayload {
    public static final Type<UnlockSkillPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(BendingMod.MODID, "unlock_skill"));
    public static final StreamCodec<ByteBuf, UnlockSkillPayload> STREAM_CODEC =
            ByteBufCodecs.stringUtf8(128).map(UnlockSkillPayload::new, UnlockSkillPayload::nodeId);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
