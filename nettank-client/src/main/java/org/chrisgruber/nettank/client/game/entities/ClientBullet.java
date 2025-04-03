package org.chrisgruber.nettank.client.game.entities;

import org.chrisgruber.nettank.common.entities.BulletData; // Use common data
import org.chrisgruber.nettank.common.entities.Entity;
import org.joml.Vector2f;

public class ClientBullet extends Entity {

    private final BulletData bulletData; // Holds initial state + client spawn time

    public ClientBullet(BulletData data) {
        super(data.position.x, data.position.y, BulletData.SIZE, BulletData.SIZE,
              data.velocity.x, data.velocity.y);

        this.bulletData = data;
        updatePositionFromData();
    }

    // Call this if BulletData state needs to re-sync with Entity state
    public void updatePositionFromData() {
        this.setPosition(bulletData.position.x, bulletData.position.y);
        this.setWidth(BulletData.SIZE);
        this.setHeight(BulletData.SIZE);
    }

    // Client-side prediction update
    public void update(float deltaTime) {
        // Calculate change in position based on velocity
        float deltaX = bulletData.velocity.x * deltaTime;
        float deltaY = bulletData.velocity.y * deltaTime;

        // Get the Entity's current position Vector2f
        Vector2f currentPos = this.getPosition(); // Assumes this gets the internal Entity position

        // Calculate the new absolute position by adding the delta
        float newX = currentPos.x + deltaX;
        float newY = currentPos.y + deltaY;

        // Set the Entity's position to the new calculated position
        this.setPosition(newX, newY);

        // Keep the BulletData position synced with the Entity's position
        // (Assuming this is desired for consistency if BulletData is accessed elsewhere)
        bulletData.position.set(newX, newY);
    }

    // Getters for rendering/logic
    public long getSpawnTime() { return bulletData.spawnTime; }
    public int getOwnerId() { return bulletData.ownerId; }

    public BulletData getBulletData() {
        return bulletData;
    }
}