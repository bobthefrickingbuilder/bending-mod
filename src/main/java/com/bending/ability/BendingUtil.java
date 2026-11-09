package com.bending.ability;

import com.bending.ability.fire.FireFx;
import com.bending.particle.ModParticles;
import com.bending.sound.ModSounds;

import java.util.List;
import java.util.Optional;

import javax.annotation.Nullable;

import com.bending.server.BendingService;
import com.bending.ability.fire.FireTechniques;
import com.bending.data.BendingData;
import com.bending.data.ModAttachments;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Mth;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.SimpleExplosionDamageCalculator;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Shared helpers for ability effects. All server-side. */
public final class BendingUtil {
    private BendingUtil() {}

    /**
     * Damages a target on behalf of a bender and awards bending XP if it lands.
     * @return true if the damage was applied
     */
    public static boolean damage(ServerPlayer bender, LivingEntity target, float amount, int fireSeconds) {
        BendingData data = bender.getData(ModAttachments.BENDING);
        long now = bender.level().getGameTime();
        boolean dancing = FireTechniques.has(data, FireTechniques.DANCING_DRAGON);
        if (dancing) {
            if (now - data.comboLastHit > COMBO_WINDOW) {
                data.comboStacks = 0; // the rhythm broke
            }
            amount *= 1f + COMBO_BONUS * data.comboStacks;
        }
        if (fireSeconds > 0 && FireTechniques.has(data, FireTechniques.BLUE_FIRE)) {
            fireSeconds = Math.round(fireSeconds * FireTechniques.BLUE_FIRE_BURN);
        }
        // Skip vanilla's half-second damage immunity: without cooldowns, follow-up hits land inside
        // that window and would otherwise lose most of their damage.
        target.invulnerableTime = 0;
        boolean hurt = target.hurt(bender.damageSources().playerAttack(bender), amount);
        if (hurt) {
            if (fireSeconds > 0) {
                target.igniteForSeconds(fireSeconds);
            }
            BendingService.addXp(bender, Math.max(1, Math.round(amount)));
            if (dancing && data.comboLastHit != now) {
                // One stack per tick at most, so a single blast hitting a crowd counts once.
                data.comboStacks = Math.min(COMBO_MAX, data.comboStacks + 1);
                data.comboLastHit = now;
                if (data.comboStacks >= 2 && data.activeSlot == -1) {
                    bender.displayClientMessage(Component.translatable("message.bending.combo", data.comboStacks,
                            Math.round(COMBO_BONUS * 100 * data.comboStacks)).withColor(0xFFB43A), true);
                }
            }
        }
        return hurt;
    }

    /** Dancing Dragon: +5% damage per back-to-back hit, up to 5 stacks; 3 s without a hit resets it. */
    private static final float COMBO_BONUS = 0.05f;
    private static final int COMBO_MAX = 5;
    private static final int COMBO_WINDOW = 60;

    /** Living entities within {@code radius} of {@code center}, excluding the bender. */
    public static List<LivingEntity> livingAround(ServerPlayer bender, Vec3 center, double radius) {
        double r2 = radius * radius;
        return bender.level().getEntitiesOfClass(LivingEntity.class,
                AABB.ofSize(center, radius * 2, radius * 2, radius * 2),
                e -> e != bender && e.isAlive() && !e.isSpectator() && e.distanceToSqr(center) <= r2);
    }

    /** Pushes an entity away from a point. */
    public static void knockAway(LivingEntity target, Vec3 from, double strength) {
        target.knockback(strength, from.x - target.getX(), from.z - target.getZ());
        target.hurtMarked = true;
    }

    /** Destroys enemy projectiles near a point (arrows, fireballs, other benders' attacks). */
    public static int destroyProjectiles(ServerLevel level, Entity owner, Vec3 center, double radius) {
        List<Projectile> projectiles = level.getEntitiesOfClass(Projectile.class,
                AABB.ofSize(center, radius * 2, radius * 2, radius * 2),
                p -> p.getOwner() != owner && p.distanceToSqr(center) <= radius * radius);
        for (Projectile p : projectiles) {
            level.sendParticles(ModParticles.FIRE_SMOKE.get(),
                    p.getX(), p.getY(), p.getZ(), 6, 0.1, 0.1, 0.1, 0.02);
            p.discard();
        }
        return projectiles.size();
    }

    /**
     * Only breaks blocks: a vanilla explosion with entity damage and knockback switched off,
     * so it never hurts or launches the bender (abilities handle their own damage).
     * Plays the normal explosion sound and particles. Follows the mobGriefing game rule.
     */
    public static void terrainBlast(ServerLevel level, @Nullable Entity source, Vec3 pos, float power) {
        level.explode(source, null, TERRAIN_ONLY, pos.x, pos.y, pos.z, power, false, Level.ExplosionInteraction.MOB,
                ParticleTypes.EXPLOSION, ParticleTypes.EXPLOSION_EMITTER, ModSounds.FIRE_EXPLOSION);
    }

    private static final ExplosionDamageCalculator TERRAIN_ONLY =
            new SimpleExplosionDamageCalculator(true, false, Optional.of(0f), Optional.empty());

