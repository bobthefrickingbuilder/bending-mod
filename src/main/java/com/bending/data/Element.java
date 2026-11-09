package com.bending.data;

import javax.annotation.Nullable;

import com.mojang.serialization.Codec;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.Mth;
import net.minecraft.util.StringRepresentable;

/** The four bending arts. */
public enum Element implements StringRepresentable {
    FIRE("fire", 0xFF6A1A),
    WATER("water", 0x3A8DFF),
    EARTH("earth", 0x5DBB3A),
    AIR("air", 0xE8E3C8);

    public static final Codec<Element> CODEC = StringRepresentable.fromEnum(Element::values);
    public static final StreamCodec<ByteBuf, Element> STREAM_CODEC = ByteBufCodecs.idMapper(
            i -> values()[Mth.clamp(i, 0, values().length - 1)], Element::ordinal);

    private final String name;
    private final int color;

    Element(String name, int color) {
        this.name = name;
        this.color = color;
    }

    @Override
    public String getSerializedName() {
        return name;
    }

    /** RGB color used for HUD and UI. */
    public int color() {
        return color;
    }

    public Component displayName() {
        return Component.translatable("element.bending." + name);
    }

    @Nullable
    public static Element byName(String name) {
        for (Element e : values()) {
            if (e.name.equalsIgnoreCase(name)) {
                return e;
            }
        }
        return null;
    }
}
