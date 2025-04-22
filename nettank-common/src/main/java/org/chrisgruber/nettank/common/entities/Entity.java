package org.chrisgruber.nettank.common.entities;

import org.chrisgruber.nettank.common.physics.Collider;
import org.joml.Vector2f;

public abstract class Entity {

    protected final Vector2f position;
    protected final Vector2f velocity;
    protected float width;
    protected float height;
    protected int playerId;
    protected float rotation;
    protected Collider collider;

    protected Entity(int playerId, Vector2f position, float width, float height, Vector2f velocity, float rotation, Collider collider) {
        this.playerId = playerId;
        this.position = position;
        this.width = width;
        this.height = height;
        this.velocity = velocity;
        this.rotation = rotation;
        this.collider = collider;
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

    public Collider getCollider() {
        return collider;
    }

    public void setPosition(Vector2f position) {
        this.position.set(position);
        this.collider.setPosition(position);
    }

    public void setVelocity(Vector2f velocity) {
        this.velocity.set(velocity);
    }

    public void setWidth(float width) {
        this.width = width;
    }

    public void setHeight(float height) {
        this.height = height;
    }

    public void setPlayerId(int playerId) { this.playerId = playerId; }

    public void setRotation(float rotation) {
        this.rotation = normalizeRotationInDegrees(rotation);
        this.collider.setRotation(this.rotation);
    }

    public abstract float getSize();

    public float getBoundingRadius() {
        return collider.getBoundingRadius();
    }

    protected float normalizeRotationInDegrees(float rotation) {
        return (rotation % 360.0f + 360.0f) % 360.0f;
    }
}