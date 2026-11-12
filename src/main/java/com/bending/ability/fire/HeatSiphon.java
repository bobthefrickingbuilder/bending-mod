package com.bending.ability.fire;

import java.util.List;

import com.bending.ability.Ability;
import com.bending.ability.AbilitySlot;
import com.bending.ability.BendingAnimations;
import com.bending.ability.BendingDamage;
import com.bending.ability.BendingUtil;
import com.bending.ability.CastType;
import com.bending.ability.ImpactFx;
import com.bending.data.BendingData;
import com.bending.data.Element;
import com.bending.entity.FireBlastEntity;
import com.bending.skill.SkillStat;
import com.bending.skill.SkillStats;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Fireball;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Slot 2 (Defense): hold to draw every flame around you into your body. Fire blocks and lit
 * campfires go out, burning creatures (you included) are put out, and fire projectiles flying near
 * you are swallowed. Fire that hits you while you hold is absorbed instead of hurting you. Let go
 * and all the heat you took in bursts out of you in a ring: the more you absorbed, the bigger and
 * harder the blast.
 */
public class HeatSiphon extends Ability {
    private static final double RADIUS = 9.0;
    /** Heat stops building here. */
    public static final float MAX_HEAT = 60f;
    /** Heat gathered from the air each tick even with no fire nearby, so it always does something. */
    private static final float AMBIENT_HEAT = 0.15f;
    private static final float HEAT_PER_BLOCK = 1.5f;
    private static final float HEAT_PER_BURNING = 3f;
    private static final float HEAT_PER_PROJECTILE = 6f;
    private static final int MAX_BLOCKS_PER_SCAN = 24;
    /** Magma and lava are drawn within this share of the radius, the nearest few at a time. */
    private static final double HEAT_REACH = 0.75;
    private static final int MAX_HEAT_BLOCKS = 12;
    /** Heat per block per draw (every other tick). */
    private static final float MAGMA_HEAT = 0.5f, LAVA_HEAT = 1.2f;
    /** Drawing time before magma cools to cobblestone (2 s) and a lava source to obsidian (8 s). */
    private static final int MAGMA_TICKS = 40, LAVA_TICKS = 160;
    /** Chi per point of heat taken in past full. */
    private static final float CHI_PER_HEAT = 0.5f;
    private static final double MIN_BLAST = 3.5, MAX_BLAST = 10.0;
    private static final float MIN_DAMAGE = 5f, MAX_DAMAGE = 22f;

    public HeatSiphon() {
        super("heat_siphon", Element.FIRE, AbilitySlot.DEFENSE, CastType.CHARGE);
    }

    @Override
    public float chiCost(int chargeTicks) {
        return 9f;
    }

    @Override
    public float startChiCost() {
        return 5f;
    }

    @Override
    public int maxChargeTicks() {
        return 100;
    }

    @Override
    public int minChargeTicks() {
        return 4;
    }

    @Override
    public float castMoveSpeed() {
        return 0.6f;
    }

    @Override
    public void onStart(ServerPlayer player, BendingData data, float power) {
        data.siphonHeat = 0f;
        data.siphonDraw.clear(); // every block's timer starts over on a new hold
    }

    private double radius(ServerPlayer player) {
        return RADIUS * SkillStats.ability(player, id(), SkillStat.RANGE);
    }

