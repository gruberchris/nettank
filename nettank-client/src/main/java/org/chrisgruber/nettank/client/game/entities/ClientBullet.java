package org.chrisgruber.nettank.client.game.entities;

import org.chrisgruber.nettank.common.entities.BulletData; // Use common data
import org.chrisgruber.nettank.common.entities.Entity;
import org.joml.Vector2f;

public class ClientBullet extends Entity {

    private final BulletData bulletData; // Holds initial state + client spawn time

    public ClientBullet(BulletData data) {
        super(data.getPlayerId(), data.getPosition(), BulletData.SIZE, BulletData.SIZE, data.getVelocity(), data.getRotation());
        this.bulletData = data;
        updatePositionFromData();
    }

    // Call this if BulletData state needs to re-sync with Entity state
    public void updatePositionFromData() {
        this.setPosition(bulletData.getX(), bulletData.getY());
        this.setWidth(BulletData.SIZE);
        this.setHeight(BulletData.SIZE);
    }

    // Client-side prediction update
    public void update(float deltaTime) {
        // Calculate change in position based on velocity
        float deltaX = bulletData.getXVelocity() * deltaTime;
        float deltaY = bulletData.getYVelocity() * deltaTime;

        // Get the Entity's current position Vector2f
        Vector2f currentPos = this.getPosition(); // Assumes this gets the internal Entity position

        // Calculate the new absolute position by adding the delta
        float newX = currentPos.x + deltaX;
        float newY = currentPos.y + deltaY;

        // Set the Entity's position to the new calculated position
        this.setPosition(newX, newY);

        // Keep the BulletData position synced with the Entity's position
        // (Assuming this is desired for consistency if BulletData is accessed elsewhere)
        bulletData.setPosition(newX, newY);
    }

    @Override
    public float getSize() {
        return BulletData.SIZE;
    }

    // Getters for rendering/logic
    public long getSpawnTime() { return bulletData.getSpawnTime(); }
    public int getOwnerId() { return bulletData.getPlayerId(); }

    public BulletData getBulletData() {
        return bulletData;
    }
}