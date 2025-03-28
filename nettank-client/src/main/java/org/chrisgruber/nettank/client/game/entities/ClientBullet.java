package org.chrisgruber.nettank.client.game.entities;

import org.chrisgruber.nettank.common.entities.BulletData; // Use common data
import org.chrisgruber.nettank.common.entities.Entity;
import org.joml.Vector2f;

public class ClientBullet extends Entity {

    private final BulletData bulletData; // Holds initial state + client spawn time

    public ClientBullet(BulletData data) {
        super(data.position.x, data.position.y, BulletData.SIZE, BulletData.SIZE);
        this.bulletData = data;
        updatePositionFromData();
    }

    // Call this if BulletData state needs to re-sync with Entity state
    public void updatePositionFromData() {
        this.position.set(bulletData.position);
        this.width = BulletData.SIZE;
        this.height = BulletData.SIZE;
    }

    // Client-side prediction update
    public void update(float deltaTime) {
        // Update the position based on initial velocity for prediction
        this.position.add(bulletData.velocity.x * deltaTime, bulletData.velocity.y * deltaTime);
        // Update the bulletData's position as well, so getPosition() is consistent
        bulletData.position.set(this.position);
    }

    // Getters for rendering/logic
    public long getSpawnTime() { return bulletData.spawnTime; }
    public int getOwnerId() { return bulletData.ownerId; }

    // Override getPosition if needed, or rely on Entity's public field
    @Override
    public Vector2f getPosition() {
        // Could ensure bulletData pos matches entity pos here if needed
        return super.getPosition();
    }

    public BulletData getBulletData() {
        return bulletData;
    }
}