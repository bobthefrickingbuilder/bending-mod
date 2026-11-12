package com.bending.client;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.util.Mth;

/**
 * First-person arm animations, made to be seen from the eye. When the server starts a move animation
 * for you, the matching arm keyframes here play on your two first-person arms (the full-body pose is
 * for third person only).
 *
 * <p>An arm pose is six numbers, in view space, mirrored for the left arm so the same numbers mean the
 * same thing on either side:
 * <ul>
 * <li>x: sideways, + out to that arm's side, - in toward the middle of the screen</li>
 * <li>y: + up, - down (about -0.9 drops the arm off screen)</li>
 * <li>z: + reaching forward, - pulled back toward you</li>
 * <li>pitch: degrees, + swings the hand up around the shoulder</li>
 * <li>yaw: degrees, + swings the hand in toward the middle</li>
 * <li>roll: degrees, + leans the top of the arm in toward the middle</li>
 * </ul>
 */
public final class FirstPersonArms {
    public static final int X = 0, Y = 1, Z = 2, PITCH = 3, YAW = 4, ROLL = 5;
    private static final float[] REST = new float[6];
    /** Ticks to blend from whatever the arms were doing into a new move (or back to rest). */
    private static final float BLEND = 3f;

    /** One keyframe: both arms at a tick. Ease: how it arrives here from the previous key. */
    record Key(float tick, float[] right, float[] left, Ease ease) {}

    enum Ease { SMOOTH, SNAP, LINEAR }

    /**
     * A move. Loops hold their last key forever, with the arms swaying (sway amplitude in x/y, speed
     * in radians per tick, and whether the arms sway in opposite phase or trace circles).
     */
    record Move(Key[] keys, boolean loop, float swayX, float swayY, float swaySpeed, boolean circles, float cycleFrom,
                boolean strike) {
        Move(Key[] keys, boolean loop, float swayX, float swayY, float swaySpeed, boolean circles) {
            this(keys, loop, swayX, swayY, swaySpeed, circles, -1f, false);
        }

        Move(Key[] keys, boolean loop, float swayX, float swayY, float swaySpeed, boolean circles, float cycleFrom) {
            this(keys, loop, swayX, swayY, swaySpeed, circles, cycleFrom, false);
        }
    }

    /** Moves thrown at the crosshair: these keep their full reach. */
    private static final java.util.Set<String> STRIKES = java.util.Set.of(
            "fire_blast_right", "fire_blast_left", "charge_release", "lightning_release", "lightning_release_left");

    private static final Map<String, Move> MOVES = new HashMap<>();

    // ---- legs: kicks bring your actual leg up into view ----

    /** Leg pose: how far in view (0 hidden below the screen, 1 up), thigh pitch, yaw, knee bend (degrees). */
    public static final int SHOW = 0, LEG_PITCH = 1, LEG_YAW = 2, KNEE = 3;

    record LegKey(float tick, float[] pose, Ease ease) {}

    record LegMove(boolean right, LegKey[] keys) {}

    private static final Map<String, LegMove> LEGS = new HashMap<>();
    private static LegMove legCurrent;
    private static float[] legFrom = new float[4];
    private static boolean legFromRight = true;

    // Current playback (local player only).
    private static Move current;
    private static float startTime;
    private static final float[][] from = {new float[6], new float[6]};
    private static float fromTime = -100;

    private FirstPersonArms() {}

    // ---- playback ----

    /** The server started (or, with an empty name, stopped) a move animation on you. */
    public static void start(String name, float now) {
        float[][] was = {new float[6], new float[6]};
        sample(now, was);
        from[0] = was[0];
        from[1] = was[1];
        fromTime = now;
        float[] legWas = new float[4];
        legFromRight = sampleLeg(now, legWas) ? legRight : legFromRight;
        legFrom = legWas;
        current = name.isEmpty() ? null : MOVES.get(name);
        legCurrent = name.isEmpty() ? null : LEGS.get(name);
        startTime = now;
    }

