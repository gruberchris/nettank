package org.chrisgruber.nettank.client.engine.core;

import org.chrisgruber.nettank.client.engine.input.InputHandler;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.glfw.GLFWVidMode;
import org.lwjgl.opengl.GL;
import org.lwjgl.system.MemoryStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.IntBuffer;

import static org.lwjgl.glfw.Callbacks.glfwFreeCallbacks;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.system.MemoryUtil.NULL;

public abstract class GameEngine implements Runnable {

    private static final Logger logger = LoggerFactory.getLogger(GameEngine.class);

    // Window Properties
    protected final int windowWidth;
    protected final int windowHeight;
    protected final String windowTitle;

    // Core Engine Components
    protected long windowHandle = NULL;
    protected InputHandler inputHandler;
    private GLFWErrorCallback errorCallback;

    // Timing
    private double lastFrameTime;

    /**
     * Constructor for the game engine.
     *
     * @param title Window title.
     * @param width Window width in pixels.
     * @param height Window height in pixels.
     */
    protected GameEngine(String title, int width, int height) {
        this.windowTitle = title;
        this.windowWidth = width;
        this.windowHeight = height;
        logger.info("GameEngine created for '{}' ({}x{})", title, width, height);
    }

    /**
     * Entry point to start and run the engine and game logic.
     */
    @Override
    public final void run() {
        try {
            initEngine(); // Initialize GLFW, OpenGL, Input
            initGame();   // Initialize game-specific resources (implemented by subclass)
            gameLoop();   // Start the main loop
        } catch (Exception e) {
            logger.error("!!! Critical Error during engine/game execution !!!", e);
            // TODO: Consider showing an error dialog
        } finally {
            logger.info("Initiating cleanup sequence...");
            try {
                cleanupGame();
            } catch (Exception e) {
                logger.error("Error during game cleanup", e);
            }
            try {
                cleanupEngine();
            } catch (Exception e) {
                logger.error("Error during engine cleanup", e);
            }
            logger.info("Cleanup sequence finished.");
        }
    }

