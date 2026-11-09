package com.bending.client.particle;

import org.joml.Vector3f;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.util.Mth;

/**
 * Cel-shaded bending particles (art style "A"). The textures are greyscale; this class colours them.
 *
 * Fire glows at full brightness and burns from white-hot through yellow, orange and red before fading.
 * Flame tongues animate and turn to point the way they're moving, so a fireball's trail streams
 * behind it like a comet. Each {@link Kind} sets size, lifetime and motion.
 */
public class CelParticle extends TextureSheetParticle {
    public enum Kind { WISP, CORE, SPARK, EMBER, SMOKE, STEAM, ARC, CLOUD }

    /** Fire colour over a particle's life: age 0 = white-hot, age 1 = dying ember red. */
    private static final float[][] RAMP = {
            {0.00f, 1.00f, 0.99f, 0.88f},
            {0.20f, 1.00f, 0.87f, 0.35f},
            {0.45f, 1.00f, 0.55f, 0.12f},
            {0.72f, 0.84f, 0.20f, 0.07f},
            {1.00f, 0.27f, 0.07f, 0.05f},
    };

    /** Blue Fire: white-hot through cyan and deep blue. */
    private static final float[][] BLUE_RAMP = {
            {0.00f, 0.93f, 0.98f, 1.00f},
            {0.20f, 0.55f, 0.86f, 1.00f},
            {0.45f, 0.24f, 0.55f, 1.00f},
            {0.72f, 0.13f, 0.24f, 0.86f},
            {1.00f, 0.06f, 0.06f, 0.30f},
    };

    private final SpriteSet sprites;
    private final boolean blue;
    private final Kind kind;
    private final float startSize;
    private final float endSize;
    /** Fraction of the colour ramp this particle burns through (smaller = stays hotter). */
    private final float rampSpan;
    private final int frameOffset;
    private final float spin;
    /** Direction a moving flame was launched in (it keeps pointing that way), or null if it just rises. */
    private final Vector3f launchDir;

    public CelParticle(ClientLevel level, double x, double y, double z, double xd, double yd, double zd,
                       SpriteSet sprites, Kind kind) {
        this(level, x, y, z, xd, yd, zd, sprites, kind, false);
    }

    public CelParticle(ClientLevel level, double x, double y, double z, double xd, double yd, double zd,
                       SpriteSet sprites, Kind kind, boolean blue) {
        super(level, x, y, z);
        this.sprites = sprites;
        this.blue = blue;
        this.kind = kind;
        this.xd = xd + (random.nextFloat() - 0.5f) * 0.01f;
        this.yd = yd + (random.nextFloat() - 0.5f) * 0.01f;
        this.zd = zd + (random.nextFloat() - 0.5f) * 0.01f;
        this.hasPhysics = false;
        this.frameOffset = random.nextInt(4);
        float spinRoll = 0f;

        switch (kind) {
            case WISP -> {
                lifetime = 8 + random.nextInt(6);
                startSize = 0.22f + random.nextFloat() * 0.1f;
                endSize = 0.06f;
                rampSpan = 1f;
                friction = 0.9f;
                gravity = -0.3f; // flames rise
            }
            case CORE -> {
                lifetime = 3 + random.nextInt(3);
                startSize = 0.42f + random.nextFloat() * 0.1f;
                endSize = 0.3f;
                rampSpan = 0.35f; // stays white-yellow
                friction = 0.6f;
                gravity = 0f;
            }
            case SPARK -> {
                lifetime = 4 + random.nextInt(4);
                startSize = 0.12f;
                endSize = 0.03f;
                rampSpan = 0.4f;
                friction = 0.85f;
                gravity = 0f;
                spinRoll = (random.nextFloat() - 0.5f) * 0.6f;
            }
            case EMBER -> {
                lifetime = 15 + random.nextInt(11);
                startSize = 0.07f;
                endSize = 0.04f;
                rampSpan = 0.75f;
                friction = 0.98f;
                gravity = 0.4f; // embers fall
                hasPhysics = true;
            }
            case ARC -> {
                // Mostly quick flashes, with a dirty tail of stray sparks that linger and drop.
                boolean stray = random.nextFloat() < 0.3f;
                lifetime = stray ? 8 + random.nextInt(10) : 3 + random.nextInt(4);
                startSize = (stray ? 0.05f : 0.08f) + random.nextFloat() * 0.05f;
                endSize = 0.02f;
                rampSpan = 0f;
                friction = stray ? 0.88f : 0.7f;
                gravity = stray ? 0.4f : 0f;
                spinRoll = (random.nextFloat() - 0.5f) * 1.2f;
            }
            case CLOUD -> {
                // A rolling ball of flame: starts as a fist-sized puff and swells to 3+ blocks
                // as it slows, drifting upward and burning down from yellow to deep red.
                lifetime = 28 + random.nextInt(18);
                startSize = 0.55f + random.nextFloat() * 0.35f;
                endSize = 2.8f + random.nextFloat() * 1.0f;
                rampSpan = 0.85f;
                friction = 0.95f;
                gravity = -0.06f;
                spinRoll = (random.nextFloat() - 0.5f) * 0.12f;
            }
            case SMOKE -> {
                lifetime = 20 + random.nextInt(16);
                startSize = 0.2f;
                endSize = 0.5f;
                rampSpan = 0f;
                friction = 0.92f;
                gravity = -0.1f;
                spinRoll = (random.nextFloat() - 0.5f) * 0.08f;
                setColor(0.24f, 0.22f, 0.22f);
            }
            default -> { // STEAM
                lifetime = 15 + random.nextInt(11);
                startSize = 0.15f;
                endSize = 0.45f;
                rampSpan = 0f;
                friction = 0.9f;
                gravity = -0.25f;
                spinRoll = (random.nextFloat() - 0.5f) * 0.08f;
                setColor(0.92f, 0.94f, 0.96f);
            }
        }
        this.spin = spinRoll;
        double speed = Math.sqrt(xd * xd + yd * yd + zd * zd);
        if (kind == Kind.WISP && speed > 0.02) {
            // A thrown flame: it keeps pointing the way it was launched and doesn't float up.
            launchDir = new Vector3f((float) (xd / speed), (float) (yd / speed), (float) (zd / speed));
            gravity = 0f;
        } else {
            launchDir = null;
        }
        this.roll = kind == Kind.WISP ? 0f : random.nextFloat() * Mth.TWO_PI;
        if (kind == Kind.WISP) {
            pointAlongMotion(true);
        }
        this.oRoll = this.roll;
        this.quadSize = startSize;
        updateLook();
    }

