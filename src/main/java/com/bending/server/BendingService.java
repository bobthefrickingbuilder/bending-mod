package com.bending.server;

import com.bending.BendingMod;
import com.bending.ability.Abilities;
import com.bending.ability.Ability;
import com.bending.data.BendingData;
import com.bending.data.Element;
import com.bending.data.ModAttachments;
import com.bending.network.BendingNetwork;
import com.bending.sound.ModSounds;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;

/** Server-side actions on a player's bending: choosing an element, resetting, gaining XP. */
public final class BendingService {
    private BendingService() {}

    public static boolean choose(ServerPlayer player, Element element) {
        BendingData data = player.getData(ModAttachments.BENDING);
        if (data.getElement() != null) {
            player.sendSystemMessage(Component.translatable("message.bending.already_chosen"));
            return false;
        }
        Ability[] loadout = Abilities.defaultLoadout(element);
        if (loadout == null) {
            player.sendSystemMessage(Component.translatable("message.bending.not_ready", element.displayName()));
            return false;
        }
        data.setElement(element);
        for (int slot = 0; slot < loadout.length; slot++) {
            data.unlock(loadout[slot].id());
            data.setLoadout(slot, loadout[slot].id());
        }
        data.setChi(data.getMaxChi());
        BendingNetwork.sync(player);
        player.sendSystemMessage(Component.translatable("message.bending.chosen",
                element.displayName().copy().withColor(element.color())));
        return true;
    }

    /**
     * Puts an ability into its slot (and unlocks it). A testing tool until the
     * skill tree and loadout screen exist.
     */
    public static boolean equip(ServerPlayer player, String abilityPath) {
        Ability ability = Abilities.get(BendingMod.MODID + ":" + abilityPath);
        if (ability == null) {
            player.sendSystemMessage(Component.translatable("message.bending.unknown_ability"));
            return false;
        }
        BendingData data = player.getData(ModAttachments.BENDING);
        if (ability.element() != data.getElement()) {
            player.sendSystemMessage(Component.translatable("message.bending.wrong_element", ability.displayName()));
            return false;
        }
        int slot = ability.slot().ordinal();
        if (data.activeSlot == slot) {
            CastHandler.cancel(data);
        }
        data.unlock(ability.id());
        data.setLoadout(slot, ability.id());
        BendingNetwork.sync(player);
        player.sendSystemMessage(Component.translatable("message.bending.equipped", ability.displayName(), slot + 1));
        return true;
    }

    public static void toggleBendingMode(ServerPlayer player) {
        setBendingMode(player, !player.getData(ModAttachments.BENDING).bendingMode);
    }

    /**
     * Enters or leaves the bending stance. Abilities and fire hops only work in it,
     * and both hands show empty while it's on. Leaving it drops whatever you were channeling.
     */
    public static void setBendingMode(ServerPlayer player, boolean on) {
        BendingData data = player.getData(ModAttachments.BENDING);
        if (on && data.getElement() == null) {
            player.displayClientMessage(Component.translatable("message.bending.no_element"), true);
            return;
        }
        if (data.bendingMode == on) {
            return;
        }
        data.bendingMode = on;
        if (!on) {
            CastHandler.cancel(data);
        }
        BendingNetwork.syncBendingMode(player);
        player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(),
                (on ? ModSounds.BENDING_MODE_ON : ModSounds.BENDING_MODE_OFF).get(), SoundSource.PLAYERS, 0.6f, 1.0f);
        player.displayClientMessage(Component.translatable(on ? "message.bending.mode_on" : "message.bending.mode_off")
                .withColor(on ? data.getElement().color() : 0xAAAAAA), true);
    }

    /** Testing mode: abilities cost no chi. */
    public static void setInfiniteChi(ServerPlayer player, boolean enabled) {
        BendingData data = player.getData(ModAttachments.BENDING);
        data.setInfiniteChi(enabled);
        BendingNetwork.sync(player);
        player.sendSystemMessage(Component.translatable(
                enabled ? "message.bending.infinite_chi_on" : "message.bending.infinite_chi_off"));
    }

    public static void reset(ServerPlayer player) {
        player.setData(ModAttachments.BENDING, new BendingData());
        BendingNetwork.sync(player);
        BendingNetwork.syncBendingMode(player);
    }

    public static void addXp(ServerPlayer player, int amount) {
        BendingData data = player.getData(ModAttachments.BENDING);
        if (data.getElement() == null) {
            return;
        }
        int levels = data.addXp(amount);
        if (levels > 0) {
            player.sendSystemMessage(Component.translatable("message.bending.level_up", data.getLevel())
                    .withStyle(ChatFormatting.GOLD));
            BendingNetwork.sync(player);
        }
    }
}
