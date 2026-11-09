package com.bending.ability;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.annotation.Nullable;

import com.bending.ability.fire.CombustionBeam;
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

    /** Starting abilities, one per slot, in slot order. Null if the element isn't built yet. */
    @Nullable
    public static Ability[] defaultLoadout(Element element) {
        return switch (element) {
            case FIRE -> new Ability[] { FIRE_BLAST, FIRE_SHIELD, FIRE_JET_DASH, FIRESTORM };
            default -> null;
        };
    }
}
