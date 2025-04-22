package org.chrisgruber.nettank.client.game.entities;

import org.chrisgruber.nettank.common.entities.BulletData; // Use common data
import org.chrisgruber.nettank.common.entities.Entity;
import org.joml.Vector2f;

import java.util.UUID;

public class ClientBullet extends ClientEntity {
    protected UUID id;
    protected long spawnTime;

    public ClientBullet(BulletData data) {
        super(data.getPosition(), data.getVelocity(), data.getRotation(), BulletData.SIZE, BulletData.SIZE, data.getPlayerId(), data.isDestroyed());
        this.id = data.getId();
        this.spawnTime = data.getSpawnTime();
    }

    public UUID getId() {
        return id;
    }

    public long getSpawnTime() {
        return spawnTime;
    }

    @Override
    public void update(float deltaTime) {
        // Calculate change in position based on velocity
        float deltaX = this.getVelocity().x() * deltaTime;
        float deltaY = this.getVelocity().y() * deltaTime;

        // Get the Entity's current position Vector2f
        Vector2f currentPos = this.getPosition(); // Assumes this gets the internal Entity position

        // Calculate the new absolute position by adding the delta
        float newX = currentPos.x + deltaX;
        float newY = currentPos.y + deltaY;

        this.position.set(new Vector2f(newX, newY));
    }

    @Override
    public float getSize() {
        return BulletData.SIZE;
    }

    @Override
    public void updateFromServerEntity(Entity entity) {
        if (entity instanceof BulletData updatedBulletData) {
            this.position = updatedBulletData.getPosition();
            this.velocity = updatedBulletData.getVelocity();
            this.rotation = updatedBulletData.getRotation();
            this.spawnTime = updatedBulletData.getSpawnTime();
            this.isDestroyed = updatedBulletData.isDestroyed();
            this.width = updatedBulletData.getWidth();
            this.height = updatedBulletData.getHeight();
        }
    }
}