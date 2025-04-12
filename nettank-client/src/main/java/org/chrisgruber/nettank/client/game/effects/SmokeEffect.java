package org.chrisgruber.nettank.client.game.effects;

import org.chrisgruber.nettank.client.engine.graphics.Texture;
import org.joml.Vector2f;
import java.util.List;

public class SmokeEffect {

    private final Vector2f position;
    private final int playerId; // ID of the player this smoke belongs to
    private final List<Texture> frameTextures;
    private final long frameDurationMillis; // How long EACH frame stays visible
    private final int totalFrames;
    private final float renderSize;

    private int currentFrameIndex;
    private long lastFrameChangeTimeMillis;
    private boolean active; // Controls if the effect is running

    public SmokeEffect(Vector2f position, int playerId, List<Texture> frameTextures, long frameDurationMillis, float renderSize) {
        if (frameTextures == null || frameTextures.isEmpty()) {
            throw new IllegalArgumentException("Frame textures list cannot be null or empty.");
        }
        this.position = new Vector2f(position); // Copy position
        this.playerId = playerId;
        this.frameTextures = frameTextures;
        this.totalFrames = frameTextures.size();
        this.frameDurationMillis = frameDurationMillis;
        this.renderSize = renderSize;

        this.currentFrameIndex = 0;
        this.lastFrameChangeTimeMillis = System.currentTimeMillis();
        this.active = true; // Start active
    }

    /**
     * Updates the animation frame index if enough time has passed.
     */
    public void update() {
        if (!active) {
            return; // Do nothing if stopped
        }

        long now = System.currentTimeMillis();
        if (now - lastFrameChangeTimeMillis >= frameDurationMillis) {
            // Move to the next frame, wrapping around using modulo
            currentFrameIndex = (currentFrameIndex + 1) % totalFrames;
            lastFrameChangeTimeMillis = now; // Reset the timer for the new frame
        }
    }

    /**
     * Stops the effect from updating and rendering.
     */
    public void stop() {
        this.active = false;
    }

    /**
     * Gets the Texture object for the current frame of the animation.
     * @return The Texture for the current frame.
     */
    public Texture getCurrentFrameTexture() {
        if (!active || currentFrameIndex < 0 || currentFrameIndex >= frameTextures.size()) {
            // Return null or a default/transparent texture if inactive or index is bad
            return null;
        }
        return frameTextures.get(currentFrameIndex);
    }

    public Vector2f getPosition() {
        return position;
    }

    public boolean isActive() {
        return active;
    }

    public float getRenderSize() {
        return renderSize;
    }

    public int getPlayerId() {
        return playerId;
    }
}