package com.bending.ability.fire;

import com.bending.ability.Ability;
import com.bending.ability.AbilitySlot;
import com.bending.ability.CastType;
import com.bending.data.BendingData;
import com.bending.data.Element;
import com.bending.entity.FireArcEntity;
import com.bending.sound.ModSounds;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.Vec3;

/**
 * Slot 1: a spinning kick that sends out a wide crescent of fire. It sweeps 18 blocks forward
 * and passes through enemies, burning everything it crosses.
 */
public class FireArc extends Ability {
    public static final float DAMAGE = 10f;

    public FireArc() {
        super("fire_arc", Element.FIRE, AbilitySlot.PROJECTILE, CastType.TAP);
    }

    @Override
    public float chiCost(int chargeTicks) {
        return 12f;
    }

    @Override
    public void onTap(ServerPlayer player, BendingData data, float power) {
        Vec3 look = player.getLookAngle();
        // The crescent lies flat (left to right), whatever angle you aim at.
        float yaw = player.getYRot() * Mth.DEG_TO_RAD;
        Vec3 side = new Vec3(-Mth.cos(yaw), 0, -Mth.sin(yaw));
        Vec3 start = player.position().add(0, 1.0, 0).add(look.scale(1.0));

        player.level().addFreshEntity(new FireArcEntity(player.level(), player, start, look, side, DAMAGE * power)
                .withRange(FireArcEntity.RANGE * com.bending.skill.SkillStats.ability(player, id(), com.bending.skill.SkillStat.RANGE)));
        // Alternate the spinning kick and the arm whip.
        data.fireArcWhip = !data.fireArcWhip;
        com.bending.ability.BendingAnimations.play(player, data.fireArcWhip ? com.bending.ability.BendingAnimations.FIRE_ARC_WHIP : com.bending.ability.BendingAnimations.FIRE_ARC_KICK);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                ModSounds.FIRE_ARC_CAST.get(), SoundSource.PLAYERS, 0.8f, 0.8f + player.getRandom().nextFloat() * 0.2f);
    }
}
