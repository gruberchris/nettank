package org.chrisgruber.nettank.client.game;

import org.chrisgruber.nettank.client.engine.core.GameEngine;
import org.chrisgruber.nettank.client.engine.graphics.Camera;
import org.chrisgruber.nettank.client.engine.graphics.Renderer;
import org.chrisgruber.nettank.client.engine.graphics.Shader;
import org.chrisgruber.nettank.client.engine.graphics.Texture;
import org.chrisgruber.nettank.client.engine.network.GameClient;
import org.chrisgruber.nettank.client.engine.network.NetworkCallbackHandler;
import org.chrisgruber.nettank.client.engine.ui.KillFeedMessage;
import org.chrisgruber.nettank.client.engine.ui.StatusMessageKind;
import org.chrisgruber.nettank.client.engine.ui.UIManager;
import org.chrisgruber.nettank.client.game.effects.ExplosionEffect;
import org.chrisgruber.nettank.client.game.effects.FlameEffect;
import org.chrisgruber.nettank.client.game.effects.SmokeEffect;
import org.chrisgruber.nettank.client.game.entities.ClientBullet;
import org.chrisgruber.nettank.client.game.entities.ClientTank;
import org.chrisgruber.nettank.client.game.world.ClientGameMap;
import org.chrisgruber.nettank.common.entities.BulletData;
import org.chrisgruber.nettank.common.entities.TankData;
import org.chrisgruber.nettank.common.util.Colors;
import org.chrisgruber.nettank.common.util.GameState;

