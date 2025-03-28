package org.chrisgruber.nettank.main;

import org.chrisgruber.nettank.entities.Bullet;
import org.chrisgruber.nettank.entities.Tank;
import org.chrisgruber.nettank.game.GameMap;
import org.chrisgruber.nettank.input.InputHandler;
import org.chrisgruber.nettank.network.GameClient;
import org.chrisgruber.nettank.network.GameServer;
import org.chrisgruber.nettank.rendering.Camera;
import org.chrisgruber.nettank.rendering.Renderer;
import org.chrisgruber.nettank.rendering.Shader;
import org.chrisgruber.nettank.rendering.Texture;
import org.chrisgruber.nettank.ui.UIManager;
import org.chrisgruber.nettank.util.Colors;
import org.chrisgruber.nettank.util.GameState;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.lwjgl.glfw.*;
import org.lwjgl.opengl.*;
import org.lwjgl.system.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.io.IOException;
import java.net.InetAddress;
import java.nio.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.lwjgl.glfw.Callbacks.*;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.GL_TEXTURE0;
import static org.lwjgl.opengl.GL13.glActiveTexture;
import static org.lwjgl.system.MemoryStack.*;
import static org.lwjgl.system.MemoryUtil.*;

public class Game {
    private static final Logger logger = LoggerFactory.getLogger(Game.class);
    private long window;
    private InputHandler inputHandler;
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
    private GameMap gameMap;
    private final Map<Integer, Tank> tanks = new ConcurrentHashMap<>();
    private final List<Bullet> bullets = new CopyOnWriteArrayList<>(); // Use concurrent list

    // Player specific
    private int localPlayerId = -1;
    private Tank localTank = null;
    private String playerName;
    private boolean isSpectating = false;
    private long roundStartTimeMillis = 0;
    private long finalElapsedTimeMillis = -1;


    // Networking
    private GameClient gameClient;
    private String serverIp;
    private int serverPort;

    // Game State
    private volatile GameState currentGameState = GameState.CONNECTING;
    private final List<String> announcements = new CopyOnWriteArrayList<>();
    private long lastAnnouncementTime = 0;
    private static final long ANNOUNCEMENT_DISPLAY_TIME_MS = 5000; // Display for 5 seconds
    private boolean isHost = false;

    // Config
    public static final int WINDOW_WIDTH = 1280;
    public static final int WINDOW_HEIGHT = 720;
    public static final float VIEW_RANGE = 300.0f; // Pixels
    public static final float TILE_SIZE = 32.0f;

    public Game(String serverIp, int serverPort, String playerName) {
        this.serverIp = serverIp;
        this.serverPort = serverPort;
        this.playerName = playerName;
    }

    public void run() {
        init();
        loop();

        logger.info("Main game loop finished. Calling cleanup...");

        cleanup();
    }

