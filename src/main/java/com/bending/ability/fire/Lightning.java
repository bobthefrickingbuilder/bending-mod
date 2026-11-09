package com.bending.ability.fire;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import javax.annotation.Nullable;

import com.bending.ability.Abilities;
import com.bending.ability.Ability;
import com.bending.data.ModAttachments;
import com.bending.server.BendingRules;
import com.bending.server.CastHandler;
import com.bending.ability.AbilitySlot;
import com.bending.ability.BendingUtil;
import com.bending.ability.CastType;
import com.bending.data.BendingData;
import com.bending.data.Element;
import com.bending.entity.LightningArcEntity;
import com.bending.particle.ModParticles;
import com.bending.sound.ModSounds;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Slot 4: charge for 1.5 s to store 4 bolts of lightning, crackling around you. While they last,
 * Fire Blast throws a bolt instead of a fireball: it hits the first target along your line of sight
 * for 20 and chains to up to 2 more within 5 blocks for 10 each. (A redirected bolt hits for 32/16.)
 * In rain or water it chains to everything nearby, including you.
 *
 * Calm mind: you can't start charging within 3 s of being hurt, and getting hit while
 * charging breaks it (6 damage backlash).
 *
 * Visuals: arcs crackle between your hands and out around them while charging, getting wilder as the
 * charge builds. The bolt is drawn in vanilla lightning's style with forks and a spray of loose sparks,
 * shooting straight out of your hands in the direction you aim.
 */
public class Lightning extends Ability {
    private static final int FULL_CHARGE = 30;
    private static final double RANGE = 30.0;
    private static final float DAMAGE = 32f;
    private static final float CHAIN_DAMAGE = 16f;
    private static final double CHAIN_RANGE = 5.0;
    private static final int CHAIN_TARGETS = 2;
    /** You can't start charging this soon after being hurt (3 s). */
    public static final int CALM_TICKS = 60;
    public static final float BACKLASH = 6f;
    /** Damage to yourself when the bolt chains back through rain or water. */
    private static final float SELF_SHOCK = 8f;

    public Lightning() {
        super("lightning", Element.FIRE, AbilitySlot.ULTIMATE, CastType.CHARGE);
    }

    @Override
    public float chiCost(int chargeTicks) {
        return 50f;
    }

    @Override
    public int maxChargeTicks() {
        return FULL_CHARGE;
    }

    @Override
    public int minChargeTicks() {
        return FULL_CHARGE; // lightning needs the full charge
    }

    @Override
    public boolean canStart(ServerPlayer player, BendingData data) {
        if (player.level().getGameTime() - data.lastHurtTime < CALM_TICKS) {
            player.displayClientMessage(Component.translatable("message.bending.calm_mind").withStyle(ChatFormatting.AQUA), true);
            return false;
        }
        return true;
    }

    /** Where your two hands are while charging: in front of your chest, a little apart. */
    private static Vec3[] hands(ServerPlayer player) {
        Vec3 forward = Vec3.directionFromRotation(0, player.getYRot());
        Vec3 side = new Vec3(-forward.z, 0, forward.x);
        Vec3 chest = player.position().add(0, player.getBbHeight() * 0.62, 0).add(forward.scale(0.45));
        return new Vec3[] {chest.add(side.scale(0.38)), chest.subtract(side.scale(0.38))};
    }

