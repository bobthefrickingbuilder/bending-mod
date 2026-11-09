package com.bending.ability;

import com.bending.network.ImpactPayload;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/** Camera shake and screen flash for everyone near a big impact. Server side. */
public final class ImpactFx {
    public static final int FIRE = 0xFF9A3C;
    public static final int BLUE_FIRE = 0x6FB8FF;
    public static final int LIGHTNING = 0xDDEBFF;

    private ImpactFx() {}

    /**
     * @param strength 0..1 at the centre (1 = a big crater); fades out to nothing at {@code reach} blocks
     */
    public static void impact(ServerLevel level, Vec3 at, float strength, double reach, int color) {
        for (ServerPlayer player : level.players()) {
            double d = player.position().distanceTo(at);
            if (d > reach) {
                continue;
            }
            float falloff = (float) (1 - d / reach);
            float s = strength * falloff * falloff;
            if (s > 0.03f) {
                PacketDistributor.sendToPlayer(player, new ImpactPayload(s, s, color));
            }
        }
    }

    public static int fireColor(net.minecraft.world.entity.Entity bender) {
        return com.bending.ability.fire.FireTechniques.blue(bender) ? BLUE_FIRE : FIRE;
    }
}