    @Override
    public void onHoldTick(ServerPlayer player, BendingData data, int ticks, float power) {
        if (data.activeTicks == 1) {
            BendingAnimations.playForCast(player, data, BendingAnimations.SIPHON_DRAW);
        }
        ServerLevel level = player.serverLevel();
        Vec3 core = player.position().add(0, 1.0, 0);
        double r = radius(player);
        // Heat from real sources (not the air): once you are full it turns into chi instead.
        float fromSources = 0f;

        if (ticks % 2 == 0) {
            fromSources += drawBlocks(level, player, data, core, r);
        }

        // Burning creatures (you too) are put out and their flame pulled in.
        AABB box = AABB.ofSize(core, r * 2, r * 2, r * 2);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box,
                e -> e.isOnFire() && e.distanceToSqr(core) <= r * r && !e.fireImmune() || e == player && e.isOnFire())) {
            e.clearFire();
            stream(level, player, e.position().add(0, e.getBbHeight() * 0.5, 0), core, 14);
            fromSources += HEAT_PER_BURNING;
        }

        // Fire projectiles flying nearby are swallowed.
        List<Projectile> projectiles = level.getEntitiesOfClass(Projectile.class, box,
                p -> p.getOwner() != player && p.distanceToSqr(core) <= r * r
                        && (p instanceof FireBlastEntity || p instanceof Fireball || p instanceof AbstractArrow a && a.isOnFire()));
        for (Projectile p : projectiles) {
            stream(level, player, p.position(), core, 20);
            if (p instanceof AbstractArrow arrow) {
                arrow.clearFire();
                fromSources += HEAT_PER_BURNING;
            } else {
                p.discard();
                fromSources += HEAT_PER_PROJECTILE;
            }
            level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.6f, 1.6f);
        }
        if (player.isOnFire()) {
            player.clearFire();
        }

        // Fill up first; whatever the sources give past full becomes chi.
        float room = MAX_HEAT - data.siphonHeat;
        float total = fromSources + AMBIENT_HEAT;
        data.siphonHeat = Math.min(MAX_HEAT, data.siphonHeat + total);
        float overflow = Math.max(0f, fromSources - Math.max(0f, room - AMBIENT_HEAT));
        if (overflow > 0f && data.getChi() < data.getMaxChi()) {
            data.addChi(overflow * CHI_PER_HEAT * SkillStats.ability(player, id(), SkillStat.REGEN));
            if (ticks % 4 == 0) {
                level.sendParticles(FireFx.spark(player), core.x, core.y + 0.3, core.z, 6, 0.25, 0.35, 0.25, 0.02);
            }
        }
        float fill = data.siphonHeat / MAX_HEAT;

        // The pull: a storm of embers converging on you from all around (only a few flames).
        int embers = 36 + Math.round(24 * fill);
        for (int i = 0; i < embers; i++) {
            double a = player.getRandom().nextDouble() * Math.PI * 2;
            double rr = r * (0.35 + 0.6 * player.getRandom().nextDouble());
            double y = core.y - 1.0 + player.getRandom().nextDouble() * 2.6;
            Vec3 from = new Vec3(core.x + Math.cos(a) * rr, y, core.z + Math.sin(a) * rr);
            pull(level, player, from, core);
        }
        if (ticks % 2 == 0) {
            for (int i = 0; i < 3; i++) {
                double a = ticks * 0.35 + i * 2.1;
                double rr = r * 0.5;
                double x = core.x + Math.cos(a) * rr, z = core.z + Math.sin(a) * rr;
                Vec3 v = core.subtract(x, core.y, z).scale(0.08);
                level.sendParticles(FireFx.wisp(player), x, core.y, z, 0, v.x, v.y, v.z, 1.0);
            }
        }
        // The stored heat glows in the chest.
        level.sendParticles(FireFx.core(player), core.x, core.y + 0.2, core.z, 1 + Math.round(5 * fill), 0.12, 0.15, 0.12, 0.01);
        if (fill > 0.3f) {
            level.sendParticles(com.bending.particle.ModParticles.HEAT_HAZE.get(), core.x, core.y, core.z, 2, 0.4, 0.5, 0.4, 0.0);
        }
        if (ticks % 6 == 0) {
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.FIRE_AMBIENT, SoundSource.PLAYERS,
                    0.8f + fill, 0.7f + fill * 0.6f);
        }
        BendingUtil.chargeBar(player, fill);
    }

    /**
     * Fire blocks and lit campfires go out at once. Magma and lava are drawn on: magma cools to
     * cobblestone after {@link #MAGMA_TICKS} of drawing, a lava source to obsidian after
     * {@link #LAVA_TICKS}. The timers only last for this hold, so a block you leave unfinished
     * starts over next time (and keeps giving heat). Runs every other tick; returns heat gained.
     */
    private float drawBlocks(ServerLevel level, ServerPlayer player, BendingData data, Vec3 core, double r) {
        float gained = 0f;
        int fires = 0;
        int ri = (int) Math.ceil(r);
        BlockPos center = player.blockPosition();
        double heatR2 = (r * HEAT_REACH) * (r * HEAT_REACH);
        List<BlockPos> heatBlocks = new java.util.ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-ri, -ri / 2 - 1, -ri), center.offset(ri, ri / 2 + 1, ri))) {
            double d2 = pos.distSqr(center);
            if (d2 > r * r) {
                continue;
            }
            BlockState state = level.getBlockState(pos);
            if (state.is(BlockTags.FIRE) || state.getBlock() instanceof CampfireBlock && state.getValue(CampfireBlock.LIT)) {
                if (fires >= MAX_BLOCKS_PER_SCAN) {
                    continue;
                }
                if (state.is(BlockTags.FIRE)) {
                    level.removeBlock(pos, false);
                } else {
                    level.setBlock(pos, state.setValue(CampfireBlock.LIT, false), 3);
                }
                stream(level, player, Vec3.atCenterOf(pos), core, 12);
                gained += HEAT_PER_BLOCK;
                fires++;
            } else if (d2 <= heatR2 && (state.is(Blocks.MAGMA_BLOCK) || isLavaSource(state))) {
                heatBlocks.add(pos.immutable());
            }
        }
        // The nearest heat blocks are drawn on.
        heatBlocks.sort(java.util.Comparator.comparingDouble(p -> p.distSqr(center)));
        for (int i = 0; i < Math.min(MAX_HEAT_BLOCKS, heatBlocks.size()); i++) {
            BlockPos pos = heatBlocks.get(i);
            boolean lava = isLavaSource(level.getBlockState(pos));
            int t = data.siphonDraw.merge(pos, 2, Integer::sum);
            gained += lava ? LAVA_HEAT : MAGMA_HEAT;
            Vec3 top = Vec3.atCenterOf(pos).add(0, lava ? 0.4 : 0.55, 0);
            for (int k = 0; k < (lava ? 6 : 4); k++) {
                Vec3 from = top.add((level.random.nextDouble() - 0.5) * 0.8, 0, (level.random.nextDouble() - 0.5) * 0.8);
                pull(level, player, from, core);
            }
            int limit = lava ? LAVA_TICKS : MAGMA_TICKS;
            if (t % 10 == 0) {
                // Cooling: steam and smoke rise as it gives up its heat.
                level.sendParticles(com.bending.particle.ModParticles.FIRE_SMOKE.get(), top.x, top.y, top.z, 2, 0.25, 0.05, 0.25, 0.01);
            }
            if (t >= limit) {
                level.setBlock(pos, (lava ? Blocks.OBSIDIAN : Blocks.COBBLESTONE).defaultBlockState(), 3);
                data.siphonDraw.remove(pos);
                level.playSound(null, pos, SoundEvents.LAVA_EXTINGUISH, SoundSource.BLOCKS, 0.8f, lava ? 0.7f : 1.1f);
                level.sendParticles(com.bending.particle.ModParticles.STEAM.get(), top.x, top.y, top.z, 12, 0.35, 0.2, 0.35, 0.02);
                level.sendParticles(com.bending.particle.ModParticles.FIRE_SMOKE.get(), top.x, top.y, top.z, 8, 0.3, 0.1, 0.3, 0.02);
            }
        }
        return gained;
    }

    private static boolean isLavaSource(BlockState state) {
        return state.getFluidState().is(net.minecraft.tags.FluidTags.LAVA) && state.getFluidState().isSource();
    }

    /** One siphon ember from a point into the bender: it lands on the chest as it fades. */
    private static void pull(ServerLevel level, ServerPlayer player, Vec3 from, Vec3 to) {
        Vec3 v = to.subtract(from).scale(1.0 / com.bending.particle.ModParticles.SIPHON_LIFE);
        var type = FireTechniques.blue(player) ? com.bending.particle.ModParticles.BLUE_SIPHON_EMBER.get()
                : com.bending.particle.ModParticles.SIPHON_EMBER.get();
        level.sendParticles(type, from.x, from.y, from.z, 0, v.x, v.y, v.z, 1.0);
    }

    /** A thread of embers pulled from a source into the bender. */
    private static void stream(ServerLevel level, ServerPlayer player, Vec3 from, Vec3 to, int count) {
        for (int i = 0; i < count; i++) {
            pull(level, player, from.add((level.random.nextDouble() - 0.5) * 0.5, (level.random.nextDouble() - 0.5) * 0.5,
                    (level.random.nextDouble() - 0.5) * 0.5), to);
        }
        level.sendParticles(com.bending.particle.ModParticles.FIRE_SMOKE.get(), from.x, from.y, from.z, 3, 0.15, 0.15, 0.15, 0.02);
    }

    /** Fire that hits you while siphoning is drawn in instead of burning you. */
    @Override
    public float modifyIncomingDamage(ServerPlayer player, BendingData data, DamageSource source, float amount) {
        if (source.is(DamageTypeTags.IS_FIRE) || source.is(BendingDamage.FIREBENDING)) {
            data.siphonHeat = Math.min(MAX_HEAT, data.siphonHeat + amount * 1.5f);
            player.serverLevel().sendParticles(FireFx.core(player), player.getX(), player.getY() + 1, player.getZ(), 12, 0.3, 0.4, 0.3, 0.05);
            return 0f;
        }
        return amount;
    }

    @Override
    public void onChargeCancelled(ServerPlayer player, BendingData data) {
        data.siphonHeat = 0f;
        data.siphonDraw.clear();
        player.displayClientMessage(net.minecraft.network.chat.Component.empty(), true);
    }

    @Override
    public void onRelease(ServerPlayer player, BendingData data, int ticks, float power) {
        BendingAnimations.play(player, BendingAnimations.SIPHON_RELEASE);
        ServerLevel level = player.serverLevel();
        float f = Mth.clamp(data.siphonHeat / MAX_HEAT, 0f, 1f);
        data.siphonHeat = 0f;
        data.siphonDraw.clear();
        float size = SkillStats.ability(player, id(), SkillStat.SIZE);
        double radius = Mth.lerp(f, MIN_BLAST, MAX_BLAST) * size;
        float damage = Mth.lerp(f, MIN_DAMAGE, MAX_DAMAGE) * power * SkillStats.ability(player, id(), SkillStat.DAMAGE);
        Vec3 c = player.position().add(0, 0.9, 0);

        // Nova: rings of flame racing outward along the ground and through the air.
        int n = 60 + Math.round(120 * f);
        for (int i = 0; i < n; i++) {
            double a = i / (double) n * Math.PI * 2;
            double dx = Math.cos(a), dz = Math.sin(a);
            double speed = radius * 0.1;
            level.sendParticles(FireFx.wisp(player), c.x + dx * 0.5, c.y - 0.6, c.z + dz * 0.5, 0, dx * speed, 0.02, dz * speed, 1.0);
            level.sendParticles(FireFx.wisp(player), c.x + dx * 0.5, c.y + 0.3, c.z + dz * 0.5, 0, dx * speed * 0.85, 0.08, dz * speed * 0.85, 1.0);
            if (i % 2 == 0) {
                level.sendParticles(FireFx.cloud(player), c.x + dx, c.y - 0.2, c.z + dz, 0, dx * speed * 0.6, 0.05, dz * speed * 0.6, 1.0);
            }
        }
        level.sendParticles(FireFx.core(player), c.x, c.y, c.z, 30 + Math.round(50 * f), 0.5, 0.6, 0.5, 0.25 + 0.3 * f);
        level.sendParticles(FireFx.spark(player), c.x, c.y, c.z, 40, 0.3, 0.3, 0.3, 0.6);
        level.sendParticles(FireFx.ember(player), c.x, c.y, c.z, 60 + Math.round(80 * f), 0.4, 0.4, 0.4, 0.5 + 0.4 * f);
        if (f > 0.5f) {
            level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, c.x, c.y, c.z, 1, 0, 0, 0, 0);
        }
        level.playSound(null, c.x, c.y, c.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.8f + 1.4f * f, 1.3f - 0.5f * f);
        level.playSound(null, c.x, c.y, c.z, SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 1.2f, 0.6f);
        ImpactFx.impact(level, c, 0.35f + 0.65f * f, radius * 3, ImpactFx.fireColor(player));

        BendingUtil.destroyProjectiles(level, player, c, radius * 0.6);
        for (LivingEntity e : BendingUtil.livingAround(player, c, radius)) {
            double d = e.position().distanceTo(c);
            float falloff = (float) Math.max(0.4, 1 - d / radius);
            BendingUtil.damage(player, e, damage * falloff, 3 + Math.round(4 * f));
            BendingUtil.knockAway(e, c, (0.8 + 1.4 * f) * falloff);
            e.setDeltaMovement(e.getDeltaMovement().add(0, 0.25 + 0.35 * f * falloff, 0));
        }
        player.displayClientMessage(net.minecraft.network.chat.Component.empty(), true);
    }
}
