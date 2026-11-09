package com.bending.ability.fire;

import com.bending.ability.Ability;
import com.bending.ability.AbilitySlot;
import com.bending.ability.BendingUtil;
import com.bending.ability.CastType;
import com.bending.data.BendingData;
import com.bending.data.Element;
import com.bending.entity.ChargeOrbEntity;
import com.bending.entity.FireBlastEntity;
import com.bending.sound.ModSounds;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.Vec3;

/**
 * Slot 1 (Projectile): hold to build a fireball between your hands, release to throw it.
 * It explodes on impact; the longer the charge, the bigger the ball, the damage and the blast.
 * A quick tap-and-release throws a small one; a full 2 s charge throws a house-wrecker.
 */
public class FireballCharge extends Ability {
    private static final int FULL_CHARGE = 40;
    private static final int MIN_CHARGE = 6;
    private static final float MIN_DAMAGE = 14f;
    private static final float FULL_DAMAGE = 34f;
    private static final float MIN_SCALE = 1.2f;
    private static final float FULL_SCALE = 2.4f;
    private static final double MIN_SPLASH = 2.2;
    private static final double FULL_SPLASH = 4.5;
    /** Terrain explosion strength (a creeper is 3, TNT is 4). */
    private static final float MIN_TERRAIN = 2.0f;
    private static final float FULL_TERRAIN = 4.0f;
    private static final float SPEED = 2.0f;
    /** About 60 blocks of flight. */
    private static final int LIFE = 30;

    public FireballCharge() {
        super("fireball_charge", Element.FIRE, AbilitySlot.PROJECTILE, CastType.CHARGE);
    }

    private static float fraction(int ticks) {
        return Mth.clamp(ticks / (float) FULL_CHARGE, 0f, 1f);
    }

    @Override
    public float chiCost(int chargeTicks) {
        return 14f + 18f * fraction(chargeTicks); // 14 to 32
    }

    @Override
    public int maxChargeTicks() {
        return FULL_CHARGE;
    }

    @Override
    public int minChargeTicks() {
        return MIN_CHARGE;
    }

    @Override
    public void onHoldTick(ServerPlayer player, BendingData data, int ticks, float power) {
        if (data.activeTicks == 1) {
            com.bending.ability.BendingAnimations.playForCast(player, data, com.bending.ability.BendingAnimations.CHARGE_STANCE);
        }
        ServerLevel level = player.serverLevel();
        float progress = fraction(ticks);
        ChargeOrbEntity orb = data.chargeOrb instanceof ChargeOrbEntity existing && !existing.isRemoved() ? existing : null;
        if (orb == null) {
            orb = new ChargeOrbEntity(level, player);
            level.addFreshEntity(orb);
            data.chargeOrb = orb;
        }
        orb.update(player, progress);
        Vec3 focus = orb.position();
        // Flame is pulled in and wrapped around the ball as it grows.
        for (int i = 0; i < 3; i++) {
            Vec3 dir = new Vec3(player.getRandom().nextGaussian(), player.getRandom().nextGaussian(), player.getRandom().nextGaussian())
                    .normalize().scale(0.7 + progress * 0.5);
            Vec3 p = focus.add(dir);
            Vec3 inward = dir.scale(-0.1);
            level.sendParticles(FireFx.wisp(player), p.x, p.y, p.z, 0, inward.x, inward.y, inward.z, 1.0);
        }
        if (ticks == FULL_CHARGE) {
            level.playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.FIREBALL_CHARGE_READY.get(), SoundSource.PLAYERS, 0.7f, 1.2f);
        } else if (ticks < FULL_CHARGE && ticks % 6 == 0) {
            level.playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.FIREBALL_CHARGE_CHARGE.get(), SoundSource.PLAYERS, 0.6f, 0.8f + progress * 0.6f);
        }
        BendingUtil.chargeBar(player, progress);
    }

    @Override
    public void onChargeCancelled(ServerPlayer player, BendingData data) {
        removeOrb(data);
        BendingUtil.fizzle(player);
    }

    @Override
    public void onRelease(ServerPlayer player, BendingData data, int ticks, float power) {
        com.bending.ability.BendingAnimations.play(player, com.bending.ability.BendingAnimations.CHARGE_RELEASE);
        removeOrb(data);
        float f = fraction(ticks);
        float size = com.bending.skill.SkillStats.ability(player, id(), com.bending.skill.SkillStat.SIZE);
        Vec3 look = player.getLookAngle();
        FireBlastEntity ball = new FireBlastEntity(player.level(), player, Mth.lerp(f, MIN_DAMAGE, FULL_DAMAGE) * power)
                .configure(Mth.lerp(f, MIN_SCALE, FULL_SCALE) * size, Mth.lerp(f, MIN_SPLASH, FULL_SPLASH) * size,
                        Mth.lerp(f, MIN_TERRAIN, FULL_TERRAIN), 3 + Math.round(5 * f), LIFE);
        Vec3 start = ChargeOrbEntity.focus(player.getEyePosition(), look).add(look.scale(0.4));
        ball.setPos(start.x, start.y, start.z);
        ball.shoot(look.x, look.y, look.z, SPEED, 0f);
        player.level().addFreshEntity(ball);
        player.swing(InteractionHand.MAIN_HAND, true);
        // A heavy throw pushes you back a little.
        player.setDeltaMovement(player.getDeltaMovement().add(look.scale(-0.25 * f)));
        player.hurtMarked = true;
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                ModSounds.FIREBALL_CHARGE_THROW.get(), SoundSource.PLAYERS, 0.8f + 0.4f * f, 0.9f - 0.3f * f);
        player.displayClientMessage(net.minecraft.network.chat.Component.empty(), true);
    }

    private static void removeOrb(BendingData data) {
        if (data.chargeOrb != null) {
            data.chargeOrb.discard();
            data.chargeOrb = null;
        }
    }
}
