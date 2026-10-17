package org.chrisgruber.nettank.client.game.effects;

import org.joml.Vector2f;
import org.joml.Vector3f;

import java.util.Random;

/**
 * One-shot burst around a power-up: a ring shockwave, a central flash and particles flying
 * out with drag. SPAWN is a small sparkle pop, PICKUP a big burst in the category colour,
 * EXPIRE a grey puff that drifts up. State is a pure function of elapsed time.
 */
public class PowerUpBurstEffect {

    public enum Kind {
        SPAWN(500, 8, 6.0f, 34.0f, 70.0f),
        PICKUP(650, 16, 8.0f, 58.0f, 140.0f),
        EXPIRE(550, 10, 6.0f, 24.0f, 45.0f);

        final long durationMs;
        final int particles;
        final float ringStart;
        final float ringEnd;
        final float particleSpeed;

        Kind(long durationMs, int particles, float ringStart, float ringEnd, float particleSpeed) {
            this.durationMs = durationMs;
            this.particles = particles;
            this.ringStart = ringStart;
            this.ringEnd = ringEnd;
            this.particleSpeed = particleSpeed;
        }
    }

    public record Particle(float x, float y, float size, float alpha) {}

    private static final float DRAG = 5.0f; // 1/s: particles cover most of their distance early
    private static final Vector3f EXPIRE_GREY = new Vector3f(0.75f, 0.75f, 0.72f);

    private final Kind kind;
    private final Vector2f position;
    private final Vector3f color;
    private final long startMillis;
    private final float[] dirX;
    private final float[] dirY;
    private final float[] speed;
    private final float[] baseSize;

    public PowerUpBurstEffect(Kind kind, Vector2f position, Vector3f color, long startMillis, long seed) {
        this.kind = kind;
        this.position = new Vector2f(position);
        this.color = kind == Kind.EXPIRE ? new Vector3f(color).lerp(EXPIRE_GREY, 0.7f) : new Vector3f(color);
        this.startMillis = startMillis;
        int n = kind.particles;
        dirX = new float[n];
        dirY = new float[n];
        speed = new float[n];
        baseSize = new float[n];
        Random random = new Random(seed);
        for (int i = 0; i < n; i++) {
            double angle = (i + random.nextDouble() * 0.6) / n * 2.0 * Math.PI;
            dirX[i] = (float) Math.cos(angle);
            dirY[i] = (float) Math.sin(angle);
            speed[i] = kind.particleSpeed * (0.6f + 0.6f * random.nextFloat());
            baseSize[i] = (kind == Kind.PICKUP ? 9.0f : 7.0f) * (0.7f + 0.6f * random.nextFloat());
        }
    }

    public boolean isFinished(long nowMillis) {
        return nowMillis - startMillis >= kind.durationMs;
    }

    public float progress(long nowMillis) {
        return Math.max(0.0f, Math.min(1.0f, (nowMillis - startMillis) / (float) kind.durationMs));
    }

    public float ringRadius(long nowMillis) {
        float p = progress(nowMillis);
        float eased = 1.0f - (1.0f - p) * (1.0f - p); // ease-out
        return kind.ringStart + (kind.ringEnd - kind.ringStart) * eased;
    }

    public float ringAlpha(long nowMillis) {
        float fade = 1.0f - progress(nowMillis);
        return (kind == Kind.EXPIRE ? 0.5f : 0.9f) * fade * fade;
    }

    /** Bright flash at the centre, strongest at the start of a pickup. */
    public float flashAlpha(long nowMillis) {
        if (kind != Kind.PICKUP) return 0.0f;
        float fade = 1.0f - progress(nowMillis);
        return fade * fade * fade;
    }

    public int particleCount() {
        return kind.particles;
    }

    public Particle particle(int i, long nowMillis) {
        float seconds = Math.max(0.0f, (nowMillis - startMillis) / 1000.0f);
        float distance = speed[i] / DRAG * (1.0f - (float) Math.exp(-DRAG * seconds));
        float rise = kind == Kind.EXPIRE ? 18.0f * seconds : 0.0f; // the puff drifts upward
        float p = progress(nowMillis);
        float size = baseSize[i] * (kind == Kind.EXPIRE ? 1.0f + p : 1.0f - 0.6f * p);
        return new Particle(position.x + dirX[i] * distance, position.y + dirY[i] * distance + rise,
                size, 1.0f - p);
    }

    public Kind getKind() { return kind; }
    public Vector2f getPosition() { return position; }
    public Vector3f getColor() { return color; }
}
