package org.chrisgruber.nettank.client.game;

import org.chrisgruber.nettank.client.engine.core.GameEngine;
import org.chrisgruber.nettank.client.engine.graphics.Camera;
import org.chrisgruber.nettank.client.engine.graphics.Renderer;
import org.chrisgruber.nettank.client.engine.graphics.Shader;
import org.chrisgruber.nettank.client.engine.graphics.Texture;
import org.chrisgruber.nettank.client.engine.network.GameClient;
import org.chrisgruber.nettank.client.engine.network.NetworkCallbackHandler;
import org.chrisgruber.nettank.client.engine.ui.UIManager;
import org.chrisgruber.nettank.client.game.entities.ClientBullet;
import org.chrisgruber.nettank.client.game.entities.ClientTank;
import org.chrisgruber.nettank.client.game.world.ClientGameMap;
import org.chrisgruber.nettank.common.entities.BulletData;
import org.chrisgruber.nettank.common.entities.TankData;
import org.chrisgruber.nettank.common.util.Colors;
import org.chrisgruber.nettank.common.util.GameState;

import org.joml.Vector2f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javax.swing.*; // For JOptionPane on error/disconnect
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.GL_TEXTURE0;
import static org.lwjgl.opengl.GL13.glActiveTexture;
import static org.lwjgl.system.MemoryUtil.NULL;

public class TankBattleGame extends GameEngine implements NetworkCallbackHandler {

    private static final Logger logger = LoggerFactory.getLogger(TankBattleGame.class);

    private static final float UI_TEXT_SCALE_NORMAL = 0.5f; // Base size
    private static final float UI_TEXT_SCALE_LARGE = 0.85f;  // Larger size
    private static final float UI_TEXT_SCALE_SECONDARY_STATUS = 0.65f; // Slightly smaller for secondary status
    private static final float UI_TEXT_SCALE_STATUS = 0.8f; // Size for status messages like HIT POINTS
    private static final float UI_TEXT_SCALE_ANNOUNCEMENT = 1.3f; // For large center messages like announcements

    private Shader shader;
    private Renderer renderer;
    private Camera camera;
    private UIManager uiManager;

    // Textures
    private Texture tankTexture;
    private Texture bulletTexture;
    private Texture grassTexture;
    private Texture dirtTexture;

    // Game Objects
    private ClientGameMap gameMap;
    private final Map<Integer, ClientTank> tanks = new ConcurrentHashMap<>();
    private final List<ClientBullet> bullets = new CopyOnWriteArrayList<>();

    // Player specific
    private int localPlayerId = -1;
    private ClientTank localTank = null;
    private final String playerName;
    private boolean isSpectating = false;
    private long roundStartTimeMillis = 0;
    private long finalElapsedTimeMillis = -1;

    // Networking
    private GameClient gameClient;
    private final String serverIp;
    private final int serverPort;

    // Game State
    private volatile GameState currentGameState = GameState.CONNECTING;
    private final List<String> announcements = new CopyOnWriteArrayList<>();
    private long lastAnnouncementTime = 0;
    private static final long ANNOUNCEMENT_DISPLAY_TIME_MS = 5000;

    // Input Game State
    private boolean prevKeyW = false;
    private boolean prevKeyS = false;
    private boolean prevKeyA = false;
    private boolean prevKeyD = false;

    // Config
    public static final float VIEW_RANGE = 300.0f;

    /**
     * Constructor for the Tank Battle Game.
     */
    public TankBattleGame(String hostIp, int port, String playerName, String title, int width, int height) {
        super(title, width, height);
        this.serverIp = hostIp;
        this.serverPort = port;
        this.playerName = playerName;
    }

    // --- Implementation of Abstract Methods from GameEngine ---

