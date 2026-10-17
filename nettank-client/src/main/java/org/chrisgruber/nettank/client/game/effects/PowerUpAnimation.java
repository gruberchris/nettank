package org.chrisgruber.nettank.client.game.effects;

/**
 * Animation curves for an in-world power-up, as pure functions of its age so they are
 * deterministic and unit-testable. A pickup pops in with an elastic overshoot, hovers
 * over a breathing ground shadow, does a quick coin flip every few seconds, twinkles,
 * and blinks faster and faster as it nears despawn.
 */
public final class PowerUpAnimation {

    public static final long SPAWN_POP_MS = 450;
    public static final long EXPIRY_WARNING_MS = 5000;
    public static final int SPARKLE_COUNT = 3;

    static final float FLIP_INTERVAL_S = 2.6f;
    static final float FLIP_DURATION_S = 0.5f;
    private static final float HOVER_BASE = 5.0f;
    private static final float HOVER_AMPLITUDE = 3.0f;
    private static final float HOVER_HZ = 0.6f;
    private static final float SPARKLE_CYCLE_S = 1.5f;
    private static final float SPARKLE_ON_FRACTION = 0.3f;

    /** Offset from the pickup centre, size and brightness of one twinkle. */
    public record Sparkle(float dx, float dy, float size, float alpha) {}

    private PowerUpAnimation() {}

    /** Elastic pop-in: 0, overshooting to about 1.2, settling at 1. */
    public static float spawnScale(long ageMs) {
        if (ageMs <= 0) return 0.0f;
        if (ageMs >= SPAWN_POP_MS) return 1.0f;
        float q = ageMs / (float) SPAWN_POP_MS - 1.0f;
        float c1 = 2.6f; // overshoot strength (easeOutBack)
        return 1.0f + (c1 + 1.0f) * q * q * q + c1 * q * q;
    }

    /** Height of the token above the ground, in world units. */
    public static float hoverHeight(long ageMs) {
        return HOVER_BASE + HOVER_AMPLITUDE * (float) Math.sin(2.0 * Math.PI * HOVER_HZ * ageMs / 1000.0);
    }

    // The shadow shrinks and fades as the token rises, which reads as hovering from above
    public static float shadowScale(float hoverHeight) {
        return 1.0f - hoverHeight / 30.0f;
    }

    public static float shadowAlpha(float hoverHeight) {
        return 0.5f - hoverHeight / 40.0f;
    }

    /** Horizontal scale for the periodic coin flip: 1 at rest, |cos| through a flip. */
    public static float flipWidth(long ageMs, int id) {
        double t = ageMs / 1000.0 + hash01(id, 17) * FLIP_INTERVAL_S; // de-sync neighbouring pickups
        double local = t % FLIP_INTERVAL_S;
        if (local > FLIP_DURATION_S) return 1.0f;
        double angle = local / FLIP_DURATION_S * 2.0 * Math.PI;
        return (float) Math.max(0.08, Math.abs(Math.cos(angle)));
    }

    /** Opacity: solid until the last EXPIRY_WARNING_MS, then a blink speeding up from 2 Hz to 8 Hz. */
    public static float expiryAlpha(long ageMs, long lifetimeMs) {
        long remaining = lifetimeMs - ageMs;
        if (remaining > EXPIRY_WARNING_MS) return 1.0f;
        if (remaining <= 0) return 0.25f;
        double warningSeconds = EXPIRY_WARNING_MS / 1000.0;
        double t = (EXPIRY_WARNING_MS - remaining) / 1000.0;
        double phase = 2.0 * t + 3.0 * t * t / warningSeconds; // integral of (2 + 6 t / T) Hz
        return phase % 1.0 < 0.5 ? 1.0f : 0.25f;
    }

    /** Gentle pulse for the glow halo. */
    public static float glowPulse(long ageMs) {
        return 0.75f + 0.2f * (float) Math.sin(2.0 * Math.PI * 1.1 * ageMs / 1000.0);
    }

    /** Rotation of the light rays behind x3 power-ups, in degrees. */
    public static float rayRotation(long ageMs) {
        return (ageMs / 1000.0f * 40.0f) % 360.0f;
    }

    /** Twinkle {@code k}: flashes for a moment at a pseudo-random spot, then waits for the next cycle. */
    public static Sparkle sparkle(long ageMs, int id, int k) {
        double t = ageMs / 1000.0 + hash01(id, 100 + k) * SPARKLE_CYCLE_S;
        int cycle = (int) Math.floor(t / SPARKLE_CYCLE_S);
        double local = (t / SPARKLE_CYCLE_S) - cycle;
        float alpha = local < SPARKLE_ON_FRACTION ? (float) Math.sin(Math.PI * local / SPARKLE_ON_FRACTION) : 0.0f;
        double angle = hash01(id * 31 + k, cycle) * 2.0 * Math.PI;
        double radius = 9.0 + 8.0 * hash01(id * 17 + k, cycle + 7);
        float size = 6.0f + 4.0f * (float) hash01(id + k, cycle + 13);
        return new Sparkle((float) (Math.cos(angle) * radius), (float) (Math.sin(angle) * radius), size, alpha);
    }

    // Deterministic hash of two ints to [0, 1)
    static double hash01(int a, int b) {
        long h = a * 0x9E3779B97F4A7C15L + b * 0xC2B2AE3D27D4EB4FL;
        h ^= h >>> 31;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 29;
        return (h >>> 11) / (double) (1L << 53);
    }
}
