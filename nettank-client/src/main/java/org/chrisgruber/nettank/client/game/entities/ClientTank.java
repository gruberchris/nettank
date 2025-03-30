package org.chrisgruber.nettank.client.game.entities;

import org.chrisgruber.nettank.common.entities.Entity;
import org.chrisgruber.nettank.common.entities.TankData; // Use common data
import org.joml.Vector2f;
import org.joml.Vector3f;

// Client-side representation of a Tank
public class ClientTank extends Entity {

    private final TankData tankData; // Holds the synchronized state from the server

    // Maybe add client-specific visual state if needed (e.g., animation frame)

    public ClientTank(TankData data) {
        // Initialize Entity position/size based on TankData
        // Note: Entity constructor uses center pos, but TankData has center pos. SIZE is static.
        super(data.position.x, data.position.y, TankData.SIZE, TankData.SIZE);
        this.tankData = data;
        // Ensure Entity's position reference points to the same Vector2f as TankData's
        // This way, when TankData.position is updated, the Entity's position is too.
        // Or, implement an update method here that copies TankData position to Entity position.
        // Let's assume direct reference works for now (Entity uses public position):
        // this.position = data.position; // Make Entity use TankData's position directly? Risky if Entity modifies it.
        // Safer: Update position explicitly when data changes
        updatePositionFromData();
    }

    // Call this after tankData is updated by network messages
    public void updatePositionFromData() {
        this.position.set(tankData.position);
        // Update width/height if they can change? (unlikely for tank)
        this.width = TankData.SIZE;
        this.height = TankData.SIZE;
    }

    // Getters that expose data needed for rendering or logic
    // Delegate to the contained TankData
    public int getPlayerId() { return tankData.playerId; }
    public String getName() { return tankData.name; }
    public float getRotation() { return tankData.rotation; }
    public Vector3f getColor() { return tankData.color; } // Returns mutable ref
    public int getLives() { return tankData.getLives(); }
    public boolean isAlive() { return tankData.isAlive(); }

    // Override getPosition to ensure it uses the potentially updated data
    @Override
    public Vector2f getPosition() {
        // Ensure the Entity's position reflects the latest data
        // This might be redundant if position reference is shared OR if updatePositionFromData() is called reliably
        updatePositionFromData();
        return super.getPosition();
    }

    @Override
    public void update(float deltaTime) {
        // TODO: Client-side prediction or interpolation logic could go here
        // For now, we rely solely on server updates via addOrUpdateTank/updateTankState
    }


    // Provides direct access to the underlying data if needed elsewhere
    public TankData getTankData() {
        return tankData;
    }
}