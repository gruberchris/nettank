package org.chrisgruber.nettank.entities;

import org.joml.Vector2f;

public abstract class Entity {
    protected Vector2f position;
    protected Vector2f size; // Width, Height

    public Entity(float x, float y, float width, float height) {
        this.position = new Vector2f(x, y);
        this.size = new Vector2f(width, height);
    }

    public Vector2f getPosition() {
        return position;
    }

    public void setPosition(float x, float y) {
        this.position.set(x, y);
    }

    public Vector2f getSize() {
        return size;
    }

    // Simple AABB collision check
    public boolean collidesWith(Entity other) {
        boolean collisionX = this.position.x + this.size.x / 2 > other.position.x - other.size.x / 2 &&
                this.position.x - this.size.x / 2 < other.position.x + other.size.x / 2;
        boolean collisionY = this.position.y + this.size.y / 2 > other.position.y - other.size.y / 2 &&
                this.position.y - this.size.y / 2 < other.position.y + other.size.y / 2;
        return collisionX && collisionY;
    }

    public abstract void update(float deltaTime);

}
