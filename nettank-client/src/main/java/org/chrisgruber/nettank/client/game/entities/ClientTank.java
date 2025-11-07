package org.chrisgruber.nettank.client.game.entities;

import org.chrisgruber.nettank.common.entities.Entity;
import org.chrisgruber.nettank.common.entities.TankData; // Use common data
import org.joml.Vector2f;
import org.joml.Vector3f;

public class ClientTank extends ClientEntity {
    protected String name;
    protected Vector3f color;
    protected int hitPoints;
    protected long cooldownRemainingMs = 0;
    protected long cooldownReceivedTime = 0;

    public ClientTank(TankData data) {
        super(data.getPosition(), data.getVelocity(), data.getRotation(), TankData.SIZE, TankData.SIZE, data.getPlayerId(), data.isDestroyed());
        this.name = data.getPlayerName();
        this.color = data.getColor();
        this.hitPoints = data.getHitPoints();
    }

    public String getName() { return this.name; }
    public Vector3f getColor() { return this.color; }
    public int getHitPoints() { return this.hitPoints; }
    
    public void setCooldown(long cooldownMs) {
        this.cooldownRemainingMs = cooldownMs;
        this.cooldownReceivedTime = System.currentTimeMillis();
    }
    
    public long getCooldownRemaining() {
        if (cooldownRemainingMs <= 0) return 0;
        long elapsed = System.currentTimeMillis() - cooldownReceivedTime;
        long remaining = cooldownRemainingMs - elapsed;
        return Math.max(0, remaining);
    }

    // TODO: re-evaluate if this is really necessary or if there is a better way to update state on PLAYER_UPDATE
    public void HandlerPlayerUpdateMessage(Vector2f position, float rotation)
    {
        this.position.set(position);
        this.rotation = rotation;
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

    @Override
    public void updateFromServerEntity(Entity entity) {
        if (entity instanceof TankData updatedTankData) {
            this.position = updatedTankData.getPosition();
            this.velocity = updatedTankData.getVelocity();
            this.rotation = updatedTankData.getRotation();
            this.hitPoints = updatedTankData.getHitPoints();
            this.color = updatedTankData.getColor();
            this.isDestroyed = updatedTankData.isDestroyed();
            this.name = updatedTankData.getPlayerName();
            this.width = updatedTankData.getWidth();
            this.height = updatedTankData.getHeight();
        }
    }
}