package com.bending;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Settings, in config/bending-common.toml (gameplay) and config/bending-client.toml (effects).
 * Block damage also still follows the mobGriefing game rule.
 */
public final class BendingConfig {
    public static final ModConfigSpec COMMON_SPEC;
    public static final ModConfigSpec CLIENT_SPEC;

    public static final ModConfigSpec.DoubleValue DAMAGE_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue CHI_COST_MULTIPLIER;
    public static final ModConfigSpec.BooleanValue PVP;
    public static final ModConfigSpec.BooleanValue EXPLOSIONS_BREAK_BLOCKS;
    public static final ModConfigSpec.BooleanValue PLACE_FIRE;
    public static final ModConfigSpec.BooleanValue MELT_SNOW_AND_ICE;

    public static final ModConfigSpec.DoubleValue SCREEN_SHAKE;
    public static final ModConfigSpec.BooleanValue IMPACT_FLASHES;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.push("combat");
        DAMAGE_MULTIPLIER = b.comment("Multiplies all bending damage.").defineInRange("damageMultiplier", 1.0, 0.0, 10.0);
        CHI_COST_MULTIPLIER = b.comment("Multiplies the chi cost of every ability (0 = free).").defineInRange("chiCostMultiplier", 1.0, 0.0, 10.0);
        PVP = b.comment("Whether bending can hurt other players.").define("pvp", true);
        b.pop();
        b.push("griefing");
        EXPLOSIONS_BREAK_BLOCKS = b.comment("Whether bending explosions (Fireball Charge, Meteor Slam craters, Flame Burst, Combustion Beam) break blocks.")
                .define("explosionsBreakBlocks", true);
        PLACE_FIRE = b.comment("Whether firebending leaves fire blocks burning where it lands.").define("placeFire", true);
        MELT_SNOW_AND_ICE = b.comment("Whether firebending melts snow and ice.").define("meltSnowAndIce", true);
        b.pop();
        COMMON_SPEC = b.build();

        ModConfigSpec.Builder c = new ModConfigSpec.Builder();
        SCREEN_SHAKE = c.comment("Camera shake from big impacts (0 = off).").defineInRange("screenShake", 1.0, 0.0, 2.0);
        IMPACT_FLASHES = c.comment("Brief light flashes on explosions and lightning.").define("impactFlashes", true);
        CLIENT_SPEC = c.build();
    }

    private BendingConfig() {}
}
