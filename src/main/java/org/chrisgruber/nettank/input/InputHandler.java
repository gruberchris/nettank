package org.chrisgruber.nettank.input;

import org.lwjgl.glfw.GLFWKeyCallback;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.lwjgl.glfw.GLFW.*;

public class InputHandler {

    private final Map<Integer, Boolean> keysDown = new HashMap<>();
    private final Set<Integer> keysPressed = new HashSet<>(); // Keys pressed this frame

    public InputHandler(long window) {
        // Setup key callback
        glfwSetKeyCallback(window, (win, key, scancode, action, mods) -> {
            keyCallback(key, action);
        });
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
    }

    // Explicitly reset a key's pressed state if needed (e.g., after processing fire input)
    public void resetKey(int keyCode) {
        keysPressed.remove(keyCode);
    }

}
