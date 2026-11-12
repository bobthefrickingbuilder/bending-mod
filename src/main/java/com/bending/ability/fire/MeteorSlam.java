package com.bending.ability.fire;

import com.bending.ability.Ability;
import com.bending.ability.AbilitySlot;
import com.bending.ability.BendingUtil;
import com.bending.ability.CastType;
import com.bending.data.BendingData;
import com.bending.data.Element;
import com.bending.particle.ModParticles;
import com.bending.sound.ModSounds;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Innate (tap Shift while airborne, in bending mode): wrap yourself in fire and dive like a meteor in
 * the direction you're looking (always at least a little downward), hitting the ground or a wall in a
 * shockwave of flame that rolls out mostly ahead of you. The higher you dive from, the harder and wider
 * the impact. No fall damage. Can be cast out of a Fire Jet Dash.
 */
public class MeteorSlam extends Ability {
    /** Minimum height above the ground to start a slam, so stepping off a block doesn't trigger it. */
    private static final double MIN_HEIGHT = 2.5;
    /**
     * The dive picks up speed the longer it lasts: it starts at 2 blocks per tick and gains 0.15 every
     * tick, up to 6 (reached after about 1.3 s of diving).
     */
    private static final double DIVE_SPEED = 2.0;
    private static final double DIVE_ACCELERATION = 0.15;
    private static final double MAX_DIVE_SPEED = 6.0;
    /** The dive always points at least this much downward (about 25 degrees below horizontal). */
    private static final double MIN_DOWN = 0.42;
    /**
     * How high a full, straight-up Fire Jet Dash takes you (1.6 launch, 40 ticks of jets, then coasting
     * to the peak: about 44 blocks). Up to this height the slam ramps from its base to its normal max.
     */
    public static final double DASH_HEIGHT = 44.0;
    private static final float BASE_DAMAGE = 8f;
    private static final float MAX_DAMAGE = 26f;
    private static final double BASE_RADIUS = 3.0;
    private static final double MAX_RADIUS = 6.0;
    /**
     * Beyond dash height the impact becomes a real explosion: extra damage and radius per block of
     * extra height, and a crater in the ground (if mobGriefing allows), up to these caps.
     */
    private static final float OVER_DAMAGE_PER_BLOCK = 0.8f;
    private static final float MAX_OVER_DAMAGE = 24f;
    private static final double OVER_RADIUS_PER_BLOCK = 0.1;
    private static final double MAX_OVER_RADIUS = 3.0;
    private static final float BASE_EXPLOSION_POWER = 1.5f;
    private static final float EXPLOSION_POWER_PER_BLOCK = 0.08f;
    /** TNT is 4. */
    private static final float MAX_EXPLOSION_POWER = 4f;
    /** Safety limit: a slam that never lands (falling into the void) ends after 10 s. */
    private static final int MAX_DIVE_TICKS = 200;

    public MeteorSlam() {
        super("meteor_slam", Element.FIRE, AbilitySlot.INNATE, CastType.TAP);
    }

    @Override
    public float chiCost(int chargeTicks) {
        return 12f;
    }

    @Override
    public boolean canStart(ServerPlayer player, BendingData data) {
        if (data.slamming || player.onGround() || player.isInWater() || player.onClimbable()
                || player.getAbilities().flying || player.isFallFlying() || player.isPassenger()) {
            return false;
        }
        Vec3 from = player.position();
        HitResult below = player.level().clip(new ClipContext(from, from.add(0, -MIN_HEIGHT, 0),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, player));
        return below.getType() == HitResult.Type.MISS;
    }

