package org.chrisgruber.nettank.client.game.effects;

import org.chrisgruber.nettank.client.engine.graphics.Texture;
import org.joml.Vector2f;
import java.util.List;

public class FlameEffect {

    private final Vector2f position;
    private final long startTimeMillis;
    private final long durationMillis;
    private final int totalFrames;
    private final List<Texture> frameTextures; // Store the list of flame textures

    private int currentFrameIndex;
    private boolean finished;
    private final float renderSize;

    // Constructor takes a List of Flame Textures
    public FlameEffect(Vector2f position, long durationMillis, List<Texture> frameTextures, float renderSize) {
        if (frameTextures == null || frameTextures.isEmpty()) {
            throw new IllegalArgumentException("Flame frame textures list cannot be null or empty.");
        }
        this.position = new Vector2f(position); // Copy position
        this.startTimeMillis = System.currentTimeMillis();
        this.durationMillis = durationMillis;
        this.frameTextures = frameTextures; // Store the reference to the list
        this.totalFrames = frameTextures.size(); // Total frames is the size of the list
        this.currentFrameIndex = 0;
        this.finished = false;
        this.renderSize = renderSize;
    }

    /**
     * Updates the animation frame based on the current time.
     * Returns true if the flame effect is finished, false otherwise.
     */
    public boolean update() {
        if (finished) {
            return true;
        }

        long elapsedTime = System.currentTimeMillis() - startTimeMillis;

        if (elapsedTime >= durationMillis) {
            finished = true;
            return true;
        }

        // Calculate current frame based on elapsed time
        currentFrameIndex = Math.min(totalFrames - 1, (int) ((elapsedTime / (float) durationMillis) * totalFrames));

        return false;
    }

    /**
     * Gets the Texture object for the current frame of the animation.
     * @return The Texture for the current frame.
     */
    public Texture getCurrentFrameTexture() {
        if (currentFrameIndex < 0 || currentFrameIndex >= frameTextures.size()) {
            // Handle potential invalid index - return last frame?
            return frameTextures.getLast();
        }
        return frameTextures.get(currentFrameIndex);
    }

    public Vector2f getPosition() {
        return position;
    }

    public boolean isFinished() {
        return finished;
    }

    public float getRenderSize() {
        return renderSize;
    }
}