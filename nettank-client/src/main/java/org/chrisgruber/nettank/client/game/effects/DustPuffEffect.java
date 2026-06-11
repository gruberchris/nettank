package org.chrisgruber.nettank.client.game.effects;

import org.joml.Vector2f;
import org.joml.Vector3f;

/**
 * Small terrain-themed particle puff kicked up behind moving tanks: tan dust on
 * DIRT/SAND, dark splatter on MUD, gray for damaged-hull smoke trickles.
 */
public class DustPuffEffect {

    private final Vector2f position;
    private final Vector3f tint;
    private final long createdAtMillis;
    private final long durationMillis;
    private final float renderSize;

    public DustPuffEffect(Vector2f position, Vector3f tint, long durationMillis, float renderSize) {
        this.position = new Vector2f(position);
        this.tint = tint;
        this.createdAtMillis = System.currentTimeMillis();
        this.durationMillis = durationMillis;
        this.renderSize = renderSize;
    }

    public boolean isFinished() {
        return System.currentTimeMillis() - createdAtMillis >= durationMillis;
    }

    private float progress() {
        return Math.min(1.0f, (System.currentTimeMillis() - createdAtMillis) / (float) durationMillis);
    }

    public float getAlpha() {
        return 0.45f * (1.0f - progress());
    }

    // Puffs grow slightly as they dissipate
    public float getCurrentSize() {
        return renderSize * (1.0f + 0.6f * progress());
    }

    public Vector2f getPosition() { return position; }
    public Vector3f getTint() { return tint; }
}
