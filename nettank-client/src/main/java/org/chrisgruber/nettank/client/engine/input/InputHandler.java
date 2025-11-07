package org.chrisgruber.nettank.client.engine.input;

import org.lwjgl.glfw.GLFWKeyCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.lwjgl.glfw.GLFW.*;

public class InputHandler {
    private static final Logger logger = LoggerFactory.getLogger(InputHandler.class);

    private final Map<Integer, Boolean> keysDown = new HashMap<>();
    private final Set<Integer> keysPressed = new HashSet<>(); // Keys pressed this frame
    
    // Configuration
    private InputConfig config;
    private int keyForward;
    private int keyBackward;
    private int keyRotateLeft;
    private int keyRotateRight;
    private int keyShoot;
    private int keyExit;
    
    // Gamepad support
    private int gamepadId = -1; // GLFW joystick ID (-1 means no gamepad)
    private boolean gamepadConnected = false;
    private boolean previousShootButton = false; // Track button state for press detection
    
    // Gamepad configuration (loaded from config)
    private int gamepadForwardAxis;
    private int gamepadBackwardAxis;
    private int gamepadRotateAxis;
    private int gamepadShootButton;
    private float stickDeadzone;
    private float triggerThreshold;
    private float rotationSensitivity;

    public InputHandler(long window) {
        // Load configuration
        loadConfiguration();
        
        // Setup key callback
        glfwSetKeyCallback(window, (win, key, scancode, action, mods) -> {
            keyCallback(key, action);
        });
        
        // Detect connected gamepad
        detectGamepad();
    }
    
    private void loadConfiguration() {
        config = InputConfig.load();
        
        // Map keyboard keys
        keyForward = InputConfig.stringToKeyCode(config.keyboard.forward);
        keyBackward = InputConfig.stringToKeyCode(config.keyboard.backward);
        keyRotateLeft = InputConfig.stringToKeyCode(config.keyboard.rotateLeft);
        keyRotateRight = InputConfig.stringToKeyCode(config.keyboard.rotateRight);
        keyShoot = InputConfig.stringToKeyCode(config.keyboard.shoot);
        keyExit = InputConfig.stringToKeyCode(config.keyboard.exit);
        
        // Map gamepad controls
        gamepadForwardAxis = InputConfig.stringToGamepadAxis(config.gamepad.forward);
        gamepadBackwardAxis = InputConfig.stringToGamepadAxis(config.gamepad.backward);
        gamepadRotateAxis = InputConfig.stringToGamepadAxis(config.gamepad.rotateAxis);
        gamepadShootButton = InputConfig.stringToGamepadButton(config.gamepad.shoot);
        
        // Load gamepad sensitivity settings
        stickDeadzone = config.gamepad.stickDeadzone;
        triggerThreshold = config.gamepad.triggerThreshold;
        rotationSensitivity = config.gamepad.rotationSensitivity;
        
        logger.info("Input configuration loaded - Forward: {}, Backward: {}, RotateLeft: {}, RotateRight: {}, Shoot: {}",
                config.keyboard.forward, config.keyboard.backward, config.keyboard.rotateLeft, 
                config.keyboard.rotateRight, config.keyboard.shoot);
    }

    public void keyCallback(int key, int action) {
        if (action == GLFW_PRESS) {
            keysDown.put(key, true);
            keysPressed.add(key); // Mark as pressed this frame
        } else if (action == GLFW_RELEASE) {
            keysDown.put(key, false);
        }
    }

    public boolean isKeyDown(int keyCode) {
        return keysDown.getOrDefault(keyCode, false);
    }

    public boolean isKeyPressed(int keyCode) {
        // Check if it was pressed this frame and consume the event
        boolean pressed = keysPressed.contains(keyCode);
        // if (pressed) keysPressed.remove(keyCode); // Consume immediately
        return pressed;
    }

    // Call this at the END of the game loop's input handling phase
    public void poll() {
        // Clear the keysPressed set for the next frame.
        // This allows isKeyPressed to only return true once per press.
        keysPressed.clear();
        // NOTE: GLFW input polling (glfwPollEvents) happens elsewhere (in Game loop)
        // This poll() is just for managing the state of isKeyPressed.
        
        // Check for gamepad connection changes
        if (!gamepadConnected) {
            detectGamepad();
        }
    }

    // Explicitly reset a key's pressed state if needed (e.g., after processing fire input)
    public void resetKey(int keyCode) {
        keysPressed.remove(keyCode);
    }
    
    // --- Gamepad Support Methods ---
    
