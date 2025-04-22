package org.chrisgruber.nettank.common.physics;

import org.joml.Vector2f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CapsuleCollider implements Collider {
    private static final Logger logger = LoggerFactory.getLogger(CapsuleCollider.class);
    private final Vector2f position;
    private float length;    // Length of the capsule (excluding hemisphere ends)
    private float radius;    // Radius of the capsule
    private float rotation;  // in radians

    public CapsuleCollider(Vector2f position, float length, float radius, float rotation) {
        this.position = new Vector2f(position);
        this.length = Math.max(0.0f, length - (2 * radius));
        this.radius = radius;
        this.rotation = (float) Math.toRadians(rotation);
    }

    @Override
    public boolean collidesWith(Collider other) {
        if (other instanceof CircleCollider) {
            return collidesWithCircle((CircleCollider) other);
        } else if (other instanceof CapsuleCollider) {
            return collidesWithCapsule((CapsuleCollider) other);
        } else if (other instanceof RectangleCollider) {
            // A complex case - simplified for now
            return other.collidesWith(this);
        }
        return false;
    }

    public boolean collidesWithCircle(CircleCollider circle) {
        // Calculate the capsule's end points
        Vector2f endOffset = new Vector2f(
                (float)(-Math.sin(rotation) * length / 2),    // Use -sin for X
                (float)(Math.cos(rotation) * length / 2)      // Use cos for Y
        );

        Vector2f capsuleStart = new Vector2f(position).sub(endOffset);
        Vector2f capsuleEnd = new Vector2f(position).add(endOffset);

        // Find the closest point on the line segment to the circle center
        Vector2f circleCenter = circle.getPosition();
        Vector2f line = new Vector2f(capsuleEnd).sub(capsuleStart);
        float lineLength = line.length();

        // Normalize line direction
        Vector2f lineDir = new Vector2f(line).div(lineLength);

        // Project the circle center onto line
        Vector2f circleToLineStart = new Vector2f(circleCenter).sub(capsuleStart);
        float projection = circleToLineStart.dot(lineDir);

        // Clamp projection to line segment
        projection = Math.max(0, Math.min(lineLength, projection));

        // Find the closest point on the line
        Vector2f closestPoint = new Vector2f(capsuleStart).add(
                new Vector2f(lineDir).mul(projection));

        // Check the distance between the circle center and the closest point
        float distance = circleCenter.distance(closestPoint);
        return distance < (circle.getRadius() + this.radius);
    }

    public boolean collidesWithCapsule(CapsuleCollider other) {
        Vector2f thisPos = new Vector2f(position);
        Vector2f otherPos = new Vector2f(other.position);

        logger.trace("Capsule positions: this={} other={}", thisPos, otherPos);
        logger.trace("Capsule rotations (radian): this={} other={}", rotation, other.rotation);
        logger.trace("Capsule dimensions: thisLen={} thisRad={} otherLen={} otherRad={}", length, radius, other.length, other.radius);


        // Calculate offsets using the game's convention (-sin for X, cos for Y)
        var thisEndOffset = new Vector2f(
                (float)(-Math.sin(rotation) * length / 2),  // Use -sin for X
                (float)(Math.cos(rotation) * length / 2)    // Use cos for Y
        );

        var otherEndOffset = new Vector2f(
                (float)(-Math.sin(other.rotation) * other.length / 2), // Use -sin for X
                (float)(Math.cos(other.rotation) * other.length / 2)  // Use cos for Y
        );

        Vector2f thisStart = new Vector2f(thisPos).sub(thisEndOffset);
        Vector2f thisEnd = new Vector2f(thisPos).add(thisEndOffset);

        Vector2f otherStart = new Vector2f(otherPos).sub(otherEndOffset);
        Vector2f otherEnd = new Vector2f(otherPos).add(otherEndOffset);

        // Find the closest distance between the two line segments
        float distSquared = closestDistanceSquaredBetweenSegments(
                thisStart, thisEnd, otherStart, otherEnd);

        // Check if the distance is less than the sum of radii
        float sumRadii = this.radius + other.radius;

        logger.trace("Closest distance squared: {} Sum of radii squared: {}", distSquared, sumRadii * sumRadii);

        boolean collides = distSquared < (sumRadii * sumRadii);

        return collides;
    }

    // Helper method to find the closest distance squared between two line segments
    private float closestDistanceSquaredBetweenSegments(
            Vector2f p1, Vector2f q1, Vector2f p2, Vector2f q2) {

        Vector2f d1 = new Vector2f(q1).sub(p1); // Direction vector of segment 1
        Vector2f d2 = new Vector2f(q2).sub(p2); // Direction vector of segment 2
        Vector2f r = new Vector2f(p1).sub(p2);

        float a = d1.dot(d1); // Squared length of segment 1
        float e = d2.dot(d2); // Squared length of segment 2
        float f = d2.dot(r);

        // Check if either segment is just a point
        if (a <= 1e-6f && e <= 1e-6f) {
            return p1.distanceSquared(p2);
        }

        float s, t;

        if (a <= 1e-6f) {
            s = 0.0f;
            t = f / e;
            t = Math.max(0.0f, Math.min(1.0f, t));
        } else {
            float c = d1.dot(r);
            if (e <= 1e-6f) {
                t = 0.0f;
                s = Math.max(0.0f, Math.min(1.0f, -c / a));
            } else {
                float b = d1.dot(d2);
                float denom = a * e - b * b;

                if (denom != 0.0f) {
                    s = Math.max(0.0f, Math.min(1.0f, (b * f - c * e) / denom));
                } else {
                    s = 0.0f;
                }

                t = (b * s + f) / e;

                if (t < 0.0f) {
                    t = 0.0f;
                    s = Math.max(0.0f, Math.min(1.0f, -c / a));
                } else if (t > 1.0f) {
                    t = 1.0f;
                    s = Math.max(0.0f, Math.min(1.0f, (b - c) / a));
                }
            }
        }

        Vector2f c1 = new Vector2f(p1).add(new Vector2f(d1).mul(s));
        Vector2f c2 = new Vector2f(p2).add(new Vector2f(d2).mul(t));

        return c1.distanceSquared(c2);
    }

    @Override
    public Vector2f getPosition() {
        return new Vector2f(position);
    }

    @Override
    public void setPosition(Vector2f position) {
        this.position.set(position);
    }

    public float getLength() {
        return length;
    }

    public float getRadius() {
        return radius;
    }

    public float getRotation() {
        return rotation;
    }

    public void setLength(float length) {
        this.length = length;
    }

    public void setRadius(float radius) {
        this.radius = radius;
    }

    public void setRotation(float rotation) {
        this.rotation = (float) Math.toRadians(rotation);
    }

    @Override
    public float getBoundingRadius() {
        // Return half the length plus radius
        return length / 2f + radius;
    }
}