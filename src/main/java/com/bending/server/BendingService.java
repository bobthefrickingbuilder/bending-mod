package com.bending.server;

import com.bending.BendingMod;
import com.bending.ability.Abilities;
import com.bending.ability.Ability;
import com.bending.ability.AbilitySlot;
import com.bending.data.BendingData;
import com.bending.data.Element;
import com.bending.data.ModAttachments;
import com.bending.network.BendingNetwork;
import com.bending.skill.SkillNode;
import com.bending.skill.SkillTree;
import com.bending.sound.ModSounds;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
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
            if (loadout[slot] != null) {
                data.unlock(loadout[slot].id());
                data.setLoadout(slot, loadout[slot].id());
            }
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
        if (ability.slot() == AbilitySlot.INNATE) {
            player.sendSystemMessage(Component.translatable("message.bending.innate", ability.displayName()));
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

    /** How long a hit (dealt or taken) keeps you in combat: 5 s. */
    public static final int COMBAT_TICKS = 100;

    public static void markCombat(ServerPlayer player) {
        BendingData data = player.getData(ModAttachments.BENDING);
        if (data.getElement() != null) {
            data.combatUntil = player.level().getGameTime() + COMBAT_TICKS;
        }
    }

    public static boolean inCombat(ServerPlayer player) {
        return player.level().getGameTime() < player.getData(ModAttachments.BENDING).combatUntil;
    }

    /** Abilities you can equip without unlocking them first: everything, in creative mode (for testing). */
    public static boolean canUseLocked(net.minecraft.world.entity.player.Player player) {
        return player.isCreative();
    }

    /**
     * From the loadout screen: put an unlocked ability of your element into its own slot, or clear the
     * slot ({@code abilityId} empty). Anything that doesn't check out is ignored silently.
     */
    public static void setSlot(ServerPlayer player, int slot, String abilityId) {
        BendingData data = player.getData(ModAttachments.BENDING);
        if (slot < 0 || slot >= BendingData.SLOTS || data.getElement() == null) {
            return;
        }
        if (inCombat(player)) {
            player.displayClientMessage(Component.translatable("message.bending.loadout_combat").withStyle(ChatFormatting.RED), true);
            return;
        }
        if (!abilityId.isEmpty()) {
            Ability ability = Abilities.get(abilityId);
            if (ability == null || ability.element() != data.getElement() || ability.slot().ordinal() != slot
                    || (!data.isUnlocked(ability.id()) && !canUseLocked(player))) {
                return;
            }
            data.unlock(ability.id());
        }
        if (data.activeSlot == slot) {
            CastHandler.cancel(data); // swapping out what you're channelling ends it
        }
        data.setLoadout(slot, abilityId);
        BendingNetwork.sync(player);
    }

    /**
     * From the skill tree screen: learn a node of your element's tree, if its requirement is learned and
     * you have the points (creative: free). A learned ability goes straight into its slot if that slot is empty.
     */
    public static void learnSkill(ServerPlayer player, String nodeId) {
        BendingData data = player.getData(ModAttachments.BENDING);
        SkillNode node = SkillTree.get(nodeId);
        if (node == null || !SkillTree.of(data.getElement()).contains(node)) {
            return;
        }
        boolean free = canUseLocked(player);
        if (SkillTree.state(node, data, free) != SkillTree.State.AVAILABLE) {
            return;
        }
        if (!free && !data.trySpendSkillPoints(node.cost())) {
            return;
        }
        int rank = SkillTree.rank(node, data) + 1;
        data.unlock(node.rankId(rank));
        if (free) {
            data.unlock(node.rankId(rank) + FREE_MARK); // learned without paying: a respec refunds nothing for it
        }
        com.bending.skill.SkillStats.applyAttributes(player);
        Ability ability = node.ability();
        if (ability != null && ability.slot() != AbilitySlot.INNATE && !inCombat(player)) {
            int slot = ability.slot().ordinal();
            if (data.getLoadout(slot).isEmpty()) {
                data.setLoadout(slot, ability.id());
            }
        }
        BendingNetwork.sync(player);
        player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.7f, 1.4f);
        Component name = node.maxRank() > 1
                ? Component.translatable("message.bending.learned_rank", node.name(), rank, node.maxRank())
                : node.name();
        player.displayClientMessage(Component.translatable("message.bending.learned", name)
                .withColor(node.branch().color()), true);
    }

    /** Special node id the skill screen sends to ask for a respec. */
    public static final String RESPEC = "#respec";
    /** Stored next to a rank's unlock when it was learned for free (creative). */
    private static final String FREE_MARK = "@free";

    /**
     * Unlearns every skill-tree node you paid for and refunds the points. The starting kit (and
     * the Inner Fire centre) stays. Not while in combat.
     */
    public static void respec(ServerPlayer player) {
        BendingData data = player.getData(ModAttachments.BENDING);
        if (data.getElement() == null) {
            return;
        }
        if (inCombat(player)) {
            player.displayClientMessage(Component.translatable("message.bending.loadout_combat").withStyle(ChatFormatting.RED), true);
            return;
        }
        java.util.Set<String> starting = new java.util.HashSet<>();
        Ability[] kit = Abilities.defaultLoadout(data.getElement());
        if (kit != null) {
            for (Ability a : kit) {
                if (a != null) {
                    starting.add(a.id());
                }
            }
        }
        int refund = 0;
        for (SkillNode node : SkillTree.of(data.getElement())) {
            if (node.kind() == SkillNode.Kind.CORE || node.id().equals(SkillTree.ROOT) || starting.contains(node.id())) {
                continue;
            }
            int rank = SkillTree.rank(node, data);
            for (int r = 1; r <= rank; r++) {
                if (!data.isUnlocked(node.rankId(r) + FREE_MARK)) {
                    refund += node.cost();
                }
                data.relock(node.rankId(r));
                data.relock(node.rankId(r) + FREE_MARK);
            }
        }
        CastHandler.cancel(data);
        // Unequip abilities you no longer know.
        for (int slot = 0; slot < BendingData.SLOTS; slot++) {
            String id = data.getLoadout(slot);
            if (!id.isEmpty() && !data.isUnlocked(id)) {
                data.setLoadout(slot, "");
            }
        }
        data.addSkillPoints(refund);
        com.bending.skill.SkillStats.applyAttributes(player);
        BendingNetwork.sync(player);
        player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.7f, 1.2f);
        player.displayClientMessage(Component.translatable("message.bending.respec", refund).withStyle(ChatFormatting.GOLD), true);
    }

    /**
     * Clears loadout entries that no longer fit their slot (an ability that moved, like
     * Fire Blast becoming the innate attack, or one that was removed).
     */
    public static void sanitizeLoadout(ServerPlayer player) {
        BendingData data = player.getData(ModAttachments.BENDING);
        for (int slot = 0; slot < BendingData.SLOTS; slot++) {
            String id = data.getLoadout(slot);
            if (id.isEmpty()) {
                continue;
            }
            Ability ability = Abilities.get(id);
            if (ability == null || ability.slot().ordinal() != slot) {
                data.setLoadout(slot, "");
            }
        }
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
