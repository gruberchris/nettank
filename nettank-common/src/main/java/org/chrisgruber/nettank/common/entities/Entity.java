package org.chrisgruber.nettank.common.entities;

import org.joml.Vector2f;

public abstract class Entity {

    private final Vector2f position;
    private final Vector2f velocity;
    private float width;
    private float height;

    protected Entity(float x, float y, float width, float height, float velX, float velY) {
        this.position = new Vector2f(x, y);
        this.width = width;
        this.height = height;
        this.velocity = new Vector2f(velX, velY);
    }

    public float getX() {
        return position.x;
    }

    public float getY() {
        return position.y;
    }

    public float getWidth() {
        return width;
    }

    public float getHeight() {
        return height;
    }

    public Vector2f getVelocity() {
        return velocity;
    }

    public float getXVelocity() {
        return velocity.x;
    }

    public float getYVelocity() {
        return velocity.y;
    }

    public Vector2f getPosition() {
        return position;
    }

    public void setPosition(float x, float y) {
        this.position.set(x, y);
    }

    public void setVelocity(float x, float y) {
        this.velocity.set(x, y);
    }

    public void setWidth(float width) {
        this.width = width;
    }

    public void setHeight(float height) {
        this.height = height;
    }

    // This update method is primarily for the SERVER's authoritative simulation
    public abstract void update(float deltaTime);
}