    /** True while a move is posing the arms (vanilla's punch swing is suppressed then). */
    public static boolean active(float now) {
        return current != null || now - fromTime < BLEND;
    }

    /** Fills out[0] (right) and out[1] (left) with the pose to draw now. */
    public static void sample(float now, float[][] out) {
        float[][] target = {new float[6], new float[6]};
        if (current != null) {
            pose(current, now - startTime, target);
            for (float[] arm : target) {
                if (current.strike) {
                    stopAtMiddle(arm);
                } else {
                    keepToSide(arm);
                }
            }
        }
        float b = Mth.clamp((now - fromTime) / BLEND, 0f, 1f);
        b = b * b * (3 - 2 * b);
        for (int arm = 0; arm < 2; arm++) {
            for (int i = 0; i < 6; i++) {
                out[arm][i] = Mth.lerp(b, from[arm][i], target[arm][i]);
            }
        }
    }

    /** How much of any move toward the middle of the screen survives: arms stay on their own side. */
    private static final float INWARD = 0.4f;

    /**
     * Punches keep their full reach toward the crosshair (squashing them made them barely move); they
     * are only stopped short of the middle so a fist never crosses over.
     */
    private static void stopAtMiddle(float[] a) {
        a[X] = Math.max(a[X], -0.38f);
        a[YAW] = Math.min(a[YAW], 28f);
        a[ROLL] = Math.min(a[ROLL], 8f);
    }

    private static void keepToSide(float[] a) {
        if (a[X] < 0) {
            a[X] *= INWARD;
        }
        if (a[YAW] > 0) {
            a[YAW] *= INWARD;
        }
        if (a[ROLL] > 0) {
            a[ROLL] *= INWARD;
        }
    }

    /** Which leg {@link #sampleLeg} last posed. */
    public static boolean legRight = true;

    /** Fills out with the kicking leg's pose; false when no leg is in view. */
    public static boolean sampleLeg(float now, float[] out) {
        float[] target = new float[4];
        boolean right = legCurrent != null ? legCurrent.right : legFromRight;
        if (legCurrent != null) {
            legPose(legCurrent, now - startTime, target);
        } else {
            System.arraycopy(legFrom, 0, target, 0, 4);
            target[SHOW] = 0f;
        }
        float b = Mth.clamp((now - fromTime) / BLEND, 0f, 1f);
        b = b * b * (3 - 2 * b);
        boolean sameLeg = right == legFromRight;
        for (int i = 0; i < 4; i++) {
            out[i] = sameLeg ? Mth.lerp(b, legFrom[i], target[i]) : target[i];
        }
        legRight = right;
        return out[SHOW] > 0.01f;
    }

    private static void legPose(LegMove move, float t, float[] out) {
        LegKey[] keys = move.keys;
        LegKey prev = keys[0];
        LegKey next = keys[keys.length - 1];
        if (t <= prev.tick) {
            next = prev;
        } else {
            for (int i = 1; i < keys.length; i++) {
                if (t <= keys[i].tick) {
                    prev = keys[i - 1];
                    next = keys[i];
                    break;
                }
                prev = keys[i];
            }
        }
        float span = next.tick - prev.tick;
        float u = span <= 0 ? 1f : Mth.clamp((t - prev.tick) / span, 0f, 1f);
        u = next.ease == Ease.SNAP ? 1 - (1 - u) * (1 - u) * (1 - u) : u * u * (3 - 2 * u);
        for (int i = 0; i < 4; i++) {
            out[i] = Mth.lerp(u, prev.pose[i], next.pose[i]);
        }
    }

    private static float[] leg(float show, float pitch, float yaw, float knee) {
        return new float[] {show, pitch, yaw, knee};
    }

    private static void legs(String name, boolean right, LegKey... keys) {
        LEGS.put(name, new LegMove(right, keys));
    }

    private static LegKey lk(float tick, float[] pose) {
        return new LegKey(tick, pose, Ease.SMOOTH);
    }

