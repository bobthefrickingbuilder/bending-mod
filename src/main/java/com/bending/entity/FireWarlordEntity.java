package com.bending.entity;

import java.util.EnumSet;

import com.bending.ability.ImpactFx;
import com.bending.ability.fire.FireFx;
import com.bending.data.BendingData;
import com.bending.data.ModAttachments;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * The Fire Warlord, a boss. Three phases by health:
 * <ul>
 * <li>Above 60%: fan volleys of fire blasts, flame bursts, meteor leaps that crater on landing.</li>
 * <li>Below 60%: blue fire, an expanding fire ring you have to jump, and it calls in firebenders.</li>
 * <li>Below 25%: it also calls lightning onto marked spots around you, and everything comes faster.</li>
 * </ul>
 * Beating it gives every bender who fought nearby skill points.
 */
public class FireWarlordEntity extends FirebenderEntity {
    private static final int SKILL_POINT_REWARD = 3;
    private final ServerBossEvent bossBar = (ServerBossEvent) new ServerBossEvent(
            Component.translatable("entity.bending.fire_warlord").withStyle(ChatFormatting.GOLD),
            BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.NOTCHED_10).setDarkenScreen(true);

    /** Meteor leap in progress: crater on landing. */
    private boolean slamming;
    private int slamAirTicks;
    /** Expanding ring of fire: current radius, or negative when none. */
    private double ringRadius = -1;
    private Vec3 ringCenter = Vec3.ZERO;
    /** Marked lightning spot and its countdown. */
    private Vec3 strikeAt;
    private int strikeTicks;