    @Override
    public void initGame() {
        logger.info("Initializing TankBattleGame...");
        try {
            glEnable(GL_BLEND); // Enable blending
            // Set the blend function for standard alpha blending
            // Formula: finalColor = (sourceColor * sourceAlpha) + (destColor * (1 - sourceAlpha))
            glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
            logger.debug("OpenGL blending enabled (GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA).");

            shader = new Shader("/shaders/quad.vert", "/shaders/quad.frag");
            shader.bind();

            renderer = new Renderer();
            camera = new Camera(windowWidth, windowHeight);
            uiManager = new UIManager();

            // Load game textures
            logger.debug("Loading textures...");
            grassTexture = new Texture("textures/grass.png");
            dirtTexture = new Texture("textures/dirt.png");
            bulletTexture = new Texture("textures/bullet.png");
            tankTexture = new Texture("textures/tank.png");
            uiManager.loadFontTexture("textures/font14px.png");
            logger.debug("Textures loaded.");

            gameMap = new ClientGameMap(50, 50);

            // Setup projection matrix
            shader.bind();
            shader.setUniformMat4f("u_projection", camera.getProjectionMatrix());
            shader.setUniform1i("u_texture", 0); // Use texture unit 0

            // Start networking game client AFTER core graphics setup
            logger.info("Starting network client...");
            gameClient = new GameClient(serverIp, serverPort, playerName, this);
            new Thread(gameClient, "GameClientThread").start();

        } catch (IOException e) {
            logger.error("Failed to initialize game resources", e);

            // Use SwingUtilities for UI thread safety with JOptionPane
            SwingUtilities.invokeLater(() -> {
                JOptionPane.showMessageDialog(null, "Failed to load game resources:\n" + e.getMessage(), "Initialization Error", JOptionPane.ERROR_MESSAGE);
            });

            // Signal engine to close if init fails critically
            if (windowHandle != NULL) {
                glfwSetWindowShouldClose(windowHandle, true);
            } else {
                System.exit(1); // Force exit if window wasn't even created
            }

        } catch (Exception e) {
            logger.error("Unexpected error during game initialization", e);

            SwingUtilities.invokeLater(() -> {
                JOptionPane.showMessageDialog(null, "Unexpected error initializing game:\n" + e.getMessage(), "Initialization Error", JOptionPane.ERROR_MESSAGE);
            });

            if (windowHandle != NULL) {
                glfwSetWindowShouldClose(windowHandle, true);
            }
            else {
                System.exit(1);
            }
        }
    }

    @Override
    public void updateGame(float deltaTime) {
        // Update local bullet positions for client-side prediction
        List<ClientBullet> bulletsToRemove = new ArrayList<>();
        long now = System.currentTimeMillis();

        for (ClientBullet bullet : bullets) {
            bullet.update(deltaTime);

            // Check for bullet expiry or out of bounds
            if ((now - bullet.getSpawnTime() >= BulletData.LIFETIME_MS) ||
                    (gameMap != null && gameMap.isOutOfBounds(bullet.getPosition().x, bullet.getPosition().y, BulletData.SIZE / 2.0f))) {
                bulletsToRemove.add(bullet);
            }
        }

        bullets.removeAll(bulletsToRemove);

        // Update camera position to follow local tank (if it exists)
        if (localTank != null) {
            camera.setPosition(localTank.getPosition().x, localTank.getPosition().y);
        } else if (isSpectating && gameMap != null) {
            camera.setPosition(gameMap.getWorldWidth() / 2.0f, gameMap.getWorldHeight() / 2.0f);
        }

        camera.update(); // Update camera matrices

        // Update announcements display timer
        if (!announcements.isEmpty() && (now - lastAnnouncementTime > ANNOUNCEMENT_DISPLAY_TIME_MS)) {
            announcements.removeFirst();    // Remove the oldest announcement
            lastAnnouncementTime = announcements.isEmpty() ? 0 : now;   // Reset timer
        }
    }

