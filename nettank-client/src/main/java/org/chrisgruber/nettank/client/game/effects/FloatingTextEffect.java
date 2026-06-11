package org.chrisgruber.nettank.client.game.effects;

import org.joml.Vector2f;
import org.joml.Vector3f;

/**
 * Short text ("-1 ARMOR (REAR)", "-2 HP", "CRIT!") drifting upward in world space
 * and fading out. Drawn via UIManager after projecting world -> screen coordinates.
 */
public class FloatingTextEffect {

    private static final float DRIFT_SPEED = 28.0f; // px/s upward in world space

    private final String text;
    private final Vector2f worldPosition;
    private final Vector3f color;
    private final long startTimeMillis;
    private final long durationMillis;

    public FloatingTextEffect(String text, Vector2f worldPosition, Vector3f color, long durationMillis) {
        this.text = text;
        this.worldPosition = new Vector2f(worldPosition);
        this.color = color;
        this.startTimeMillis = System.currentTimeMillis();
        this.durationMillis = durationMillis;
    }

    public boolean isFinished() {
        return System.currentTimeMillis() - startTimeMillis >= durationMillis;
    }

    private float progress() {
        return Math.min(1.0f, (System.currentTimeMillis() - startTimeMillis) / (float) durationMillis);
    }

    public Vector2f getCurrentWorldPosition() {
        float elapsedSeconds = (System.currentTimeMillis() - startTimeMillis) / 1000.0f;
        return new Vector2f(worldPosition.x, worldPosition.y + DRIFT_SPEED * elapsedSeconds);
    }

    // Fades out over the back half of the lifetime
    public float getAlpha() {
        float p = progress();
        return p < 0.5f ? 1.0f : 1.0f - (p - 0.5f) * 2.0f;
    }

    public String getText() { return text; }
    public Vector3f getColor() { return color; }
}
