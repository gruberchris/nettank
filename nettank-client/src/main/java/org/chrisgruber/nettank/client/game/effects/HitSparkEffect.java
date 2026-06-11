package org.chrisgruber.nettank.client.game.effects;

import org.chrisgruber.nettank.client.engine.graphics.Texture;
import org.joml.Vector2f;

import java.util.List;

/**
 * Short bright burst rendered at the hull edge of a struck side.
 * PLACEHOLDER ART: reuses scaled explosion frames until a dedicated spark sheet exists.
 */
public class HitSparkEffect {

    private final Vector2f position;
    private final long startTimeMillis;
    private final long durationMillis;
    private final List<Texture> frameTextures;
    private final float renderSize;
    private final boolean critical;

    private int currentFrameIndex;
    private boolean finished;

    public HitSparkEffect(Vector2f position, long durationMillis, List<Texture> frameTextures, float renderSize, boolean critical) {
        if (frameTextures == null || frameTextures.isEmpty()) {
            throw new IllegalArgumentException("Hit spark frame textures list cannot be null or empty.");
        }
        this.position = new Vector2f(position);
        this.startTimeMillis = System.currentTimeMillis();
        this.durationMillis = durationMillis;
        this.frameTextures = frameTextures;
        this.renderSize = renderSize;
        this.critical = critical;
        this.currentFrameIndex = 0;
        this.finished = false;
    }

    public boolean update() {
        if (finished) return true;

        long elapsed = System.currentTimeMillis() - startTimeMillis;
        if (elapsed >= durationMillis) {
            finished = true;
            return true;
        }

        currentFrameIndex = Math.min(frameTextures.size() - 1,
                (int) ((elapsed / (float) durationMillis) * frameTextures.size()));
        return false;
    }

    public Texture getCurrentFrameTexture() {
        if (currentFrameIndex < 0 || currentFrameIndex >= frameTextures.size()) {
            return frameTextures.getLast();
        }
        return frameTextures.get(currentFrameIndex);
    }

    public Vector2f getPosition() { return position; }
    public float getRenderSize() { return renderSize; }
    public boolean isFinished() { return finished; }
    public boolean isCritical() { return critical; }
}
