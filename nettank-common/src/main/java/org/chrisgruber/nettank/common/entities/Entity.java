package org.chrisgruber.nettank.common.entities;

import org.joml.Vector2f;

// Basic abstract entity with position - no rendering or update logic here
public abstract class Entity {

    // Use public fields for simple data structures, or getters if encapsulation preferred
    public final Vector2f position; // final reference, but Vector2f itself is mutable
    public float width;
    public float height;

    // Constructor
    protected Entity(float x, float y, float width, float height) {
        this.position = new Vector2f(x, y);
        this.width = width;
        this.height = height;
    }

    // Common getters might be useful
    public float getX() {
        return position.x;
    }

    public float getY() {
        return position.y;
    }

    public Vector2f getPosition() {
        return position; // Returns the mutable vector reference
    }

    // Maybe a setter - use with caution as it bypasses physics/collision
    public void setPosition(float x, float y) {
        this.position.set(x, y);
    }

    // This update method is primarily for the SERVER's authoritative simulation
    public abstract void update(float deltaTime);
}