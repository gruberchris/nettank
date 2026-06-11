package org.chrisgruber.nettank.common.entities;

import org.chrisgruber.nettank.common.physics.CapsuleCollider;
import org.joml.Vector2f;

import java.util.UUID;

public class BulletData extends Entity {
    public static final float SIZE = 15.0f; // Size of the bullet in pixels
    public static final float COLLISION_RADIUS = SIZE * 0.5f;
    public static final float SPEED = 600.0f; // Pixels per second
    public static final long LIFETIME_MS = 2000; // Max travel time in ms

    protected long spawnTime;
    protected boolean isDestroyed;
    protected UUID id;
    // Captured at fire time so a damage buff expiring mid-flight does not retro-nerf an in-flight shot
    protected int damage;

    public BulletData(UUID id, int playerId, Vector2f position, Vector2f velocity, float rotation, long spawnTime, boolean isDestroyed, int damage) {
        super(playerId, position, SIZE, SIZE, velocity, rotation, new CapsuleCollider(position, SIZE, COLLISION_RADIUS, rotation));
        this.spawnTime = spawnTime;
        this.isDestroyed = isDestroyed;
        this.id = id;
        this.damage = damage;
    }

    public long getSpawnTime() { return spawnTime; }

    public void setSpawnTime(long spawnTime) { this.spawnTime = spawnTime; }

    public boolean isDestroyed() { return isDestroyed; }

    public void setDestroyed(boolean destroyed) { this.isDestroyed = destroyed; }

    public UUID getId() { return id; }

    public void setId(UUID id) { this.id = id; }

    public int getDamage() { return damage; }

    public void setDamage(int damage) { this.damage = damage; }

    @Override
    public float getSize() {
        return SIZE;
    }
}