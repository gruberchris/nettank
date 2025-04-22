package org.chrisgruber.nettank.client.game.entities;

import org.chrisgruber.nettank.common.entities.Entity;
import org.joml.Vector2f;

public abstract class ClientEntity {
    protected Vector2f position;
    protected Vector2f velocity;
    protected float rotation;
    protected float width;
    protected float height;
    protected int playerId;
    protected boolean isDestroyed;

    protected ClientEntity(Vector2f position, Vector2f velocity, float rotation, float width, float height, int playerId, boolean isDestroyed) {
        this.position = position;
        this.velocity = velocity;
        this.rotation = rotation;
        this.width = width;
        this.height = height;
        this.playerId = playerId;
        this.isDestroyed = isDestroyed;
    }

    public Vector2f getPosition() {
        return position;
    }

    public Vector2f getVelocity() {
        return velocity;
    }

    public float getRotation() {
        return rotation;
    }

    public float getWidth() {
        return width;
    }

    public float getHeight() {
        return height;
    }

    public int getPlayerId() {
        return playerId;
    }

    public boolean isDestroyed() {
        return isDestroyed;
    }

    public abstract void update(float deltaTime);

    public abstract float getSize();

    public abstract void updateFromServerEntity(Entity entity);
}
