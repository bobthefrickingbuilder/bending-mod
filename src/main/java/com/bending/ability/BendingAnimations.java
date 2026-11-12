package com.bending.ability;

import com.bending.data.BendingData;
import com.bending.data.ModAttachments;
import com.bending.network.PlayAnimationPayload;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Server side: plays the bender's body animation for a move on everyone who can see them
 * (including themselves in third person). The animations are keyframed JSON files in
 * {@code assets/bending/player_animations/}, played by Player Animation Library.
 *
 * <p>An animation can be bound to something so it stops by itself: the current cast, being in the
 * air, or the dash-ready crouch (held Ctrl).
 */
public final class BendingAnimations {
    public static final String FIRE_BLAST_RIGHT = "fire_blast_right";
    public static final String FIRE_BLAST_LEFT = "fire_blast_left";
    public static final String FIRE_BLAST_FRONT_KICK = "fire_blast_front_kick";
    public static final String FIRE_BLAST_ROUNDHOUSE = "fire_blast_roundhouse";
    public static final String FIRE_ARC_KICK = "fire_arc_kick";
    public static final String FIRE_SWEEP = "fire_sweep";
    public static final String FIRE_ARC_WHIP = "fire_arc_whip";
    public static final String AIR_KICK = "air_kick";
    public static final String LIGHTNING_STANCE = "lightning_stance";
    public static final String LIGHTNING_RELEASE = "lightning_release";
    public static final String LIGHTNING_RELEASE_LEFT = "lightning_release_left";
    public static final String SHIELD_GUARD = "shield_guard";
    public static final String SHIELD_BURST = "shield_burst";
    public static final String SIPHON_DRAW = "siphon_draw";
    public static final String SIPHON_RELEASE = "siphon_release";
    public static final String FLAME_PARRY = "flame_parry";
    public static final String WALL_RISE = "wall_rise";
    public static final String COMBUSTION_FOCUS = "combustion_focus";
    public static final String COMBUSTION_RELEASE = "combustion_release";
    /** Looping: hands drawn in, gathering energy (Lightning, Combustion Beam, Fireball Charge). */
    public static final String CHARGE_STANCE = "charge_stance";
    /** Both palms driven forward: the release of a charged move. */
    public static final String CHARGE_RELEASE = "charge_release";
    /** Looping: deep horse stance while Firestorm builds. */
    public static final String FIRESTORM_STANCE = "firestorm_stance";
    public static final String FIRESTORM_STOMP = "firestorm_stomp";
    /** Looping: head-first dive with fists leading. */
    public static final String METEOR_DIVE = "meteor_dive";
    public static final String METEOR_LAND = "meteor_land";
    /** Looping: hands cupped to the mouth, leaning into the breath. */
    public static final String BREATH_STANCE = "breath_stance";
    /** Looping: coiled crouch, arms swept back, ready to launch (Ctrl held; Blazing Leap charging). */
    public static final String LAUNCH_CROUCH = "launch_crouch";
    /** Held: streamlined, leaning into the dash with flame trailing from the hands. */
    public static final String FIRE_DASH = "fire_dash";
    /** Looping: leaning glide on foot jets. */
    public static final String JET_GLIDE = "jet_glide";
    /** Held: arms thrown up, legs trailing, rising off a Blazing Leap. */
    public static final String LEAP_AIR = "leap_air";

    private static final int NONE = 0, CAST = 1, AIRBORNE = 2, PREP = 3;
    /** An airborne pose ignores the ground for this many ticks after it starts (you launch from it). */
    private static final int AIRBORNE_GRACE = 4;

    private BendingAnimations() {}

    /** Plays a one-shot animation (or a loop that something else will stop). */
    public static void play(ServerPlayer player, String name) {
        bind(player.getData(ModAttachments.BENDING), player, NONE);
        send(player, name);
    }

    /** Plays a looping animation tied to the current cast: it stops by itself when the cast ends. */
    public static void playForCast(ServerPlayer player, BendingData data, String name) {
        bind(data, player, CAST);
        send(player, name);
    }

    /** Like {@link #playForCast}, but only starts it if this cast isn't already showing a pose. */
    public static void ensureForCast(ServerPlayer player, BendingData data, String name) {
        if (data.animBinding != CAST) {
            playForCast(player, data, name);
        }
    }

    /**
     * Plays a one-shot move in mid-air; if an air pose (dash, glide, leap) was showing, it comes back
     * after {@code ticks} as long as you're still airborne.
     */
    public static void playOverAirPose(ServerPlayer player, BendingData data, String name, int ticks) {
        boolean airborne = data.animBinding == AIRBORNE && !data.animAirPose.isEmpty();
        play(player, name);
        data.airPoseResumeAt = airborne ? player.level().getGameTime() + ticks : 0;
    }

    /** Plays an air pose that holds until you land (or hit water). */
    public static void playUntilLanded(ServerPlayer player, BendingData data, String name) {
        data.animAirPose = name;
        bind(data, player, AIRBORNE);
        send(player, name);
    }

    /** The client says Ctrl went down (ready to dash) or up. */
    public static void setDashReady(ServerPlayer player, BendingData data, boolean ready) {
        data.crouchReady = ready;
        if (ready) {
            if (data.animBinding == NONE && data.activeSlot == -1 && player.onGround()) {
                bind(data, player, PREP);
                send(player, LAUNCH_CROUCH);
            }
        } else if (data.animBinding == PREP) {
            bind(data, player, NONE);
            stop(player);
        }
    }

    public static void stop(ServerPlayer player) {
        player.getData(ModAttachments.BENDING).animBinding = NONE;
        send(player, "");
    }

    /** Each tick: stops a bound animation once what it was bound to is over. */
    public static void tick(ServerPlayer player, BendingData data) {
        if (data.airPoseResumeAt > 0 && player.level().getGameTime() >= data.airPoseResumeAt) {
            data.airPoseResumeAt = 0;
            if (!player.onGround() && !player.isInWater() && data.animBinding == NONE) {
                playUntilLanded(player, data, data.animAirPose);
            }
        }
        boolean over = switch (data.animBinding) {
            case CAST -> data.activeSlot == -1;
            case AIRBORNE -> player.level().getGameTime() - data.animStart > AIRBORNE_GRACE
                    && (player.onGround() || player.isInWater() || player.isFallFlying());
            case PREP -> !player.onGround() && data.activeSlot == -1;
            default -> false;
        };
        if (over) {
            stop(player);
        }
    }

    private static void bind(BendingData data, ServerPlayer player, int binding) {
        data.animBinding = binding;
        data.animStart = player.level().getGameTime();
    }

    private static void send(ServerPlayer player, String name) {
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, new PlayAnimationPayload(player.getId(), name));
    }
}
