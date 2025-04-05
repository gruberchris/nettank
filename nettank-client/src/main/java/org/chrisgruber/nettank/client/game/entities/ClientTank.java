package org.chrisgruber.nettank.client.game.entities;

import org.chrisgruber.nettank.common.entities.Entity;
import org.chrisgruber.nettank.common.entities.TankData; // Use common data
import org.joml.Vector2f;
import org.joml.Vector3f;

// Client-side representation of a Tank
public class ClientTank extends Entity {

    private final TankData tankData; // Holds the synchronized state from the server

    public ClientTank(TankData data) {
        // Initialize Entity position/size based on TankData
        super(data.getPlayerId(), data.getPosition(), TankData.SIZE, TankData.SIZE, data.getVelocity(), data.getRotation());
        this.tankData = data;
        updatePositionFromData();
    }

    // Call this after tankData is updated by network messages
    public void updatePositionFromData() {
        this.setPosition(tankData.getX(), tankData.getY());
        this.setWidth(TankData.SIZE);
        this.setHeight(TankData.SIZE);
    }

    public String getName() { return tankData.getPlayerName(); }
    public Vector3f getColor() { return tankData.getColor(); } // Returns mutable ref
    public int getHitPoints() { return tankData.getHitPoints(); }
    public boolean isDestroyed() { return tankData.isDestroyed(); }

    @Override
    public Vector2f getPosition() {
        // Ensure the Entity's position reflects the latest data
        // This might be redundant if position reference is shared OR if updatePositionFromData() is called reliably
        updatePositionFromData();
        return super.getPosition();
    }

    @Override
    public float getRotation() {
        // Return the rotation directly from the authoritative TankData object
        return this.tankData.getRotation();
    }

    @Override
    public void update(float deltaTime) {
        // TODO: Client-side prediction or interpolation logic could go here
        // For now, we rely solely on server updates via addOrUpdateTank/updateTankState
    }

    @Override
    public float getSize() {
        return TankData.SIZE;
    }

    // Provides direct access to the underlying data if needed elsewhere
    public TankData getTankData() {
        return tankData;
    }
}