    public FireWarlordEntity(EntityType<? extends FireWarlordEntity> type, Level level) {
        super(type, level);
        xpReward = 250;
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return FirebenderEntity.createAttributes()
                .add(Attributes.MAX_HEALTH, 400)
                .add(Attributes.MOVEMENT_SPEED, 0.32)
                .add(Attributes.FOLLOW_RANGE, 48)
                .add(Attributes.ARMOR, 10)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.8)
                .add(Attributes.ATTACK_DAMAGE, 8);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new WarlordGoal(this));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 16f));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, false));
    }

    public int phase() {
        float f = getHealth() / getMaxHealth();
        return f > 0.6f ? 1 : f > 0.25f ? 2 : 3;
    }

    @Override
    protected boolean blueFire() {
        return phase() >= 2;
    }

    @Override
    protected float blastDamage() {
        return phase() >= 2 ? 8f : 6.5f;
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (!(level() instanceof ServerLevel server)) {
            // Aura of heat around it.
            if (random.nextInt(2) == 0) {
                level().addParticle(FireFx.wisp(phase() >= 2), getRandomX(0.8), getY() + random.nextDouble() * 0.3, getRandomZ(0.8), 0, 0.05, 0);
            }
            return;
        }
        bossBar.setProgress(getHealth() / getMaxHealth());
        bossBar.setColor(phase() >= 2 ? BossEvent.BossBarColor.BLUE : BossEvent.BossBarColor.RED);
        if (slamming) {
            tickSlam(server);
        }
        if (ringRadius >= 0) {
            tickRing(server);
        }
        if (strikeAt != null) {
            tickStrike(server);
        }
    }

    // ---- Meteor leap ----

    void meteorLeap(LivingEntity target) {
        Vec3 to = target.position().subtract(position());
        double dist = Math.min(18, to.horizontalDistance());
        Vec3 flat = to.multiply(1, 0, 1).normalize();
        setDeltaMovement(flat.scale(0.09 * dist).add(0, 1.25, 0));
        hurtMarked = true;
        slamming = true;
        slamAirTicks = 0;
        swing(net.minecraft.world.InteractionHand.MAIN_HAND);
        level().playSound(null, getX(), getY(), getZ(), SoundEvents.FIRECHARGE_USE, SoundSource.HOSTILE, 2f, 0.6f);
    }

    private void tickSlam(ServerLevel server) {
        slamAirTicks++;
        fallDistance = 0;
        server.sendParticles(FireFx.wisp(blueFire()), getX(), getY() + 0.5, getZ(), 10, 0.3, 0.4, 0.3, 0.03);
        server.sendParticles(FireFx.cloud(blueFire()), getX(), getY() + 0.3, getZ(), 4, 0.25, 0.2, 0.25, 0.02);
        if (slamAirTicks > 8 && getDeltaMovement().y < 0) {
            setDeltaMovement(getDeltaMovement().add(0, -0.12, 0)); // dive
        }
        if (slamAirTicks > 5 && (onGround() || slamAirTicks > 80)) {
            slamming = false;
            crater(server, position(), 6.0, blastDamage() + 4f);
        }
    }

    private void crater(ServerLevel server, Vec3 c, double radius, float damage) {
        server.playSound(null, c.x, c.y, c.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 2.5f, 0.7f);
        server.sendParticles(ParticleTypes.EXPLOSION_EMITTER, c.x, c.y + 0.3, c.z, 1, 0, 0, 0, 0);
        for (int i = 0; i < 140; i++) {
            double a = i / 140.0 * Math.PI * 2;
            double dx = Math.cos(a), dz = Math.sin(a);
            server.sendParticles(FireFx.wisp(blueFire()), c.x + dx, c.y + 0.2, c.z + dz, 0, dx * 0.9, 0.08, dz * 0.9, 1.0);
            if (i % 2 == 0) {
                server.sendParticles(FireFx.cloud(blueFire()), c.x + dx * 1.5, c.y + 0.3, c.z + dz * 1.5, 0, dx * 0.5, 0.15, dz * 0.5, 1.0);
            }
        }
        server.sendParticles(FireFx.core(blueFire()), c.x, c.y + 0.5, c.z, 60, 1.2, 0.6, 1.2, 0.2);
        ImpactFx.impact(server, c, 1.0f, 30, blueFire() ? ImpactFx.BLUE_FIRE : ImpactFx.FIRE);
        for (LivingEntity e : enemiesAround(c, radius)) {
            double d = e.position().distanceTo(c);
            float scale = (float) Math.max(0.35, 1 - d / radius);
            e.hurt(fireSource(), damage * scale);
            e.igniteForSeconds(4);
            Vec3 push = e.position().subtract(c).multiply(1, 0, 1).normalize().scale(1.6 * scale);
            e.push(push.x, 0.7 * scale, push.z);
            e.hurtMarked = true;
        }
    }

    // ---- Fire ring: a wall of flame that rolls outward; jump it ----

    void startRing() {
        ringRadius = 1.0;
        ringCenter = position();
        swing(net.minecraft.world.InteractionHand.MAIN_HAND);
        level().playSound(null, getX(), getY(), getZ(), SoundEvents.BLAZE_SHOOT, SoundSource.HOSTILE, 2f, 0.5f);
        if (level() instanceof ServerLevel server) {
            ImpactFx.impact(server, ringCenter, 0.5f, 24, blueFire() ? ImpactFx.BLUE_FIRE : ImpactFx.FIRE);
        }
    }

    private void tickRing(ServerLevel server) {
        double prev = ringRadius;
        ringRadius += 0.55;
        int points = (int) (ringRadius * 14);
        for (int i = 0; i < points; i++) {
            double a = i / (double) points * Math.PI * 2;
            double x = ringCenter.x + Math.cos(a) * ringRadius, z = ringCenter.z + Math.sin(a) * ringRadius;
            server.sendParticles(FireFx.wisp(blueFire()), x, ringCenter.y + 0.1, z, 0, 0, 0.14, 0, 1.0);
            if (i % 3 == 0) {
                server.sendParticles(FireFx.cloud(blueFire()), x, ringCenter.y + 0.4, z, 0, 0, 0.08, 0, 1.0);
            }
        }
        // The wall is about a block tall: anything on the ground in its path gets hit.
        for (LivingEntity e : enemiesAround(ringCenter, ringRadius + 1)) {
            double d = e.position().multiply(1, 0, 1).distanceTo(ringCenter.multiply(1, 0, 1));
            if (d >= prev - 0.3 && d <= ringRadius + 0.3 && e.getY() - ringCenter.y < 1.1) {
                e.hurt(fireSource(), blastDamage());
                e.igniteForSeconds(4);
                Vec3 push = e.position().subtract(ringCenter).multiply(1, 0, 1).normalize();
                e.push(push.x * 0.8, 0.5, push.z * 0.8);
                e.hurtMarked = true;
            }
        }
        if (ringRadius > 16) {
            ringRadius = -1;
        }
    }

    // ---- Lightning: marks a spot, then strikes it ----

    void markStrike(LivingEntity target) {
        strikeAt = target.position().add(target.getDeltaMovement().scale(10));
        strikeTicks = 22;
        level().playSound(null, strikeAt.x, strikeAt.y, strikeAt.z, SoundEvents.BEACON_ACTIVATE, SoundSource.HOSTILE, 1.5f, 1.8f);
    }

    private void tickStrike(ServerLevel server) {
        strikeTicks--;
        // Crackling warning circle.
        for (int i = 0; i < 16; i++) {
            double a = i / 16.0 * Math.PI * 2 + strikeTicks * 0.2;
            server.sendParticles(ParticleTypes.ELECTRIC_SPARK, strikeAt.x + Math.cos(a) * 2, strikeAt.y + 0.15,
                    strikeAt.z + Math.sin(a) * 2, 1, 0, 0.05, 0, 0);
        }
        if (strikeTicks > 0) {
            return;
        }
        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(server);
        if (bolt != null) {
            bolt.moveTo(strikeAt.x, strikeAt.y, strikeAt.z);
            bolt.setVisualOnly(true);
            server.addFreshEntity(bolt);
        }
        ImpactFx.impact(server, strikeAt, 0.8f, 20, ImpactFx.LIGHTNING);
        for (LivingEntity e : enemiesAround(strikeAt, 2.2)) {
            e.hurt(com.bending.ability.BendingDamage.source(this, com.bending.ability.BendingDamage.LIGHTNING), 10f);
        }
        strikeAt = null;
    }

    // ---- Reinforcements ----

    void callReinforcements(ServerLevel server) {
        int nearby = server.getEntitiesOfClass(FirebenderEntity.class, getBoundingBox().inflate(32),
                e -> !(e instanceof FireWarlordEntity)).size();
        for (int i = 0; i < 2 && nearby + i < 4; i++) {
            FirebenderEntity add = ModEntities.FIREBENDER.get().create(server);
            if (add == null) {
                continue;
            }
            double a = random.nextDouble() * Math.PI * 2;
            BlockPos pos = BlockPos.containing(getX() + Math.cos(a) * 4, getY(), getZ() + Math.sin(a) * 4);
            add.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, random.nextFloat() * 360, 0);
            add.finalizeSpawn(server, server.getCurrentDifficultyAt(pos), MobSpawnType.MOB_SUMMONED, null);
            add.setTarget(getTarget());
            server.addFreshEntity(add);
            server.sendParticles(FireFx.cloud(true), add.getX(), add.getY() + 1, add.getZ(), 30, 0.4, 0.8, 0.4, 0.05);
        }
        server.playSound(null, getX(), getY(), getZ(), SoundEvents.EVOKER_PREPARE_SUMMON, SoundSource.HOSTILE, 1.5f, 0.8f);
    }

    // ---- Boss bar, rewards, save ----

    @Override
    public void startSeenByPlayer(ServerPlayer player) {
        super.startSeenByPlayer(player);
        bossBar.addPlayer(player);
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        bossBar.removePlayer(player);
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (level() instanceof ServerLevel server) {
            for (ServerPlayer player : server.getPlayers(p -> p.distanceTo(this) < 48)) {
                BendingData data = player.getData(ModAttachments.BENDING);
                if (data.getElement() != null) {
                    data.addSkillPoints(SKILL_POINT_REWARD);
                    player.sendSystemMessage(Component.translatable("message.bending.warlord_defeated", SKILL_POINT_REWARD)
                            .withStyle(ChatFormatting.GOLD));
                }
            }
            ImpactFx.impact(server, position(), 1f, 40, ImpactFx.FIRE);
        }
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (hasCustomName()) {
            bossBar.setName(getDisplayName());
        }
    }

    /** Picks attacks by phase and distance. */
    static class WarlordGoal extends Goal {
        private final FireWarlordEntity boss;
        private int cooldown = 30;
        private int volley;
        private int strafeTicks;
        private boolean strafeLeft;
        private int reinforceCooldown = 0;

        WarlordGoal(FireWarlordEntity boss) {
            this.boss = boss;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity t = boss.getTarget();
            return t != null && t.isAlive();
        }

        @Override
        public void start() {
            boss.setAggressive(true);
        }

        @Override
        public void stop() {
            boss.setAggressive(false);
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            LivingEntity target = boss.getTarget();
            if (target == null || !(boss.level() instanceof ServerLevel server)) {
                return;
            }
            int phase = boss.phase();
            double dist = boss.distanceTo(target);
            boolean sees = boss.getSensing().hasLineOfSight(target);
            boss.getLookControl().setLookAt(target, 30f, 30f);
            if (!boss.slamming) {
                if (!sees || dist > 16) {
                    boss.getNavigation().moveTo(target, 1.15);
                } else {
                    boss.getNavigation().stop();
                    if (--strafeTicks <= 0) {
                        strafeTicks = 25 + boss.random.nextInt(25);
                        strafeLeft = boss.random.nextBoolean();
                    }
                    boss.getMoveControl().strafe(dist < 6 ? -0.4f : dist > 11 ? 0.5f : 0f, strafeLeft ? 0.45f : -0.45f);
                }
            }
            if (phase >= 2 && --reinforceCooldown <= 0) {
                boss.callReinforcements(server);
                reinforceCooldown = 600;
            }

            // A volley in progress: one blast every few ticks, fanned out.
            if (volley > 0) {
                if (boss.tickCount % 3 == 0) {
                    int n = phase >= 3 ? 7 : 5;
                    int i = n - volley;
                    float spread = (i - (n - 1) / 2f) * 9f;
                    boss.throwBlast(target, i % 2 == 0 ? 1 : -1, spread);
                    volley--;
                }
                return;
            }
            if (--cooldown > 0 || boss.slamming) {
                return;
            }
            float haste = phase == 3 ? 0.6f : phase == 2 ? 0.8f : 1f;
            int roll = boss.random.nextInt(phase >= 3 ? 5 : phase == 2 ? 4 : 3);
            if (dist < 4 && roll < 2) {
                boss.flameBurst(4.5, 9f);
                cooldown = 25;
            } else if (roll == 0 && sees) {
                volley = phase >= 3 ? 7 : 5;
                cooldown = 35;
            } else if (roll == 1 && boss.onGround()) {
                boss.meteorLeap(target);
                cooldown = 55;
            } else if (roll == 2) {
                if (phase >= 2) {
                    boss.startRing();
                    cooldown = 45;
                } else {
                    boss.throwBlast(target, 1, 0f);
                    cooldown = 12;
                }
            } else if (roll == 3) {
                boss.markStrike(target);
                if (phase >= 3) {
                    boss.throwBlast(target, -1, 0f);
                }
                cooldown = 30;
            } else {
                boss.throwBlast(target, 1, -6f);
                boss.throwBlast(target, -1, 6f);
                cooldown = 20;
            }
            cooldown = Math.max(6, Math.round(cooldown * haste));
        }
    }

    /** The summon ritual's flourish. */
    public void announce(ServerLevel server) {
        server.sendParticles(ParticleTypes.EXPLOSION_EMITTER, getX(), getY() + 1, getZ(), 1, 0, 0, 0, 0);
        server.sendParticles(FireFx.cloud(false), getX(), getY() + 1, getZ(), 120, 1.2, 1.5, 1.2, 0.12);
        server.sendParticles(FireFx.wisp(false), getX(), getY() + 0.2, getZ(), 120, 2, 0.1, 2, 0.08);
        server.playSound(null, getX(), getY(), getZ(), SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 1.5f, 1.3f);
        ImpactFx.impact(server, position(), 1f, 48, ImpactFx.FIRE);
        for (ServerPlayer p : server.getPlayers(p -> p.distanceTo(this) < 48)) {
            p.sendSystemMessage(Component.translatable("message.bending.warlord_arrives").withStyle(ChatFormatting.RED));
        }
        setYRot(Mth.wrapDegrees(random.nextFloat() * 360));
    }
}
