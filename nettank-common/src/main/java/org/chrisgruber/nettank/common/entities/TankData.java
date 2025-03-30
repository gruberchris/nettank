package org.chrisgruber.nettank.common.entities;

import org.chrisgruber.nettank.common.util.Colors;
import org.joml.Vector3f;
import org.joml.Vector2f;

// Data Transfer Object / State Holder for Tank information
public class TankData {
    private int lives = 0;
    private boolean infiniteLivesAllowed = false;

    // --- Constants related to Tank state/size ---
    public static final float SIZE = 30.0f;
    public static final float COLLISION_RADIUS = SIZE * 0.45f;

    // --- Core Data Fields ---
    public int playerId;
    public String name;
    public Vector2f position = new Vector2f(); // Mutable JOML vector
    public float rotation; // Degrees
    public Vector3f color = new Vector3f(1f, 1f, 1f); // Mutable JOML vector

    // --- Server-Side Input State (volatile for thread-safety) ---
    public transient volatile boolean movingForward = false; // transient: not part of network state typically
    public transient volatile boolean movingBackward = false;
    public transient volatile boolean turningLeft = false;
    public transient volatile boolean turningRight = false;

    // --- Server-Side Logic State ---
    public transient long lastShotTime = 0; // Server tracks cooldown

    // Default constructor needed for some frameworks/libraries
    public TankData() {}

    // Constructor used by GameServer
    public TankData(int playerId, float x, float y, Vector3f color, String name) {
        this.playerId = playerId;
        this.position.set(x, y);
        this.color.set(color != null ? color : Colors.WHITE);
        this.name = name;
        this.rotation = 0;
        this.infiniteLivesAllowed = false;
        // lives/alive set separately after creation by server
    }

    // --- Methods used BY SERVER to modify state ---

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

    public void setLives(int lives) {
        if (this.infiniteLivesAllowed) {
            this.lives = Integer.MAX_VALUE; // Infinite lives
            return;
        }

        this.lives = Math.max(0, lives);
    }

    public void setInfiniteLivesAllowed(boolean allowed) {
        this.infiniteLivesAllowed = allowed;
        if (allowed) {
            this.lives = Integer.MAX_VALUE; // Set to max value for infinite lives
        }
    }

    // Apply hit (decrements lives, updates alive)
    public void takeHit() {
        if (this.lives > 0 && !this.infiniteLivesAllowed) {
            this.lives--;
        }
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
    public void updateFromServer(int id, String name, float x, float y, float rot, float r, float g, float b, int lives) {
        this.playerId = id;
        this.name = name;
        this.position.set(x, y);
        this.rotation = (rot % 360.0f + 360.0f) % 360.0f;
        this.color.set(r, g, b);
        setLives(lives); // Use setter to keep alive flag consistent
    }

    // Update state based on PLAYER_UPDATE (only position/rotation)
    public void updateFromServer(float x, float y, float rot) {
        this.position.set(x,y);
        this.rotation = (rot % 360.0f + 360.0f) % 360.0f;
    }

    // Update state based on PLAYER_LIVES
    public void updateFromServer(int lives) {
        setLives(lives); // Use setter
    }


    // --- Simple Getters (Generally safe without synchronization for reads) ---
    // Be mindful that position and color return mutable references
    public int getPlayerId() { return playerId; }
    public String getName() { return name; }
    public Vector2f getPosition() { return position; }
    public float getRotation() { return rotation; }
    public Vector3f getColor() { return color; }
    public int getLives() { return lives; }

    public boolean isAlive() {
        return this.lives > 0;
    }

    @Override
    public String toString() {
        return "TankData{" + "playerId=" + playerId + ", name='" + name + '\'' + ", pos=" + position + ", rot=" + rotation + ", lives=" + this.getLives() + ", alive=" + this.isAlive() + '}';
    }
}