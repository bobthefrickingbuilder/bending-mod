package com.bending.network;

import com.bending.BendingMod;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Server -> client: you entered or left combat (the loadout is locked while in combat). */
public record CombatStatePayload(boolean inCombat) implements CustomPacketPayload {
    public static final Type<CombatStatePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(BendingMod.MODID, "combat_state"));
    public static final StreamCodec<ByteBuf, CombatStatePayload> STREAM_CODEC =
            ByteBufCodecs.BOOL.map(CombatStatePayload::new, CombatStatePayload::inCombat);

    /** Client side: whether the local player is in combat right now. */
    public static boolean clientInCombat;

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
