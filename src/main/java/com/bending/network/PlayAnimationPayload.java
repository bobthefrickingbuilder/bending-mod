package com.bending.network;

import java.util.function.Consumer;

import com.bending.BendingMod;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server -> client: play a bending move animation on a player (an empty name stops it).
 * Animations live in {@code assets/bending/player_animations/}.
 */
public record PlayAnimationPayload(int entityId, String name) implements CustomPacketPayload {
    public static final Type<PlayAnimationPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(BendingMod.MODID, "play_animation"));
    public static final StreamCodec<ByteBuf, PlayAnimationPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, PlayAnimationPayload::entityId,
            ByteBufCodecs.STRING_UTF8, PlayAnimationPayload::name,
            PlayAnimationPayload::new);

    /** Set by the client entry point (the animation library is client-only). */
    public static Consumer<PlayAnimationPayload> clientHandler = p -> {};

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
