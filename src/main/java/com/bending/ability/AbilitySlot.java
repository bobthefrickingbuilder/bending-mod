package com.bending.ability;

/** The four loadout slots. Every ability belongs to exactly one. */
public enum AbilitySlot {
    PROJECTILE,
    /** Every slot-2 ability must block in some way: shields, walls, parries. */
    DEFENSE,
    MOBILITY,
    ULTIMATE
}
