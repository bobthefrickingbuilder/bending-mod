package com.bending.server;

import java.util.Arrays;

import com.bending.BendingMod;
import com.bending.ability.Abilities;
import com.bending.ability.Ability;
import com.bending.ability.fire.FireJetDash;
import com.bending.ability.fire.FlameParry;
import com.bending.ability.fire.MeteorSlam;
import com.bending.data.BendingData;
import com.bending.data.Element;
import com.bending.data.ModAttachments;
import com.bending.network.BendingNetwork;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;

import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/** Game-bus listeners: ticking, syncing, passives and the /bending command. */
public final class BendingServerEvents {
    /** Chi starts regenerating this many ticks after the last cast. */
    private static final int REGEN_DELAY = 30;
    /** Base chi regen: 5 per second. */
    private static final float REGEN_PER_TICK = 0.25f;

    private BendingServerEvents() {}

    public static void register(IEventBus bus) {
        bus.addListener(BendingServerEvents::onPlayerTick);
        bus.addListener(BendingServerEvents::putOutFire);
        bus.addListener(BendingServerEvents::onLogin);
        bus.addListener(BendingServerEvents::onRespawn);
        bus.addListener(BendingServerEvents::onChangeDimension);
        bus.addListener(BendingServerEvents::onStartTracking);
        bus.addListener(BendingServerEvents::onIncomingDamage);
        bus.addListener(BendingServerEvents::onFall);
        bus.addListener(BendingServerEvents::onRegisterCommands);
    }

    /**
     * Firebenders never burn: any flames on them go out at once, on both sides, so the burning
     * overlay and the flames drawn on the body never show (other players see it through the
     * synced on-fire flag, which the server clears).
     */
    private static void putOutFire(PlayerTickEvent.Post event) {
        var player = event.getEntity();
        if (player.getRemainingFireTicks() > 0 && player.getData(ModAttachments.BENDING).getElement() == Element.FIRE) {
            player.clearFire();
        }
    }

    private static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        BendingData data = player.getData(ModAttachments.BENDING);
        Element element = data.getElement();
        if (element == null) {
            return;
        }
        long now = player.level().getGameTime();
        if (now % 20 == 0) {
            com.bending.skill.SkillStats.applyAttributes(player); // health and move speed from the tree
        }

        if (player.onGround() || player.isInWater() || player.onClimbable()) {
            data.airJumpsUsed = 0;
        }
        if (data.pendingBacklash > 0f) {
            float backlash = data.pendingBacklash;
            data.pendingBacklash = 0f;
            player.hurt(player.damageSources().lightningBolt(), backlash);
        }
        CastHandler.tick(player, data);
        if (data.dashTrailTicks > 0) {
            FireJetDash.tickTrail(player, data);
        }
        if (data.slamming) {
            MeteorSlam.tickSlam(player, data);
        }

