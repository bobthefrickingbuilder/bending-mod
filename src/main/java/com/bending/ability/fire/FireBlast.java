package com.bending.ability.fire;

import com.bending.sound.ModSounds;

import com.bending.ability.Ability;
import com.bending.ability.BendingAnimations;
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

/** Innate firebending attack (left click in bending mode): a cycle of fire punches and kicks. */
public class FireBlast extends Ability {
    /** Two direct hits kill a normal 20-health mob (zombie, skeleton, creeper), day or night. */
    public static final float DAMAGE = 12f;
    public static final float SPEED = 3.0f;

    public FireBlast() {
        super("fire_blast", Element.FIRE, AbilitySlot.INNATE, CastType.TAP);
    }

    @Override
    public float chiCost(int chargeTicks) {
        return 6f;
    }

    /**
     * One step of the Fire Blast cycle: which limb throws it, and where on the body the fireball
     * leaves from (sideways to your right, and down from your eyes).
     */
    private record Strike(String animation, double right, double down, boolean punch, InteractionHand hand) {}

    /** Jab, cross, front kick, roundhouse; then round again. */
    private static final Strike[] CYCLE = {
            new Strike(BendingAnimations.FIRE_BLAST_RIGHT, 0.3, 0.2, true, InteractionHand.MAIN_HAND),
            new Strike(BendingAnimations.FIRE_BLAST_LEFT, -0.3, 0.2, true, InteractionHand.OFF_HAND),
            new Strike(BendingAnimations.FIRE_BLAST_FRONT_KICK, 0.15, 0.85, false, InteractionHand.MAIN_HAND),
            new Strike(BendingAnimations.FIRE_BLAST_ROUNDHOUSE, -0.45, 0.6, false, InteractionHand.OFF_HAND),
    };
    private static final float AIR_KICK_EXTRA_CHI = 6f;
    private static final float AIR_KICK_DAMAGE = 11f;

    private static void airKick(ServerPlayer player, BendingData data, float power) {
        Vec3 look = player.getLookAngle();
        // Aimed where you look, but always angled at least 35 degrees down.
        Vec3 flat = new Vec3(look.x, 0, look.z);
        flat = flat.lengthSqr() > 1e-4 ? flat.normalize() : Vec3.directionFromRotation(0, player.getYRot());
        double down = Math.min(look.y, -0.7);
        Vec3 dir = new Vec3(flat.x, down, flat.z).normalize();
        float yaw = player.getYRot() * net.minecraft.util.Mth.DEG_TO_RAD;
        Vec3 side = new Vec3(-net.minecraft.util.Mth.cos(yaw), 0, -net.minecraft.util.Mth.sin(yaw));
        Vec3 start = player.position().add(0, 0.4, 0).add(flat.scale(0.6));
        player.level().addFreshEntity(new com.bending.entity.FireArcEntity(player.level(), player, start, dir, side,
                AIR_KICK_DAMAGE * power).withRange(14).withGroundBurst());
        // The kick's recoil checks your fall and pops you up a little.
        Vec3 v = player.getDeltaMovement();
        player.setDeltaMovement(v.x * 0.6, Math.max(v.y, 0.28), v.z * 0.6);
        player.hurtMarked = true;
        player.fallDistance = 0;
        BendingAnimations.play(player, BendingAnimations.AIR_KICK);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                ModSounds.FIRE_ARC_CAST.get(), SoundSource.PLAYERS, 0.9f, 0.8f + player.getRandom().nextFloat() * 0.15f);
    }

    /** Pause this long between shots and the cycle starts over from the jab. */
    private static final int COMBO_RESET_TICKS = 25;

    @Override
    public boolean canStart(ServerPlayer player, BendingData data) {
        return !FireSweep.sweeping(data);
    }

    @Override
    public void onTap(ServerPlayer player, BendingData data, float power) {
        // Ctrl held on the ground: the low spinning leg sweep instead of a strike.
        if (data.crouchReady && player.onGround() && data.lightningCharges <= 0 && data.trySpend(FireSweep.EXTRA_CHI)) {
            FireSweep.start(player, data, power);
            data.fireBlastStep = 0;
            return;
        }
        // Ctrl held in mid-air: a stamping kick that drives a crescent of fire down at the ground ahead.
        if (data.ctrlHeld && !player.onGround() && !player.isInWater() && data.lightningCharges <= 0
                && data.trySpend(AIR_KICK_EXTRA_CHI)) {
            airKick(player, data, power);
            data.fireBlastStep = 0;
            return;
        }
        long now = player.level().getGameTime();
        if (now - data.fireBlastLastShot > COMBO_RESET_TICKS) {
            data.fireBlastStep = 0;
        }
        Strike strike = CYCLE[data.fireBlastStep];
        data.fireBlastStep = (data.fireBlastStep + 1) % CYCLE.length;
        data.fireBlastLastShot = now;

        if (data.lightningCharges > 0) {
            Lightning.fireStored(player, data);
            BendingAnimations.play(player, strike.hand() == InteractionHand.MAIN_HAND
                    ? BendingAnimations.LIGHTNING_RELEASE : BendingAnimations.LIGHTNING_RELEASE_LEFT);
            return;
        }

        Vec3 look = player.getLookAngle();
        Vec3 right = look.cross(new Vec3(0, 1, 0)).normalize();
        FireBlastEntity blast = new FireBlastEntity(player.level(), player, DAMAGE * power);
        Vec3 start = player.getEyePosition().add(look.scale(0.6)).add(right.scale(strike.right())).add(0, -strike.down(), 0);
        blast.setPos(start.x, start.y, start.z);
        blast.shoot(look.x, look.y, look.z, SPEED * com.bending.skill.SkillStats.ability(player, id(), com.bending.skill.SkillStat.SPEED), 0f);
        player.level().addFreshEntity(blast);

        if (strike.punch()) {
            player.swing(strike.hand(), true);
        }
        BendingAnimations.play(player, strike.animation());
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                ModSounds.FIRE_BLAST_CAST.get(), SoundSource.PLAYERS, 0.5f,
                (strike.punch() ? 1.3f : 1.05f) + player.getRandom().nextFloat() * 0.2f);
    }
}
