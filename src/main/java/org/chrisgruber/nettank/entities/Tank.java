package org.chrisgruber.nettank.entities;

import org.joml.Vector3f;
import org.chrisgruber.nettank.util.Colors;

public class Tank extends Entity {

    public static final float SIZE = 30.0f; // Visual size
    public static final float COLLISION_RADIUS = SIZE * 0.45f; // Collision approximation
    public static final float MOVE_SPEED = 100.0f; // Pixels per second
    public static final float TURN_SPEED = 100.0f; // Degrees per second for A/D keys
    public static final int INITIAL_LIVES = 3;
    public static final long SHOOT_COOLDOWN_MS = 500; // Milliseconds between shots

    private int playerId;
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
        float moveAmount = 0;
        if (movingForward) moveAmount += MOVE_SPEED * deltaTime;
        if (movingBackward) moveAmount -= MOVE_SPEED * deltaTime * 0.7f; // Slower backward movement

        float turnAmount = 0;
        if (turningLeft) turnAmount += TURN_SPEED * deltaTime;
        if (turningRight) turnAmount -= TURN_SPEED * deltaTime;

        rotation += turnAmount;
        // Keep rotation within 0-360 range (optional)
        // rotation = (rotation % 360 + 360) % 360;

        if (moveAmount != 0) {
            float angleRad = (float) Math.toRadians(rotation);
            float dx = (float) Math.sin(angleRad) * moveAmount;
            float dy = (float) Math.cos(angleRad) * moveAmount; // Use cos for Y as 0 degrees is UP
            position.add(dx, dy);
            // Collision with map boundaries should be handled by the server after this update
        }
    }

    // Method for server to check if tank can shoot
    public boolean canShoot(long currentTime) {
        return currentTime - lastShotTime >= SHOOT_COOLDOWN_MS;
    }

    // Method for server to call when tank shoots
    public void recordShot(long currentTime) {
        lastShotTime = currentTime;
    }


    // --- Getters and Setters ---

    public float getRotation() {
        return rotation;
    }

    public void setRotation(float rotation) {
        this.rotation = rotation;
    }

    public Vector3f getColor() {
        return color;
    }

    public void setColor(Vector3f color) {
        this.color = color != null ? color : Colors.WHITE;
    }

    public int getPlayerId() {
        return playerId;
    }

    public int getLives() {
        return lives;
    }

    public void setLives(int lives) {
        this.lives = Math.max(0, lives); // Ensure lives don't go below 0
    }

    public void takeHit() {
        if (this.lives > 0) {
            this.lives--;
        }
    }

    public boolean isAlive() {
        return this.lives > 0;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    // --- Input State (for Server) ---
    public void setInputState(boolean forward, boolean backward, boolean left, boolean right) {
        this.movingForward = forward;
        this.movingBackward = backward;
        this.turningLeft = left;
        this.turningRight = right;
    }

}
