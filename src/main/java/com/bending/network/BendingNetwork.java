package com.bending.network;

import com.bending.ability.AirJump;
import com.bending.data.BendingData;
import com.bending.data.ModAttachments;
import com.bending.server.BendingService;
import com.bending.server.CastHandler;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class BendingNetwork {
    private BendingNetwork() {}

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");

        registrar.playToServer(CastAbilityPayload.TYPE, CastAbilityPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player) {
                        CastHandler.onInput(player, payload.slot(), payload.pressed());
                    }
                }));

        registrar.playToServer(ChooseElementPayload.TYPE, ChooseElementPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player) {
                        BendingService.choose(player, payload.element());
                    }
                }));

        registrar.playToServer(AirJumpPayload.TYPE, AirJumpPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player) {
                        AirJump.perform(player);
                    }
                }));

        registrar.playToServer(EquipAbilityPayload.TYPE, EquipAbilityPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player) {
                        BendingService.setSlot(player, payload.slot(), payload.abilityId());
                    }
                }));

        registrar.playToServer(UnlockSkillPayload.TYPE, UnlockSkillPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player) {
                        BendingService.learnSkill(player, payload.nodeId());
                    }
                }));

        registrar.playToServer(MoveInputPayload.TYPE, MoveInputPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player) {
                        BendingData data = player.getData(ModAttachments.BENDING);
                        data.moveForward = Math.max(-1, Math.min(1, payload.forward()));
                        data.moveLeft = Math.max(-1, Math.min(1, payload.left()));
                    }
                }));

        registrar.playToServer(ToggleBendingModePayload.TYPE, ToggleBendingModePayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player) {
                        BendingService.toggleBendingMode(player);
                    }
                }));

        // Client side: store the server's copy on the local player. No client-only classes needed.
        registrar.playToClient(SyncBendingDataPayload.TYPE, SyncBendingDataPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    BendingData data = payload.data();
                    data.synced = true;
                    // Bending mode isn't part of the full sync (it has its own packet), so keep it.
                    data.bendingMode = context.player().getData(ModAttachments.BENDING).bendingMode;
                    context.player().setData(ModAttachments.BENDING, data);
                }));

        registrar.playToClient(CombatStatePayload.TYPE, CombatStatePayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> CombatStatePayload.clientInCombat = payload.inCombat()));

        registrar.playToClient(BendingModePayload.TYPE, BendingModePayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player().level().getEntity(payload.entityId()) instanceof Player target) {
                        target.getData(ModAttachments.BENDING).bendingMode = payload.on();
                    }
                }));
    }

    /** Tells the player and everyone who can see them whether they are in bending mode. */
    public static void syncBendingMode(ServerPlayer player) {
        boolean on = player.getData(ModAttachments.BENDING).bendingMode;
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, new BendingModePayload(player.getId(), on));
    }

    /** Tells one viewer whether {@code target} is in bending mode. */
    public static void syncBendingModeTo(ServerPlayer target, ServerPlayer viewer) {
        boolean on = target.getData(ModAttachments.BENDING).bendingMode;
        PacketDistributor.sendToPlayer(viewer, new BendingModePayload(target.getId(), on));
    }

    /** Sends the player's current bending data to their client. */
    public static void sync(ServerPlayer player) {
        BendingData data = player.getData(ModAttachments.BENDING);
        data.dirty = false;
        PacketDistributor.sendToPlayer(player, new SyncBendingDataPayload(data.copy()));
    }
}
