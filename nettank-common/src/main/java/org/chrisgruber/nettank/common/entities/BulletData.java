package org.chrisgruber.nettank.common.entities;

import org.joml.Vector2f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

public class BulletData extends Entity {
    private static final Logger logger = LoggerFactory.getLogger(BulletData.class);
    public static final float SIZE = 15.0f; // Size of the bullet in pixels
    public static final float SPEED = 450.0f; // Pixels per second
    public static final long LIFETIME_MS = 2000; // Max travel time in ms

    protected long spawnTime;
    protected boolean isDestroyed;
    protected UUID id;

    public BulletData(UUID id, int playerId, Vector2f position, Vector2f velocity, float rotation, long spawnTime, boolean isDestroyed) {
        super(playerId, position, SIZE, SIZE, velocity, rotation);
        this.spawnTime = spawnTime;
        this.isDestroyed = isDestroyed;
        this.id = id;
    }

    @Override
    public void update(float deltaTime) {
        // TODO:
    }

    @Override
    public float getSize() {
        return SIZE;
    }

    public long getSpawnTime() { return spawnTime; }

    public void setSpawnTime(long spawnTime) { this.spawnTime = spawnTime; }

    public boolean isDestroyed() { return isDestroyed; }

    public void setDestroyed(boolean destroyed) { this.isDestroyed = destroyed; }

    public UUID getId() { return id; }

    public void setId(UUID id) { this.id = id; }
}