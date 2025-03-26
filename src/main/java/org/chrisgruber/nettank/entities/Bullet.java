package org.chrisgruber.nettank.entities;

import org.joml.Vector2f;

public class Bullet extends Entity {

    public static final float SIZE = 8.0f;
    public static final float SPEED = 350.0f; // Pixels per second
    public static final float LIFETIME_SECONDS = 2.0f; // Max travel time

    private Vector2f velocity;
    private int ownerId; // ID of the tank that fired it
    private float lifeTimer;

    public Bullet(int ownerId, float startX, float startY, Vector2f velocity) {
        super(startX, startY, SIZE, SIZE);
        this.ownerId = ownerId;
        this.velocity = new Vector2f(velocity); // Ensure we have a copy
        this.lifeTimer = LIFETIME_SECONDS;
    }

    @Override
    public void update(float deltaTime) {
        position.add(velocity.x * deltaTime, velocity.y * deltaTime);
        lifeTimer -= deltaTime;
    }

    public boolean isExpired() {
        return lifeTimer <= 0;
    }

    public int getOwnerId() {
        return ownerId;
    }

    public Vector2f getVelocity() {
        return velocity;
    }
}