    private void detectGamepad() {
        // Check for gamepads on GLFW joystick slots 0-15
        for (int jid = GLFW_JOYSTICK_1; jid <= GLFW_JOYSTICK_LAST; jid++) {
            if (glfwJoystickPresent(jid) && glfwJoystickIsGamepad(jid)) {
                gamepadId = jid;
                gamepadConnected = true;
                String name = glfwGetGamepadName(jid);
                logger.info("Gamepad detected: {} (ID: {})", name, jid);
                return;
            }
        }
    }
    
    /**
     * Returns true if forward input is active (configured key or trigger)
     */
    public boolean isForwardPressed() {
        // Keyboard input
        if (isKeyDown(keyForward)) {
            return true;
        }
        
        // Gamepad input - configured trigger
        if (gamepadConnected) {
            float trigger = getGamepadAxis(gamepadForwardAxis);
            return trigger > triggerThreshold;
        }
        
        return false;
    }
    
    /**
     * Returns true if backward input is active (configured key or trigger)
     */
    public boolean isBackwardPressed() {
        // Keyboard input
        if (isKeyDown(keyBackward)) {
            return true;
        }
        
        // Gamepad input - configured trigger
        if (gamepadConnected) {
            float trigger = getGamepadAxis(gamepadBackwardAxis);
            return trigger > triggerThreshold;
        }
        
        return false;
    }
    
    /**
     * Returns true if rotate left input is active (configured key or stick)
     */
    public boolean isRotateLeftPressed() {
        // Keyboard input
        if (isKeyDown(keyRotateLeft)) {
            return true;
        }
        
        // Gamepad input - configured stick (negative = left)
        if (gamepadConnected) {
            float stickValue = getGamepadAxis(gamepadRotateAxis);
            return stickValue < -stickDeadzone;
        }
        
        return false;
    }
    
    /**
     * Returns true if rotate right input is active (configured key or stick)
     */
    public boolean isRotateRightPressed() {
        // Keyboard input
        if (isKeyDown(keyRotateRight)) {
            return true;
        }
        
        // Gamepad input - configured stick (positive = right)
        if (gamepadConnected) {
            float stickValue = getGamepadAxis(gamepadRotateAxis);
            return stickValue > stickDeadzone;
        }
        
        return false;
    }
    
    /**
     * Returns the rotation input value (for analog control)
     * Returns 0 if using keyboard, or analog stick value if using gamepad
     */
    public float getRotationInput() {
        // If keyboard is being used, return digital values
        if (isKeyDown(keyRotateLeft) || isKeyDown(keyRotateRight)) {
            if (isKeyDown(keyRotateLeft)) return -1.0f;
            if (isKeyDown(keyRotateRight)) return 1.0f;
            return 0.0f;
        }
        
        // Gamepad analog input
        if (gamepadConnected) {
            float stickValue = getGamepadAxis(gamepadRotateAxis);
            
            // Apply deadzone
            if (Math.abs(stickValue) < stickDeadzone) {
                return 0.0f;
            }
            
            // Apply sensitivity and return analog value
            return stickValue * rotationSensitivity;
        }
        
        return 0.0f;
    }
    
    /**
     * Returns true if shoot input is pressed (configured key or button)
     */
    public boolean isShootPressed() {
        // Keyboard input
        if (isKeyPressed(keyShoot)) {
            return true;
        }
        
        // Gamepad input - configured button
        if (gamepadConnected) {
            boolean currentShootButton = getGamepadButton(gamepadShootButton);
            
            // Detect button press (transition from not pressed to pressed)
            boolean pressed = currentShootButton && !previousShootButton;
            previousShootButton = currentShootButton;
            
            return pressed;
        }
        
        return false;
    }
    
    /**
     * Returns true if exit key is pressed
     */
    public boolean isExitPressed() {
        return isKeyDown(keyExit);
    }
    
    /**
     * Helper method to get gamepad button state
     */
    private boolean getGamepadButton(int button) {
        if (!gamepadConnected) return false;
        
        org.lwjgl.glfw.GLFWGamepadState state = org.lwjgl.glfw.GLFWGamepadState.create();
        if (!glfwGetGamepadState(gamepadId, state)) {
            gamepadConnected = false;
            logger.warn("Gamepad disconnected");
            return false;
        }
        
        ByteBuffer buttons = state.buttons();
        return buttons.get(button) == GLFW_PRESS;
    }
    
    /**
     * Helper method to get gamepad axis value
     */
    private float getGamepadAxis(int axis) {
        if (!gamepadConnected) return 0.0f;
        
        org.lwjgl.glfw.GLFWGamepadState state = org.lwjgl.glfw.GLFWGamepadState.create();
        if (!glfwGetGamepadState(gamepadId, state)) {
            gamepadConnected = false;
            logger.warn("Gamepad disconnected");
            return 0.0f;
        }
        
        FloatBuffer axes = state.axes();
        return axes.get(axis);
    }
    
    public boolean isGamepadConnected() {
        return gamepadConnected;
    }

}