    @Override
    public void renderGame() {
        shader.bind();
        shader.setUniformMat4f("u_view", camera.getViewMatrix());
        shader.setUniform1i("u_texture", 0); // Ensure texture unit 0
        glActiveTexture(GL_TEXTURE0);

        // Render Map
        if (gameMap != null) {
            // Remove unused centerPos variable
            float range = isSpectating ? Float.MAX_VALUE : VIEW_RANGE;
            gameMap.render(renderer, shader, grassTexture, dirtTexture, camera, range);
        }

        // Render Tanks
        Vector2f playerPos = (localTank != null) ? localTank.getPosition() : null;
        float renderRangeSq = isSpectating ? Float.MAX_VALUE : VIEW_RANGE * VIEW_RANGE;

        tankTexture.bind(); // Bind tank texture once

        for (ClientTank tank : tanks.values()) {
            if (isObjectVisible(tank.getPosition(), playerPos, renderRangeSq)) {
                shader.setUniform3f("u_tintColor", tank.getColor());

                renderer.drawQuad(tank.getPosition().x, tank.getPosition().y,
                        TankData.SIZE, TankData.SIZE, // <<< CHANGED HERE
                        tank.getRotation(), shader);
            }
        }

        shader.setUniform3f("u_tintColor", 1.0f, 1.0f, 1.0f);

        // Render Bullets
        bulletTexture.bind(); // Bind bullet texture once
        shader.setUniform3f("u_tintColor", 1.0f, 1.0f, 1.0f);

        for (ClientBullet bullet : bullets) {
            if (isObjectVisible(bullet.getPosition(), playerPos, renderRangeSq)) {
                Vector2f velocity = bullet.getVelocity();

                float rotationDegrees = 0.0f;

                // Calculate rotation only if the bullet is actually moving
                // (Avoids issues with atan2(0, 0) which is undefined but often returns 0)
                if (velocity.lengthSquared() > 0.0001f) { // Use a small threshold
                    // Calculate angle in radians from velocity vector components
                    // atan2(y, x) gives angle relative to positive X axis
                    double angleRadians = Math.atan2(velocity.y, velocity.x);

                    // Convert radians to degrees
                    float angleDegrees = (float) Math.toDegrees(angleRadians);

                    // Adjust angle:
                    // Texture points UP at 0 degrees rotation.
                    // atan2 gives 90 degrees for UP.
                    // atan2 gives 0 degrees for RIGHT.
                    // We need to subtract 90 degrees from atan2 result.
                    rotationDegrees = angleDegrees - 90.0f;
                }

                Vector2f bulletPosition = bullet.getPosition();
                logger.trace("Rendering Bullet: Pos=({}, {}), Vel=({}, {}), Rotation={}",
                        bulletPosition.x, bulletPosition.y, velocity.x, velocity.y, rotationDegrees);

                // Draw the quad using the calculated rotation
                renderer.drawQuad(bullet.getPosition().x, bullet.getPosition().y,
                        BulletData.SIZE, BulletData.SIZE,
                        rotationDegrees,
                        shader);
            }
        }

        renderUI();
    }

    // Helper for renderGame
    private boolean isObjectVisible(Vector2f objectPos, Vector2f playerPos, float rangeSq) {
        if (isSpectating || playerPos == null) return true; // Spectators see everything
        return objectPos.distanceSquared(playerPos) <= rangeSq;
    }