    @Override
    public void onTap(ServerPlayer player, BendingData data, float power) {
        ServerLevel level = player.serverLevel();
        data.slamming = true;
        data.slamStartY = player.getY();
        data.slamTicks = 0;
        data.slamPower = power;
        data.noFallUntil = level.getGameTime() + MAX_DIVE_TICKS;

        data.slamDir = diveDirection(player.getLookAngle());
        com.bending.ability.BendingAnimations.play(player, com.bending.ability.BendingAnimations.METEOR_DIVE);
        player.setDeltaMovement(data.slamDir.scale(DIVE_SPEED));
        player.hurtMarked = true;

        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                ModSounds.METEOR_SLAM_DIVE.get(), SoundSource.PLAYERS, 1.0f, 0.8f);
        level.sendParticles(FireFx.wisp(player), player.getX(), player.getY() + 0.8, player.getZ(), 25, 0.4, 0.4, 0.4, 0.1);
        level.sendParticles(FireFx.spark(player), player.getX(), player.getY() + 0.8, player.getZ(), 10, 0.3, 0.3, 0.3, 0.2);
    }

    /** Speed (blocks per tick) after diving for {@code ticks} ticks. */
    private static double diveSpeed(int ticks) {
        return Math.min(MAX_DIVE_SPEED, DIVE_SPEED + DIVE_ACCELERATION * ticks);
    }

    /** Where you're looking, bent down if needed so the dive always heads toward the ground. */
    private static Vec3 diveDirection(Vec3 look) {
        if (look.y <= -MIN_DOWN) {
            return look.normalize();
        }
        Vec3 flat = new Vec3(look.x, 0, look.z);
        if (flat.lengthSqr() < 1e-6) {
            return new Vec3(0, -1, 0);
        }
        flat = flat.normalize().scale(Math.sqrt(1 - MIN_DOWN * MIN_DOWN));
        return new Vec3(flat.x, -MIN_DOWN, flat.z);
    }

    /** Called every server tick while diving. */
    public static void tickSlam(ServerPlayer player, BendingData data) {
        ServerLevel level = player.serverLevel();
        data.slamTicks++;
        if (player.isInWater()) {
            endInWater(player, data);
            return;
        }
        if (player.onGround() || (player.horizontalCollision && data.slamTicks > 1)) {
            impact(player, data);
            return;
        }
        if (data.slamTicks > MAX_DIVE_TICKS || !player.isAlive()) {
            data.slamming = false;
            com.bending.ability.BendingAnimations.stop(player);
            return;
        }
        Vec3 dir = data.slamDir;
        double speed = diveSpeed(data.slamTicks);
        player.setDeltaMovement(dir.scale(speed));
        player.hurtMarked = true;
        player.fallDistance = 0;

        // A comet tail: flames streaming back off you along the dive, longer and fuller as you speed up.
        RandomSource random = level.getRandom();
        double heat = (speed - DIVE_SPEED) / (MAX_DIVE_SPEED - DIVE_SPEED); // 0 at the start, 1 at top speed
        int tongues = 6 + (int) (heat * 14);
        for (int i = 0; i < tongues; i++) {
            // Spread the tail over the whole distance covered this tick, so it stays solid at speed.
            double back = random.nextDouble() * speed;
            level.sendParticles(FireFx.wisp(player),
                    player.getX() - dir.x * back + random.nextGaussian() * 0.3, player.getY() + 0.2 + random.nextDouble() * 1.4 - dir.y * back,
                    player.getZ() - dir.z * back + random.nextGaussian() * 0.3, 0, -dir.x, -dir.y, -dir.z, 0.35 + random.nextDouble() * 0.3);
        }
        int clouds = 1 + (int) (heat * 4);
        for (int i = 0; i < clouds; i++) {
            double back = 1 + random.nextDouble() * speed;
            level.sendParticles(FireFx.cloud(player), player.getX() - dir.x * back, player.getY() + 0.9 - dir.y * back,
                    player.getZ() - dir.z * back, 0, -dir.x, -dir.y, -dir.z, 0.2 + random.nextDouble() * 0.2);
        }
        level.sendParticles(FireFx.spark(player), player.getX(), player.getY() + 0.5, player.getZ(), 3, 0.3, 0.5, 0.3, 0.05);
        level.sendParticles(ModParticles.FIRE_SMOKE.get(), player.getX(), player.getY() + 1.6, player.getZ(), 1, 0.2, 0.2, 0.2, 0.02);
    }

    /** After a ground-breaking landing you can't move for this long (a third of a second). */
    private static final int ROOT_TICKS = 7;
    private static final net.minecraft.resources.ResourceLocation ROOT_ID =
            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(com.bending.BendingMod.MODID, "slam_root");

    /** Every tick: holds you in place while rooted by a landing, then lets go. */
    public static void tickRooted(ServerPlayer player, BendingData data) {
        boolean rooted = player.level().getGameTime() < data.rootedUntil;
        var speed = player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);
        var jump = player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.JUMP_STRENGTH);
        for (var attribute : new net.minecraft.world.entity.ai.attributes.AttributeInstance[] {speed, jump}) {
            if (attribute == null) {
                continue;
            }
            boolean has = attribute.hasModifier(ROOT_ID);
            if (rooted && !has) {
                attribute.addTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(ROOT_ID, -1.0,
                        net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
            } else if (!rooted && has) {
                attribute.removeModifier(ROOT_ID);
            }
        }
        if (rooted) {
            Vec3 v = player.getDeltaMovement();
            if (v.x * v.x + v.z * v.z > 1e-4) {
                player.setDeltaMovement(0, Math.min(0, v.y), 0);
                player.hurtMarked = true;
            }
        }
    }

    private static void impact(ServerPlayer player, BendingData data) {
        com.bending.ability.BendingAnimations.play(player, com.bending.ability.BendingAnimations.METEOR_LAND);
        ServerLevel level = player.serverLevel();
        data.slamming = false;
        data.noFallUntil = level.getGameTime() + 5;
        player.fallDistance = 0;

        double height = Math.max(0, data.slamStartY - player.getY());
        // Normal slam: ramps from base to max over the height of a straight-up dash.
        double normal = Math.min(1.0, height / DASH_HEIGHT);
        float damage = BASE_DAMAGE + (MAX_DAMAGE - BASE_DAMAGE) * (float) normal;
        double radius = BASE_RADIUS + (MAX_RADIUS - BASE_RADIUS) * normal;
        // Beyond dash height (a cliff, a mountain, a dash plus air hops): the impact explodes.
        double over = Math.max(0, height - DASH_HEIGHT);
        float explosionPower = 0f;
        if (over > 0) {
            damage += Math.min(MAX_OVER_DAMAGE, (float) over * OVER_DAMAGE_PER_BLOCK);
            radius += Math.min(MAX_OVER_RADIUS, over * OVER_RADIUS_PER_BLOCK);
            explosionPower = Math.min(MAX_EXPLOSION_POWER, BASE_EXPLOSION_POWER + (float) over * EXPLOSION_POWER_PER_BLOCK);
        }
        damage *= data.slamPower;
        radius *= com.bending.skill.SkillStats.ability(player, com.bending.ability.Abilities.METEOR_SLAM.id(), com.bending.skill.SkillStat.SIZE);
        Vec3 at = player.position().add(0, 0.2, 0);

        // The blast is centred a little ahead of you, so a dive carries into what you aimed at.
        Vec3 flat = new Vec3(data.slamDir.x, 0, data.slamDir.z);
        double lean = flat.length(); // 0 = straight down, ~0.9 = shallow dive
        Vec3 forward = lean > 1e-3 ? flat.scale(1 / lean) : Vec3.ZERO;
        Vec3 centre = at.add(forward.scale(radius * 0.35 * lean));
        BendingUtil.fireBurst(player, centre, radius, damage, 1.3 + Math.min(1.0, over * 0.03), 3, null, explosionPower);
        // Every landing kills your momentum: you stop dead where you hit.
        player.setDeltaMovement(0, Math.min(0, player.getDeltaMovement().y), 0);
        player.hurtMarked = true;
        if (explosionPower > 0f) {
            // A crater-making landing also plants you for a beat before you can move.
            data.rootedUntil = level.getGameTime() + ROOT_TICKS;
            tickRooted(player, data);
            level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, at.x, at.y, at.z, 1, 0, 0, 0, 0);
            level.sendParticles(FireFx.wisp(player), at.x, at.y + 1, at.z, 60, radius * 0.3, 1.0, radius * 0.3, 0.25);
            level.sendParticles(ModParticles.FIRE_SMOKE.get(), at.x, at.y + 1.5, at.z, 20, radius * 0.3, 1.0, radius * 0.3, 0.05);
        }
        for (LivingEntity target : BendingUtil.livingAround(player, centre, radius)) {
            Vec3 tv = target.getDeltaMovement().add(forward.scale(0.8 * lean)); // carried along the dive
            target.setDeltaMovement(tv.x, Math.max(tv.y, 0.45), tv.z); // thrown up off their feet
            target.hurtMarked = true;
        }
        BendingUtil.scatterFire(level, BlockPos.containing(at), 3 + (int) (height / 6));

        // The shockwave: a ring of flame rolling out along the ground, plus a second, faster one of sparks.
        // On an angled dive the ring is lopsided: the front of it rolls out further and faster.
        int count = 56;
        for (int i = 0; i < count; i++) {
            double a = i * Math.PI * 2 / count;
            double dx = Math.cos(a), dz = Math.sin(a);
            double ahead = Math.max(0, dx * forward.x + dz * forward.z) * lean;
            level.sendParticles(FireFx.wisp(player), at.x + dx * 0.5, at.y, at.z + dz * 0.5,
                    0, dx, 0.04, dz, (0.45 + radius * 0.06) * (1 + 1.2 * ahead));
            if (i % 2 == 0) {
                level.sendParticles(FireFx.spark(player), at.x, at.y + 0.1, at.z, 0, dx, 0.15, dz, 0.9);
                level.sendParticles(ModParticles.FIRE_SMOKE.get(), at.x + dx * 0.8, at.y, at.z + dz * 0.8,
                        0, dx, 0.05, dz, 0.15);
            }
        }
        // A tongue of flame carried on along the dive.
        if (lean > 0.2) {
            RandomSource random = level.getRandom();
            for (int i = 0; i < 24; i++) {
                double spread = (random.nextDouble() - 0.5) * 0.7;
                Vec3 d = forward.add(-forward.z * spread, 0.05 + random.nextDouble() * 0.15, forward.x * spread);
                level.sendParticles(FireFx.wisp(player), at.x, at.y + 0.2, at.z, 0, d.x, d.y, d.z,
                        0.6 + random.nextDouble() * 0.6);
            }
        }
        // A pillar of flame and embers thrown up from the crater.
        level.sendParticles(FireFx.wisp(player), at.x, at.y + 0.5, at.z, 30, 0.5, 0.8, 0.5, 0.15);
        level.sendParticles(FireFx.ember(player), at.x, at.y + 0.3, at.z, 24, 0.8, 0.3, 0.8, 0.25);
        level.playSound(null, at.x, at.y, at.z, ModSounds.METEOR_SLAM_IMPACT.get(), SoundSource.PLAYERS,
                1.0f + (float) Math.min(1.0, height / 20), 0.7f);
    }

    private static void endInWater(ServerPlayer player, BendingData data) {
        com.bending.ability.BendingAnimations.stop(player);
        ServerLevel level = player.serverLevel();
        data.slamming = false;
        data.noFallUntil = level.getGameTime() + 5;
        level.sendParticles(ModParticles.STEAM.get(), player.getX(), player.getY() + 0.5, player.getZ(), 30, 0.8, 0.4, 0.8, 0.05);
        BendingUtil.fizzle(player);
    }
}