    @Override
    public void onHoldTick(ServerPlayer player, BendingData data, int ticks, float power) {
        if (ticks == 1) {
            com.bending.ability.BendingAnimations.playForCast(player, data, com.bending.ability.BendingAnimations.CHARGE_STANCE);
        }
        ServerLevel level = player.serverLevel();
        RandomSource random = level.getRandom();
        float progress = Math.min(1f, ticks / (float) FULL_CHARGE);
        Vec3[] hands = hands(player);

        // Arcs snap between your hands, more often as the charge builds.
        if (ticks % 2 == 0 || random.nextFloat() < progress) {
            spawnArc(level, jitter(hands[0], random, 0.08), jitter(hands[1], random, 0.08), 0.3f + 0.15f * progress, 2);
        }
        // Arcs lash out from your hands into the air around you; wilder and longer near full charge.
        int outward = (int) (progress * 2.5f) + (random.nextFloat() < 0.4f ? 1 : 0);
        for (int i = 0; i < outward; i++) {
            Vec3 from = hands[random.nextInt(2)];
            Vec3 dir = new Vec3(random.nextGaussian(), random.nextGaussian() * 0.7, random.nextGaussian()).normalize();
            spawnArc(level, from, from.add(dir.scale(0.6 + random.nextDouble() * (0.6 + 1.2 * progress))), 0.25f + 0.2f * progress, 2);
        }
        // Loose static hanging around your hands and body: dirtier the closer you are to full charge.
        int loose = 4 + (int) (progress * 12);
        for (Vec3 hand : hands) {
            level.sendParticles(ModParticles.LIGHTNING_ARC.get(), hand.x, hand.y, hand.z, loose, 0.18, 0.18, 0.18, 0.08 + 0.1 * progress);
        }
        level.sendParticles(ModParticles.LIGHTNING_ARC.get(), player.getX(), player.getY() + 1.0, player.getZ(),
                (int) (progress * 8), 0.45, 0.6, 0.45, 0.05);

        if (ticks == FULL_CHARGE) {
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    ModSounds.LIGHTNING_READY.get(), SoundSource.PLAYERS, 0.8f, 1.6f);
        } else if (ticks % 6 == 0) {
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    ModSounds.LIGHTNING_CHARGE.get(), SoundSource.PLAYERS, 0.5f, 0.8f + progress);
        }
        BendingUtil.chargeBar(player, progress);
    }

    @Override
    public void onChargeCancelled(ServerPlayer player, BendingData data) {
        ServerLevel level = player.serverLevel();
        RandomSource random = level.getRandom();
        Vec3[] hands = hands(player);
        for (int i = 0; i < 3; i++) {
            Vec3 from = hands[i % 2];
            spawnArc(level, from, jitter(from, random, 1.2), 0.35f, 3);
        }
        level.sendParticles(ModParticles.LIGHTNING_ARC.get(), player.getX(), player.getY() + 1.2, player.getZ(), 30, 0.4, 0.4, 0.4, 0.15);
        BendingUtil.fizzle(player);
    }

    /** Hit while charging: the charge breaks and the energy lashes back into you. */
    @Override
    public void onHurtWhileActive(ServerPlayer player, BendingData data) {
        onChargeCancelled(player, data);
        data.pendingBacklash = BACKLASH;
        player.displayClientMessage(Component.translatable("message.bending.lightning_backlash").withStyle(ChatFormatting.RED), true);
    }

    @Override
    public boolean cancelWhenHurt() {
        return true;
    }

    /** A full charge holds this many bolts; each Fire Blast fires one instead of a fireball. */
    public static final int STORED_BOLTS = 4;
    private static final float STORED_DAMAGE = 20f;
    private static final float STORED_CHAIN_DAMAGE = 10f;

    @Override
    public void onRelease(ServerPlayer player, BendingData data, int ticks, float power) {
        // The charge isn't thrown: it stays in you, crackling, until you spend it with Fire Blast.
        data.lightningCharges = STORED_BOLTS;
        ServerLevel level = player.serverLevel();
        Vec3[] hands = hands(player);
        for (Vec3 hand : hands) {
            level.sendParticles(ModParticles.LIGHTNING_ARC.get(), hand.x, hand.y, hand.z, 30, 0.3, 0.3, 0.3, 0.2);
        }
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                ModSounds.LIGHTNING_READY.get(), SoundSource.PLAYERS, 1.0f, 1.2f);
        showStored(player, data);
    }

    /** Fire Blast with lightning stored: one bolt instead of a fireball. */
    public static void fireStored(ServerPlayer player, BendingData data, float power) {
        data.lightningCharges--;
        fireBolt(player, data, power, STORED_DAMAGE, STORED_CHAIN_DAMAGE);
        showStored(player, data);
    }

    private static void showStored(ServerPlayer player, BendingData data) {
        player.displayClientMessage(Component.translatable("message.bending.lightning_stored", data.lightningCharges)
                .withStyle(ChatFormatting.AQUA), true);
    }

    /**
     * Every tick: while lightning is stored, a cloud of electricity hangs around you. It thins out
     * with each bolt you spend.
     */
    public static void tickStored(ServerPlayer player, BendingData data) {
        if (data.lightningCharges <= 0) {
            return;
        }
        ServerLevel level = player.serverLevel();
        RandomSource random = level.getRandom();
        float f = data.lightningCharges / (float) STORED_BOLTS;
        Vec3[] hands = hands(player);
        for (Vec3 hand : hands) {
            level.sendParticles(ModParticles.LIGHTNING_ARC.get(), hand.x, hand.y, hand.z, 1 + (int) (f * 4), 0.2, 0.2, 0.2, 0.06);
        }
        level.sendParticles(ModParticles.LIGHTNING_ARC.get(), player.getX(), player.getY() + 1.0, player.getZ(),
                (int) (f * 7), 0.4 + 0.35 * f, 0.6 + 0.3 * f, 0.4 + 0.35 * f, 0.04);
        // Arcs snapping between your hands and out into the air.
        if (random.nextFloat() < 0.35f * f) {
            spawnArc(level, jitter(hands[0], random, 0.1), jitter(hands[1], random, 0.1), 0.2f + 0.15f * f, 2);
        }
        if (random.nextFloat() < 0.3f * f) {
            Vec3 from = hands[random.nextInt(2)];
            Vec3 dir = new Vec3(random.nextGaussian(), random.nextGaussian() * 0.7, random.nextGaussian()).normalize();
            spawnArc(level, from, from.add(dir.scale(0.5 + random.nextDouble() * (0.5 + 1.2 * f))), 0.2f + 0.15f * f, 2);
        }
        if (level.getGameTime() % 10 == 0) {
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    ModSounds.LIGHTNING_CHARGE.get(), SoundSource.PLAYERS, 0.15f + 0.2f * f, 1.4f);
        }
    }

    /** A redirected bolt: full strength. */
    private static void fireBolt(ServerPlayer player, BendingData data, float power) {
        fireBolt(player, data, power, DAMAGE, CHAIN_DAMAGE);
    }

    /** Fires a bolt along the bender's line of sight: a stored bolt, or lightning they redirected. */
    private static void fireBolt(ServerPlayer player, BendingData data, float power, float damage, float chainDamage) {
        ServerLevel level = player.serverLevel();
        Vec3 look = player.getLookAngle();
        Vec3 start = player.getEyePosition();
        Vec3 end = start.add(look.scale(RANGE));
        BlockHitResult blockHit = level.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        Vec3 stop = blockHit.getType() == HitResult.Type.MISS ? end : blockHit.getLocation();
        EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(level, player, start, stop,
                player.getBoundingBox().expandTowards(look.scale(RANGE)).inflate(1.0),
                e -> e instanceof LivingEntity && e != player && !e.isSpectator() && e.isPickable());

        // From the leading hand, slightly in front so it doesn't fill your screen.
        Vec3 hand = start.add(look.scale(0.9)).add(0, -0.3, 0);
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                ModSounds.LIGHTNING_STRIKE.get(), SoundSource.PLAYERS, 1.2f, 1.0f);
        // Recoil sparks off your hands.
        level.sendParticles(ModParticles.LIGHTNING_ARC.get(), hand.x, hand.y, hand.z, 40, 0.25, 0.25, 0.25, 0.25);

        @Nullable LivingEntity first = entityHit != null && entityHit.getEntity() instanceof LivingEntity l ? l : null;
        if (first == null) {
            // Missed every creature: the bolt cracks into the ground or fades out.
            spawnArc(level, hand, stop, 1.0f, 6);
            if (blockHit.getType() != HitResult.Type.MISS) {
                BendingUtil.scatterFire(level, BlockPos.containing(stop), 1);
            }
            return;
        }
        Vec3 firstAt = centre(first);
        spawnArc(level, hand, firstAt, 1.0f, 6);
        strike(player, first, damage * power, hand);

        // Chain: normally to the 2 nearest others; in rain or water, to everything (you included).
        boolean wet = player.isInWaterOrRain() || first.isInWaterOrRain();
        List<LivingEntity> nearby = new ArrayList<>(BendingUtil.livingAround(player, firstAt, CHAIN_RANGE));
        nearby.remove(first);
        nearby.sort(Comparator.comparingDouble(e -> e.distanceToSqr(firstAt)));
        List<LivingEntity> chained = wet ? nearby : nearby.subList(0, Math.min(CHAIN_TARGETS, nearby.size()));
        for (LivingEntity target : chained) {
            spawnArc(level, firstAt, centre(target), 0.7f, 5);
            strike(player, target, chainDamage * power, firstAt);
        }
        if (wet && player.distanceToSqr(firstAt) <= CHAIN_RANGE * CHAIN_RANGE * 4) {
            spawnArc(level, firstAt, centre(player), 0.7f, 5);
            data.pendingBacklash = SELF_SHOCK;
        }
    }

    /** Lightning hitting someone: a firebender redirecting it catches it instead of taking the hit. */
    private static void strike(ServerPlayer caster, LivingEntity target, float damage, Vec3 from) {
        if (target instanceof ServerPlayer catcher && catcher != caster
                && tryRedirect(catcher, catcher.getData(ModAttachments.BENDING), from)) {
            return;
        }
        BendingUtil.damage(caster, target, damage, 0);
    }

    /** Refund for catching lightning. */
    private static final float REDIRECT_REFUND = 30f;
    /** After a catch, further lightning in this window is just absorbed (vanilla bolts hit several ticks). */
    private static final int REDIRECT_GRACE = 10;

    /**
     * Lightning Redirection: if the player knows it and is holding Fire Shield facing the lightning
     * ({@code from}, or any direction for a bolt from the sky when null), the lightning runs through
     * their body and fires back out along where they look. Refunds chi. True if it was caught.
     */
    public static boolean tryRedirect(ServerPlayer player, BendingData data, @Nullable Vec3 from) {
        if (!FireTechniques.has(data, FireTechniques.LIGHTNING_REDIRECTION)
                || data.activeSlot < 0 || data.activeSlot >= BendingData.SLOTS
                || CastHandler.abilityIn(data, data.activeSlot) != Abilities.FIRE_SHIELD
                || data.activeTicks < CastHandler.TAP_THRESHOLD) {
            return false;
        }
        if (from != null && from.subtract(player.getEyePosition()).normalize().dot(player.getLookAngle()) < 0.2) {
            return false; // it has to come at you from the front
        }
        long now = player.level().getGameTime();
        if (now - data.lastRedirect < REDIRECT_GRACE) {
            return true;
        }
        data.lastRedirect = now;
        data.addChi(REDIRECT_REFUND);
        ServerLevel level = player.serverLevel();
        // The current runs in through one arm, down through the stomach and out the other.
        Vec3[] hands = hands(player);
        Vec3 stomach = player.position().add(0, player.getBbHeight() * 0.45, 0);
        spawnArc(level, hands[1], stomach, 0.5f, 4);
        spawnArc(level, stomach, hands[0], 0.5f, 4);
        level.sendParticles(ModParticles.LIGHTNING_ARC.get(), stomach.x, stomach.y, stomach.z, 40, 0.3, 0.5, 0.3, 0.12);
        player.displayClientMessage(Component.translatable("message.bending.redirected").withStyle(ChatFormatting.AQUA), true);
        fireBolt(player, data, BendingRules.power(player, Element.FIRE));
        return true;
    }

    private static Vec3 centre(Entity e) {
        return e.position().add(0, e.getBbHeight() * 0.6, 0);
    }

    private static Vec3 jitter(Vec3 p, RandomSource random, double amount) {
        return p.add(random.nextGaussian() * amount, random.nextGaussian() * amount, random.nextGaussian() * amount);
    }

    private static void spawnArc(ServerLevel level, Vec3 from, Vec3 to, float thickness, int life) {
        level.addFreshEntity(new LightningArcEntity(level, from, to, thickness, life));
    }
}