    private void init() {
        // Setup an error callback. The default implementation
        // will print the error message in System.err.
        GLFWErrorCallback.createPrint(System.err).set();

        // Initialize GLFW. Most GLFW functions will not work before doing this.
        if (!glfwInit()) {
            throw new IllegalStateException("Unable to initialize GLFW");
        }

        // Configure GLFW
        glfwDefaultWindowHints(); // optional, the current window hints are already the default
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 4);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 1);
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
        glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, GLFW_TRUE); // Required on macOS
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE); // the window will stay hidden after creation
        glfwWindowHint(GLFW_RESIZABLE, GLFW_FALSE); // the window will be resizable

        // Create the window
        window = glfwCreateWindow(WINDOW_WIDTH, WINDOW_HEIGHT, "Tank Battle", NULL, NULL);
        if (window == NULL) {
            throw new RuntimeException("Failed to create the GLFW window");
        }

        inputHandler = new InputHandler(window);
        //glfwSetKeyCallback(window, inputHandler::keyCallback);
        glfwSetKeyCallback(window, (thewindow, key, scancode, action, mods) -> inputHandler.keyCallback(key, action));

        // Get the thread stack and push a new frame
        try (MemoryStack stack = stackPush()) {
            IntBuffer pWidth = stack.mallocInt(1); // int*
            IntBuffer pHeight = stack.mallocInt(1); // int*

            // Get the window size passed to glfwCreateWindow
            glfwGetWindowSize(window, pWidth, pHeight);

            // Get the resolution of the primary monitor
            GLFWVidMode vidmode = glfwGetVideoMode(glfwGetPrimaryMonitor());

            // Center the window
            glfwSetWindowPos(
                    window,
                    (vidmode.width() - pWidth.get(0)) / 2,
                    (vidmode.height() - pHeight.get(0)) / 2
            );
        } // the stack frame is popped automatically

        // Make the OpenGL context current
        glfwMakeContextCurrent(window);
        // Enable v-sync
        glfwSwapInterval(1);

        // Make the window visible
        glfwShowWindow(window);

        // This line is critical for LWJGL's interoperation with GLFW's
        // OpenGL context, or any context that is managed externally.
        // LWJGL detects the context that is current in the current thread,
        // creates the GLCapabilities instance and makes the OpenGL
        // bindings available for use.
        GL.createCapabilities();

        System.out.println("OpenGL Version: " + glGetString(GL_VERSION));
        //System.out.println("LWJGL Version: " + Version.getVersion());

        // Set the clear color
        glClearColor(0.0f, 0.0f, 0.0f, 0.0f); // Black background

        // Enable blending for transparency
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        // Load shaders
        try {
            shader = new Shader("/shaders/quad.vert", "/shaders/quad.frag");
            shader.bind();
        } catch (IOException e) {
            throw new RuntimeException("Failed to load shaders: " + e.getMessage(), e);
        }

        // Create renderer and camera
        renderer = new Renderer();
        camera = new Camera(WINDOW_WIDTH, WINDOW_HEIGHT);
        uiManager = new UIManager(); // Initialize UI Manager

        // Load textures (provide actual paths)
        try {
            grassTexture = new Texture("textures/grass.png");
            dirtTexture = new Texture("textures/dirt.png");
            bulletTexture = new Texture("textures/bullet.png");
            tankTexture = new Texture("textures/tank.png");
            uiManager.loadFontTexture("textures/font.png"); // Assuming a font texture exists
        } catch (IOException e) {
            throw new RuntimeException("Failed to load textures: " + e.getMessage(), e);
        }

        // Initialize Map (hardcoded for now)
        gameMap = new GameMap(50, 50); // Example size

        // Setup projection matrix
        shader.bind();
        shader.setUniformMat4f("u_projection", camera.getProjectionMatrix());
        shader.setUniform1i("u_texture", 0); // Use texture unit 0

        // Start networking
        startNetworkClient();
    }

    private void startNetworkClient() {
        gameClient = new GameClient(serverIp, serverPort, playerName, this);
        new Thread(gameClient).start();
        logger.info("Started network client");
    }

    private void loop() {
        double lastTime = glfwGetTime();
        double currentTime;
        float deltaTime;

        // Run the rendering loop until the user has attempted to close
        // the window or has pressed the ESCAPE key.
        while (!glfwWindowShouldClose(window)) {
            currentTime = glfwGetTime();
            deltaTime = (float) (currentTime - lastTime);
            lastTime = currentTime;

            // --- Input ---
            glfwPollEvents(); // Poll for window events. The key callback will only be invoked during this call.
            handleInput(deltaTime); // Handle continuous input

            // --- Update ---
            update(deltaTime);
            inputHandler.poll();

            // --- Render ---
            render();

            glfwSwapBuffers(window); // swap the color buffers
        }
    }

    private void handleInput(float deltaTime) {
        // Check if we can process input
        if (localTank != null && !isSpectating && currentGameState == GameState.PLAYING) {

            // Determine current key states
            boolean keyW = inputHandler.isKeyDown(GLFW_KEY_W);
            boolean keyS = inputHandler.isKeyDown(GLFW_KEY_S);
            boolean keyA = inputHandler.isKeyDown(GLFW_KEY_A);
            boolean keyD = inputHandler.isKeyDown(GLFW_KEY_D);
            boolean keySpace = inputHandler.isKeyPressed(GLFW_KEY_SPACE); // Check for shooting press

            // --- Send Input State to Server ---
            // Send regardless of client-side 'moved' flag. Server needs the actual key states.
            if (gameClient != null && gameClient.isConnected()) {
                gameClient.sendInput(keyW, keyS, keyA, keyD);
            } else {
                // Optional: Log if trying to send but not connected
                // logger.warn("Cannot send input, not connected.");
            }

            // --- Handle Shooting ---
            if (keySpace) {
                if (gameClient != null && gameClient.isConnected()) {
                    logger.debug("Sending SHOOT command"); // Log shoot attempt
                    gameClient.sendShoot();
                }
                // Reset space immediately after checking to prevent holding it down
                // causing multiple isKeyPressed calls (though throttling helps server-side)
                inputHandler.resetKey(GLFW_KEY_SPACE);
            }

            // Optional Client-Side Prediction (Commented out - rely on server for now)
        /*
        float rotationChange = 0.0f;
        if (keyA) rotationChange -= Tank.TURN_SPEED * deltaTime;
        if (keyD) rotationChange += Tank.TURN_SPEED * deltaTime;
        localTank.setRotation(localTank.getRotation() + rotationChange); // Predict rotation

        float moveAmount = 0.0f;
        if (keyW) moveAmount = Tank.MOVE_SPEED * deltaTime;
        else if (keyS) moveAmount = -Tank.MOVE_SPEED * deltaTime * 0.7f;

        if (moveAmount != 0) {
            float angleRad = (float) Math.toRadians(localTank.getRotation());
            float dx = (float) Math.sin(angleRad) * moveAmount;
            // Correct prediction based on likely screen coordinates (+Y is DOWN)
            float dy = (float) -Math.cos(angleRad) * moveAmount;
            localTank.getPosition().add(dx, dy); // Predict position
        }
        */

        } // end if (localTank != null...)

        // --- Handle Input Polling ---
        // This MUST be called every frame after input checks are done
        inputHandler.poll();

        // --- Handle Escape Key ---
        if (inputHandler.isKeyDown(GLFW_KEY_ESCAPE)) {
            logger.info("Escape key pressed.");
            // Check if this client is the host and is connected
            if (isHost && gameClient != null && gameClient.isConnected()) {
                logger.info("Host client requesting server shutdown...");
                gameClient.sendShutdownServer(); // Send the new command
                // Add a small delay to allow the message to be sent before closing socket
                try {
                    Thread.sleep(200); // 200ms, adjust if needed
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    logger.warn("Sleep interrupted while waiting for shutdown message send.");
                }
            } else {
                logger.info("Closing client window (not host or not connected).");
            }
            // Always close the client window on Escape
            glfwSetWindowShouldClose(window, true);
        }
    }

    public void setHostStatus(boolean amHost) {
        this.isHost = amHost;
        if (this.isHost) {
            logger.info("<<< This client IS designated as the HOST by the server >>>");
        } else {
            logger.info("<<< This client is NOT the host >>>");
        }
    }

    private void update(float deltaTime) {
        logger.trace("Game update start");
        // Update local bullet positions (client-side prediction)
        // Bullets are simpler to predict locally as they move linearly
        List<Bullet> bulletsToRemove = new ArrayList<>();
        for (Bullet bullet : bullets) {
            bullet.update(deltaTime);
            // Simple boundary check (could be refined with map bounds)
            if (Math.abs(bullet.getPosition().x) > gameMap.getWidth() * TILE_SIZE ||
                    Math.abs(bullet.getPosition().y) > gameMap.getHeight() * TILE_SIZE ||
                    bullet.isExpired()) {
                bulletsToRemove.add(bullet);
            }
        }
        bullets.removeAll(bulletsToRemove);

        // Update camera position if local tank exists
        if (localTank != null) {
            camera.setPosition(localTank.getPosition().x, localTank.getPosition().y);
        } else if (isSpectating) {
            // Allow free camera movement maybe? Or just center on map?
            camera.setPosition(gameMap.getWidth() * TILE_SIZE / 2.0f, gameMap.getHeight() * TILE_SIZE / 2.0f);
        }
        camera.update();

        // Update announcements display
        long now = System.currentTimeMillis();
        if (!announcements.isEmpty() && (now - lastAnnouncementTime > ANNOUNCEMENT_DISPLAY_TIME_MS)) {
            announcements.remove(0); // Remove the oldest announcement
            lastAnnouncementTime = now; // Reset timer if more announcements exist
            if (announcements.isEmpty()) {
                lastAnnouncementTime = 0; // No more announcements
            }
        }
        logger.trace("Game update end");
    }

    private void render() {
        logger.trace("Game render start");
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT); // clear the framebuffer

        shader.bind();
        shader.setUniformMat4f("u_view", camera.getViewMatrix());
        shader.setUniform1i("u_texture", 0); // Ensure texture unit 0

        glActiveTexture(GL_TEXTURE0);

        float viewLeft = camera.getPosition().x - WINDOW_WIDTH / 2.0f / camera.getZoom();
        float viewRight = camera.getPosition().x + WINDOW_WIDTH / 2.0f / camera.getZoom();
        float viewBottom = camera.getPosition().y - WINDOW_HEIGHT / 2.0f / camera.getZoom();
        float viewTop = camera.getPosition().y + WINDOW_HEIGHT / 2.0f / camera.getZoom();

        // --- Render Map ---
        gameMap.render(renderer, shader, grassTexture, dirtTexture, viewLeft, viewRight, viewBottom, viewTop, localTank != null ? localTank.getPosition() : null, isSpectating ? Float.MAX_VALUE : VIEW_RANGE);

        // --- Render Tanks ---
        Vector2f playerPos = (localTank != null) ? localTank.getPosition() : null;
        float renderRangeSq = isSpectating ? Float.MAX_VALUE : VIEW_RANGE * VIEW_RANGE;

        for (Tank tank : tanks.values()) {
            if (isTankVisible(tank.getPosition(), playerPos, renderRangeSq)) {
                tankTexture.bind();
                shader.setUniform3f("u_tintColor", tank.getColor());
                renderer.drawQuad(tank.getPosition().x, tank.getPosition().y, Tank.SIZE, Tank.SIZE, tank.getRotation(), shader);
            }
        }
        shader.setUniform3f("u_tintColor", 1.0f, 1.0f, 1.0f); // Reset tint for others

        // --- Render Bullets ---
        bulletTexture.bind();
        for (Bullet bullet : bullets) {
            if (isTankVisible(bullet.getPosition(), playerPos, renderRangeSq)) { // Reuse visibility check
                renderer.drawQuad(bullet.getPosition().x, bullet.getPosition().y, Bullet.SIZE, Bullet.SIZE, 0, shader); // Bullets don't rotate visually
            }
        }

        // --- Render UI ---
        renderUI();
        logger.trace("Game render end");
    }

    private boolean isTankVisible(Vector2f objectPos, Vector2f playerPos, float rangeSq) {
        if (isSpectating || playerPos == null) return true; // Spectators see everything
        return objectPos.distanceSquared(playerPos) <= rangeSq;
    }


    private void renderUI() {
        // Use a separate orthographic projection for UI, mapping directly to screen pixels
        uiManager.startUIRendering(WINDOW_WIDTH, WINDOW_HEIGHT);

        // Render Lives
        if (localTank != null && !isSpectating) {
            uiManager.drawText("LIVES: " + localTank.getLives(), 10, 10, 1.5f); // Position (10, 10), Scale 1.5
        } else if (isSpectating) {
            uiManager.drawText("SPECTATING", 10, 10, 1.5f);
        } else {
            uiManager.drawText("CONNECTING...", 10, 10, 1.5f);
        }

        // Render Timer
        if (currentGameState == GameState.PLAYING && roundStartTimeMillis > 0) {
            long elapsedMillis = System.currentTimeMillis() - roundStartTimeMillis;
            long seconds = (elapsedMillis / 1000) % 60;
            long minutes = (elapsedMillis / (1000 * 60)) % 60;
            String timeStr = String.format("TIME: %02d:%02d", minutes, seconds);
            uiManager.drawText(timeStr, WINDOW_WIDTH - 150, 10, 1.5f); // Top right
        } else if (currentGameState == GameState.ROUND_OVER && finalElapsedTimeMillis >= 0) {
            long seconds = (finalElapsedTimeMillis / 1000) % 60;
            long minutes = (finalElapsedTimeMillis / (1000 * 60)) % 60;
            // Message is handled via announcements
        }

        // Render Announcements (centered)
        if (!announcements.isEmpty()) {
            String announcement = announcements.get(0); // Show the oldest current announcement
            float textWidth = uiManager.getTextWidth(announcement, 2.0f);
            float x = (WINDOW_WIDTH - textWidth) / 2.0f;
            float y = WINDOW_HEIGHT / 2.0f - 50; // Adjust position as needed
            uiManager.drawText(announcement, x, y, 2.0f, Colors.YELLOW);
        }

        // Render Game State messages (like waiting, countdown)
        String stateMessage = "";
        switch (currentGameState) {
            case WAITING:
                stateMessage = "WAITING FOR PLAYERS (" + tanks.size() + "/" + GameServer.MAX_PLAYERS + ")";
                break;
            case COUNTDOWN:
                // Countdown time is tricky to sync perfectly, server should send updates
                // For now, just display generic countdown message
                stateMessage = "ROUND STARTING..."; // Server sends announcements for numbers
                break;
            case CONNECTING:
                stateMessage = "CONNECTING TO SERVER...";
                break;
            // PLAYING and ROUND_OVER messages handled by timer/announcements mostly
        }

        if (!stateMessage.isEmpty() && announcements.isEmpty()) { // Don't overlap with announcements
            float textWidth = uiManager.getTextWidth(stateMessage, 2.0f);
            float x = (WINDOW_WIDTH - textWidth) / 2.0f;
            float y = WINDOW_HEIGHT / 2.0f - 50;
            uiManager.drawText(stateMessage, x, y, 2.0f, Colors.WHITE);
        }

        uiManager.endUIRendering();
    }

    public void cleanup() {
        logger.info("Cleaning up game resources...");

        // 1. Stop network client first
        logger.debug("Stopping network client...");
        if (gameClient != null) {
            try {
                gameClient.stop(); // Signal the client thread to stop and close resources
                Thread.sleep(100); // Optional short pause
            } catch (Exception e) {
                logger.error("Error during game client stop", e);
            } finally {
                gameClient = null; // Dereference
                logger.debug("Network client stopped.");
            }
        } else {
            logger.debug("Network client was already null.");
        }

        // 2. Clean up OpenGL resources (ensure these delete methods exist)
        logger.debug("Deleting OpenGL resources...");
        try {
            if (tankTexture != null) tankTexture.delete();
            if (bulletTexture != null) bulletTexture.delete();
            if (grassTexture != null) grassTexture.delete();
            if (dirtTexture != null) dirtTexture.delete();
            if (uiManager != null) uiManager.cleanup(); // Assuming UIManager holds OpenGL font texture
            if (shader != null) shader.delete();
            if (renderer != null) renderer.cleanup(); // Assuming Renderer holds VAO/VBOs
            logger.debug("OpenGL resources deleted.");
        } catch (Exception e) {
            logger.error("Error deleting OpenGL resources", e);
            // Continue cleanup despite errors here
        }


        // 3. Clean up LWJGL window and context
        logger.debug("Destroying GLFW window...");
        try {
            if (window != NULL) { // Check if window was created
                // Unset callbacks *before* destroying window
                glfwSetKeyCallback(window, null);
                // Add other callbacks here if you set them (mouse, etc.)

                glfwFreeCallbacks(window); // Free standard callbacks
                glfwDestroyWindow(window);
                logger.debug("GLFW window destroyed.");
            } else {
                logger.debug("GLFW window was already NULL.");
            }
        } catch (Exception e) {
            logger.error("Error destroying GLFW window", e);
            // Continue cleanup
        } finally {
            window = NULL; // Mark as destroyed
        }

        // 4. Terminate GLFW
        logger.debug("Terminating GLFW...");
        try {
            glfwTerminate();
            logger.debug("GLFW terminated.");
            GLFWErrorCallback callback = glfwSetErrorCallback(null);
            if (callback != null) {
                callback.free();
                logger.debug("GLFW error callback freed.");
            }
        } catch (Exception e) {
            logger.error("Error terminating GLFW or freeing callback", e);
        }

        logger.info("Game cleanup finished.");
    }


    // --- Network Callback Methods ---

    public void setLocalPlayerId(int id, float colorR, float colorG, float colorB) {
        this.localPlayerId = id;
        logger.info("Assigned local player ID: {}", id);
        // We might not have the tank object yet, color will be applied when NEW_PLAYER is received
    }

    public void addOrUpdateTank(int id, float x, float y, float rotation, String name, float r, float g, float b) {
        Tank tank = tanks.computeIfAbsent(id, k -> {
            logger.info("Creating new tank for player ID: {} Name: {}", id, name);
            Tank newTank = new Tank(id, x, y, new Vector3f(r, g, b), name);
            if (id == localPlayerId) {
                localTank = newTank; // Assign local tank reference
                isSpectating = false; // Ensure not spectating if just joined/respawned
                logger.info("Adding new tank: {}", newTank);
            }
            return newTank;
        });
        tank.setPosition(x, y);
        tank.setRotation(rotation);
        tank.setName(name); // Update name if needed
        tank.setColor(new Vector3f(r, g, b)); // Ensure color is set/updated

        // Update lives if player was previously defeated (and is now respawning)
        if(id == localPlayerId && isSpectating && tank.getLives() <= 0) {
            // Server should send PLAYER_LIVES, but as a backup:
            // tank.setLives(Tank.INITIAL_LIVES); // Handled by PLAYER_LIVES message
            isSpectating = false;
        }
    }

    public void removeTank(int id) {
        Tank removed = tanks.remove(id);
        if (removed != null) {
            logger.info("Removed tank for player ID: {} Name: {}", id, removed.getName());
            if (id == localPlayerId) {
                localTank = null;
                logger.info("Removing local tank: {}", removed);
                // Server should transition state or handle appropriately
            }
        }
    }

    public void updateTankState(int id, float x, float y, float rotation) {
        Tank tank = tanks.get(id);
        if (tank != null) {
            // Apply server position directly, overriding client prediction
            tank.setPosition(x, y);
            tank.setRotation(rotation);
        }
    }

    public void spawnBullet(int ownerId, float x, float y, float dirX, float dirY) {
        // Don't add if owner is self and prediction is used? Or just let server confirm?
        // Add bullet regardless for now, server handles hits.
        // Calculate velocity based on direction
        Vector2f velocity = new Vector2f(dirX, dirY).normalize().mul(Bullet.SPEED);
        Bullet bullet = new Bullet(ownerId, x, y, velocity);
        bullets.add(bullet);
    }

    public void destroyBullet(int bulletId) {
        // Bullets don't have IDs in this simple model, client removes on expiry/collision prediction
        // Server handles authoritative hits. This method might not be needed with current setup.
    }

    public void handlePlayerHit(int targetId, int shooterId) {
        // Can add visual effect here later (e.g., flash tank color)
        Tank target = tanks.get(targetId);
        if (target != null) {
            // Optional: Trigger a brief visual effect on the tank
        }
    }

    public void handlePlayerDestroyed(int targetId, int shooterId) {
        Tank target = tanks.get(targetId);
        if (target != null) {
            // Lives update will come via PLAYER_LIVES, this is mainly for effects/knowing it happened
            var victimName = target.getName();
            var shooterName = tanks.getOrDefault(shooterId, null);
            logger.info("Player {} destroyed by {}", victimName, shooterName);
            // Maybe spawn explosion effect?
        }
    }

    public void updatePlayerLives(int playerId, int lives) {
        Tank tank = tanks.get(playerId);
        if (tank != null) {
            tank.setLives(lives);
            logger.info("Player {} lives set to: {}", tank.getName(), lives);
            if (playerId == localPlayerId && lives <= 0) {
                logger.info("Local player defeated. Spectator mode activated.");
                isSpectating = true;
                localTank = null; // Technically the tank object still exists in 'tanks' map until server removes
            }
        }
    }

    public void setGameState(GameState state, long timeData) {
        logger.info("Game state changed to: {} with time data: {}", state, timeData);
        this.currentGameState = state;
        announcements.clear(); // Clear old announcements on state change

        switch (state) {
            case PLAYING:
                this.roundStartTimeMillis = timeData;
                this.finalElapsedTimeMillis = -1; // Reset final time
                // Maybe clear existing bullets from previous round? Server should handle this implicitly.
                bullets.clear();
                // Ensure local player state matches (in case of late join/reconnect)
                if(localPlayerId != -1) {
                    Tank tank = tanks.get(localPlayerId);
                    if(tank != null && tank.getLives() > 0) {
                        isSpectating = false;
                    } else {
                        isSpectating = true; // Remain spectator if joined late and dead
                    }
                }
                break;
            case ROUND_OVER:
                this.finalElapsedTimeMillis = timeData;
                // Winner info comes from announcement
                bullets.clear();
                break;
            case WAITING:
            case COUNTDOWN:
            case CONNECTING:
                this.roundStartTimeMillis = 0;
                this.finalElapsedTimeMillis = -1;
                bullets.clear(); // Clear bullets while waiting/counting down
                break;
        }
    }

    public void addAnnouncement(String message) {
        logger.info("Announcement: {}", message);
        // Add to the end of the list
        announcements.add(message);
        // If this is the only announcement, start its display timer
        if (announcements.size() == 1) {
            lastAnnouncementTime = System.currentTimeMillis();
        }
    }

    public void connectionFailed(String reason) {
        logger.error("Connection failed: {}", reason);
        // Ensure cleanup happens even on connection failure
        // Signal the main loop to exit cleanly
        if (window != NULL) { // Check if window exists before trying to close
            glfwSetWindowShouldClose(window, true);
        } else {
            logger.error("Window handle is NULL during connection failure, cannot signal close.");
            // Consider a more drastic exit if window isn't up?
            // System.exit(1); // Use with caution
        }
        // Show message dialog on the EDT
        SwingUtilities.invokeLater(() -> {
            JOptionPane.showMessageDialog(null, "Connection failed: " + reason + "\nPlease check logs.\nExiting.", "Connection Error", JOptionPane.ERROR_MESSAGE);
        });
        logger.error("Signaled window close because connection failed.");
    }

    public void disconnected() {
        logger.warn("Disconnected from server."); // Changed to warn level
        if (window != NULL) {
            glfwSetWindowShouldClose(window, true);
        } else {
            logger.error("Window handle is NULL during disconnect, cannot signal close.");
        }
        // Show message dialog on the EDT
        SwingUtilities.invokeLater(() -> {
            JOptionPane.showMessageDialog(null, "Disconnected from server.\nPlease check logs.\nExiting.", "Disconnected", JOptionPane.INFORMATION_MESSAGE);
        });
        logger.warn("Signaled window close because disconnected.");
    }

    private static String getBestLocalIpAddress() {
        try {
            for (java.net.NetworkInterface ni : java.util.Collections.list(java.net.NetworkInterface.getNetworkInterfaces())) {
                if (!ni.isLoopback() && ni.isUp()) {
                    for (java.net.InterfaceAddress ia : ni.getInterfaceAddresses()) {
                        if (ia.getAddress() instanceof java.net.Inet4Address) {
                            return ia.getAddress().getHostAddress();
                        }
                    }
                }
            }
            return InetAddress.getLocalHost().getHostAddress();
        } catch (Exception ex) {
            return "127.0.0.1"; // Ultimate fallback
        }
    }
}