    /**
     * A fiery explosion: damages and pushes back everything nearby except the bender (and {@code exclude}).
     * If {@code terrainPower} is above 0 it also blasts the terrain (a creeper is 3, TNT is 4).
     */
    public static void fireBurst(ServerPlayer bender, Vec3 center, double radius, float damage, double knockback,
                                 int fireSeconds, @Nullable Entity exclude, float terrainPower) {
        ServerLevel level = bender.serverLevel();
        if (terrainPower > 0f) {
            terrainBlast(level, bender, center, terrainPower);
        } else {
            level.sendParticles(ParticleTypes.EXPLOSION, center.x, center.y, center.z, 1, 0, 0, 0, 0);
            level.playSound(null, center.x, center.y, center.z, ModSounds.FIRE_EXPLOSION.get(), SoundSource.PLAYERS, 0.5f, 1.5f);
        }
        level.sendParticles(FireFx.core(bender), center.x, center.y, center.z, 3, 0.2, 0.2, 0.2, 0.0);
        level.sendParticles(FireFx.wisp(bender), center.x, center.y, center.z, 30, radius * 0.2, radius * 0.2, radius * 0.2, 0.12);
        level.sendParticles(FireFx.spark(bender), center.x, center.y, center.z, 10, 0.2, 0.2, 0.2, 0.3);
        level.sendParticles(FireFx.ember(bender), center.x, center.y, center.z, 6, 0.3, 0.3, 0.3, 0.15);
        level.sendParticles(ModParticles.FIRE_SMOKE.get(), center.x, center.y, center.z, 4, 0.3, 0.3, 0.3, 0.02);
        for (LivingEntity target : livingAround(bender, center, radius)) {
            if (target != exclude) {
                damage(bender, target, damage, fireSeconds);
                knockAway(target, center, knockback);
            }
        }
    }

    /**
     * Sets a few fire blocks around a spot, if the mobGriefing game rule allows it.
     * The first try is the origin itself; the rest land on random nearby spots.
     */
    public static void scatterFire(Level level, BlockPos origin, int count) {
        if (!level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) {
            return;
        }
        RandomSource random = level.getRandom();
        for (int i = 0; i < count; i++) {
            BlockPos pos = i == 0 ? origin
                    : origin.offset(random.nextInt(3) - 1, random.nextInt(3) - 1, random.nextInt(3) - 1);
            if (BaseFireBlock.canBePlacedAt(level, pos, Direction.UP)) {
                level.setBlockAndUpdate(pos, BaseFireBlock.getState(level, pos));
            }
        }
    }

    /**
     * Melts snow and ice within {@code radius} blocks: snow vanishes, ice turns to water
     * (or evaporates in the Nether). Packed and blue ice are too dense to melt.
     * Follows the mobGriefing game rule, like fire placement.
     * @return how many blocks melted
     */
    public static int meltSnowAndIce(ServerLevel level, BlockPos center, int radius) {
        if (!level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) {
            return 0;
        }
        boolean evaporate = level.dimensionType().ultraWarm();
        int melted = 0;
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-radius, -radius, -radius), center.offset(radius, radius, radius))) {
            if (pos.distSqr(center) > radius * radius + 1) {
                continue; // keep the melted area roughly round
            }
            BlockState state = level.getBlockState(pos);
            BlockState result;
            if (state.is(Blocks.SNOW) || state.is(Blocks.SNOW_BLOCK) || state.is(Blocks.POWDER_SNOW)) {
                result = Blocks.AIR.defaultBlockState();
            } else if (state.is(Blocks.ICE) || state.is(Blocks.FROSTED_ICE)) {
                result = evaporate ? Blocks.AIR.defaultBlockState() : Blocks.WATER.defaultBlockState();
            } else {
                continue;
            }
            level.setBlockAndUpdate(pos, result);
            level.sendParticles(ModParticles.STEAM.get(), pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 2, 0.3, 0.3, 0.3, 0.01);
            melted++;
        }
        if (melted > 0) {
            level.playSound(null, center, ModSounds.FIRE_MELT.get(), SoundSource.BLOCKS, 0.7f, 1.2f);
        }
        return melted;
    }

    /** Shows a 10-segment charge meter above the hotbar. Turns yellow when full. */
    public static void chargeBar(ServerPlayer player, float fraction) {
        int filled = Math.round(Mth.clamp(fraction, 0f, 1f) * 10);
        MutableComponent bar = Component.literal("■".repeat(filled))
                .withStyle(fraction >= 1f ? ChatFormatting.YELLOW : ChatFormatting.GOLD)
                .append(Component.literal("■".repeat(10 - filled)).withStyle(ChatFormatting.DARK_GRAY));
        player.displayClientMessage(bar, true);
    }

    /** Puff of smoke and a hiss: a fire charge that fizzled out. */
    public static void fizzle(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        level.sendParticles(ModParticles.FIRE_SMOKE.get(), player.getX(), player.getEyeY() - 0.3, player.getZ(), 10, 0.3, 0.3, 0.3, 0.02);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.FIRE_FIZZLE.get(), SoundSource.PLAYERS, 0.5f, 1.4f);
        player.displayClientMessage(Component.empty(), true);
    }

    /** A horizontal ring of particles. */
    public static void ring(ServerLevel level, ParticleOptions particle, Vec3 center, double radius, int count) {
        for (int i = 0; i < count; i++) {
            double angle = (Math.PI * 2 * i) / count;
            level.sendParticles(particle,
                    center.x + Math.cos(angle) * radius, center.y, center.z + Math.sin(angle) * radius,
                    1, 0.05, 0.1, 0.05, 0.01);
        }
    }
}
