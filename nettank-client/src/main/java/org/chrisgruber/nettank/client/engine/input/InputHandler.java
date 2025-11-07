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
    
    // Gamepad support
    private int gamepadId = -1; // GLFW joystick ID (-1 means no gamepad)
    private boolean gamepadConnected = false;
    private boolean previousShootButton = false; // Track button state for press detection
    
    // Gamepad configuration
    private static final float TRIGGER_THRESHOLD = 0.1f; // Deadzone for triggers
    private static final float STICK_DEADZONE = 0.2f; // Deadzone for analog sticks
    private static final float ROTATION_SENSITIVITY = 1.0f; // Multiplier for rotation speed

    public InputHandler(long window) {
        // Setup key callback
        glfwSetKeyCallback(window, (win, key, scancode, action, mods) -> {
            keyCallback(key, action);
        });
        
        // Detect connected gamepad
        detectGamepad();
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
     * Returns true if forward input is active (W key or Right Trigger)
     */
    public boolean isForwardPressed() {
        // Keyboard input
        if (isKeyDown(GLFW_KEY_W)) {
            return true;
        }
        
        // Gamepad input - Right Trigger
        if (gamepadConnected) {
            float rightTrigger = getGamepadAxis(GLFW_GAMEPAD_AXIS_RIGHT_TRIGGER);
            return rightTrigger > TRIGGER_THRESHOLD;
        }
        
        return false;
    }
    
    /**
     * Returns true if backward input is active (S key or Left Trigger)
     */
    public boolean isBackwardPressed() {
        // Keyboard input
        if (isKeyDown(GLFW_KEY_S)) {
            return true;
        }
        
        // Gamepad input - Left Trigger
        if (gamepadConnected) {
            float leftTrigger = getGamepadAxis(GLFW_GAMEPAD_AXIS_LEFT_TRIGGER);
            return leftTrigger > TRIGGER_THRESHOLD;
        }
        
        return false;
    }
    
    /**
     * Returns true if rotate left input is active (A key or Right Stick left)
     */
    public boolean isRotateLeftPressed() {
        // Keyboard input
        if (isKeyDown(GLFW_KEY_A)) {
            return true;
        }
        
        // Gamepad input - Right Stick horizontal (negative = left)
        if (gamepadConnected) {
            float rightStickX = getGamepadAxis(GLFW_GAMEPAD_AXIS_RIGHT_X);
            return rightStickX < -STICK_DEADZONE;
        }
        
        return false;
    }
    
    /**
     * Returns true if rotate right input is active (D key or Right Stick right)
     */
    public boolean isRotateRightPressed() {
        // Keyboard input
        if (isKeyDown(GLFW_KEY_D)) {
            return true;
        }
        
        // Gamepad input - Right Stick horizontal (positive = right)
        if (gamepadConnected) {
            float rightStickX = getGamepadAxis(GLFW_GAMEPAD_AXIS_RIGHT_X);
            return rightStickX > STICK_DEADZONE;
        }
        
        return false;
    }
    
    /**
     * Returns the rotation input value (for analog control)
     * Returns 0 if using keyboard, or analog stick value if using gamepad
     */
    public float getRotationInput() {
        // If a keyboard is being used, return digital values
        if (isKeyDown(GLFW_KEY_A) || isKeyDown(GLFW_KEY_D)) {
            if (isKeyDown(GLFW_KEY_A)) return -1.0f;
            if (isKeyDown(GLFW_KEY_D)) return 1.0f;
            return 0.0f;
        }
        
        // Gamepad analog input
        if (gamepadConnected) {
            float rightStickX = getGamepadAxis(GLFW_GAMEPAD_AXIS_RIGHT_X);
            
            // Apply deadzone
            if (Math.abs(rightStickX) < STICK_DEADZONE) {
                return 0.0f;
            }
            
            // Apply sensitivity and return analog value
            return rightStickX * ROTATION_SENSITIVITY;
        }
        
        return 0.0f;
    }
    
    /**
     * Returns true if shoot input is pressed (Space key or A/X button)
     */
    public boolean isShootPressed() {
        // Keyboard input
        if (isKeyPressed(GLFW_KEY_SPACE)) {
            return true;
        }
        
        // Gamepad input - A button (Xbox) / X button (PlayStation)
        if (gamepadConnected) {
            boolean currentShootButton = getGamepadButton(GLFW_GAMEPAD_BUTTON_A);
            
            // Detect button press (transition from not pressed to pressed)
            boolean pressed = currentShootButton && !previousShootButton;
            previousShootButton = currentShootButton;
            
            return pressed;
        }
        
        return false;
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
