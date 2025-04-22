package org.chrisgruber.nettank.common.entities;

import org.chrisgruber.nettank.common.physics.CapsuleCollider;
import org.joml.Vector3f;
import org.joml.Vector2f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// Data Transfer Object / State Holder for Tank information
public class TankData extends Entity {
    private static final Logger logger = LoggerFactory.getLogger(TankData.class);
    public static final float SIZE = 30.0f;
    public static final float COLLISION_RADIUS = SIZE * 0.45f;

    protected String playerName;
    protected Vector3f color = new Vector3f(1f, 1f, 1f);
    protected int hitPoints = 1;
    protected long lastShotTime = 0;
    protected long deathTimeMillis = 0;

    // Movement input state
    protected volatile boolean movingForward = false;
    protected volatile boolean movingBackward = false;
    protected volatile boolean turningLeft = false;
    protected volatile boolean turningRight = false;

    public TankData() {
        super(0, new Vector2f(), SIZE, SIZE, new Vector2f(), 0.0f, new CapsuleCollider(new Vector2f(), SIZE, COLLISION_RADIUS, 0.0f));
    }

    public TankData(int playerId, Vector2f position, Vector2f velocity, float rotation, Vector3f color, String playerName) {
        super(playerId, position, SIZE, SIZE, velocity, rotation, new CapsuleCollider(new Vector2f(), SIZE, COLLISION_RADIUS, rotation));
        this.color = color;
        this.playerName = playerName;
    }

    public void addPosition(float dx, float dy) {
        this.position.add(dx, dy);
        this.collider.setPosition(this.position);
    }

    public void takeHit(int damage) {
        if (this.hitPoints > 0) {
            this.hitPoints = Math.max(0, this.hitPoints - damage);
        }

        if (this.hitPoints <= 0) {
            this.deathTimeMillis = System.currentTimeMillis();
        }

        logger.debug("Tank for playerId {} took {} damage, remaining hit points: {} deathTimeMillis: {}", this.playerId, damage, this.hitPoints, this.deathTimeMillis);
    }

    public String getPlayerName() { return playerName; }
    public Vector3f getColor() { return color; }
    public int getHitPoints() { return hitPoints; }
    public long getDeathTimeMillis() { return deathTimeMillis; }
    public long getLastShotTime() { return lastShotTime; }

    public boolean isDestroyed() { return hitPoints <= 0; }
    public boolean isMovingForward() { return movingForward; }
    public boolean isMovingBackward() { return movingBackward; }
    public boolean isTurningLeft() { return turningLeft; }
    public boolean isTurningRight() { return turningRight; }
    public void setMovingForward(boolean movingForward) { this.movingForward = movingForward; }
    public void setMovingBackward(boolean movingBackward) { this.movingBackward = movingBackward; }
    public void setTurningLeft(boolean turningLeft) { this.turningLeft = turningLeft; }
    public void setTurningRight(boolean turningRight) { this.turningRight = turningRight; }
    public void setPlayerName(String playerName) { this.playerName = playerName; }
    public void setColor(Vector3f color) { this.color = color; }
    public void setHitPoints(int hitPoints) { this.hitPoints = hitPoints; }
    public void setDeathTimeMillis(long deathTimeMillis) { this.deathTimeMillis = deathTimeMillis; }
    public void setLastShotTime(long lastShotTime) { this.lastShotTime = lastShotTime; }

    // Set input flags (called by ClientHandler thread)
    public void setInputState(boolean forward, boolean backward, boolean left, boolean right) {
        this.movingForward = forward;
        this.movingBackward = backward;
        this.turningLeft = left;
        this.turningRight = right;
    }

    // Record shot time (called by GameServer)
    public void recordShot(long currentTime) {
        this.lastShotTime = currentTime;
    }

    // --- Methods used BY CLIENT NETWORK to update state from messages ---

    // Update state based on NEW_PLAYER or full update (lives included)
    public void updateFromServer(int id, String playerName, float x, float y, float rot, float r, float g, float b) {
        this.playerId = id;
        this.playerName = playerName;
        this.setPosition(new Vector2f(x, y));
        float rotation = normalizeRotationInDegrees(rot);
        this.setRotation(rotation);
        this.color.set(r, g, b);
    }

    public void setForSpawn(Vector2f spawnPoint, float rotation, int hitPoints, long deathTimeMillis, long lastShotTime) {
        this.setPosition(spawnPoint);
        this.setRotation(rotation);
        this.collider.setPosition(spawnPoint);
        this.hitPoints = hitPoints;
        this.deathTimeMillis = deathTimeMillis;
        this.lastShotTime = lastShotTime;

        logger.debug("Tank for playerId {} set to spawn at {} with rotation (degrees) {} and hit points {}", this.playerId, this.position, this.rotation, this.hitPoints);
    }

    @Override
    public float getSize() {
        return SIZE;
    }

    @Override
    public String toString() {
        return "TankData{" + "playerId=" + playerId + ", playerName='" + playerName + '\'' + ", pos=" + position + ", rot (degrees)=" + rotation + ", isDestroyed=" + this.isDestroyed() + ", hitPoints=" + this.hitPoints + '}';
    }
}