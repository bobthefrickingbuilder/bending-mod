package com.bending.ability;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.annotation.Nullable;

import com.bending.ability.fire.CombustionBeam;
import com.bending.ability.fire.FireArc;
import com.bending.ability.fire.FlameParry;
import com.bending.ability.fire.JetPropulsion;
import com.bending.ability.fire.Lightning;
import com.bending.ability.fire.MeteorSlam;
import com.bending.ability.fire.FireBlast;
import com.bending.ability.fire.FireJetDash;
import com.bending.ability.fire.FireShield;
import com.bending.ability.fire.Firestorm;
import com.bending.data.Element;

/** All abilities in the mod, by id. */
public final class Abilities {
    private static final Map<String, Ability> BY_ID = new LinkedHashMap<>();

    // ---- Firebending ----
    public static final Ability FIRE_BLAST = register(new FireBlast());
    public static final Ability FIRE_SHIELD = register(new FireShield());
    public static final Ability FIRE_JET_DASH = register(new FireJetDash());
    public static final Ability FIRESTORM = register(new Firestorm());
    public static final Ability COMBUSTION_BEAM = register(new CombustionBeam());
    public static final Ability FIRE_ARC = register(new FireArc());
    public static final Ability FLAME_PARRY = register(new FlameParry());
    public static final Ability JET_PROPULSION = register(new JetPropulsion());
    public static final Ability LIGHTNING = register(new Lightning());
    public static final Ability METEOR_SLAM = register(new MeteorSlam());

    private Abilities() {}

    private static Ability register(Ability ability) {
        BY_ID.put(ability.id(), ability);
        return ability;
    }

    @Nullable
    public static Ability get(String id) {
        return BY_ID.get(id);
    }

    public static Collection<Ability> all() {
        return Collections.unmodifiableCollection(BY_ID.values());
    }

    /** Whether an element has its starting kit implemented and can be chosen. */
    public static boolean isImplemented(Element element) {
        return defaultLoadout(element) != null;
    }

    /**
     * Starting abilities, one per slot, in slot order (a null entry = slot starts empty).
     * Null if the element isn't built yet.
     */
    @Nullable
    public static Ability[] defaultLoadout(Element element) {
        return switch (element) {
            // Slots 1 and 3 start empty: Fire Blast (left click) and Fire Jet Dash (Ctrl+Space) are innate.
            case FIRE -> new Ability[] { null, FIRE_SHIELD, null, FIRESTORM };
            default -> null;
        };
    }

    /** The element's basic attack, cast with left click in bending mode. */
    @Nullable
    public static Ability innateAttack(@Nullable Element element) {
        return element == Element.FIRE ? FIRE_BLAST : null;
    }

    /** The element's basic movement, cast with Ctrl+Space in bending mode (hold Ctrl to channel). */
    @Nullable
    public static Ability innateMobility(@Nullable Element element) {
        return element == Element.FIRE ? FIRE_JET_DASH : null;
    }

    /** The element's aerial attack, cast by tapping Shift while airborne in bending mode. */
    @Nullable
    public static Ability innateSlam(@Nullable Element element) {
        return element == Element.FIRE ? METEOR_SLAM : null;
    }
}
