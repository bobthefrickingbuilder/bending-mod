package com.bending.ability;

/** How an ability's key is used. */
public enum CastType {
    /** Fires once on key press. */
    TAP,
    /** Channels while the key is held, draining chi each tick. */
    HOLD,
    /** Builds up while held, fires on release. */
    CHARGE,
    /** A quick tap does one thing, holding does another (e.g. Fire Shield / Flame Burst). */
    TAP_OR_HOLD
}
