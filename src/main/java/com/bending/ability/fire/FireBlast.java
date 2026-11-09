package com.bending.ability.fire;

import com.bending.sound.ModSounds;

import com.bending.ability.Ability;
import com.bending.ability.AbilitySlot;
import com.bending.ability.CastType;
import com.bending.data.BendingData;
import com.bending.data.Element;
import com.bending.entity.FireBlastEntity;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.Vec3;

/** Slot 1: a quick fire punch, alternating hands. */
public class FireBlast extends Ability {
    /** Two direct hits kill a normal 20-health mob (zombie, skeleton, creeper), day or night. */
    public static final float DAMAGE = 12f;
    public static final float SPEED = 3.0f;

    public FireBlast() {
        super("fire_blast", Element.FIRE, AbilitySlot.PROJECTILE, CastType.TAP);
    }

    @Override
    public float chiCost(int chargeTicks) {
        return 6f;
    }

    @Override
    public void onTap(ServerPlayer player, BendingData data, float power) {
        Vec3 look = player.getLookAngle();
        Vec3 side = look.cross(new Vec3(0, 1, 0)).normalize().scale(data.altHand ? 0.3 : -0.3);
        data.altHand = !data.altHand;

        FireBlastEntity blast = new FireBlastEntity(player.level(), player, DAMAGE * power);
        Vec3 start = player.getEyePosition().add(look.scale(0.6)).add(side).add(0, -0.2, 0);
        blast.setPos(start.x, start.y, start.z);
        blast.shoot(look.x, look.y, look.z, SPEED, 0f);
        player.level().addFreshEntity(blast);

        player.swing(data.altHand ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND, true);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                ModSounds.FIRE_BLAST_CAST.get(), SoundSource.PLAYERS, 0.5f, 1.3f + player.getRandom().nextFloat() * 0.2f);
    }
}