        boolean inCombat = now < data.combatUntil;
        if (inCombat != data.combatSent) {
            data.combatSent = inCombat;
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, new com.bending.network.CombatStatePayload(inCombat));
        }

        if (data.activeSlot == -1 && now - data.lastCastTime >= com.bending.skill.SkillStats.regenDelay(data, REGEN_DELAY) && data.getChi() < data.getMaxChi()) {
            data.addChi(REGEN_PER_TICK * BendingRules.regen(player, element));
        }

        if (data.dirty && now % 5 == 0) {
            BendingNetwork.sync(player);
        }
    }

    private static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            BendingService.sanitizeLoadout(player);
            BendingNetwork.sync(player);
        }
    }

    private static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            CastHandler.cancel(player.getData(ModAttachments.BENDING));
            player.getData(ModAttachments.BENDING).slamming = false;
            player.getData(ModAttachments.BENDING).combatUntil = 0;
            BendingNetwork.sync(player);
        }
    }

    private static void onChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            BendingNetwork.sync(player);
            BendingNetwork.syncBendingMode(player); // the client made a fresh player object
        }
    }

    /** Someone new can see this player: tell them if the player is in bending mode. */
    private static void onStartTracking(PlayerEvent.StartTracking event) {
        if (event.getTarget() instanceof ServerPlayer target && event.getEntity() instanceof ServerPlayer viewer
                && target.getData(ModAttachments.BENDING).bendingMode) {
            BendingNetwork.syncBendingModeTo(target, viewer);
        }
    }

    /** Blocking from held abilities, then the firebender passive "Inner Fire" (immune to fire and freezing, half lava damage). */
    private static void onIncomingDamage(LivingIncomingDamageEvent event) {
        // Hitting something puts you in combat (the loadout locks for a few seconds).
        if (event.getSource().getEntity() instanceof ServerPlayer attacker && attacker != event.getEntity()) {
            BendingService.markCombat(attacker);
        }
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        BendingData data = player.getData(ModAttachments.BENDING);
        if (event.getSource().getEntity() instanceof net.minecraft.world.entity.LivingEntity attacker && attacker != player) {
            BendingService.markCombat(player);
        }
        if (event.getSource().is(net.minecraft.world.damagesource.DamageTypes.LIGHTNING_BOLT)
                && com.bending.ability.fire.Lightning.tryRedirect(player, data, null)) {
            event.setCanceled(true);
            return;
        }
        if (FlameParry.tryParry(player, data, event.getSource())) {
            event.setCanceled(true);
            return;
        }
        if (data.activeSlot != -1) {
            Ability active = CastHandler.abilityIn(data, data.activeSlot);
            if (active != null) {
                float amount = active.modifyIncomingDamage(player, data, event.getSource(), event.getAmount());
                if (amount <= 0f) {
                    event.setCanceled(true);
                    return;
                }
                event.setAmount(amount);
                if (active.cancelWhenHurt()) {
                    CastHandler.cancel(data);
                    active.onHurtWhileActive(player, data);
                }
            }
        }
        data.lastHurtTime = player.level().getGameTime();
        float resist = com.bending.skill.SkillStats.resistance(data);
        if (resist > 0f && !event.getSource().is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            event.setAmount(event.getAmount() * (1f - resist)); // Iron Body
        }
        if (data.getElement() != Element.FIRE) {
            return;
        }
        if (event.getSource().is(DamageTypeTags.IS_FREEZING)) {
            event.setCanceled(true);
        } else if (event.getSource().is(net.minecraft.world.damagesource.DamageTypes.LAVA)) {
            event.setAmount(event.getAmount() * 0.5f); // lava is molten rock, not just fire: it still hurts
        } else if (event.getSource().is(DamageTypeTags.IS_FIRE)) {
            event.setCanceled(true); // immune to fire, burning and fire blasts (including other benders')
        }
    }

    /** No fall damage shortly after a mobility ability. */
    private static void onFall(LivingFallEvent event) {
        if (event.getEntity() instanceof ServerPlayer player
                && player.getData(ModAttachments.BENDING).noFallUntil > player.level().getGameTime()) {
            event.setCanceled(true);
        }
    }

    private static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("bending")
                .then(Commands.literal("choose")
                        .then(Commands.argument("element", StringArgumentType.word())
                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                        Arrays.stream(Element.values()).map(Element::getSerializedName), builder))
                                .executes(ctx -> {
                                    Element element = Element.byName(StringArgumentType.getString(ctx, "element"));
                                    if (element == null) {
                                        ctx.getSource().sendFailure(Component.translatable("message.bending.unknown_element"));
                                        return 0;
                                    }
                                    return BendingService.choose(ctx.getSource().getPlayerOrException(), element) ? 1 : 0;
                                })))
                .then(Commands.literal("equip")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("ability", StringArgumentType.word())
                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                        Abilities.all().stream().map(a -> a.id().substring(BendingMod.MODID.length() + 1)), builder))
                                .executes(ctx -> BendingService.equip(ctx.getSource().getPlayerOrException(),
                                        StringArgumentType.getString(ctx, "ability")) ? 1 : 0)))
                .then(Commands.literal("infinitechi")
                        .requires(source -> source.hasPermission(2))
                        .executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            BendingService.setInfiniteChi(player, !player.getData(ModAttachments.BENDING).hasInfiniteChi());
                            return 1;
                        })
                        .then(Commands.argument("enabled", BoolArgumentType.bool())
                                .executes(ctx -> {
                                    BendingService.setInfiniteChi(ctx.getSource().getPlayerOrException(),
                                            BoolArgumentType.getBool(ctx, "enabled"));
                                    return 1;
                                })))
                .then(Commands.literal("respec")
                        .executes(ctx -> {
                            BendingService.respec(ctx.getSource().getPlayerOrException());
                            return 1;
                        }))
                .then(Commands.literal("reset")
                        .requires(source -> source.hasPermission(2))
                        .executes(ctx -> {
                            BendingService.reset(ctx.getSource().getPlayerOrException());
                            ctx.getSource().sendSuccess(() -> Component.translatable("message.bending.reset"), false);
                            return 1;
                        }))
                .then(Commands.literal("xp")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                                .executes(ctx -> {
                                    BendingService.addXp(ctx.getSource().getPlayerOrException(),
                                            IntegerArgumentType.getInteger(ctx, "amount"));
                                    return 1;
                                }))));
    }
}