    private static LegKey lsnap(float tick, float[] pose) {
        return new LegKey(tick, pose, Ease.SNAP);
    }

    private static void pose(Move move, float t, float[][] out) {
        Key[] keys = move.keys;
        float last = keys[keys.length - 1].tick;
        if (move.cycleFrom >= 0 && t > last) {
            // A cycle: after the intro, play cycleFrom..last over and over.
            t = move.cycleFrom + (t - move.cycleFrom) % (last - move.cycleFrom);
        }
        Key prev = keys[0];
        Key next = keys[keys.length - 1];
        if (t <= prev.tick) {
            next = prev;
        } else {
            for (int i = 1; i < keys.length; i++) {
                if (t <= keys[i].tick) {
                    prev = keys[i - 1];
                    next = keys[i];
                    break;
                }
                prev = keys[i];
            }
        }
        float span = next.tick - prev.tick;
        float u = span <= 0 ? 1f : Mth.clamp((t - prev.tick) / span, 0f, 1f);
        u = switch (next.ease) {
            case SNAP -> 1 - (1 - u) * (1 - u) * (1 - u);
            case LINEAR -> u;
            default -> u * u * (3 - 2 * u);
        };
        for (int i = 0; i < 6; i++) {
            out[0][i] = Mth.lerp(u, prev.right[i], next.right[i]);
            out[1][i] = Mth.lerp(u, prev.left[i], next.left[i]);
        }
        if (move.loop && (move.swayX != 0 || move.swayY != 0)) {
            // Fade the sway in after the intro so it never jerks the arms.
            float in = Mth.clamp((t - keys[keys.length - 1].tick) / 6f, 0f, 1f);
            float a = t * move.swaySpeed;
            out[0][X] += in * move.swayX * Mth.sin(a);
            out[0][Y] += in * move.swayY * (move.circles ? Mth.cos(a) : Mth.sin(a * 2));
            float b = move.circles ? a + Mth.PI : a + Mth.PI;
            out[1][X] += in * move.swayX * Mth.sin(b);
            out[1][Y] += in * move.swayY * (move.circles ? Mth.cos(b) : Mth.sin(b * 2));
        }
    }

    // ---- authoring helpers ----

    private static float[] p(float x, float y, float z, float pitch, float yaw, float roll) {
        return new float[] {x, y, z, pitch, yaw, roll};
    }

    private static Key k(float tick, float[] right, float[] left) {
        return new Key(tick, right, left, Ease.SMOOTH);
    }

    private static Key snap(float tick, float[] right, float[] left) {
        return new Key(tick, right, left, Ease.SNAP);
    }

    private static void once(String name, Key... keys) {
        MOVES.put(name, new Move(keys, false, 0, 0, 0, false, -1f, STRIKES.contains(name)));
    }

    private static void loop(String name, float swayX, float swayY, float speed, boolean circles, Key... keys) {
        MOVES.put(name, new Move(keys, true, swayX, swayY, speed, circles));
    }

    /** Same move with the arms swapped. */
    private static void mirror(String name, String of) {
        Move m = MOVES.get(of);
        Key[] keys = new Key[m.keys.length];
        for (int i = 0; i < keys.length; i++) {
            Key key = m.keys[i];
            keys[i] = new Key(key.tick, key.left, key.right, key.ease);
        }
        MOVES.put(name, new Move(keys, m.loop, m.swayX, m.swayY, m.swaySpeed, m.circles, m.cycleFrom,
                STRIKES.contains(name)));
    }

    // ---- the moves ----

