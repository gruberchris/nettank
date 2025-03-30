package org.chrisgruber.nettank.common.entities;

import org.chrisgruber.nettank.common.util.Colors;
import org.joml.Vector3f;
import org.joml.Vector2f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// Data Transfer Object / State Holder for Tank information
public class TankData {
    private static final Logger logger = LoggerFactory.getLogger(TankData.class);

    // --- Constants related to Tank state/size ---
    public static final float SIZE = 30.0f;
    public static final float COLLISION_RADIUS = SIZE * 0.45f;

    // --- Core Data Fields ---
    public int playerId;
    public String name;
    public Vector2f position = new Vector2f(); // Mutable JOML vector
    public float rotation = 0; // Degrees
    public Vector3f color = new Vector3f(1f, 1f, 1f); // Mutable JOML vector
    private int hitPoints = 1; // Default hit points for a tank

    // --- Server-Side Input State (volatile for thread-safety) ---
    public transient volatile boolean movingForward = false;
    public transient volatile boolean movingBackward = false;
    public transient volatile boolean turningLeft = false;
    public transient volatile boolean turningRight = false;

    // --- Server-Side Logic State ---
    public transient long lastShotTime = 0; // Server tracks cooldown
    public transient long deathTimeMillis = 0;  // Time of death for respawn logic

    // Default constructor needed for some frameworks/libraries
    public TankData() {}

    // Constructor used by GameServer
    public TankData(int playerId, float x, float y, Vector3f color, String name) {
        this.playerId = playerId;
        this.position.set(x, y);
        this.color.set(color != null ? color : Colors.WHITE);
        this.name = name;
    }

    // Set position and rotation (e.g., on respawn or if corrected)
    public void setPosition(float x, float y) {
        this.position.set(x, y);
    }
    public void setRotation(float rotation) {
        this.rotation = (rotation % 360.0f + 360.0f) % 360.0f;
    }
    public void addPosition(float dx, float dy) {
        this.position.add(dx, dy);
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
    public void updateFromServer(int id, String name, float x, float y, float rot, float r, float g, float b) {
        this.playerId = id;
        this.name = name;
        this.position.set(x, y);
        this.rotation = (rot % 360.0f + 360.0f) % 360.0f;
        this.color.set(r, g, b);
    }

    // Update state based on PLAYER_UPDATE (only position/rotation)
    public void updateFromServer(float x, float y, float rot) {
        this.position.set(x,y);
        this.rotation = (rot % 360.0f + 360.0f) % 360.0f;
    }

    public void setForSpawn(Vector2f spawnPoint, float rotation, int hitPoints, long deathTimeMillis, long lastShotTime) {
        this.position.set(spawnPoint);
        this.rotation = rotation;
        this.hitPoints = hitPoints;
        this.deathTimeMillis = deathTimeMillis;
        this.lastShotTime = lastShotTime;

        logger.debug("Tank for playerId {} set to respawn at {} with rotation {} and hit points {}", this.playerId, this.position, this.rotation, this.hitPoints);
    }

    public int getPlayerId() { return playerId; }
    public String getName() { return name; }
    public Vector2f getPosition() { return position; }
    public float getRotation() { return rotation; }
    public Vector3f getColor() { return color; }
    public int getHitPoints() { return hitPoints; }
    public boolean isDestroyed() { return hitPoints <= 0; }
    public long getDeathTimeMillis() { return deathTimeMillis; }

    @Override
    public String toString() {
        return "TankData{" + "playerId=" + playerId + ", name='" + name + '\'' + ", pos=" + position + ", rot=" + rotation + ", isDestroyed=" + this.isDestroyed() + ", hitpoints=" + this.hitPoints + '}';
    }
}