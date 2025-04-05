package org.chrisgruber.nettank.common.entities;

import org.joml.Vector2f;

public abstract class Entity {

    protected final Vector2f position;
    protected final Vector2f velocity;
    protected float width;
    protected float height;
    protected int playerId;
    protected float rotation;

    protected Entity(int playerId, Vector2f position, float width, float height, Vector2f velocity, float rotation) {
        this.playerId = playerId;
        this.position = position;
        this.width = width;
        this.height = height;
        this.velocity = velocity;
        this.rotation = rotation;
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

    public int getPlayerId() { return playerId; }

    public Vector2f getPosition() {
        return position;
    }

    public float getRotation() { return rotation; }

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

    public void setPlayerId(int playerId) { this.playerId = playerId; }

    public void setRotation(float rotation) { this.rotation = (rotation % 360.0f + 360.0f) % 360.0f; }

    public abstract void update(float deltaTime);

    public abstract float getSize();

    public float getCollisionRadius() {
        return this.getSize() / 2.0f;
    }
}