    private void renderUI() {
        uiManager.startUIRendering(windowWidth, windowHeight);

        // Define positions for top-left UI elements
        final float statusTextX = 10; // X position for status text
        final float hitPointsY = 10; // Y position for Hit Points
        final float timerY = 35;     // Y position for Timer (below Hit Points + padding)

        // Render tank health and game state
        if (localTank != null && !isSpectating) {
            uiManager.drawText("HIT POINTS: " + localTank.getHitPoints(), statusTextX, hitPointsY, UI_TEXT_SCALE_STATUS, Colors.GREEN);
        } else if (isSpectating) {
            uiManager.drawText("SPECTATING", statusTextX, hitPointsY, UI_TEXT_SCALE_STATUS, Colors.YELLOW);
        } else {
            uiManager.drawText(currentGameState == GameState.CONNECTING ? "CONNECTING..." : "LOADING...", statusTextX, hitPointsY, UI_TEXT_SCALE_STATUS, Colors.WHITE);
        }

        // Render Timer
        if (currentGameState == GameState.PLAYING && roundStartTimeMillis > 0) {
            long elapsedMillis = System.currentTimeMillis() - roundStartTimeMillis;
            long seconds = (elapsedMillis / 1000) % 60;
            long minutes = (elapsedMillis / (1000 * 60)) % 60;
            long hours = (elapsedMillis / (1000 * 60 * 60)) % 24;

            String timeStr;

            if (hours > 0) {
                timeStr = String.format("TIME: %02d:%02d:%02d", hours, minutes, seconds);
            }
            else {
                timeStr = String.format("TIME: %02d:%02d", minutes, seconds);
            }

            uiManager.drawText(timeStr, statusTextX, timerY, UI_TEXT_SCALE_SECONDARY_STATUS, Colors.WHITE);
        } else if (currentGameState == GameState.ROUND_OVER && finalElapsedTimeMillis >= 0) {
            // TODO: Timer display handled by announcements in ROUND_OVER
        }

        // --- Render Centered Messages ---
        final float centerMessageY = windowHeight * 0.4f; // Vertical position (adjust 0.4f if needed)

        // Render Announcements
        if (!announcements.isEmpty()) {
            String announcement = announcements.getFirst();
            float textWidth = uiManager.getTextWidth(announcement, UI_TEXT_SCALE_ANNOUNCEMENT);
            float x = (windowWidth - textWidth) / 2.0f; // Center horizontally
            uiManager.drawText(announcement, x, centerMessageY, UI_TEXT_SCALE_ANNOUNCEMENT, Colors.RED);
        }

        // Render Game State messages
        String stateMessage = "";

        switch (currentGameState) {
            case WAITING:
                stateMessage = "WAITING FOR PLAYERS (" + tanks.size() + ")";
                break;
            case COUNTDOWN:
                // TODO: I want to show the countdown number here, "3... 2... 1...FIGHT!"
                if (announcements.isEmpty()) { // Only show if no countdown number announcement
                    stateMessage = "ROUND STARTING...";
                }
                break;
        }

        if (!stateMessage.isEmpty()) { // Check if we have a state message to display
            // Calculate width needed to center the text
            float textWidth = uiManager.getTextWidth(stateMessage, UI_TEXT_SCALE_ANNOUNCEMENT);
            float x = (windowWidth - textWidth) / 2.0f; // Center horizontally
            // Draw using the large announcement scale
            uiManager.drawText(stateMessage, x, centerMessageY, UI_TEXT_SCALE_ANNOUNCEMENT, Colors.RED);
        }

        uiManager.endUIRendering();
    }


    @Override
    public void handleGameInput(float deltaTime) {
        if (inputHandler == null) return;

        if (inputHandler.isKeyDown(GLFW_KEY_ESCAPE)) {
            logger.info("Escape key pressed. Closing game window.");
            if(windowHandle != NULL) glfwSetWindowShouldClose(windowHandle, true);
            return;
        }

        // Handle Game Controls only if playing
        if (localTank != null && !isSpectating && currentGameState == GameState.PLAYING) {
            boolean keyW = inputHandler.isKeyDown(GLFW_KEY_W);
            boolean keyS = inputHandler.isKeyDown(GLFW_KEY_S);
            boolean keyA = inputHandler.isKeyDown(GLFW_KEY_A);
            boolean keyD = inputHandler.isKeyDown(GLFW_KEY_D);
            boolean keySpace = inputHandler.isKeyPressed(GLFW_KEY_SPACE);

            // Check if movement input state has changed
            if (keyW != prevKeyW || keyS != prevKeyS || keyA != prevKeyA || keyD != prevKeyD) {
                // Send only when input state changes
                if (gameClient != null && gameClient.isConnected()) {
                    logger.debug("Sending movement input: W:{} S:{} A:{} D:{}", keyW, keyS, keyA, keyD);
                    gameClient.sendInput(keyW, keyS, keyA, keyD);
                }

                // Update previous state
                prevKeyW = keyW;
                prevKeyS = keyS;
                prevKeyA = keyA;
                prevKeyD = keyD;
            }

            // Handle shooting command
            if (keySpace) {
                if (gameClient != null && gameClient.isConnected()) {
                    logger.debug("Sending SHOOT command");
                    gameClient.sendShoot();
                }

                inputHandler.resetKey(GLFW_KEY_SPACE); // Consume press event
            }
        }
    }

