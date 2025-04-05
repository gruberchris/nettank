package org.chrisgruber.nettank.common.entities;

import org.joml.Vector2f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BulletData extends Entity {
    private static final Logger logger = LoggerFactory.getLogger(BulletData.class);
    public static final float SIZE = 15.0f; // Size of the bullet in pixels
    public static final float SPEED = 450.0f; // Pixels per second
    public static final long LIFETIME_MS = 2000; // Max travel time in ms

    protected long spawnTime;

    public BulletData(int playerId, Vector2f position, Vector2f velocity, float rotation, long spawnTime) {
        super(playerId, position, SIZE, SIZE, velocity, rotation);
        this.spawnTime = spawnTime;
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
}