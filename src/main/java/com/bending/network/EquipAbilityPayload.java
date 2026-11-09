package com.bending.network;

import com.bending.BendingMod;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Client -> server (loadout screen): put an ability in a slot, or clear the slot if the id is empty. */
public record EquipAbilityPayload(int slot, String abilityId) implements CustomPacketPayload {
    public static final Type<EquipAbilityPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(BendingMod.MODID, "equip_ability"));
    public static final StreamCodec<ByteBuf, EquipAbilityPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, EquipAbilityPayload::slot,
            ByteBufCodecs.stringUtf8(128), EquipAbilityPayload::abilityId,
            EquipAbilityPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