    @Override
    public void cleanupGame() {
        logger.info("Cleaning up TankBattleGame resources...");

        // Stop the game client if it's running

        if (gameClient != null) {
            logger.debug("Stopping network client...");

            try {
                gameClient.stop();
            } catch (Exception e) {
                logger.error("Error stopping game client", e);
            } finally {
                gameClient = null;
                logger.debug("Network client stopped and dereferenced.");
            }
        }

        // Cleanup OpenGL resources

        logger.debug("Deleting game textures, shaders, renderer, UI...");

        try { if (tankTexture != null) tankTexture.delete(); } catch (Exception e) { logger.error("Error deleting tankTexture", e); }
        try { if (bulletTexture != null) bulletTexture.delete(); } catch (Exception e) { logger.error("Error deleting bulletTexture", e); }
        try { if (grassTexture != null) grassTexture.delete(); } catch (Exception e) { logger.error("Error deleting grassTexture", e); }
        try { if (dirtTexture != null) dirtTexture.delete(); } catch (Exception e) { logger.error("Error deleting dirtTexture", e); }

        // UIManager cleanup deletes its font texture, shader, renderer
        try {
            if (uiManager != null) {
                uiManager.cleanup();
            }
        } catch (Exception e) {
            logger.error("Error cleaning up uiManager", e);
        }

        logger.debug("Game-specific OpenGL resources deleted.");

        // Clear game state collections
        tanks.clear();
        bullets.clear();
        announcements.clear();

        logger.info("TankBattleGame cleanup finished.");
    }

    // Called when ASSIGN_ID is received
    public void setLocalPlayerId(int id) {
        this.localPlayerId = id;
        logger.info("Assigned local player ID: {}", id);
    }

    @Override
    public void addOrUpdateTank(int id, float x, float y, float rotation, String name, float r, float g, float b) {
        ClientTank tank = tanks.get(id);

        if (tank == null) {
            logger.info("Creating new ClientTank for player ID: {} Name: {}", id, name);

            TankData data = new TankData();

            data.updateFromServer(id, name, x, y, rotation, r, g, b);

            tank = new ClientTank(data); // Create client wrapper

            tanks.put(id, tank);    // Put the NEW tank into the map *after* creating it

            if (id == localPlayerId) {
                localTank = tank;
                isSpectating = tank.isDestroyed();
                logger.info("Local tank object created. Spectating: {}", isSpectating);
            }
        } else {
            logger.trace("Updating existing ClientTank for player ID: {}", id);

            tank.getTankData().updateFromServer(id, name, x, y, rotation, r, g, b);
            tank.updatePositionFromData();

            if (id == localPlayerId) {
                localTank = tank;
            }
        }
    }

    // Called when PLAYER_LEFT is received
    public void removeTank(int id) {
        ClientTank removed = tanks.remove(id);

        if (removed != null) {
            logger.info("Removed tank for player ID: {} Name: {}", id, removed.getName());

            if (id == localPlayerId) {
                localTank = null;
                isSpectating = true;

                logger.info("Local tank removed. Entering spectator mode.");
            }
        } else {
            logger.warn("Received removeTank for unknown ID: {}", id);
        }
    }

    // Called when PLAYER_UPDATE is received
    public void updateTankState(int id, float x, float y, float rotation) {
        ClientTank tank = tanks.get(id);

        if (tank != null) {
            var tankData = tank.getTankData();
            tankData.setPosition(x, y);
            tankData.setRotation(rotation);
        }
    }

    // Called when SHOOT is received
    public void spawnBullet(int ownerId, float x, float y, float dirX, float dirY) {
        // Calculate velocity based on direction and common speed
        Vector2f velocity = new Vector2f(dirX, dirY).normalize().mul(BulletData.SPEED);
        long spawnTime = System.currentTimeMillis(); // Client uses its own time for prediction expiry

        // Create common BulletData
        BulletData bulletData = new BulletData(ownerId, x, y, velocity.x, velocity.y, spawnTime);
        // Create ClientBullet wrapper for rendering/prediction
        ClientBullet clientBullet = new ClientBullet(bulletData);

        bullets.add(clientBullet);

        logger.trace("Spawned bullet owned by {}", ownerId);
    }

    @Override
    public void handlePlayerHit(int targetId, int shooterId) {
        // TODO: Handle player hit logic
    }

    @Override
    public void handlePlayerDestroyed(int targetId, int shooterId) {
        // TODO: Handle player destroyed logic
    }

