package com.bending.entity;

import java.util.ArrayList;
import java.util.List;

import org.joml.Vector3f;

import com.bending.particle.ModParticles;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * A short-lived bolt of lightning between two points: the main bolt, chain jumps, and the little
 * arcs that crackle around your hands while charging. Purely visual (damage is dealt by the ability).
 *
 * Clients draw it in vanilla lightning's glowing style (see LightningArcRenderer) and spray loose
 * sparks along it every tick. The shape re-forks every 2 ticks so it flickers like real lightning.
 */
public class LightningArcEntity extends Entity {
    private static final EntityDataAccessor<Vector3f> END =
            SynchedEntityData.defineId(LightningArcEntity.class, EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<Integer> SEED =
            SynchedEntityData.defineId(LightningArcEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> THICKNESS =
            SynchedEntityData.defineId(LightningArcEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> LIFE =
            SynchedEntityData.defineId(LightningArcEntity.class, EntityDataSerializers.INT);

    public LightningArcEntity(EntityType<? extends LightningArcEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
    }

    /**
     * @param thickness 1 = a full bolt, ~0.7 = chain jump, ~0.3 = a charge-up crackle
     * @param life      ticks it stays visible
     */
    public LightningArcEntity(Level level, Vec3 from, Vec3 to, float thickness, int life) {
        this(ModEntities.LIGHTNING_ARC.get(), level);
        setPos(from.x, from.y, from.z);
        Vec3 end = to.subtract(from);
        entityData.set(END, new Vector3f((float) end.x, (float) end.y, (float) end.z));
        entityData.set(SEED, level.getRandom().nextInt());
        entityData.set(THICKNESS, thickness);
        entityData.set(LIFE, life);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(END, new Vector3f());
        builder.define(SEED, 0);
        builder.define(THICKNESS, 1f);
        builder.define(LIFE, 6);
    }

    public Vec3 end() {
        Vector3f e = entityData.get(END);
        return new Vec3(e.x(), e.y(), e.z());
    }

    public float thickness() {
        return entityData.get(THICKNESS);
    }

    public int life() {
        return entityData.get(LIFE);
    }

    /**
     * The bolt's shape, relative to this entity: the main jagged line first, then forks.
     * {@code variant} changes every couple of ticks so the bolt flickers.
     */
    public List<Vec3[]> shape(int variant) {
        RandomSource random = RandomSource.create(entityData.get(SEED) * 31L + variant);
        Vec3 end = end();
        double length = end.length();
        List<Vec3[]> lines = new ArrayList<>();
        if (length < 1e-3) {
            return lines;
        }
        Vec3 dir = end.scale(1 / length);
        float thick = thickness();
        double jitter = Math.min(0.45, 0.12 + length * 0.04) * (0.6 + 0.6 * thick);
        Vec3[] main = jagged(random, Vec3.ZERO, end, Math.max(2, (int) (length / 0.8)), jitter);
        lines.add(main);

        int forks = thick >= 0.9 ? 3 + random.nextInt(4) : thick >= 0.6 ? 1 + random.nextInt(3) : random.nextInt(2);
        for (int f = 0; f < forks && main.length > 2; f++) {
            Vec3 from = main[1 + random.nextInt(main.length - 2)];
            Vec3 forkDir = dir.scale(0.6).add(randomUnit(random)).normalize();
            double forkLen = (0.6 + random.nextDouble() * 2.0) * Math.max(0.4, thick);
            lines.add(jagged(random, from, from.add(forkDir.scale(forkLen)), 3 + random.nextInt(2), jitter * 0.8));
        }
        return lines;
    }

    private static Vec3[] jagged(RandomSource random, Vec3 from, Vec3 to, int segments, double jitter) {
        Vec3[] points = new Vec3[segments + 1];
        Vec3 span = to.subtract(from);
        Vec3 dir = span.normalize();
        for (int i = 0; i <= segments; i++) {
            Vec3 p = from.add(span.scale(i / (double) segments));
            if (i > 0 && i < segments) {
                Vec3 off = randomUnit(random);
                off = off.subtract(dir.scale(off.dot(dir))); // sideways only
                p = p.add(off.scale(jitter * (0.5 + random.nextDouble())));
            }
            points[i] = p;
        }
        return points;
    }

    private static Vec3 randomUnit(RandomSource random) {
        return new Vec3(random.nextGaussian(), random.nextGaussian(), random.nextGaussian()).normalize();
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide) {
            if (tickCount >= life()) {
                discard();
            }
            return;
        }
        sprayParticles();
    }

    /** Client: lots of loose sparks along the bolt, heaviest in the first ticks, plus a burst where it lands. */
    private void sprayParticles() {
        float thick = thickness();
        int fade = Math.max(0, life() - tickCount);
        if (fade == 0) {
            return;
        }
        RandomSource random = this.random;
        Vec3 origin = position();
        double spacing = thick >= 0.6 ? 0.18 : 0.3;
        int perPoint = tickCount <= 2 ? (thick >= 0.6 ? 3 : 1) : 1;
        for (Vec3[] line : shape(tickCount / 2)) {
            for (int s = 1; s < line.length; s++) {
                Vec3 a = line[s - 1];
                Vec3 seg = line[s].subtract(a);
                int steps = Math.max(1, (int) (seg.length() / spacing));
                for (int i = 0; i < steps; i++) {
                    Vec3 p = origin.add(a).add(seg.scale((i + random.nextDouble()) / steps));
                    for (int k = 0; k < perPoint; k++) {
                        // Loose, dirty sparks: most hang near the bolt, some get flung off.
                        double speed = random.nextFloat() < 0.2 ? 0.25 * thick : 0.05;
                        level().addParticle(ModParticles.LIGHTNING_ARC.get(), p.x, p.y, p.z,
                                random.nextGaussian() * speed, random.nextGaussian() * speed, random.nextGaussian() * speed);
                    }
                }
            }
        }
        // Where a real bolt lands: a burst of sparks, embers and scorched smoke.
        if (thick >= 0.6 && tickCount <= 1) {
            Vec3 hit = origin.add(end());
            for (int i = 0; i < (int) (40 * thick); i++) {
                level().addParticle(ModParticles.LIGHTNING_ARC.get(), hit.x, hit.y, hit.z,
                        random.nextGaussian() * 0.3, random.nextGaussian() * 0.3, random.nextGaussian() * 0.3);
            }
            for (int i = 0; i < 6; i++) {
                level().addParticle(ModParticles.EMBER.get(), hit.x, hit.y, hit.z,
                        random.nextGaussian() * 0.15, random.nextDouble() * 0.2, random.nextGaussian() * 0.15);
                level().addParticle(ModParticles.FIRE_SMOKE.get(), hit.x, hit.y, hit.z,
                        random.nextGaussian() * 0.03, 0.03, random.nextGaussian() * 0.03);
            }
        }
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 128 * 128;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {}

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {}
}
