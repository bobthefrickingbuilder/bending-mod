package com.bending.client;

import com.bending.BendingMod;
import com.bending.network.PlayAnimationPayload;
import com.zigythebird.playeranim.animation.PlayerAnimationController;
import com.zigythebird.playeranim.api.PlayerAnimationAccess;
import com.zigythebird.playeranim.api.PlayerAnimationFactory;
import com.zigythebird.playeranimcore.animation.layered.modifier.AbstractFadeModifier;
import com.zigythebird.playeranimcore.easing.EasingType;
import com.zigythebird.playeranimcore.enums.PlayState;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;

/** Client side of the move animations: one animation layer on every player, driven by the server. */
public final class MoveAnimations {
    private static final ResourceLocation LAYER = ResourceLocation.fromNamespaceAndPath(BendingMod.MODID, "moves");
    /** Above vanilla-ish emote layers (1000) so a move always shows. */
    private static final int PRIORITY = 1600;
    /** Ticks to blend into a new animation. */
    private static final int FADE_TICKS = 2;

    private MoveAnimations() {}

    /** Call from client setup (inside enqueueWork). */
    public static void init() {
        PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(LAYER, PRIORITY,
                player -> new PlayerAnimationController(player, (controller, state, setter) -> PlayState.STOP));
        PlayAnimationPayload.clientHandler = MoveAnimations::handle;
    }

    private static void handle(PlayAnimationPayload payload) {
        var level = Minecraft.getInstance().level;
        if (level == null || !(level.getEntity(payload.entityId()) instanceof AbstractClientPlayer player)) {
            return;
        }
        if (!(PlayerAnimationAccess.getPlayerAnimationLayer(player, LAYER) instanceof PlayerAnimationController controller)) {
            return;
        }
        if (payload.name().isEmpty()) {
            controller.stopTriggeredAnimation();
            return;
        }
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(BendingMod.MODID, payload.name());
        if (!controller.replaceAnimationWithFade(AbstractFadeModifier.standardFadeIn(FADE_TICKS, EasingType.EASE_OUT_SINE), id)) {
            if (!controller.triggerAnimation(id)) {
                BendingMod.LOGGER.warn("Missing player animation {}", id);
            }
        }
    }
}
