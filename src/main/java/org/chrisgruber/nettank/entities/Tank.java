package org.chrisgruber.nettank.entities;

import org.joml.Vector3f;
import org.chrisgruber.nettank.util.Colors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Tank extends Entity {
    private static final Logger logger = LoggerFactory.getLogger(Tank.class);
    public static final float SIZE = 30.0f; // Visual size
    public static final float COLLISION_RADIUS = SIZE * 0.45f; // Collision approximation
    public static final float MOVE_SPEED = 100.0f; // Pixels per second
    public static final float TURN_SPEED = 100.0f; // Degrees per second for A/D keys
    public static final int INITIAL_LIVES = 3;
    public static final long SHOOT_COOLDOWN_MS = 500; // Milliseconds between shots

    private final int playerId;
    private float rotation; // Degrees, 0 = up, positive = clockwise
    private Vector3f color;
    private int lives;
    private String name;
    private long lastShotTime = 0;

    // Input state (used by server)
    private boolean movingForward = false;
    private boolean movingBackward = false;
    private boolean turningLeft = false;
    private boolean turningRight = false;

    public Tank(int playerId, float x, float y, Vector3f color, String name) {
        super(x, y, SIZE, SIZE);
        this.playerId = playerId;
        this.rotation = 0; // Start facing up
        this.color = color != null ? color : Colors.WHITE; // Default if null
        this.lives = INITIAL_LIVES;
        this.name = name;
    }

    // This update method is primarily for the SERVER's authoritative simulation
    @Override
    public void update(float deltaTime) {
        if (!isAlive()) {
            return; // Don't update dead tanks
        }

        // --- 1. Calculate Rotation Change ---
        float turnAmount = 0;
        // If turningLeft ('A' key), decrease rotation (counter-clockwise)
        if (turningLeft) {
            turnAmount += TURN_SPEED * deltaTime;
        }
        // If turningRight ('D' key), increase rotation (clockwise)
        // Use 'else if' if you don't want A+D held down to cancel rotation,
        // or just 'if' if you want them to cancel out. Let's allow cancel:
        if (turningRight) {
            turnAmount -= TURN_SPEED * deltaTime;
        }

        // --- 2. Apply Rotation ---
        this.rotation += turnAmount;
        // Keep rotation within 0-360 range
        this.rotation = (this.rotation % 360.0f + 360.0f) % 360.0f;

        // --- 3. Calculate Forward/Backward Movement ---
        float moveAmount = 0;
        // Use 'else if' to prevent moving forward and backward simultaneously
        if (movingForward) {
            moveAmount = -MOVE_SPEED * deltaTime;
        } else if (movingBackward) {
            moveAmount = MOVE_SPEED * deltaTime * 0.7f; // Slower backward movement
        }

        // --- 4. Calculate Position Change based on *NEW* Rotation ---
        if (moveAmount != 0) {
            float angleRad = (float) Math.toRadians(this.rotation);

            // Assuming 0 degrees rotation faces UP, positive rotation is CLOCKWISE,
            // and screen coordinate +Y is DOWN:
            float dx = (float) Math.sin(angleRad) * moveAmount;
            float dy = (float) -Math.cos(angleRad) * moveAmount; // NEGATIVE Cos for Y

            // --- 5. Apply Position Change ---
            position.add(dx, dy);
            // logger.trace("Tank {} updated pos: {}, rot: {}", playerId, position, rotation);
        }
        // Collision checks happen AFTER this in the GameServer update loop
    }

    // Method for server to check if tank can shoot
    public boolean canShoot(long currentTime) {
        return isAlive() && (currentTime - lastShotTime >= SHOOT_COOLDOWN_MS);
    }

    // Method for server to call when tank shoots
    public void recordShot(long currentTime) {
        lastShotTime = currentTime;
    }


    // --- Getters and Setters ---

    public float getRotation() {
        return rotation;
    }

    // Setter called by network updates, should be synchronized or fields volatile
    public synchronized void setRotation(float rotation) {
        this.rotation = (rotation % 360.0f + 360.0f) % 360.0f; // Normalize here too
    }

    public Vector3f getColor() {
        // Vector3f is mutable, return a copy if external modification is unwanted
        // Or make color final if it never changes after creation
        return color; // Fine if color is only set at creation or via synchronized setter
    }

    // If color needs to be changed after creation (e.g. power-up), synchronize
    public synchronized void setColor(Vector3f color) {
        this.color = color != null ? color : Colors.WHITE;
    }


    public int getPlayerId() {
        return playerId;
    }

    public int getLives() {
        return lives;
    }

    // Setter called by network updates or hit detection, synchronize
    public synchronized void setLives(int lives) {
        this.lives = Math.max(0, lives); // Ensure lives don't go below 0
    }

    // Called by collision detection potentially in GameLoop thread
    public synchronized void takeHit() {
        if (this.lives > 0) {
            this.lives--;
            // logger.debug("Tank {} took hit, lives remaining: {}", playerId, this.lives); // Optional
        }
    }

    public boolean isAlive() {
        return this.lives > 0;
    }

    public String getName() {
        return name;
    }

    // Potentially called by network update, synchronize if needed
    public synchronized void setName(String name) {
        this.name = name;
    }

    // --- Input State (for Server) ---
    public synchronized void setInputState(boolean forward, boolean backward, boolean left, boolean right) {
        this.movingForward = forward;
        this.movingBackward = backward;
        this.turningLeft = left;
        this.turningRight = right;
        logger.trace("Tank {} setInputState: W={}, S={}, A={}, D={}", playerId, forward, backward, left, right);
    }

    @Override
    public String toString() {
        // Simplified for brevity
        return "Tank{" +
                "id=" + playerId +
                ", name='" + name + '\'' +
                ", pos=" + position + // Relies on Entity.position having a good toString
                ", rot=" + rotation +
                ", lives=" + lives +
                '}';
    }
}
