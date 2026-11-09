package com.bending.server;

import com.bending.ability.fire.FireConditions;
import com.bending.ability.fire.FireTechniques;
import com.bending.data.Element;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** Element-specific world rules: where you can bend, and how strong you are right now. */
public final class BendingRules {
    private BendingRules() {}

    public static boolean canBend(ServerPlayer player, Element element) {
        return switch (element) {
            case FIRE -> FireConditions.canBend(player);
            default -> true;
        };
    }

    public static Component cannotBendMessage(Element element) {
        return switch (element) {
            case FIRE -> Component.translatable("message.bending.fire_underwater");
            default -> Component.empty();
        };
    }

    /** Damage multiplier from the environment. */
    public static float power(ServerPlayer player, Element element) {
        return switch (element) {
            case FIRE -> FireConditions.power(player)
                    * (FireTechniques.blue(player) ? FireTechniques.BLUE_FIRE_POWER : 1f);
            default -> 1f;
        };
    }

    /** Chi regeneration multiplier from the environment. */
    public static float regen(ServerPlayer player, Element element) {
        return switch (element) {
            case FIRE -> FireConditions.regen(player)
                    * (FireTechniques.has(player, FireTechniques.DANCING_DRAGON) && FireConditions.isDaytime(player) ? 1.5f : 1f);
            default -> 1f;
        };
    }
}
