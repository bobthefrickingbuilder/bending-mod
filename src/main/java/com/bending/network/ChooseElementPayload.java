package com.bending.network;

import com.bending.BendingMod;
import com.bending.data.Element;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Client -> server: the player picked an element on the selection screen. */
public record ChooseElementPayload(Element element) implements CustomPacketPayload {
    public static final Type<ChooseElementPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(BendingMod.MODID, "choose_element"));

    public static final StreamCodec<ByteBuf, ChooseElementPayload> STREAM_CODEC = StreamCodec.composite(
            Element.STREAM_CODEC, ChooseElementPayload::element,
            ChooseElementPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