    static {
        float[] rest = REST;
        float[] guard = p(-0.06f, 0.02f, -0.02f, 6, 6, 4);
        float[] chamber = p(0.06f, -0.16f, -0.2f, -12, -2, 0);

        // Fire Blast: a straight punch, the other fist pulling back to the hip.
        // A real jab: a one-tick cock back, the fist snapped out to the crosshair in two ticks with
        // the arm turning in behind it, a beat at full reach, then pulled back to guard. The other
        // fist drops back to the chin. Forward reach stays near vanilla's own swing (0.4-0.5) so
        // the arm's cut end never shows at the bottom of the screen.
        float[] jabOut = p(-0.34f, 0.1f, 0.52f, 2, 26, 0);
        float[] rearGuard = p(0.04f, -0.24f, -0.26f, -14, 4, 0);
        once("fire_blast_right",
                k(0, rest, rest),
                k(1, chamber, guard),
                snap(3, jabOut, rearGuard),
                k(4, p(-0.33f, 0.1f, 0.49f, 2, 25, 0), rearGuard),
                k(8, p(-0.04f, -0.02f, 0.04f, 0, 4, 0), guard),
                k(10, rest, rest));
        mirror("fire_blast_left", "fire_blast_right");

        // Kicks: the arms stay up in a tight guard and counter-swing for balance while the leg
        // (see the leg moves below) comes up into view.
        once("fire_blast_front_kick",
                k(0, rest, rest),
                k(2, p(-0.08f, -0.04f, 0.0f, 8, 10, 4), p(-0.08f, -0.04f, 0.0f, 8, 10, 4)),
                snap(4, p(0.1f, -0.14f, -0.14f, -8, -10, 0), p(-0.1f, 0.02f, 0.06f, 10, 12, 6)),
                k(8, p(-0.04f, -0.04f, 0.0f, 4, 6, 2), p(-0.04f, -0.04f, 0.0f, 4, 6, 2)),
                k(11, rest, rest));
        mirror("fire_blast_roundhouse", "fire_blast_front_kick");

        // Fire Sweep: drop low, a hand down to the ground, sweep round, come back up.
        once("fire_sweep",
                k(0, rest, rest),
                k(3, p(-0.02f, -0.42f, 0.12f, -36, 4, 0), p(0.1f, -0.2f, -0.04f, -12, -10, 0)),
                k(7, p(-0.22f, -0.46f, 0.16f, -40, 30, 0), p(0.2f, -0.18f, -0.06f, -10, -28, 0)),
                k(11, p(0.16f, -0.44f, 0.1f, -38, -24, 0), p(-0.04f, -0.22f, 0.0f, -14, 10, 0)),
                k(15, rest, rest));

        // Air Kick: arms flung up and out for balance while the leg stamps down.
        once("air_kick",
                k(0, rest, rest),
                k(3, p(0.16f, 0.1f, -0.06f, 16, -24, -6), p(0.16f, 0.1f, -0.06f, 16, -24, -6)),
                k(8, p(0.2f, 0.06f, -0.08f, 12, -30, -8), p(0.2f, 0.06f, -0.08f, 12, -30, -8)),
                k(11, rest, rest));

        // Launch crouch: coiled, arms swept down and back like jets about to fire.
        loop("launch_crouch", 0.01f, 0.02f, 0.5f, false,
                k(0, rest, rest),
                k(4, p(0.1f, -0.46f, -0.22f, -34, -10, -4), p(0.1f, -0.46f, -0.22f, -34, -10, -4)));

        // Fire dash: palms pointed down and back as the jets, hands just in view, a little shake.
        loop("fire_dash", 0.012f, 0.025f, 1.3f, false,
                k(0, rest, rest),
                k(3, p(0.14f, -0.42f, -0.16f, -40, -12, -4), p(0.14f, -0.42f, -0.16f, -40, -12, -4)));
        loop("jet_glide", 0.01f, 0.03f, 0.35f, false,
                k(0, rest, rest),
                k(5, p(0.12f, -0.36f, -0.1f, -32, -8, -4), p(0.12f, -0.36f, -0.1f, -32, -8, -4)));

        // Leap: both fists driven up out of the crouch, held while airborne.
        loop("leap_air", 0.0f, 0.015f, 0.4f, false,
                k(0, p(0.1f, -0.4f, -0.2f, -30, -8, 0), p(0.1f, -0.4f, -0.2f, -30, -8, 0)),
                snap(4, p(-0.06f, 0.24f, 0.08f, 30, 10, 4), p(-0.06f, 0.24f, 0.08f, 30, 10, 4)));

        // Dragon's Breath: hands cupped together under the mouth, pulsing with the flow.
        loop("breath_stance", 0.01f, 0.025f, 0.9f, false,
                k(0, rest, rest),
                k(4, p(-0.4f, -0.08f, 0.02f, 14, 36, 22), p(-0.4f, -0.08f, 0.02f, 14, 36, 22)));

        // Fire Arc kick: arms flung wide through the spin.
        once("fire_arc_kick",
                k(0, rest, rest),
                k(4, p(0.3f, 0.04f, -0.1f, 6, -40, -8), p(0.3f, 0.04f, -0.1f, 6, -40, -8)),
                k(11, p(0.32f, 0.0f, -0.12f, 2, -44, -8), p(0.32f, 0.0f, -0.12f, 2, -44, -8)),
                k(15, rest, rest));

        // Fire Arc whip: the right arm wound across the body, then whipped round flat.
        once("fire_arc_whip",
                k(0, rest, rest),
                k(4, p(-0.46f, 0.04f, 0.0f, 4, 46, 6), p(0.06f, -0.12f, -0.06f, -6, 0, 0)),
                snap(7, p(0.36f, 0.04f, 0.16f, 4, -42, -6), p(0.0f, -0.08f, 0.0f, -2, 6, 0)),
                k(12, rest, rest));

        // Fireball charge: the hands circle in front of you, gathering the ball between them.
        loop("charge_stance", 0.05f, 0.05f, 0.3f, true,
                k(0, rest, rest),
                k(5, p(-0.26f, -0.08f, 0.1f, 4, 22, 8), p(-0.26f, -0.08f, 0.1f, 4, 22, 8)));
        once("charge_release",
                k(0, p(-0.26f, -0.08f, 0.1f, 4, 22, 8), p(-0.26f, -0.08f, 0.1f, 4, 22, 8)),
                snap(3, p(-0.3f, 0.06f, 0.52f, 6, 24, 6), p(-0.3f, 0.06f, 0.52f, 6, 24, 6)),
                k(8, p(-0.26f, 0.04f, 0.42f, 4, 20, 4), p(-0.26f, 0.04f, 0.42f, 4, 20, 4)),
                k(12, rest, rest));

        // Firestorm: fists chambered low, rising as it builds; then thrown high and hammered down.
        loop("firestorm_stance", 0.01f, 0.03f, 0.25f, false,
                k(0, rest, rest),
                k(6, p(0.04f, -0.36f, -0.14f, -22, 0, 0), p(0.04f, -0.36f, -0.14f, -22, 0, 0)),
                k(40, p(0.0f, -0.2f, -0.06f, -10, 4, 0), p(0.0f, -0.2f, -0.06f, -10, 4, 0)));
        once("firestorm_stomp",
                k(0, p(0.0f, -0.2f, -0.06f, -10, 4, 0), p(0.0f, -0.2f, -0.06f, -10, 4, 0)),
                k(6, p(0.0f, 0.36f, 0.0f, 40, 10, 6), p(0.0f, 0.36f, 0.0f, 40, 10, 6)),
                snap(9, p(0.26f, -0.5f, 0.2f, -40, -16, -6), p(0.26f, -0.5f, 0.2f, -40, -16, -6)),
                k(16, p(0.22f, -0.44f, 0.16f, -34, -12, -4), p(0.22f, -0.44f, 0.16f, -34, -12, -4)),
                k(20, rest, rest));

        // Meteor Slam: fists locked together out in front, held through the dive.
        loop("meteor_dive", 0.0f, 0.02f, 1.6f, false,
                k(0, rest, rest),
                snap(4, p(-0.42f, 0.0f, 0.3f, 10, 34, 14), p(-0.42f, 0.0f, 0.3f, 10, 34, 14)));
        // Landing: a fist planted in the crater, the other arm out for balance, then rising.
        once("meteor_land",
                k(0, p(-0.42f, 0.0f, 0.3f, 10, 34, 14), p(-0.42f, 0.0f, 0.3f, 10, 34, 14)),
                snap(2, p(-0.1f, -0.6f, 0.26f, -50, 10, 0), p(0.26f, -0.12f, -0.06f, -6, -30, -6)),
                k(10, p(-0.08f, -0.56f, 0.22f, -46, 8, 0), p(0.24f, -0.14f, -0.06f, -8, -28, -6)),
                k(20, rest, rest));

        // Lightning: the hands trace slow separating circles; then two fingers driven at the target.
        loop("lightning_stance", 0.09f, 0.07f, 0.18f, true,
                k(0, rest, rest),
                k(6, p(-0.12f, -0.02f, 0.06f, 4, 10, 4), p(-0.12f, -0.02f, 0.06f, 4, 10, 4)));
        once("lightning_release",
                k(0, p(-0.12f, -0.02f, 0.06f, 4, 10, 4), p(-0.12f, -0.02f, 0.06f, 4, 10, 4)),
                snap(3, p(-0.26f, 0.1f, 0.56f, 10, 20, 0), p(0.22f, -0.62f, -0.3f, -40, -30, 0)),
                k(7, p(-0.24f, 0.08f, 0.5f, 8, 18, 0), p(0.2f, -0.58f, -0.28f, -38, -28, 0)),
                k(10, rest, rest));
        mirror("lightning_release_left", "lightning_release");

        // Fire Shield: forearms crossed upright in front of the face.
        loop("shield_guard", 0.008f, 0.015f, 0.4f, false,
                k(0, rest, rest),
                k(3, p(-0.44f, 0.16f, 0.04f, 24, 38, 30), p(-0.4f, 0.12f, 0.08f, 22, 36, 30)));
        once("shield_burst",
                k(0, rest, rest),
                k(3, p(-0.3f, -0.16f, -0.06f, 0, 30, 10), p(-0.3f, -0.16f, -0.06f, 0, 30, 10)),
                snap(6, p(0.36f, 0.1f, 0.06f, 10, -46, -10), p(0.36f, 0.1f, 0.06f, 10, -46, -10)),
                k(12, rest, rest));

        // Flame Parry: the lead forearm snaps up and outward across the face.
        once("flame_parry",
                k(0, rest, rest),
                snap(3, p(-0.32f, 0.3f, 0.1f, 34, 30, 34), p(0.04f, -0.14f, -0.08f, -8, 0, 0)),
                snap(5, p(0.12f, 0.3f, 0.06f, 34, -10, 20), p(0.04f, -0.14f, -0.08f, -8, 0, 0)),
                k(9, rest, rest));

        // Wall of Fire: palms low to the ground, then both arms heaved up.
        once("wall_rise",
                k(0, rest, rest),
                k(5, p(0.0f, -0.5f, 0.14f, -40, 0, 0), p(0.0f, -0.5f, 0.14f, -40, 0, 0)),
                snap(10, p(0.06f, 0.36f, 0.16f, 44, 4, 0), p(0.06f, 0.36f, 0.16f, 44, 4, 0)),
                k(15, rest, rest));

        // Combustion: two fingers pressed to the brow; then the arms flung back as the beam fires.
        loop("combustion_focus", 0.005f, 0.01f, 0.7f, false,
                k(0, rest, rest),
                k(6, p(-0.34f, 0.36f, -0.12f, 40, 34, 24), p(-0.34f, 0.36f, -0.12f, 40, 34, 24)));
        once("combustion_release",
                k(0, p(-0.34f, 0.36f, -0.12f, 40, 34, 24), p(-0.34f, 0.36f, -0.12f, 40, 34, 24)),
                snap(2, p(0.3f, -0.62f, -0.3f, -46, -34, 0), p(0.3f, -0.62f, -0.3f, -46, -34, 0)),
                k(9, p(0.28f, -0.58f, -0.28f, -42, -30, 0), p(0.28f, -0.58f, -0.28f, -42, -30, 0)),
                k(14, rest, rest));

        // Heat Siphon: reach out wide with open hands, haul the heat into the chest, again and
        // again (one breath every 1.6 s, like the body animation).
        float[] reach = p(0.28f, 0.0f, 0.05f, 6, -30, -8);
        float[] haul = p(0.0f, -0.3f, -0.28f, -14, 10, 0);
        MOVES.put("siphon_draw", new Move(new Key[] {
                k(0, rest, rest),
                k(4, reach, reach),
                k(10, p(0.3f, 0.02f, 0.06f, 7, -32, -8), p(0.3f, 0.02f, 0.06f, 7, -32, -8)),
                k(20, haul, haul),
                k(28, p(0.0f, -0.32f, -0.3f, -15, 11, 0), p(0.0f, -0.32f, -0.3f, -15, 11, 0)),
                k(36, reach, reach)}, true, 0, 0, 0, false, 4f));
        // Release: fists sink to the chest, then both arms are thrown out wide at once.
        once("siphon_release",
                k(0, haul, haul),
                k(2, p(0.0f, -0.36f, -0.32f, -16, 12, 0), p(0.0f, -0.36f, -0.32f, -16, 12, 0)),
                snap(4, p(0.4f, -0.1f, 0.08f, 0, -50, -14), p(0.4f, -0.1f, 0.08f, 0, -50, -14)),
                k(10, p(0.38f, -0.12f, 0.06f, 0, -46, -12), p(0.38f, -0.12f, 0.06f, 0, -46, -12)),
                k(15, rest, rest));

        // ---- kicking legs (yaw: + swings the foot in toward the middle) ----
        // Front kick: knee chambered up, then the foot snapped straight out at the target.
        legs("fire_blast_front_kick", true,
                lk(0, leg(0, 40, 0, 100)),
                lk(2, leg(1, 92, 4, 115)),
                lsnap(4, leg(1, 102, 4, 0)),
                lk(7, leg(1, 96, 4, 15)),
                lk(10, leg(0, 50, 0, 90)));
        // Roundhouse: chambered out to the side, then whipped round across the front.
        legs("fire_blast_roundhouse", false,
                lk(0, leg(0, 60, -50, 90)),
                lk(3, leg(1, 94, -55, 105)),
                lsnap(6, leg(1, 100, 8, 0)),
                lk(8, leg(1, 96, 16, 10)),
                lk(11, leg(0, 60, 10, 90)));
        // Air Kick: tucked, then stamped down and out.
        legs("air_kick", true,
                lk(0, leg(0, 50, 0, 100)),
                lk(3, leg(1, 82, 2, 120)),
                lsnap(5, leg(1, 76, 2, 0)),
                lk(9, leg(1, 72, 2, 10)),
                lk(11, leg(0, 50, 0, 90)));
        // Fire Arc: the spinning roundhouse sweeps across the view.
        legs("fire_arc_kick", true,
                lk(0, leg(0, 60, -50, 80)),
                lk(4, leg(1, 94, -60, 40)),
                lsnap(8, leg(1, 100, 18, 0)),
                lk(11, leg(1, 96, 26, 5)),
                lk(15, leg(0, 60, 20, 80)));
        // Fire Sweep: the leg swept round low and straight.
        legs("fire_sweep", true,
                lk(0, leg(0, 60, -40, 60)),
                lk(4, leg(0.85f, 82, -60, 5)),
                lk(11, leg(0.85f, 84, 22, 0)),
                lk(15, leg(0, 60, 20, 60)));
        // Firestorm stomp: knee driven high into view, then stamped down out of it.
        legs("firestorm_stomp", true,
                lk(0, leg(0, 40, 0, 110)),
                lk(5, leg(1, 96, 2, 125)),
                lsnap(8, leg(0.3f, 20, 2, 0)),
                lk(11, leg(0, 10, 0, 0)));
    }
}