    private boolean isFire() {
        return kind != Kind.SMOKE && kind != Kind.STEAM;
    }

    @Override
    public void tick() {
        super.tick();
        if (removed) {
            return;
        }
        oRoll = roll;
        if (kind == Kind.WISP) {
            pointAlongMotion(false);
        } else {
            roll += spin;
        }
        updateLook();
    }

    /** Sprite frame, colour and transparency for the current age. */
    private void updateLook() {
        float age = Math.min(1f, this.age / (float) lifetime);
        if (kind == Kind.WISP) {
            setSprite(sprites.get((frameOffset + this.age / 2) % 4, 3)); // flicker through 4 frames
        } else if (this.age == 0) {
            pickSprite(sprites); // smoke and steam pick one of their puff shapes
        }
        if (kind == Kind.ARC) {
            // White-hot flash fading to electric blue.
            setColor(Mth.lerp(age, 1f, 0.55f), Mth.lerp(age, 1f, 0.78f), 1f);
            alpha = 1f - smoothstep(0.6f, 1f, age);
        } else if (kind == Kind.CLOUD) {
            float[] c = rampColor(blue ? BLUE_RAMP : RAMP, 0.12f + age * rampSpan);
            setColor(c[0], c[1], c[2]);
            alpha = 0.85f * (1f - smoothstep(0.5f, 1f, age));
        } else if (isFire()) {
            float[] c = rampColor(blue ? BLUE_RAMP : RAMP, age * rampSpan);
            float pulse = kind == Kind.EMBER ? 0.8f + 0.2f * Mth.sin(this.age * 1.3f) : 1f;
            setColor(c[0] * pulse, c[1] * pulse, c[2] * pulse);
            alpha = 1f - smoothstep(0.7f, 1f, age);
        } else {
            alpha = (kind == Kind.SMOKE ? 0.85f : 0.7f) * (1f - smoothstep(0.4f, 1f, age));
        }
    }

    /**
     * Turns the flame so its tip points the way it's drifting, as seen from the camera.
     * Slow or still flames stand upright and flicker.
     */
    private void pointAlongMotion(boolean snap) {
        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        Vector3f left = camera.getLeftVector();
        Vector3f up = camera.getUpVector();
        double dx = launchDir != null ? launchDir.x() : xd;
        double dy = launchDir != null ? launchDir.y() : yd;
        double dz = launchDir != null ? launchDir.z() : zd;
        double sx = -(dx * left.x() + dy * left.y() + dz * left.z()); // screen right
        double sy = dx * up.x() + dy * up.y() + dz * up.z();          // screen up
        // Launched flames point along their path unless it runs straight at or away from the camera.
        double threshold = launchDir != null ? 0.02 : 0.0004;
        float target = (sx * sx + sy * sy) > threshold ? (float) Math.atan2(-sx, sy) : 0f;
        target += (random.nextFloat() - 0.5f) * 0.15f; // a little flicker
        // Turn the short way round so the flame never spins a full circle in one frame.
        float diff = target - roll;
        diff -= Mth.TWO_PI * Math.round(diff / Mth.TWO_PI);
        roll += snap ? diff : diff * 0.6f;
    }

    private static float[] rampColor(float[][] ramp, float age) {
        for (int i = 1; i < ramp.length; i++) {
            if (age <= ramp[i][0]) {
                float[] a = ramp[i - 1];
                float[] b = ramp[i];
                float t = (age - a[0]) / (b[0] - a[0]);
                return new float[] {Mth.lerp(t, a[1], b[1]), Mth.lerp(t, a[2], b[2]), Mth.lerp(t, a[3], b[3])};
            }
        }
        float[] last = ramp[ramp.length - 1];
        return new float[] {last[1], last[2], last[3]};
    }

    private static float smoothstep(float from, float to, float x) {
        float t = Mth.clamp((x - from) / (to - from), 0f, 1f);
        return t * t * (3 - 2 * t);
    }

    @Override
    public float getQuadSize(float partialTicks) {
        float age = Mth.clamp((this.age + partialTicks) / lifetime, 0f, 1f);
        return Mth.lerp(age, startSize, endSize);
    }

    @Override
    protected int getLightColor(float partialTick) {
        // Fire lights itself up; smoke and steam take the world's light.
        return isFire() ? LightTexture.FULL_BRIGHT : super.getLightColor(partialTick);
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }
}