    // Called when PLAYER_LIVES is received
    public void updatePlayerLives(int playerId, int lives) {
        // TODO: Handle player lives update logic
        // This is a placeholder for the actual logic to update player lives on the UI.
        // The server has the actual number of lives or respawns allowed. The client will just display it.
        ClientTank tank = tanks.get(playerId);

        if (tank != null) {
            logger.info("Player {} lives set to: {}", tank.getName(), lives);

            if (playerId == localPlayerId) {
                // TODO: Move this logic to spectate to its own NetworkProtocol command
                /*
                if (lives <= 0 && !isSpectating) {
                    logger.info("Local player defeated. Enabling spectator mode.");
                    isSpectating = true;
                    // localTank reference remains but rendering/input handles isSpectating
                } else if (lives > 0 && isSpectating) {
                    // This case handled in addOrUpdateTank generally
                    logger.info("Local player has lives again, spectator mode should disable.");
                    isSpectating = false;
                }
                */
            }
        } else {
            logger.warn("Received updatePlayerLives for unknown ID: {}", playerId);
        }
    }

    // Called when GAME_STATE is received
    public void setGameState(GameState state, long timeData) {
        logger.info("Game state changed to: {} with time data: {}", state, timeData);

        this.currentGameState = state;

        // Clear announcements on most state changes, except maybe within countdown?
        if(state != GameState.COUNTDOWN) { // Keep announcements during countdown? Optional.
            announcements.clear();
            lastAnnouncementTime = 0;
        }

        switch (state) {
            case PLAYING:
                this.roundStartTimeMillis = timeData;
                this.finalElapsedTimeMillis = -1;
                // Clear bullets from previous round? Server should dictate respawn/reset.
                // Maybe clear client-side bullets optimistically.
                bullets.clear();
                // Update spectator status based on current lives
                if (localTank != null) {
                    logger.info("Local tank object created. Local tank: {}", localTank.getName());
                    isSpectating = localTank.isDestroyed();
                } else {
                    logger.info("Local tank object not found. Spectating.");
                    isSpectating = true; // Spectate if local tank doesn't exist yet
                }
                logger.info("Game state PLAYING. Spectating: {}", isSpectating);
                break;
            case ROUND_OVER:
                this.finalElapsedTimeMillis = timeData;
                bullets.clear(); // Clear bullets at round end
                isSpectating = true; // Force spectate at round over
                logger.info("Game state ROUND_OVER. Spectating forced.");
                break;
            case WAITING:
            case COUNTDOWN:
            case CONNECTING:
                this.roundStartTimeMillis = 0;
                this.finalElapsedTimeMillis = -1;
                bullets.clear(); // Clear bullets while not actively playing
                isSpectating = true; // Spectate while waiting/connecting/counting
                logger.info("Game state {}. Spectating forced.", state);
                break;
        }
    }

    // Called when ANNOUNCE is received
    public void addAnnouncement(String message) {
        logger.info("Announcement: {}", message);

        announcements.add(message);

        if (announcements.size() == 1) { // Start timer only if it was empty
            lastAnnouncementTime = System.currentTimeMillis();
        }
    }

    // Called by GameClient on connection failure
    public void connectionFailed(String reason) {
        logger.error("Connection failed: {}", reason);

        currentGameState = GameState.ERROR; // Set an error state
        announcements.clear();

        announcements.add("CONNECTION FAILED");
        announcements.add(reason);
        lastAnnouncementTime = System.currentTimeMillis();

        // Show message and signal window close
        SwingUtilities.invokeLater(() -> {
            JOptionPane.showMessageDialog(null, "Connection failed: " + reason + "\nExiting.", "Connection Error", JOptionPane.ERROR_MESSAGE);
        });

        if (windowHandle != NULL) glfwSetWindowShouldClose(windowHandle, true);
    }

    // Called by GameClient on graceful disconnect from server
    public void disconnected() {
        logger.warn("Disconnected from server.");

        currentGameState = GameState.ERROR; // Set an error state or specific disconnected state
        announcements.clear();
        announcements.add("DISCONNECTED");
        lastAnnouncementTime = System.currentTimeMillis();

        SwingUtilities.invokeLater(() -> {
            JOptionPane.showMessageDialog(null, "Disconnected from server.\nExiting.", "Disconnected", JOptionPane.INFORMATION_MESSAGE);
        });

        if (windowHandle != NULL) glfwSetWindowShouldClose(windowHandle, true);
    }
}