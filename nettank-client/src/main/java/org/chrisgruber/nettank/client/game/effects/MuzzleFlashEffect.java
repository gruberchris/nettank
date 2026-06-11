package org.chrisgruber.nettank.client.game.effects;

import org.chrisgruber.nettank.client.engine.graphics.Texture;
import org.joml.Vector2f;

import java.util.List;

/**
 * Brief flash at the turret tip when a shot is fired, rotated to the fire direction.
 * PLACEHOLDER ART: reuses the first explosion frames until a muzzle flash sheet exists.
 */
public class MuzzleFlashEffect {

    private final Vector2f position;
    private final float rotationDegrees;
    private final long startTimeMillis;
    private final long durationMillis;
    private final List<Texture> frameTextures;
    private final float renderSize;

    private int currentFrameIndex;
    private boolean finished;

    public MuzzleFlashEffect(Vector2f position, float rotationDegrees, long durationMillis,
                             List<Texture> frameTextures, float renderSize) {
        if (frameTextures == null || frameTextures.isEmpty()) {
            throw new IllegalArgumentException("Muzzle flash frame textures list cannot be null or empty.");
        }
        this.position = new Vector2f(position);
        this.rotationDegrees = rotationDegrees;
        this.startTimeMillis = System.currentTimeMillis();
        this.durationMillis = durationMillis;
        this.frameTextures = frameTextures;
        this.renderSize = renderSize;
        this.finished = false;
    }

    public boolean update() {
        if (finished) return true;

        long elapsed = System.currentTimeMillis() - startTimeMillis;
        if (elapsed >= durationMillis) {
            finished = true;
            return true;
        }

        // Only the first few frames of the placeholder sheet read as a flash
        int usableFrames = Math.min(3, frameTextures.size());
        currentFrameIndex = Math.min(usableFrames - 1, (int) ((elapsed / (float) durationMillis) * usableFrames));
        return false;
    }

    public Texture getCurrentFrameTexture() {
        return frameTextures.get(Math.min(currentFrameIndex, frameTextures.size() - 1));
    }

    public Vector2f getPosition() { return position; }
    public float getRotationDegrees() { return rotationDegrees; }
    public float getRenderSize() { return renderSize; }
    public boolean isFinished() { return finished; }
}
