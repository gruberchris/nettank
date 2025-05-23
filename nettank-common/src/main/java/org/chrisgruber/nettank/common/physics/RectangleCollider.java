package org.chrisgruber.nettank.common.physics;

import org.joml.Vector2f;

public class RectangleCollider implements Collider {
    private final Vector2f position;
    private float width;
    private float height;
    private float rotation; // in radians

    public RectangleCollider(Vector2f position, float width, float height, float rotation) {
        this.position = new Vector2f(position);
        this.width = width;
        this.height = height;
        this.rotation = (float) Math.toRadians(rotation);
    }

    @Override
    public boolean collidesWith(Collider other) {
        if (other instanceof CircleCollider) {
            return collidesWithCircle((CircleCollider) other);
        } else if (other instanceof RectangleCollider) {
            // TODO: this is a Axis-Aligned Bounding Box (AABB) check, not a rotated rectangle check
            // Update later with a Separating Axis Theorem (SAT) or similar
            // This implementation will report false positives for rotated rectangles
            return collidesWithRectangle((RectangleCollider) other);
        } else if (other instanceof CapsuleCollider) {
            // Delegate to Capsule's check if it exists, otherwise unsupported
            return other.collidesWith(this);
        }
        return false;
    }

    public boolean collidesWithCircle(CircleCollider circle) {
        // Transform the circle center to rectangle's local space
        float cosA = (float)Math.cos(rotation);
        float sinA = (float)Math.sin(rotation);

        Vector2f circlePos = circle.getPosition();
        float dx = circlePos.x - position.x;
        float dy = circlePos.y - position.y;

        // Rotate point to align with rectangle axes
        float localX = dx * cosA + dy * sinA;
        float localY = -dx * sinA + dy * cosA;

        // Find the closest point on rectangle to circle center
        float halfWidth = width / 2;
        float halfHeight = height / 2;
        float closestX = Math.max(-halfWidth, Math.min(halfWidth, localX));
        float closestY = Math.max(-halfHeight, Math.min(halfHeight, localY));

        // Calculate distance from the closest point to circle center
        float distanceX = localX - closestX;
        float distanceY = localY - closestY;
        float distanceSquared = distanceX * distanceX + distanceY * distanceY;

        return distanceSquared < (circle.getRadius() * circle.getRadius());
    }

    public boolean collidesWithRectangle(RectangleCollider other) {
        // Simple implementation - if either rectangle is rotated, this is a very
        // conservative check that will report collisions even when not exactly touching

        // Calculate absolute distances between centers
        float dx = Math.abs(position.x - other.position.x);
        float dy = Math.abs(position.y - other.position.y);

        // Sum of half-widths and half-heights with some padding for rotation
        float paddingFactor = (float) Math.max(
                Math.abs(Math.sin(rotation)) + Math.abs(Math.sin(other.rotation)),
                Math.abs(Math.cos(rotation)) + Math.abs(Math.cos(other.rotation))
        );

        float sumHalfWidths = (width + other.width) / 2 * paddingFactor;
        float sumHalfHeights = (height + other.height) / 2 * paddingFactor;

        // Check for overlap
        return dx < sumHalfWidths && dy < sumHalfHeights;
    }

    @Override
    public Vector2f getPosition() {
        return new Vector2f(position);
    }

    @Override
    public void setPosition(Vector2f position) {
        this.position.set(position);
    }

    public float getWidth() {
        return width;
    }

    public float getHeight() {
        return height;
    }

    public float getRotation() {
        return rotation;
    }

    public void setWidth(float width) {
        this.width = width;
    }

    public void setHeight(float height) {
        this.height = height;
    }

    public void setRotation(float rotation) {
        this.rotation = rotation;
    }

    @Override
    public float getBoundingRadius() {
        // Return half the diagonal of the rectangle as a conservative estimate
        return (float) Math.sqrt(width * width + height * height) / 2f;
    }
}