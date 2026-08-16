package org.chrisgruber.nettank.client.game.entities;

import org.chrisgruber.nettank.common.entities.Entity;
import org.chrisgruber.nettank.common.entities.TankData; // Use common data
import org.joml.Vector2f;
import org.joml.Vector3f;

public class ClientTank extends ClientEntity {
    protected String name;
    protected Vector3f color;
    protected int hitPoints;
    protected float turretRotation;
    protected org.chrisgruber.nettank.common.entities.TankType tankType = org.chrisgruber.nettank.common.entities.TankType.STANDARD;
    protected long cooldownRemainingMs = 0;
    protected long cooldownReceivedTime = 0;

    // Cloak rendering: alpha eases toward targetAlpha (1 visible, 0 hidden, 0.5 own cloak)
    protected float alpha = 1.0f;
    protected float targetAlpha = 1.0f;

    // Firing recoil animation (barrel kickback and recovery)
    protected float recoilOffset = 0.0f;
    protected static final float MAX_RECOIL_OFFSET = 5.5f;
    protected static final float RECOIL_RECOVERY_SPEED = 32.0f;

    // Track movement animation
    protected float trackDistance = 0.0f;
    protected final Vector2f lastTrackPosition = new Vector2f();
    protected float lastTrackRotation = 0.0f;

    // Hit-confirm flash (white for normal hits, gold for crits) and respawn shimmer
    protected long hitFlashUntilMillis = 0;
    protected boolean hitFlashGold = false;
    protected long respawnShimmerStartMillis = 0;

    public ClientTank(TankData data) {
        super(data.getPosition(), data.getVelocity(), data.getRotation(), TankData.SIZE, TankData.SIZE, data.getPlayerId(), data.isDestroyed());
        this.name = data.getPlayerName();
        this.color = data.getColor();
        this.hitPoints = data.getHitPoints();
        this.turretRotation = data.getTurretRotation();
        this.tankType = data.getTankType();
        this.lastTrackPosition.set(data.getPosition());
        this.lastTrackRotation = data.getRotation();
    }

    public String getName() { return this.name; }
    public Vector3f getColor() { return this.color; }
    public int getHitPoints() { return this.hitPoints; }
    public void setHitPoints(int hitPoints) { this.hitPoints = hitPoints; }
    public void applyDamage(int damage) { this.hitPoints = Math.max(0, this.hitPoints - damage); }
    public float getTurretRotation() { return this.turretRotation; }
    public void setTurretRotation(float turretRotation) { this.turretRotation = turretRotation; }
    public org.chrisgruber.nettank.common.entities.TankType getTankType() { return this.tankType; }
    public void setTankType(org.chrisgruber.nettank.common.entities.TankType tankType) { this.tankType = tankType; }
    public float getAlpha() { return this.alpha; }
    public float getTargetAlpha() { return this.targetAlpha; }
    public void setTargetAlpha(float targetAlpha) { this.targetAlpha = targetAlpha; }

    public void triggerHitFlash(boolean gold, long durationMs) {
        this.hitFlashUntilMillis = System.currentTimeMillis() + durationMs;
        this.hitFlashGold = gold;
    }

    public boolean isHitFlashing() { return System.currentTimeMillis() < hitFlashUntilMillis; }
    public boolean isHitFlashGold() { return hitFlashGold; }

    public void startRespawnShimmer() { this.respawnShimmerStartMillis = System.currentTimeMillis(); }

    // Returns spawn-in progress in [0, 1]; 1 = fully materialized
    public float getRespawnShimmerProgress(long shimmerDurationMs) {
        if (respawnShimmerStartMillis == 0) return 1.0f;
        return Math.min(1.0f, (System.currentTimeMillis() - respawnShimmerStartMillis) / (float) shimmerDurationMs);
    }

    // Eases alpha toward targetAlpha; rate is alpha units per second
    public void updateAlpha(float deltaTime, float ratePerSecond) {
        if (alpha == targetAlpha) return;
        float step = ratePerSecond * deltaTime;
        if (alpha < targetAlpha) {
            alpha = Math.min(targetAlpha, alpha + step);
        } else {
            alpha = Math.max(targetAlpha, alpha - step);
        }
    }
    
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
    public void HandlerPlayerUpdateMessage(Vector2f position, float rotation, float turretRotation)
    {
        this.position.set(position);
        this.rotation = rotation;
        this.turretRotation = turretRotation;
    }

    public void triggerFiringRecoil() {
        this.recoilOffset = MAX_RECOIL_OFFSET;
    }

    public float getRecoilOffset() {
        return this.recoilOffset;
    }

    public float getTrackDistance() {
        return this.trackDistance;
    }

    public void updateDynamics(float deltaTime) {
        if (recoilOffset > 0) {
            recoilOffset = Math.max(0.0f, recoilOffset - RECOIL_RECOVERY_SPEED * deltaTime);
        }
        float moveDist = position.distance(lastTrackPosition);
        float rotDelta = Math.abs(rotation - lastTrackRotation);
        trackDistance += moveDist + rotDelta * 0.25f;
        lastTrackPosition.set(position);
        lastTrackRotation = rotation;
    }

    @Override
    public void update(float deltaTime) {
        updateDynamics(deltaTime);
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
            this.turretRotation = updatedTankData.getTurretRotation();
            this.tankType = updatedTankData.getTankType();
            // hitPoints intentionally NOT copied: NEW messages carry no HP, so the
            // freshly-parsed TankData holds only the placeholder default. Authoritative
            // HP arrives via ARM (own tank), DES (zero), and respawn (per-type max).
            this.color = updatedTankData.getColor();
            this.isDestroyed = updatedTankData.isDestroyed();
            this.name = updatedTankData.getPlayerName();
            this.width = updatedTankData.getWidth();
            this.height = updatedTankData.getHeight();
        }
    }
}