package org.chrisgruber.nettank.common.entities;

import org.joml.Vector2f;

public class BulletData {

    // --- Constants needed by both Client and Server ---
    public static final float SIZE = 8.0f;
    public static final float SPEED = 350.0f; // Pixels per second
    public static final long LIFETIME_MS = 2000; // Max travel time in ms

    // --- Data Fields ---
    public int ownerId;
    public Vector2f position = new Vector2f();
    public Vector2f velocity = new Vector2f(); // Initial velocity sent by server
    public long spawnTime; // Authoritative server spawn time OR client receive time

    // Constructor used by Server
    public BulletData(int ownerId, float x, float y, float velX, float velY, long serverSpawnTime) {
        this.ownerId = ownerId;
        this.position.set(x, y);
        this.velocity.set(velX, velY);
        this.spawnTime = serverSpawnTime;
    }

    // Constructor used by Client when receiving a SHOOT message
    public BulletData(int ownerId, float startX, float startY, float velX, float velY) {
        this.ownerId = ownerId;
        this.position.set(startX, startY);
        this.velocity.set(velX, velY);
        this.spawnTime = System.currentTimeMillis(); // Client uses local time for prediction expiry
    }

    // Getters
    public Vector2f getPosition() { return position; }
    public int getOwnerId() { return ownerId; }
    public long getSpawnTime() { return spawnTime; }
    public Vector2f getVelocity() { return velocity; }
}