    /**
     * Initializes GLFW, creates the window, sets up OpenGL context, and input.
     */
    private void initEngine() {
        logger.info("Initializing Game Engine...");

        // Setup error callback
        errorCallback = GLFWErrorCallback.createPrint(System.err);
        glfwSetErrorCallback(errorCallback);

        // Initialize GLFW
        if (!glfwInit()) {
            throw new IllegalStateException("Unable to initialize GLFW");
        }

        // Configure GLFW window hints
        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 4);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 1);
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
        glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, GLFW_TRUE); // macOS requirement
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE); // Hidden until ready
        glfwWindowHint(GLFW_RESIZABLE, GLFW_FALSE); // Not resizable

        // Create the window
        windowHandle = glfwCreateWindow(windowWidth, windowHeight, windowTitle, NULL, NULL);
        if (windowHandle == NULL) {
            throw new RuntimeException("Failed to create the GLFW window");
        }

        // Center the window on the primary monitor
        try (MemoryStack stack = stackPush()) {
            IntBuffer pWidth = stack.mallocInt(1);
            IntBuffer pHeight = stack.mallocInt(1);
            glfwGetWindowSize(windowHandle, pWidth, pHeight);
            GLFWVidMode vidmode = glfwGetVideoMode(glfwGetPrimaryMonitor());
            if (vidmode != null) {
                glfwSetWindowPos(
                        windowHandle,
                        (vidmode.width() - pWidth.get(0)) / 2,
                        (vidmode.height() - pHeight.get(0)) / 2
                );
            } else {
                logger.warn("Could not get primary monitor video mode to center window.");
            }
        }

        // Make the OpenGL context current
        glfwMakeContextCurrent(windowHandle);
        // Enable v-sync
        glfwSwapInterval(1);

        // Initialize InputHandler (registers GLFW callbacks)
        // InputHandler needs the window handle
        inputHandler = new InputHandler(windowHandle);

        // IMPORTANT: Create OpenGL capabilities AFTER context is current
        GL.createCapabilities();

        logger.info("OpenGL Version: {}", glGetString(GL_VERSION));

        // Basic OpenGL settings (subclass might override or add more)
        glClearColor(0.0f, 0.0f, 0.0f, 0.0f); // Default black background
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        // Note: Depth testing might be enabled/disabled per frame or in subclass
        // glEnable(GL_DEPTH_TEST);

        // Make the window visible
        glfwShowWindow(windowHandle);

        lastFrameTime = glfwGetTime(); // Initialize timing
        logger.info("Game Engine Initialized.");
    }

    /**
     * The main game loop. Handles timing, input polling, updates, and rendering.
     */
    private void gameLoop() {
        logger.info("Starting game loop...");
        while (!glfwWindowShouldClose(windowHandle)) {
            // --- Timing ---
            double currentTime = glfwGetTime();
            float deltaTime = (float) (currentTime - lastFrameTime);
            lastFrameTime = currentTime;
            // Optional: Add frame rate limiting or monitoring here

            // --- Input Polling ---
            // GLFW events (key presses/releases handled by InputHandler callback)
            glfwPollEvents();
            // Abstract method for game-specific continuous input checks
            handleGameInput(deltaTime);
            // Clear single-press input state AFTER game logic has checked it
            inputHandler.poll();

            // --- Update ---
            // Abstract method for game-specific state updates
            updateGame(deltaTime);

            // --- Render ---
            // Clear buffer (might be done in renderGame impl. if more complex clearing needed)
            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
            // Abstract method for game-specific rendering
            renderGame();

            // --- Swap Buffers ---
            glfwSwapBuffers(windowHandle);
        }
        logger.info("Game loop finished.");
    }

    /**
     * Cleans up engine resources (GLFW window, callbacks, terminates GLFW).
     */
    private void cleanupEngine() {
        logger.debug("Cleaning up Engine/GLFW resources...");
        try {
            if (windowHandle != NULL) {
                // Free callbacks FIRST
                glfwFreeCallbacks(windowHandle);
                // Destroy window
                glfwDestroyWindow(windowHandle);
                logger.debug("GLFW window destroyed.");
            } else {
                logger.debug("GLFW window handle was NULL, skipping destruction.");
            }
        } catch (Exception e) {
            logger.error("Error destroying window/freeing callbacks", e);
        } finally {
            windowHandle = NULL; // Mark as destroyed
        }

        try {
            // Terminate GLFW
            glfwTerminate();
            logger.debug("GLFW terminated.");
            // Free error callback
            if (errorCallback != null) {
                errorCallback.free();
                logger.debug("GLFW error callback freed.");
                errorCallback = null;
            }
        } catch (Exception e) {
            logger.error("Error terminating GLFW or freeing error callback", e);
        }
        logger.debug("Engine/GLFW cleanup finished.");
    }


    // --- Abstract methods to be implemented by the specific game ---

    /**
     * Initialize game-specific resources (textures, shaders, entities, map, UI, network client).
     * Called once after engine initialization.
     */
    protected abstract void initGame();

    /**
     * Update game-specific logic (entity movement, AI, physics, state changes).
     * Called every frame within the game loop.
     * @param deltaTime Time elapsed since the last frame in seconds.
     */
    protected abstract void updateGame(float deltaTime);

    /**
     * Render the current game state.
     * Called every frame within the game loop after updateGame.
     */
    protected abstract void renderGame();

    /**
     * Handle game-specific input checks (continuous key holds, mouse actions).
     * Called every frame within the game loop after polling GLFW events.
     * @param deltaTime Time elapsed since the last frame in seconds.
     */
    protected abstract void handleGameInput(float deltaTime);

    /**
     * Clean up game-specific resources (textures, shaders, VBOs, network client).
     * Called once before the engine cleans up GLFW resources.
     */
    protected abstract void cleanupGame();

}