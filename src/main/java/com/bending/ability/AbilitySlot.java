package com.bending.ability;

/** The four loadout slots, plus the innate attack. Every ability belongs to exactly one. */
public enum AbilitySlot {
    PROJECTILE,
    /** Every slot-2 ability must block in some way: shields, walls, parries. */
    DEFENSE,
    MOBILITY,
    ULTIMATE,
    /** Not a loadout slot: always available in bending mode (basic attack on left click, basic movement on Ctrl+Space). */
    INNATE
}
