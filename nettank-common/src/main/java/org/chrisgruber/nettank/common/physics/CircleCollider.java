package org.chrisgruber.nettank.common.physics;

import org.joml.Vector2f;

public class CircleCollider implements Collider {
    private final Vector2f position;
    private float radius;

    public CircleCollider(Vector2f position, float radius) {
        this.position = new Vector2f(position);
        this.radius = radius;
    }

    @Override
    public boolean collidesWith(Collider other) {
        if (other instanceof CircleCollider) {
            return collidesWithCircle((CircleCollider) other);
        } else if (other instanceof RectangleCollider) {
            return ((RectangleCollider) other).collidesWithCircle(this);
        }
        return false;
    }

    public boolean collidesWithCircle(CircleCollider other) {
        float radiusSum = this.radius + other.getRadius();
        return position.distanceSquared(other.position) < (radiusSum * radiusSum);
    }

    @Override
    public Vector2f getPosition() {
        return new Vector2f(position);
    }

    @Override
    public void setPosition(Vector2f position) {
        this.position.set(position);
    }

    @Override
    public void setRotation(float rotation) {
        // No rotation for circle collider
    }

    public float getRadius() {
        return radius;
    }

    public void setRadius(float radius) {
        this.radius = radius;
    }

    @Override
    public float getBoundingRadius() {
        return radius;
    }
}