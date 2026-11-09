package com.bending.ability.fire;

import com.bending.ability.Ability;
import com.bending.ability.AbilitySlot;
import com.bending.ability.BendingUtil;
import com.bending.ability.CastType;
import com.bending.data.BendingData;
import com.bending.data.Element;
import com.bending.particle.ModParticles;
import com.bending.sound.ModSounds;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Slot 2 (Defense): a split-second block with a fiery counter. For 0.4 s after the tap, any hit
 * from the front is blocked completely. If something hits you in that window, you burn it back
 * (10 damage, knockback, sets it on fire) and get 15 chi back. Miss the timing and you get nothing.
 */
public class FlameParry extends Ability {
    public static final int WINDOW_TICKS = 8;
    private static final double FRONT_CONE = 0.2;
    private static final float COUNTER_DAMAGE = 10f;
    private static final float CHI_REFUND = 15f;
    private static final double COUNTER_RANGE = 8.0;

    public FlameParry() {
        super("flame_parry", Element.FIRE, AbilitySlot.DEFENSE, CastType.TAP);
    }

    @Override
    public float chiCost(int chargeTicks) {
        return 10f;
    }

    @Override
    public void onTap(ServerPlayer player, BendingData data, float power) {
        data.parryUntil = player.level().getGameTime() + WINDOW_TICKS;
        data.parryCountered = false;
        ServerLevel level = player.serverLevel();
        // A quick flare of flame in front of your hands.
        Vec3 look = player.getLookAngle();
        Vec3 front = player.getEyePosition().add(look.scale(0.9)).add(0, -0.3, 0);
        level.sendParticles(FireFx.wisp(player), front.x, front.y, front.z, 8, 0.35, 0.35, 0.35, 0.02);
        level.sendParticles(FireFx.spark(player), front.x, front.y, front.z, 4, 0.3, 0.3, 0.3, 0.05);
        player.swing(InteractionHand.MAIN_HAND, true);
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                ModSounds.FLAME_PARRY_READY.get(), SoundSource.PLAYERS, 0.6f, 1.5f);
    }

    /**
     * Called for every hit on a bender. Returns true if a parry is up and the hit came from the
     * front, in which case the hit is blocked (and the first one gets countered).
     */
    public static boolean tryParry(ServerPlayer player, BendingData data, DamageSource source) {
        if (player.level().getGameTime() > data.parryUntil || source.is(DamageTypeTags.BYPASSES_SHIELD)) {
            return false;
        }
        Vec3 from = source.getSourcePosition();
        if (from == null || from.subtract(player.getEyePosition()).normalize().dot(player.getLookAngle()) < FRONT_CONE) {
            return false;
        }
        ServerLevel level = player.serverLevel();
        Vec3 front = player.getEyePosition().add(player.getLookAngle().scale(0.9));
        level.sendParticles(FireFx.core(player), front.x, front.y, front.z, 2, 0.1, 0.1, 0.1, 0);
        level.sendParticles(FireFx.spark(player), front.x, front.y, front.z, 10, 0.2, 0.2, 0.2, 0.25);

        if (!data.parryCountered) {
            data.parryCountered = true;
            data.addChi(CHI_REFUND);
            // Burn back whoever (or whatever fired the projectile) attacked you.
            if (source.getEntity() instanceof LivingEntity attacker && attacker != player
                    && attacker.distanceTo(player) <= COUNTER_RANGE) {
                Vec3 to = attacker.position().add(0, attacker.getBbHeight() / 2, 0);
                Vec3 step = to.subtract(front);
                for (double d = 0; d < 1; d += 0.12) {
                    Vec3 p = front.add(step.scale(d));
                    level.sendParticles(FireFx.wisp(player), p.x, p.y, p.z, 0, step.x, step.y, step.z, 0.05);
                }
                level.sendParticles(FireFx.wisp(player), to.x, to.y, to.z, 15, 0.3, 0.4, 0.3, 0.08);
                BendingUtil.damage(player, attacker, COUNTER_DAMAGE * com.bending.server.BendingRules.power(player, Element.FIRE), 4);
                BendingUtil.knockAway(attacker, player.position(), 1.0);
            }
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    ModSounds.FLAME_PARRY_COUNTER.get(), SoundSource.PLAYERS, 1.0f, 1.0f);
        }
        return true;
    }
}
