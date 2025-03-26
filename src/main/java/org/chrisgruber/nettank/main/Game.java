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

import javax.swing.*;
import java.io.IOException;
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

        // Set the clear color
        glClearColor(0.0f, 0.0f, 0.0f, 0.0f); // Black background

        // Enable blending for transparency
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        // Load shaders
        try {
            shader = new Shader("src/main/resources/shaders/quad.vert", "src/main/resources/shaders/quad.frag");
            shader.bind();
        } catch (IOException e) {
            throw new RuntimeException("Failed to load shaders", e);
        }

        // Create renderer and camera
        renderer = new Renderer();
        camera = new Camera(WINDOW_WIDTH, WINDOW_HEIGHT);
        uiManager = new UIManager(); // Initialize UI Manager

        // Load textures (provide actual paths)
        try {
            tankTexture = new Texture("src/main/resources/textures/tank.png");
            bulletTexture = new Texture("src/main/resources/textures/bullet.png");
            grassTexture = new Texture("src/main/resources/textures/grass.png");
            dirtTexture = new Texture("src/main/resources/textures/dirt.png");
            uiManager.loadFontTexture("src/main/resources/textures/font.png"); // Assuming a font texture exists
        } catch (IOException e) {
            throw new RuntimeException("Failed to load textures", e);
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

            // --- Render ---
            render();

            glfwSwapBuffers(window); // swap the color buffers
        }
    }

    private void handleInput(float deltaTime) {
        if (localTank != null && !isSpectating && currentGameState == GameState.PLAYING) {
            boolean moved = false;
            float moveX = 0;
            float moveY = 0;

            if (inputHandler.isKeyDown(GLFW_KEY_W)) {
                moveY = 1;
                moved = true;
            }
            if (inputHandler.isKeyDown(GLFW_KEY_S)) {
                moveY = -1;
                moved = true;
            }
            if (inputHandler.isKeyDown(GLFW_KEY_A)) {
                moveX = -1;
                moved = true;
            }
            if (inputHandler.isKeyDown(GLFW_KEY_D)) {
                moveX = 1;
                moved = true;
            }

            if (moved && gameClient != null && gameClient.isConnected()) {
                // Calculate actual movement based on rotation for top-down
                float angleRad = (float) Math.toRadians(localTank.getRotation());
                float directionX = (float) Math.sin(angleRad);
                float directionY = (float) Math.cos(angleRad);

                float forwardMove = moveY * Tank.MOVE_SPEED * deltaTime;
                float sideMove = moveX * Tank.TURN_SPEED * deltaTime; // Use A/D for turning

                float newX = localTank.getPosition().x + directionX * forwardMove;
                float newY = localTank.getPosition().y + directionY * forwardMove;
                float newRot = localTank.getRotation() - sideMove; // Subtract because positive angle is clockwise usually in screen coords

                // Simple client-side prediction (optional but makes movement feel better)
                // localTank.setPosition(newX, newY); // Server will correct if wrong
                // localTank.setRotation(newRot);

                // Send input state to server instead of predicted position
                gameClient.sendInput(inputHandler.isKeyDown(GLFW_KEY_W),
                        inputHandler.isKeyDown(GLFW_KEY_S),
                        inputHandler.isKeyDown(GLFW_KEY_A),
                        inputHandler.isKeyDown(GLFW_KEY_D));
            }


            if (inputHandler.isKeyPressed(GLFW_KEY_SPACE)) { // Use isKeyPressed for single shot per press
                if (gameClient != null && gameClient.isConnected()) {
                    gameClient.sendShoot();
                }
                inputHandler.resetKey(GLFW_KEY_SPACE); // Prevent repeat firing if held down
            }
        }

        // Close on Escape
        if (inputHandler.isKeyDown(GLFW_KEY_ESCAPE)) {
            glfwSetWindowShouldClose(window, true);
        }
    }

    private void update(float deltaTime) {
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
    }

    private void render() {
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


    private void cleanup() {
        System.out.println("Cleaning up game resources...");
        // Stop network client
        if (gameClient != null) {
            gameClient.stop();
        }

        // Free textures
        if (tankTexture != null) tankTexture.delete();
        if (bulletTexture != null) bulletTexture.delete();
        if (grassTexture != null) grassTexture.delete();
        if (dirtTexture != null) dirtTexture.delete();
        uiManager.cleanup();


        // Free renderer resources
        if (renderer != null) renderer.cleanup();

        // Free shaders
        if (shader != null) shader.delete();

        // Free the window callbacks and destroy the window
        if (window != NULL) {
            glfwFreeCallbacks(window);
            glfwDestroyWindow(window);
        }

        // Terminate GLFW and free the error callback
        glfwTerminate();
        GLFWErrorCallback callback = glfwSetErrorCallback(null);
        if (callback != null) {
            callback.free();
        }
        System.out.println("Cleanup complete.");
    }

    // --- Network Callback Methods ---

    public void setLocalPlayerId(int id, float colorR, float colorG, float colorB) {
        this.localPlayerId = id;
        System.out.println("Assigned local player ID: " + id);
        // We might not have the tank object yet, color will be applied when NEW_PLAYER is received
    }

    public void addOrUpdateTank(int id, float x, float y, float rotation, String name, float r, float g, float b) {
        Tank tank = tanks.computeIfAbsent(id, k -> {
            System.out.println("Creating new tank for player ID: " + id + " Name: " + name);
            Tank newTank = new Tank(id, x, y, new Vector3f(r, g, b), name);
            if (id == localPlayerId) {
                localTank = newTank; // Assign local tank reference
                isSpectating = false; // Ensure not spectating if just joined/respawned
                System.out.println("Local tank object created/updated.");
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
            System.out.println("Removed tank for player ID: " + id + " Name: " + removed.getName());
            if (id == localPlayerId) {
                localTank = null;
                System.out.println("Local tank removed.");
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
            System.out.println("Player " + target.getName() + " destroyed by " + tanks.getOrDefault(shooterId, null) + ".");
            // Maybe spawn explosion effect?
        }
    }

    public void updatePlayerLives(int playerId, int lives) {
        Tank tank = tanks.get(playerId);
        if (tank != null) {
            tank.setLives(lives);
            System.out.println("Player " + tank.getName() + " lives set to " + lives);
            if (playerId == localPlayerId && lives <= 0) {
                System.out.println("Local player defeated. Entering spectator mode.");
                isSpectating = true;
                localTank = null; // Technically the tank object still exists in 'tanks' map until server removes
            }
        }
    }

    public void setGameState(GameState state, long timeData) {
        System.out.println("Game state changed to: " + state + " with time data: " + timeData);
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
        System.out.println("Announcement: " + message);
        // Add to the end of the list
        announcements.add(message);
        // If this is the only announcement, start its display timer
        if (announcements.size() == 1) {
            lastAnnouncementTime = System.currentTimeMillis();
        }
    }

    public void connectionFailed(String reason) {
        System.err.println("Connection failed: " + reason);
        // Show message and potentially exit or return to launcher
        SwingUtilities.invokeLater(() -> {
            JOptionPane.showMessageDialog(null, "Connection failed: " + reason + "\nExiting.", "Connection Error", JOptionPane.ERROR_MESSAGE);
            // Ensure cleanup happens even on connection failure
            // May need to signal the main loop to exit cleanly
            glfwSetWindowShouldClose(window, true);
            // System.exit(1); // Force exit if needed, but cleanup is preferred
        });
        // Setting window should close allows the main loop to exit and run cleanup()
    }

    public void disconnected() {
        System.err.println("Disconnected from server.");
        SwingUtilities.invokeLater(() -> {
            JOptionPane.showMessageDialog(null, "Disconnected from server.\nExiting.", "Disconnected", JOptionPane.INFORMATION_MESSAGE);
            glfwSetWindowShouldClose(window, true);
        });
    }

}
