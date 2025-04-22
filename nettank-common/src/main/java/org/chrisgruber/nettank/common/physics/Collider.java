package org.chrisgruber.nettank.common.physics;

import org.joml.Vector2f;

public interface Collider {
    boolean collidesWith(Collider other);
    Vector2f getPosition();
    void setPosition(Vector2f position);
    void setRotation(float rotation);
    float getBoundingRadius();
}