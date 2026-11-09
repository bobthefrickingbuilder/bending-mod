package com.bending.ability.fire;

import com.bending.particle.ModParticles;
import com.bending.sound.ModSounds;

import com.bending.ability.Ability;
import com.bending.ability.AbilitySlot;
import com.bending.ability.BendingUtil;
import com.bending.ability.CastType;
import com.bending.data.BendingData;
import com.bending.data.Element;
import com.bending.server.CastHandler;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Slot 2. Hold: a disc of flame in front of you that burns up projectiles and scorches attackers.
 * Tap: Flame Burst, a ring of fire that knocks back everything nearby.
 */
public class FireShield extends Ability {
    private static final double SHIELD_DISTANCE = 1.5;
    private static final double SHIELD_RADIUS = 1.4;
    private static final double BURST_RADIUS = 4.0;
    /** Flame Burst's explosion strength against terrain, centered on you (a creeper is 3). */
    private static final float BURST_TERRAIN_POWER = 2.0f;
    /** How wide the blocking cone is: 0.2 ≈ 78° either side of where you look. */
    private static final double FRONT_CONE = 0.2;
    /** Share of melee/explosion damage blocked from the front. */
    private static final float MELEE_BLOCK = 0.75f;

    public FireShield() {
        super("fire_shield", Element.FIRE, AbilitySlot.DEFENSE, CastType.TAP_OR_HOLD);
    }

    /**
     * While the shield is up, hits from the front are blocked: projectiles completely,
     * melee and explosions mostly. Damage from behind, and from fire or lava, still gets through.
     */
    @Override
    public float modifyIncomingDamage(ServerPlayer player, BendingData data, DamageSource source, float amount) {
        if (data.activeTicks < CastHandler.TAP_THRESHOLD || source.is(DamageTypeTags.BYPASSES_SHIELD)) {
            return amount;
        }
        Vec3 from = source.getSourcePosition();
        if (from == null) {
            return amount;
        }
        Vec3 toSource = from.subtract(player.getEyePosition()).normalize();
        if (toSource.dot(player.getLookAngle()) < FRONT_CONE) {
            return amount;
        }
        ServerLevel level = player.serverLevel();
        Vec3 center = player.getEyePosition().add(player.getLookAngle().scale(SHIELD_DISTANCE));
        level.sendParticles(ModParticles.FIRE_WISP.get(), center.x, center.y, center.z, 15, 0.4, 0.4, 0.4, 0.1);
        level.playSound(null, center.x, center.y, center.z, ModSounds.FIRE_SHIELD_BLOCK.get(), SoundSource.PLAYERS, 0.6f, 1.6f);
        return source.is(DamageTypeTags.IS_PROJECTILE) ? 0f : amount * (1f - MELEE_BLOCK);
    }

    @Override
    public float chiCost(int chargeTicks) {
        return 15f; // Flame Burst
    }

    @Override
    public float chiPerTick() {
        return 0.4f; // 8 chi per second while holding the shield
    }

    @Override
    public void onHoldTick(ServerPlayer player, BendingData data, int ticks, float power) {
        ServerLevel level = player.serverLevel();
        Vec3 look = player.getLookAngle();
        Vec3 center = player.getEyePosition().add(look.scale(SHIELD_DISTANCE));
        Vec3 right = look.cross(new Vec3(0, 1, 0)).normalize();
        if (right.lengthSqr() < 1.0E-4) {
            right = new Vec3(1, 0, 0); // looking straight up or down
        }
        Vec3 up = right.cross(look).normalize();

        // A spinning disc of flame facing where you look.
        for (int i = 0; i < 14; i++) {
            double angle = player.getRandom().nextDouble() * Math.PI * 2;
            double r = SHIELD_RADIUS * Math.sqrt(player.getRandom().nextDouble());
            Vec3 p = center.add(right.scale(Math.cos(angle) * r)).add(up.scale(Math.sin(angle) * r));
            level.sendParticles(ModParticles.FIRE_WISP.get(), p.x, p.y, p.z, 1, 0, 0, 0, 0.01);
        }

        if (BendingUtil.destroyProjectiles(level, player, center, SHIELD_RADIUS + 1.0) > 0) {
            level.playSound(null, center.x, center.y, center.z, ModSounds.FIRE_SHIELD_DEFLECT.get(), SoundSource.PLAYERS, 0.5f, 1.5f);
        }

        if (ticks % 10 == 0) {
            for (LivingEntity target : BendingUtil.livingAround(player, center, SHIELD_RADIUS + 0.6)) {
                BendingUtil.damage(player, target, 4f * power, 2); // shield contact, every 0.5 s
            }
            level.playSound(null, center.x, center.y, center.z, ModSounds.FIRE_SHIELD_LOOP.get(), SoundSource.PLAYERS, 0.6f, 1f);
        }
    }

    @Override
    public void onTap(ServerPlayer player, BendingData data, float power) {
        ServerLevel level = player.serverLevel();
        Vec3 pos = player.position();
        BendingUtil.terrainBlast(level, player, pos.add(0, 0.5, 0), BURST_TERRAIN_POWER);
        for (double r = 1; r <= BURST_RADIUS; r += 1) {
            BendingUtil.ring(level, ModParticles.FIRE_WISP.get(), pos.add(0, 0.6, 0), r, (int) (r * 12));
        }
        for (LivingEntity target : BendingUtil.livingAround(player, pos, BURST_RADIUS)) {
            BendingUtil.damage(player, target, 8f * power, 2); // Flame Burst
            BendingUtil.knockAway(target, pos, 1.0);
        }
        level.playSound(null, pos.x, pos.y, pos.z, ModSounds.FLAME_BURST.get(), SoundSource.PLAYERS, 1f, 0.8f);
    }
}
