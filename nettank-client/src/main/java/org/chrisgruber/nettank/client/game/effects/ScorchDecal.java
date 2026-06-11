package org.chrisgruber.nettank.client.game.effects;

import org.joml.Vector2f;

/**
 * Persistent dark scorch mark left where an explosion happened, so the
 * battlefield visibly tells the story of the fight. Long-lived; callers keep a
 * capped pool and fade out the oldest entries.
 */
public class ScorchDecal {

    public static final long LIFETIME_MS = 60000;
    private static final long FADE_OUT_MS = 5000;

    private final Vector2f position;
    private final float rotationDegrees;
    private final float renderSize;
    private final long createdAtMillis;

    public ScorchDecal(Vector2f position, float rotationDegrees, float renderSize) {
        this.position = new Vector2f(position);
        this.rotationDegrees = rotationDegrees;
        this.renderSize = renderSize;
        this.createdAtMillis = System.currentTimeMillis();
    }

    public boolean isFinished() {
        return System.currentTimeMillis() - createdAtMillis >= LIFETIME_MS;
    }

    public float getAlpha() {
        long age = System.currentTimeMillis() - createdAtMillis;
        long fadeStart = LIFETIME_MS - FADE_OUT_MS;
        if (age <= fadeStart) return 0.55f;
        return Math.max(0.0f, 0.55f * (1.0f - (age - fadeStart) / (float) FADE_OUT_MS));
    }

    public Vector2f getPosition() { return position; }
    public float getRotationDegrees() { return rotationDegrees; }
    public float getRenderSize() { return renderSize; }
}