import org.joml.Vector2f;
import org.joml.Vector3f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javax.swing.*; // For JOptionPane on error/disconnect
import java.io.IOException;
import java.util.*;
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
    private static final float UI_TEXT_SCALE_SECONDARY_STATUS = 0.95f; // Slightly smaller for secondary status
    private static final float UI_TEXT_SCALE_STATUS = 1.0f; // Size for status messages like HIT POINTS
    private static final float UI_TEXT_SCALE_ANNOUNCEMENT = 1.3f; // For large center messages like announcements
    private static final float UI_TEXT_SCALE_KILL_FEED = 0.75f; // Scale for kill feed messages

    private static final long KILL_FEED_DISPLAY_TIME_MS = 10000L; // 10 seconds display time

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
    private int playerKills = 0;
    private final List<KillFeedMessage> killFeedMessages = new CopyOnWriteArrayList<>();

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
    public static final float VIEW_RANGE = 400.0f;

    // Game world map
    private int mapWidthTiles = -1;
    private int mapHeightTiles = -1;
    private float mapTileSize = -1.0f;
    private boolean mapInitialized = false;
    private volatile boolean mapInfoReceivedForProcessing = false;

    // Tank explosion effect
    private final List<Texture> explosionFrameTextures = new ArrayList<>();
    private final List<ExplosionEffect> activeExplosions = new CopyOnWriteArrayList<>();
    private static final int EXPLOSION_TOTAL_FRAMES = 8; // 8 animation frames
    private static final long EXPLOSION_DURATION_MS = 600; // Adjust duration as needed (e.g., 600ms for 8 frames)
    private static final float EXPLOSION_RENDER_SIZE = 80.0f; // How big to draw it
    private static final String EXPLOSION_FILENAME_PREFIX = "textures/explosion/Explosion_"; // Base name
    private static final String EXPLOSION_FILENAME_SUFFIX = ".png"; // File extension

    // Flame effect
    private final List<Texture> flameFrameTextures = new ArrayList<>();
    private final List<FlameEffect> activeFlames = new CopyOnWriteArrayList<>();
    private static final int FLAME_TOTAL_FRAMES = 8;
    private static final long FLAME_DURATION_MS = 1000; // Flames last longer? (1 second)
    private static final float FLAME_RENDER_SIZE = 65.0f; // Flames slightly smaller?
    private static final String FLAME_FILENAME_PREFIX = "textures/flame/Flame_"; // Adjust path/prefix
    private static final String FLAME_FILENAME_SUFFIX = ".png";

    // Smoke effect
    private final List<Texture> smokeFrameTextures = new ArrayList<>();
    private final Map<Integer, SmokeEffect> activeSmokeEffects = new ConcurrentHashMap<>();
    private static final int SMOKE_TOTAL_FRAMES = 3;
    private static final long SMOKE_FRAME_DURATION_MS = 150; // Adjust speed (e.g., 150ms per frame)
    private static final float SMOKE_RENDER_SIZE = 55.0f;   // Slightly smaller than explosion? 35.0f
    private static final String SMOKE_FILENAME_PREFIX = "textures/smoke/Smoke_"; // Base name
    private static final String SMOKE_FILENAME_SUFFIX = ".png"; // File extension

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
            bulletTexture = new Texture("textures/bullet.png");
            tankTexture = new Texture("textures/tank.png");
            uiManager.loadFontTexture("textures/font.png");
            logger.debug("Textures loaded.");

            // --- Load Individual Explosion Frames ---
            logger.debug("Loading explosion frame textures...");
            boolean explosionLoadSuccess = true;
            for (int i = 0; i < EXPLOSION_TOTAL_FRAMES; i++) {
                String filename = EXPLOSION_FILENAME_PREFIX + i + EXPLOSION_FILENAME_SUFFIX;
                try {
                    logger.trace("Loading explosion frame: {}", filename);
                    Texture frameTexture = new Texture(filename);
                    explosionFrameTextures.add(frameTexture);
                } catch (IOException e) {
                    logger.error("Failed to load explosion frame texture: {}", filename, e);
                    explosionLoadSuccess = false;
                    // Option 1: Stop loading further frames
                    // break;
                    // Option 2: Continue, but log that the animation will be incomplete
                    // (Need to handle potential errors during rendering/effect creation later if continuing)
                    // For simplicity, let's break on first failure
                    break;
                }
            }
            if (explosionLoadSuccess && explosionFrameTextures.size() == EXPLOSION_TOTAL_FRAMES) {
                logger.debug("Successfully loaded {} explosion frame textures.", EXPLOSION_TOTAL_FRAMES);
            } else {
                logger.error("Failed to load all explosion frames. Animation may be incomplete or broken.");
                // You might want to throw an exception or handle this more gracefully
                // For now, it will proceed with however many frames loaded successfully before failure.
                // Clear the list if loading failed partway to prevent using incomplete data?
                // if (!explosionLoadSuccess) explosionFrameTextures.clear(); // Example cleanup
            }
            // ---------------------------------------

            // --- Load Flame Frames ---
            logger.debug("Loading flame frame textures...");
            boolean flameLoadSuccess = true;
            for (int i = 0; i < FLAME_TOTAL_FRAMES; i++) {
                String filename = FLAME_FILENAME_PREFIX + i + FLAME_FILENAME_SUFFIX;
                try {
                    logger.trace("Loading flame frame: {}", filename);
                    Texture frameTexture = new Texture(filename);
                    flameFrameTextures.add(frameTexture);
                } catch (IOException e) {
                    logger.error("Failed to load flame frame texture: {}", filename, e);
                    flameLoadSuccess = false;
                    break; // Stop loading if one fails
                }
            }
            if (flameLoadSuccess && flameFrameTextures.size() == FLAME_TOTAL_FRAMES) {
                logger.debug("Successfully loaded {} flame frame textures.", FLAME_TOTAL_FRAMES);
            } else {
                logger.error("Failed to load all flame frames. Flame effect may be broken.");
                // Consider clearing flameFrameTextures if loading failed?
            }
            // ---------------------------

            // --- Load Smoke Frame Textures ---
            logger.debug("Loading smoke frame textures...");
            boolean smokeLoadSuccess = true;
            for (int i = 0; i < SMOKE_TOTAL_FRAMES; i++) {
                String filename = SMOKE_FILENAME_PREFIX + i + SMOKE_FILENAME_SUFFIX;
                try {
                    logger.trace("Loading smoke frame: {}", filename);
                    Texture frameTexture = new Texture(filename);
                    smokeFrameTextures.add(frameTexture);
                } catch (IOException e) {
                    logger.error("Failed to load smoke frame texture: {}", filename, e);
                    smokeLoadSuccess = false;
                    break; // Stop loading on failure
                }
            }
            if (smokeLoadSuccess && smokeFrameTextures.size() == SMOKE_TOTAL_FRAMES) {
                logger.debug("Successfully loaded {} smoke frame textures.", SMOKE_TOTAL_FRAMES);
            } else {
                logger.error("Failed to load all smoke frames. Smoke effect disabled.");
                smokeFrameTextures.clear(); // Ensure list is empty if loading failed
            }
            // ----------------------------------


            // Setup projection matrix
            shader.bind();
            shader.setUniformMat4f("u_projection", camera.getProjectionMatrix());
            shader.setUniform1i("u_texture", 0); // Use texture unit 0

            // Start networking game client AFTER core graphics setup
            logger.info("Starting network client...");
            currentGameState = GameState.CONNECTING;
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
        // --- Update Active Explosions & Trigger Flames ---
        activeExplosions.removeIf(explosion -> {
            boolean explosionFinished = explosion.update(); // Update explosion animation
            if (explosionFinished) {
                logger.trace("Explosion finished at ({},{}), triggering flame.", explosion.getPosition().x, explosion.getPosition().y);
                // *** TRIGGER FLAME EFFECT HERE ***
                if (!flameFrameTextures.isEmpty()) { // Check if flame textures are loaded
                    try {
                        FlameEffect flame = new FlameEffect(
                                explosion.getPosition(), // Start flame at explosion's location
                                FLAME_DURATION_MS,
                                flameFrameTextures,      // Pass the list of flame textures
                                FLAME_RENDER_SIZE
                        );
                        activeFlames.add(flame); // Add to the list of active flames
                        logger.trace("Added new flame effect.");
                    } catch (Exception e) {
                        logger.error("Failed to create FlameEffect instance", e);
                    }
                } else {
                    logger.warn("Cannot create flame effect, flame textures not loaded or list is empty.");
                }
                // ********************************
            }
            return explosionFinished; // Return true to remove the explosion itself
        });
        // ----------------------------------------------

        // --- Update Active Flames ---
        activeFlames.removeIf(flame -> {
            boolean flameFinished = flame.update(); // Update flame animation
            if (flameFinished) {
                logger.trace("Flame effect finished at ({},{}).", flame.getPosition().x, flame.getPosition().y);
            }
            return flameFinished; // removeIf removes finished flames
        });
        // --------------------------------

        // --- Update Active Smoke Effects ---
        List<Integer> playersToRemoveSmoke = new ArrayList<>();
        for (Map.Entry<Integer, SmokeEffect> entry : activeSmokeEffects.entrySet()) {
            SmokeEffect smoke = entry.getValue();
            if (smoke.isActive()) {
                smoke.update(); // Update animation frame
            } else {
                // If smoke was stopped (by respawn/disconnect), mark its player ID for removal
                playersToRemoveSmoke.add(entry.getKey());
            }
        }
        // Remove stopped smoke effects from the map *after* iterating
        for (Integer playerId : playersToRemoveSmoke) {
            activeSmokeEffects.remove(playerId);
            logger.trace("Removed stopped smoke effect for player {}.", playerId);
        }
        // ----------------------------------

        if (mapInfoReceivedForProcessing && !mapInitialized) {
            initializeMapAndTextures();
            mapInfoReceivedForProcessing = false; // Reset the signal flag
        }

        // Update local bullet positions for client-side prediction
        List<ClientBullet> bulletsToRemove = new ArrayList<>();
        long now = System.currentTimeMillis();

        killFeedMessages.removeIf(msg -> now >= msg.expiryTimeMillis());

        for (ClientBullet bullet : bullets) {
            bullet.update(deltaTime);

            // Check for bullet expiry or out of bounds
            if ((now - bullet.getSpawnTime() >= BulletData.LIFETIME_MS) ||
                    (mapInitialized && gameMap != null && gameMap.isOutOfBounds(bullet))) {
                bulletsToRemove.add(bullet);
            }
        }

        bullets.removeAll(bulletsToRemove);

        // Update camera position to follow local tank (if it exists)
        if (localTank != null) {
            camera.setPosition(localTank.getPosition().x(), localTank.getPosition().y());
        } else if (isSpectating && mapInitialized && gameMap != null) {
            camera.setPosition(gameMap.getWorldWidth() / 2.0f, gameMap.getWorldHeight() / 2.0f);
        } else if (!mapInitialized) {
            // Keep camera at default or 0,0 while map loads
            camera.setPosition(0,0);
        }

        camera.update(); // Update camera matrices

        // Update announcements display timer
        if (!announcements.isEmpty() && (now - lastAnnouncementTime > ANNOUNCEMENT_DISPLAY_TIME_MS)) {
            announcements.removeFirst();    // Remove the oldest announcement
            lastAnnouncementTime = announcements.isEmpty() ? 0 : now;   // Reset timer
        }
    }

    @Override
    public void storeMapInfo(int widthTiles, int heightTiles, float tileSize) {
        logger.info("Stored Map Info from network: WidthTiles={}, HeightTiles={}, TileSize={}", widthTiles, heightTiles, tileSize);
        this.mapWidthTiles = widthTiles;
        this.mapHeightTiles = heightTiles;
        this.mapTileSize = tileSize;
        this.mapInfoReceivedForProcessing = true; // Signal the main thread
    }

    private void initializeMapAndTextures() {
        if (mapInitialized) return; // Should not happen if logic is correct, but safe check

        logger.info("Main thread initializing map with dimensions: {}x{}", mapWidthTiles, mapHeightTiles);
        try {
            // Create the map object (uses the stored dimensions)
            this.gameMap = new ClientGameMap(mapWidthTiles, mapHeightTiles);

            logger.debug("Main thread loading map textures...");
            grassTexture = new Texture("textures/grass.png");
            dirtTexture = new Texture("textures/dirt.png");
            logger.debug("Map textures loaded by main thread.");

            mapInitialized = true; // Mark map as fully ready
            logger.info("ClientGameMap and textures initialized successfully.");

        } catch (IOException e) {
            logger.error("Failed to initialize map or map textures on main thread", e);
            handleInitializationError("Failed to load map resources:\n" + e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error during map initialization on main thread", e);
            handleInitializationError("Unexpected error initializing map:\n" + e.getMessage());
        }
    }

    // Helper for error handling during initialization
    private void handleInitializationError(String message) {
        if (gameClient != null) gameClient.stop();
        SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(null, message, "Initialization Error", JOptionPane.ERROR_MESSAGE));
        if (windowHandle != NULL) glfwSetWindowShouldClose(windowHandle, true);
    }


    @Override
    public void renderGame() {
        shader.bind();
        shader.setUniformMat4f("u_view", camera.getViewMatrix());
        shader.setUniform1i("u_texture", 0); // Ensure texture unit 0
        glActiveTexture(GL_TEXTURE0);

        // Render Map
        if (mapInitialized && gameMap != null) {
            float range = isSpectating ? Float.MAX_VALUE : VIEW_RANGE;
            // Ensure map textures are loaded before rendering
            if (grassTexture != null && dirtTexture != null) {
                gameMap.render(renderer, shader, grassTexture, dirtTexture, camera, range);
            } else {
                logger.warn("Attempted to render map, but map textures are not loaded.");
            }
        } else if (!mapInitialized) {
            // Optionally render a "Loading Map..." message or just black background
            // logger.trace("Map not yet initialized, skipping map rendering.");
        }

        // Render Tanks
        Vector2f playerPos = (localTank != null) ? localTank.getPosition() : null;
        float renderRangeSq = isSpectating ? Float.MAX_VALUE : VIEW_RANGE * VIEW_RANGE;

        tankTexture.bind(); // Bind tank texture once

        for (ClientTank tank : tanks.values()) {
            if (isObjectVisible(tank.getPosition(), playerPos, renderRangeSq)) {
                shader.setUniform3f("u_tintColor", tank.getColor());

                renderer.drawQuad(tank.getPosition().x, tank.getPosition().y,
                        TankData.SIZE, TankData.SIZE,
                        tank.getRotation(), shader);
            }
        }

        shader.setUniform3f("u_tintColor", 1.0f, 1.0f, 1.0f);

        // Render Bullets
        bulletTexture.bind(); // Bind bullet texture once
        shader.setUniform3f("u_tintColor", 1.0f, 1.0f, 1.0f);

        for (ClientBullet bullet : bullets) {
            if (isObjectVisible(bullet.getPosition(), playerPos, renderRangeSq) && !bullet.isDestroyed()) {
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

        // --- Render Explosions ---
        if (!activeExplosions.isEmpty()) {
            shader.setUniform3f("u_tintColor", 1.0f, 1.0f, 1.0f); // Reset tint

            for (ExplosionEffect explosion : activeExplosions) {
                if (explosion.isFinished()) continue;

                // Get the specific texture for the current frame
                Texture currentFrameTexture = explosion.getCurrentFrameTexture();
                if (currentFrameTexture == null) {
                    logger.warn("Explosion effect returned null texture for frame, skipping render.");
                    continue; // Skip if texture is missing
                }

                // *** Bind the texture for THIS frame ***
                currentFrameTexture.bind();

                // Get position and size
                Vector2f pos = explosion.getPosition();
                float size = explosion.getRenderSize();

                // Use the original drawQuad method, as we render the whole texture
                renderer.drawQuad(pos.x, pos.y, size, size, 0f, shader);

                // Unbind texture? Not strictly necessary if the next loop iteration or
                // subsequent rendering binds another texture, but can be good practice.
                // currentFrameTexture.unbind(); // Optional
            }
        }
        // -------------------------

        // --- NEW: Render Flames ---
        if (!activeFlames.isEmpty()) {
            shader.setUniform3f("u_tintColor", 1.0f, 1.0f, 1.0f); // Reset tint if needed
            for (FlameEffect flame : activeFlames) {
                if (flame.isFinished()) continue; // Skip finished ones

                Texture currentFrameTexture = flame.getCurrentFrameTexture();
                if (currentFrameTexture == null) {
                    logger.warn("Flame effect returned null texture for frame, skipping render.");
                    continue;
                }

                // Bind the specific flame texture for this frame
                currentFrameTexture.bind();

                Vector2f pos = flame.getPosition();
                float size = flame.getRenderSize();

                // Render the quad using the bound flame texture
                renderer.drawQuad(pos.x, pos.y, size, size, 0f, shader);
            }
        }
        // -------------------------

        // --- Render Smoke Effects ---
        if (!activeSmokeEffects.isEmpty()) {
            shader.setUniform3f("u_tintColor", 1.0f, 1.0f, 1.0f); // Reset tint

            for (SmokeEffect smoke : activeSmokeEffects.values()) {
                if (!smoke.isActive()) continue; // Skip rendering if stopped

                Texture currentFrameTexture = smoke.getCurrentFrameTexture();
                if (currentFrameTexture != null) {
                    currentFrameTexture.bind(); // Bind the texture for THIS frame

                    Vector2f pos = smoke.getPosition();
                    float size = smoke.getRenderSize();

                    // Use the standard drawQuad for the whole frame texture
                    renderer.drawQuad(pos.x, pos.y, size, size, 0f, shader);
                }
            }
        }
        // --------------------------

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
        final float killsY = 55;     // Y position for Player Kills (below Timer + padding)
        final float killFeedPaddingX = 10; // Padding from the right edge
        final float killFeedStartY = 10;   // Starting Y position from the top
        final float killFeedLineHeight = 20; // Vertical space between messages (adjust as needed)
        final float playersCountY = 75; // Y position for Players Count (below Kills + padding)

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

        // Render Player's Kills
        if (localTank != null && !isSpectating) {
            String killsStr = "KILLS: " + playerKills;
            // Use statusTextX, the new killsY, a suitable scale, and color
            uiManager.drawText(killsStr,
                    statusTextX, killsY, UI_TEXT_SCALE_SECONDARY_STATUS, Colors.RED); // Using secondary scale, white color
        }

        if (localTank != null && !isSpectating) {
            var playersCountStr = "PLAYERS: " + tanks.size();
            uiManager.drawText(playersCountStr, statusTextX, playersCountY, UI_TEXT_SCALE_SECONDARY_STATUS, Colors.WHITE);
        }

        // Render Kill Feed Messages
        float currentKillFeedY = killFeedStartY;

        for (KillFeedMessage feedMessage : killFeedMessages) {
            // Calculate the width of the message text
            float textWidth = uiManager.getTextWidth(feedMessage.message(), UI_TEXT_SCALE_KILL_FEED);

            // Calculate the X position (right-aligned)
            float x = windowWidth - textWidth - killFeedPaddingX;

            // Select the text color
            Vector3f textColor;

            switch (feedMessage.getStatusMessageKind()) {
                case StatusMessageKind.PlayerKilled -> textColor = Colors.RED;
                case StatusMessageKind.PlayerLeft -> textColor = Colors.ORANGE;
                case StatusMessageKind.PlayerJoined -> textColor = Colors.WHITE;
                default -> textColor = Colors.CYAN;
            }

            // Draw the text
            uiManager.drawText(feedMessage.message(), x, currentKillFeedY, UI_TEXT_SCALE_KILL_FEED, textColor);

            // Move down for the next message
            currentKillFeedY += killFeedLineHeight;
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

        // --- Cleanup Explosion Frame Textures ---
        logger.debug("Deleting explosion frame textures...");
        for (Texture frameTexture : explosionFrameTextures) {
            try {
                if (frameTexture != null) frameTexture.delete();
            } catch (Exception e) {
                logger.error("Error deleting explosion frame texture", e);
            }
        }
        explosionFrameTextures.clear(); // Clear the list
        // ---------------------------------------

        // --- Cleanup Flame Frame Textures ---
        logger.debug("Deleting flame frame textures...");
        for (Texture frameTexture : flameFrameTextures) {
            try {
                if (frameTexture != null) frameTexture.delete();
            } catch (Exception e) {
                logger.error("Error deleting flame frame texture", e);
            }
        }
        flameFrameTextures.clear(); // Clear the list
        // ---------------------------------------

        // --- Cleanup Smoke Frame Textures ---
        logger.debug("Deleting smoke frame textures...");
        for (Texture frameTexture : smokeFrameTextures) {
            try {
                if (frameTexture != null) frameTexture.delete();
            } catch (Exception e) {
                logger.error("Error deleting smoke frame texture", e);
            }
        }
        smokeFrameTextures.clear();
        // ---------------------------------------

        // Clear explosion list
        activeExplosions.clear();
        activeFlames.clear();
        activeSmokeEffects.clear();

        logger.debug("Deleting game textures, shaders, renderer, UI...");

        try { if (tankTexture != null) tankTexture.delete(); } catch (Exception e) { logger.error("Error deleting tankTexture", e); }
        try { if (bulletTexture != null) bulletTexture.delete(); } catch (Exception e) { logger.error("Error deleting bulletTexture", e); }

        if (mapInitialized) {
            try { if (grassTexture != null) grassTexture.delete(); } catch (Exception e) { logger.error("Error deleting grassTexture", e); }
            try { if (dirtTexture != null) dirtTexture.delete(); } catch (Exception e) { logger.error("Error deleting dirtTexture", e); }
        }

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
        killFeedMessages.clear();

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

        TankData data = new TankData();
        data.updateFromServer(id, name, x, y, rotation, r, g, b);

        if (tank == null) {
            logger.info("Creating new ClientTank for player ID: {} Name: {}", id, name);

            tank = new ClientTank(data); // Create client wrapper

            tanks.put(id, tank);    // Put the NEW tank into the map *after* creating it

            if (id == localPlayerId) {
                localTank = tank;
                isSpectating = tank.isDestroyed();
                logger.info("Local tank object created. Spectating: {}", isSpectating);
            }

            var statusMessage = String.format("%s has joined", tank.getName());
            addStatusMessage(StatusMessageKind.PlayerJoined, statusMessage);
        } else {
            logger.trace("Updating existing ClientTank for player ID: {}", id);

            tank.updateFromServerEntity(data);

            if (id == localPlayerId) {
                localTank = tank;
            }
        }
    }

    // Called when PLAYER_LEFT is received
    public void removeTank(int id) {
        SmokeEffect smoke = activeSmokeEffects.remove(id);

        if (smoke != null) {
            smoke.stop(); // Mark as inactive (though it's removed anyway)
            logger.info("Stopped and removed smoke effect for leaving player {}.", id);
        }

        ClientTank removed = tanks.remove(id);

        if (removed != null) {
            logger.info("Removed tank for player ID: {} Name: {}", id, removed.getName());

            var statusMessage = String.format("%s has left", removed.getName());
            addStatusMessage(StatusMessageKind.PlayerLeft, statusMessage);
        } else {
            logger.warn("Received removeTank for unknown ID: {}", id);
        }
    }

    // Called when PLAYER_UPDATE is received
    public void updateTankState(int id, float x, float y, float rotation, boolean isRespawn) {
        ClientTank tank = tanks.get(id);

        if (tank == null) {
            logger.warn("Received updateTankState for unknown Player ID: {}", id);
            return;
        }

        if (tank.getPosition().x() == x && tank.getPosition().y() == y && tank.getRotation() == rotation) {
            // no change in state
            logger.trace("No state change for tank ID: {} x: {}, y: {}, rotation: {}", id, x, y, rotation);
            return;
        }

        if (isRespawn) {
            // Stop and remove the smoke effect for this player
            SmokeEffect smoke = activeSmokeEffects.get(tank.getPlayerId());
            if (smoke != null) {
                smoke.stop(); // Mark as inactive
                // Removal happens during the update loop's cleanup phase
            } else {
                logger.warn("No active smoke effect found for respawning player {}.", tank.getPlayerId());
            }
        }

        logger.trace("Updating tank state for player ID: {}. Existing state is x: {}, y: {}, rotation: {}", id, tank.getPosition().x(), tank.getPosition().y(), tank.getRotation());

        tank.HandlerPlayerUpdateMessage(new Vector2f(x, y), rotation);

        logger.trace("Updated tank state for player ID: {} x: {}, y: {}, rotation: {}", id, x, y, rotation);
    }

    // Called when SHOOT is received
    public void spawnBullet(UUID bulletId, int ownerId, float x, float y, float dirX, float dirY) {
        // Calculate velocity based on direction and common speed
        Vector2f velocity = new Vector2f(dirX, dirY).normalize().mul(BulletData.SPEED);
        Vector2f position = new Vector2f(x, y);
        long spawnTime = System.currentTimeMillis(); // Client uses its own time for prediction expiry

        // TODO: Where is bullet rotation set and should it be here?
        float rotation = 0.0f;

        // Create common BulletData
        BulletData bulletData = new BulletData(bulletId, ownerId, position, velocity, rotation, spawnTime, false);
        // Create ClientBullet wrapper for rendering/prediction
        ClientBullet clientBullet = new ClientBullet(bulletData);

        bullets.add(clientBullet);

        logger.trace("Spawned bullet owned by {}", ownerId);
    }

    @Override
    public void handlePlayerHit(int targetId, int shooterId, UUID bulletId, int damage) {
        // TODO: sprites, animations and UI updates

        logger.debug("Player hit: Target={}, Shooter={}, BulletID={}, Damage={}",
                targetId, shooterId, bulletId, damage);

        // Create a separate list to avoid ConcurrentModificationException
        List<ClientBullet> bulletsToRemove = new ArrayList<>();

        // Find the bullet to remove
        for (ClientBullet bullet : bullets) {
            if (bullet.getId().equals(bulletId)) {
                bulletsToRemove.add(bullet);
                logger.debug("Marked bullet with ID {} for removal", bulletId);
                break;
            }
        }

        // Remove the bullets outside the iteration
        bullets.removeAll(bulletsToRemove);
    }

    @Override
    public void handlePlayerDestroyed(int targetId, int shooterId) {
        // Existing logic to handle visual effects or messages for any destruction might go here.
        logger.debug("Received PlayerDestroyed: Target={}, Shooter={}", targetId, shooterId);

        // Check if the shooter is the *local* player
        if (shooterId == this.localPlayerId && localPlayerId != -1) {
            // Increment the local player's kill count
            this.playerKills++;
            logger.info("Local player ({}) got a kill! Total kills: {}", localPlayerId, playerKills);
            // TODO: maybe add a temporary announcement here like "YOU DESTROYED A TANK!"
        }

        ClientTank shooterTank = tanks.get(shooterId);
        ClientTank targetTank = tanks.get(targetId);

        // --- Spawn Explosion ---
        // Check if target exists AND if explosion textures were loaded successfully
        if (targetTank != null && !explosionFrameTextures.isEmpty()) {
            logger.info("Spawning explosion at target {}'s location: ({}, {})", targetId, targetTank.getPosition().x(), targetTank.getPosition().y());
            try {
                ExplosionEffect explosion = new ExplosionEffect(
                        targetTank.getPosition(),
                        EXPLOSION_DURATION_MS,
                        explosionFrameTextures, // Pass the whole list
                        EXPLOSION_RENDER_SIZE
                );
                activeExplosions.add(explosion);
            } catch (Exception e) {
                // Catch potential errors from ExplosionEffect constructor (e.g., empty list if loading failed)
                logger.error("Failed to create ExplosionEffect instance", e);
            }
        } else {
            if (targetTank == null) logger.warn("Cannot spawn explosion, target tank {} not found.", targetId);
            if (explosionFrameTextures.isEmpty()) logger.warn("Cannot spawn explosion, frame textures not loaded or list is empty.");
        }
        // ---------------------

        // --- Spawn Smoke Effect ---
        // Check if target exists AND if smoke textures were loaded
        if (targetTank != null && !smokeFrameTextures.isEmpty()) {
            // Remove existing smoke for this player first, just in case (shouldn't happen often)
            SmokeEffect existingSmoke = activeSmokeEffects.remove(targetId);
            if (existingSmoke != null) {
                logger.warn("Removed existing smoke effect for player {} before creating new one.", targetId);
                // Note: Existing effect object is now orphaned and will be garbage collected.
            }

            logger.info("Spawning smoke effect for player {} at ({}, {})", targetId, targetTank.getPosition().x(), targetTank.getPosition().y());
            try {
                SmokeEffect smoke = new SmokeEffect(
                        targetTank.getPosition(),
                        targetId, // Store the player ID with the effect
                        smokeFrameTextures,
                        SMOKE_FRAME_DURATION_MS,
                        SMOKE_RENDER_SIZE
                );
                activeSmokeEffects.put(targetId, smoke); // Put in map using player ID as key
            } catch (Exception e) {
                logger.error("Failed to create SmokeEffect instance for player {}", targetId, e);
            }
        } else {
            if (targetTank == null) logger.warn("Cannot spawn smoke, target tank {} not found.", targetId);
            if (smokeFrameTextures.isEmpty()) logger.warn("Cannot spawn smoke, frame textures not loaded.");
        }
        // ------------------------

        String shooterName = (shooterTank != null) ? shooterTank.getName() : ("Player " + shooterId);
        String targetName = (targetTank != null) ? targetTank.getName() : ("Player " + targetId);

        // Construct the message
        String killMessage = shooterName + " KILLED " + targetName;
        addStatusMessage(StatusMessageKind.PlayerKilled, killMessage);
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
        if (state != GameState.COUNTDOWN) { // Keep announcements during countdown? Optional.
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
                playerKills = 0; // Reset player kills on new round
                this.killFeedMessages.clear();
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

    private void addStatusMessage(StatusMessageKind statusMessageKind, String message) {
        // Calculate expiry time
        long expiryTime = System.currentTimeMillis() + KILL_FEED_DISPLAY_TIME_MS;

        // Create and add the message object
        killFeedMessages.add(new KillFeedMessage(statusMessageKind, message, expiryTime));
        logger.trace("Added status message: '{}'", message);
    }
}