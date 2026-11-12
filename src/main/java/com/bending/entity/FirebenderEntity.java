package com.bending.entity;

import java.util.EnumSet;
import java.util.List;

import javax.annotation.Nullable;

import com.bending.ability.BendingDamage;
import com.bending.ability.ImpactFx;
import com.bending.ability.fire.FireFx;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * A hostile firebender. Keeps its distance and strafes, throws fire blasts (sometimes a three-hit
 * combo), lashes out with a flame burst when you get close, and jet-leaps at you from far away.
 * Shrugs off burning, but bending hurts it like anything else.
 */
public class FirebenderEntity extends Monster {
    public FirebenderEntity(EntityType<? extends FirebenderEntity> type, Level level) {
        super(type, level);
        xpReward = 12;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 32)
                .add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.FOLLOW_RANGE, 32)
                .add(Attributes.ATTACK_DAMAGE, 4)
                .add(Attributes.ARMOR, 4);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new FirebendGoal(this));
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.9));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 10f));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this, FirebenderEntity.class).setAlertOthers());
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
        targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, IronGolem.class, true));
    }

    /** Damage of one thrown blast. */
    protected float blastDamage() {
        return 5f;
    }

    protected boolean blueFire() {
        return false;
    }

    /** Throws one fire blast at the target from the given hand side (+1 right, -1 left). */
    public void throwBlast(LivingEntity target, float side, float spreadYaw) {
        Vec3 eye = getEyePosition();
        Vec3 aim = target.getEyePosition().add(0, -0.4, 0).add(target.getDeltaMovement().scale(6)).subtract(eye).normalize();
        if (spreadYaw != 0f) {
            aim = aim.yRot(spreadYaw * Mth.DEG_TO_RAD);
        }
        Vec3 right = aim.cross(new Vec3(0, 1, 0)).normalize();
        Vec3 start = eye.add(aim.scale(0.7)).add(right.scale(0.35 * side)).add(0, -0.25, 0);
        FireBlastEntity blast = new FireBlastEntity(level(), this, blastDamage())
                .configure(1f, 1.6, 0f, 0, 16).blue(blueFire());
        blast.setPos(start.x, start.y, start.z);
        blast.shoot(aim.x, aim.y, aim.z, 2.0f, 2.5f);
        level().addFreshEntity(blast);
        swing(side > 0 ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND);
        level().playSound(null, getX(), getY(), getZ(), SoundEvents.BLAZE_SHOOT, SoundSource.HOSTILE, 0.9f, 1.1f + random.nextFloat() * 0.3f);
        if (level() instanceof ServerLevel server) {
            server.sendParticles(FireFx.core(blueFire()), start.x, start.y, start.z, 10, 0.1, 0.1, 0.1, 0.05);
        }
    }

    /** Flame burst around itself: hurts and shoves everything close. */
    public void flameBurst(double radius, float damage) {
        if (!(level() instanceof ServerLevel server)) {
            return;
        }
        swing(InteractionHand.MAIN_HAND);
        Vec3 c = position().add(0, 0.6, 0);
        for (int i = 0; i < 70; i++) {
            double a = i / 70.0 * Math.PI * 2;
            double dx = Math.cos(a), dz = Math.sin(a);
            server.sendParticles(FireFx.wisp(blueFire()), c.x + dx * 0.6, c.y, c.z + dz * 0.6, 0, dx * radius * 0.12, 0.04, dz * radius * 0.12, 1.0);
            if (i % 2 == 0) {
                server.sendParticles(FireFx.cloud(blueFire()), c.x + dx, c.y, c.z + dz, 0, dx * radius * 0.08, 0.05, dz * radius * 0.08, 1.0);
            }
        }
        server.sendParticles(FireFx.core(blueFire()), c.x, c.y, c.z, 25, 0.4, 0.3, 0.4, 0.15);
        server.playSound(null, getX(), getY(), getZ(), SoundEvents.FIRECHARGE_USE, SoundSource.HOSTILE, 1.2f, 0.8f);
        ImpactFx.impact(server, c, 0.35f, radius * 3, blueFire() ? ImpactFx.BLUE_FIRE : ImpactFx.FIRE);
        for (LivingEntity e : enemiesAround(c, radius)) {
            e.hurt(fireSource(), damage);
            e.igniteForSeconds(3);
            Vec3 push = e.position().subtract(position()).multiply(1, 0, 1).normalize().scale(1.1);
            e.push(push.x, 0.45, push.z);
            e.hurtMarked = true;
        }
    }

    protected List<LivingEntity> enemiesAround(Vec3 c, double radius) {
        return level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(radius),
                e -> e != this && e.isAlive() && !(e instanceof FirebenderEntity) && e.position().distanceTo(c) <= radius + e.getBbWidth()
                        && !(e instanceof Player p && (p.isCreative() || p.isSpectator())));
    }

    public DamageSource fireSource() {
        return BendingDamage.source(this, BendingDamage.FIREBENDING);
    }

    /** Jet-leap toward a far target, trailing fire. */
    public void jetLeap(LivingEntity target) {
        Vec3 to = target.position().subtract(position());
        Vec3 flat = to.multiply(1, 0, 1).normalize();
        double dist = Math.min(14, to.horizontalDistance());
        setDeltaMovement(flat.scale(0.12 * dist).add(0, 0.75, 0));
        hurtMarked = true;
        jetTicks = 14;
        level().playSound(null, getX(), getY(), getZ(), SoundEvents.FIRECHARGE_USE, SoundSource.HOSTILE, 1f, 1.3f);
    }

    private int jetTicks;

    @Override
    public void aiStep() {
        super.aiStep();
        if (level() instanceof ServerLevel server) {
            if (jetTicks > 0) {
                jetTicks--;
                fallDistance = 0;
                server.sendParticles(FireFx.wisp(blueFire()), getX(), getY() + 0.1, getZ(), 6, 0.15, 0.05, 0.15, 0.02);
                server.sendParticles(FireFx.cloud(blueFire()), getX(), getY(), getZ(), 2, 0.1, 0.05, 0.1, 0.01);
            }
        } else if (isAggressive() && random.nextInt(2) == 0) {
            // Fire flickering in the hands while fighting.
            float yaw = yBodyRot * Mth.DEG_TO_RAD;
            double cos = Math.cos(yaw), sin = Math.sin(yaw);
            for (int side = -1; side <= 1; side += 2) {
                double hx = getX() - cos * 0.38 * side - sin * 0.25;
                double hz = getZ() - sin * 0.38 * side + cos * 0.25;
                level().addParticle(FireFx.ember(blueFire()), hx, getY() + 0.75, hz, 0, 0.03, 0);
            }
        }
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        return jetTicks <= 0 && super.causeFallDamage(distance, multiplier, source);
    }

    @Override
    protected net.minecraft.sounds.SoundEvent getAmbientSound() {
        return SoundEvents.PILLAGER_AMBIENT;
    }

    @Override
    protected net.minecraft.sounds.SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.PILLAGER_HURT;
    }

    @Override
    protected net.minecraft.sounds.SoundEvent getDeathSound() {
        return SoundEvents.PILLAGER_DEATH;
    }

    /** Strafe at range, throw blasts, burst up close, leap in from afar. */
    static class FirebendGoal extends Goal {
        private final FirebenderEntity mob;
        private int cooldown = 20;
        private int combo;
        private int strafeTicks;
        private boolean strafeLeft;
        private int leapCooldown = 60;

        FirebendGoal(FirebenderEntity mob) {
            this.mob = mob;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity t = mob.getTarget();
            return t != null && t.isAlive();
        }

        @Override
        public void start() {
            mob.setAggressive(true);
        }

        @Override
        public void stop() {
            mob.setAggressive(false);
            mob.getNavigation().stop();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            LivingEntity target = mob.getTarget();
            if (target == null) {
                return;
            }
            double dist = mob.distanceTo(target);
            boolean sees = mob.getSensing().hasLineOfSight(target);
            mob.getLookControl().setLookAt(target, 30f, 30f);

            if (!sees || dist > 14) {
                mob.getNavigation().moveTo(target, 1.1);
            } else {
                mob.getNavigation().stop();
                if (--strafeTicks <= 0) {
                    strafeTicks = 20 + mob.random.nextInt(30);
                    strafeLeft = mob.random.nextBoolean();
                }
                float forward = dist < 5 ? -0.5f : dist > 10 ? 0.5f : 0f;
                mob.getMoveControl().strafe(forward, strafeLeft ? 0.5f : -0.5f);
                mob.setYRot(Mth.rotateIfNecessary(mob.getYRot(), mob.yHeadRot, 0f));
            }

            if (--leapCooldown <= 0 && dist > 16 && sees && mob.onGround()) {
                mob.jetLeap(target);
                leapCooldown = 100 + mob.random.nextInt(60);
            }

            if (--cooldown > 0 || !sees) {
                return;
            }
            if (dist < 3.5) {
                mob.flameBurst(3.5, 5f);
                cooldown = 30 + mob.random.nextInt(15);
                combo = 0;
            } else if (dist < 24) {
                mob.throwBlast(target, combo % 2 == 0 ? 1 : -1, 0f);
                if (combo == 0 && mob.random.nextInt(3) == 0) {
                    combo = 2; // two more quick ones
                }
                if (combo > 0) {
                    combo--;
                    cooldown = 6;
                } else {
                    cooldown = 25 + mob.random.nextInt(20);
                }
            }
        }
    }

    @Override
    public boolean isAlliedTo(net.minecraft.world.entity.Entity other) {
        return other instanceof FirebenderEntity || super.isAlliedTo(other);
    }
}
