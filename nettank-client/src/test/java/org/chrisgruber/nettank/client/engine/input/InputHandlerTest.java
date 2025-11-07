package org.chrisgruber.nettank.client.engine.input;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.glfw.GLFW.*;

/**
 * Tests for InputHandler class.
 * Note: These tests focus on configuration loading and input mapping logic.
 * Full GLFW input testing requires a window context which is difficult in unit tests.
 */
class InputHandlerTest {

    @TempDir
    Path tempDir;
    
    private String originalUserHome;

    @BeforeEach
    void setUp() {
        // Set temp directory as user home for testing
        originalUserHome = System.getProperty("user.home");
        System.setProperty("user.home", tempDir.toString());
    }

    @AfterEach
    void tearDown() {
        // Restore original user.home
        System.setProperty("user.home", originalUserHome);
    }

    @Test
    void testConfigurationLoading() throws Exception {
        // Create a custom config
        InputConfig config = new InputConfig();
        config.keyboard.forward = "UP";
        config.keyboard.backward = "DOWN";
        config.keyboard.rotateLeft = "LEFT";
        config.keyboard.rotateRight = "RIGHT";
        config.keyboard.shoot = "ENTER";
        config.keyboard.exit = "ESC";
        
        config.gamepad.stickDeadzone = 0.3f;
        config.gamepad.triggerThreshold = 0.2f;
        config.gamepad.rotationSensitivity = 1.5f;
        
        config.save();
        
        // Verify the config was saved
        Path configPath = tempDir.resolve(".nettank").resolve("input-config.json");
        assertTrue(Files.exists(configPath));
        
        // Load it back and verify
        InputConfig loaded = InputConfig.load();
        assertEquals("UP", loaded.keyboard.forward);
        assertEquals("DOWN", loaded.keyboard.backward);
        assertEquals("LEFT", loaded.keyboard.rotateLeft);
        assertEquals("RIGHT", loaded.keyboard.rotateRight);
        assertEquals("ENTER", loaded.keyboard.shoot);
        assertEquals("ESC", loaded.keyboard.exit);
        assertEquals(0.3f, loaded.gamepad.stickDeadzone, 0.001f);
        assertEquals(0.2f, loaded.gamepad.triggerThreshold, 0.001f);
        assertEquals(1.5f, loaded.gamepad.rotationSensitivity, 0.001f);
    }

    @Test
    void testDefaultConfigurationCreation() {
        // When no config exists, default should be created
        InputConfig config = InputConfig.load();
        
        assertNotNull(config);
        assertNotNull(config.keyboard);
        assertNotNull(config.gamepad);
        
        // Check defaults
        assertEquals("W", config.keyboard.forward);
        assertEquals("S", config.keyboard.backward);
        assertEquals("A", config.keyboard.rotateLeft);
        assertEquals("D", config.keyboard.rotateRight);
        assertEquals("SPACE", config.keyboard.shoot);
        assertEquals("ESCAPE", config.keyboard.exit);
    }

    @Test
    void testKeyCodeMapping() {
        // Test that key strings map to correct GLFW key codes
        assertEquals(GLFW_KEY_W, InputConfig.stringToKeyCode("W"));
        assertEquals(GLFW_KEY_A, InputConfig.stringToKeyCode("A"));
        assertEquals(GLFW_KEY_S, InputConfig.stringToKeyCode("S"));
        assertEquals(GLFW_KEY_D, InputConfig.stringToKeyCode("D"));
        assertEquals(GLFW_KEY_SPACE, InputConfig.stringToKeyCode("SPACE"));
        assertEquals(GLFW_KEY_ESCAPE, InputConfig.stringToKeyCode("ESCAPE"));
        
        // Test arrow keys
        assertEquals(GLFW_KEY_UP, InputConfig.stringToKeyCode("UP"));
        assertEquals(GLFW_KEY_DOWN, InputConfig.stringToKeyCode("DOWN"));
        assertEquals(GLFW_KEY_LEFT, InputConfig.stringToKeyCode("LEFT"));
        assertEquals(GLFW_KEY_RIGHT, InputConfig.stringToKeyCode("RIGHT"));
    }

    @Test
    void testGamepadAxisMapping() {
        // Test axis mappings
        assertEquals(GLFW_GAMEPAD_AXIS_LEFT_X, InputConfig.stringToGamepadAxis("LEFT_STICK_X"));
        assertEquals(GLFW_GAMEPAD_AXIS_LEFT_Y, InputConfig.stringToGamepadAxis("LEFT_STICK_Y"));
        assertEquals(GLFW_GAMEPAD_AXIS_RIGHT_X, InputConfig.stringToGamepadAxis("RIGHT_STICK_X"));
        assertEquals(GLFW_GAMEPAD_AXIS_RIGHT_Y, InputConfig.stringToGamepadAxis("RIGHT_STICK_Y"));
        assertEquals(GLFW_GAMEPAD_AXIS_LEFT_TRIGGER, InputConfig.stringToGamepadAxis("LEFT_TRIGGER"));
        assertEquals(GLFW_GAMEPAD_AXIS_RIGHT_TRIGGER, InputConfig.stringToGamepadAxis("RIGHT_TRIGGER"));
    }

    @Test
    void testGamepadButtonMapping() {
        // Test button mappings
        assertEquals(GLFW_GAMEPAD_BUTTON_A, InputConfig.stringToGamepadButton("BUTTON_A"));
        assertEquals(GLFW_GAMEPAD_BUTTON_B, InputConfig.stringToGamepadButton("BUTTON_B"));
        assertEquals(GLFW_GAMEPAD_BUTTON_X, InputConfig.stringToGamepadButton("BUTTON_X"));
        assertEquals(GLFW_GAMEPAD_BUTTON_Y, InputConfig.stringToGamepadButton("BUTTON_Y"));
        assertEquals(GLFW_GAMEPAD_BUTTON_LEFT_BUMPER, InputConfig.stringToGamepadButton("LEFT_BUMPER"));
        assertEquals(GLFW_GAMEPAD_BUTTON_RIGHT_BUMPER, InputConfig.stringToGamepadButton("RIGHT_BUMPER"));
        
        // Test short names
        assertEquals(GLFW_GAMEPAD_BUTTON_A, InputConfig.stringToGamepadButton("A"));
        assertEquals(GLFW_GAMEPAD_BUTTON_LEFT_BUMPER, InputConfig.stringToGamepadButton("LB"));
        assertEquals(GLFW_GAMEPAD_BUTTON_RIGHT_BUMPER, InputConfig.stringToGamepadButton("RB"));
    }

    @Test
    void testESDFLayoutConfiguration() throws Exception {
        // Test creating and loading ESDF layout
        InputConfig config = new InputConfig();
        config.keyboard.forward = "E";
        config.keyboard.backward = "D";
        config.keyboard.rotateLeft = "S";
        config.keyboard.rotateRight = "F";
        config.save();
        
        InputConfig loaded = InputConfig.load();
        assertEquals("E", loaded.keyboard.forward);
        assertEquals("D", loaded.keyboard.backward);
        assertEquals("S", loaded.keyboard.rotateLeft);
        assertEquals("F", loaded.keyboard.rotateRight);
        
        // Verify the key codes
        assertEquals(GLFW_KEY_E, InputConfig.stringToKeyCode(loaded.keyboard.forward));
        assertEquals(GLFW_KEY_D, InputConfig.stringToKeyCode(loaded.keyboard.backward));
        assertEquals(GLFW_KEY_S, InputConfig.stringToKeyCode(loaded.keyboard.rotateLeft));
        assertEquals(GLFW_KEY_F, InputConfig.stringToKeyCode(loaded.keyboard.rotateRight));
    }

    @Test
    void testArrowKeysLayoutConfiguration() throws Exception {
        // Test arrow keys layout
        InputConfig config = new InputConfig();
        config.keyboard.forward = "UP";
        config.keyboard.backward = "DOWN";
        config.keyboard.rotateLeft = "LEFT";
        config.keyboard.rotateRight = "RIGHT";
        config.save();
        
        InputConfig loaded = InputConfig.load();
        assertEquals(GLFW_KEY_UP, InputConfig.stringToKeyCode(loaded.keyboard.forward));
        assertEquals(GLFW_KEY_DOWN, InputConfig.stringToKeyCode(loaded.keyboard.backward));
        assertEquals(GLFW_KEY_LEFT, InputConfig.stringToKeyCode(loaded.keyboard.rotateLeft));
        assertEquals(GLFW_KEY_RIGHT, InputConfig.stringToKeyCode(loaded.keyboard.rotateRight));
    }

    @Test
    void testCustomGamepadConfiguration() throws Exception {
        // Test custom gamepad settings
        InputConfig config = new InputConfig();
        config.gamepad.forward = "RIGHT_TRIGGER";
        config.gamepad.backward = "LEFT_TRIGGER";
        config.gamepad.rotateAxis = "LEFT_STICK_X";
        config.gamepad.shoot = "RIGHT_BUMPER";
        config.gamepad.stickDeadzone = 0.15f;
        config.gamepad.triggerThreshold = 0.25f;
        config.gamepad.rotationSensitivity = 2.0f;
        config.save();
        
        InputConfig loaded = InputConfig.load();
        assertEquals("LEFT_STICK_X", loaded.gamepad.rotateAxis);
        assertEquals("RIGHT_BUMPER", loaded.gamepad.shoot);
        assertEquals(0.15f, loaded.gamepad.stickDeadzone, 0.001f);
        assertEquals(0.25f, loaded.gamepad.triggerThreshold, 0.001f);
        assertEquals(2.0f, loaded.gamepad.rotationSensitivity, 0.001f);
    }

    @Test
    void testSensitivityBoundaries() throws Exception {
        // Test extreme sensitivity values
        InputConfig config = new InputConfig();
        
        // Minimum sensitivity
        config.gamepad.stickDeadzone = 0.0f;
        config.gamepad.triggerThreshold = 0.0f;
        config.gamepad.rotationSensitivity = 0.1f;
        config.save();
        
        InputConfig loaded = InputConfig.load();
        assertEquals(0.0f, loaded.gamepad.stickDeadzone, 0.001f);
        assertEquals(0.0f, loaded.gamepad.triggerThreshold, 0.001f);
        assertEquals(0.1f, loaded.gamepad.rotationSensitivity, 0.001f);
        
        // Maximum sensitivity
        config.gamepad.stickDeadzone = 1.0f;
        config.gamepad.triggerThreshold = 1.0f;
        config.gamepad.rotationSensitivity = 5.0f;
        config.save();
        
        loaded = InputConfig.load();
        assertEquals(1.0f, loaded.gamepad.stickDeadzone, 0.001f);
        assertEquals(1.0f, loaded.gamepad.triggerThreshold, 0.001f);
        assertEquals(5.0f, loaded.gamepad.rotationSensitivity, 0.001f);
    }

    @Test
    void testCaseInsensitiveKeyNames() {
        // Test that key names are case-insensitive
        assertEquals(GLFW_KEY_W, InputConfig.stringToKeyCode("w"));
        assertEquals(GLFW_KEY_W, InputConfig.stringToKeyCode("W"));
        assertEquals(GLFW_KEY_SPACE, InputConfig.stringToKeyCode("space"));
        assertEquals(GLFW_KEY_SPACE, InputConfig.stringToKeyCode("SPACE"));
        assertEquals(GLFW_KEY_ESCAPE, InputConfig.stringToKeyCode("escape"));
        assertEquals(GLFW_KEY_ESCAPE, InputConfig.stringToKeyCode("ESCAPE"));
        assertEquals(GLFW_KEY_ESCAPE, InputConfig.stringToKeyCode("esc"));
        assertEquals(GLFW_KEY_ESCAPE, InputConfig.stringToKeyCode("ESC"));
    }

    @Test
    void testCaseInsensitiveGamepadNames() {
        // Test that gamepad names are case-insensitive
        assertEquals(GLFW_GAMEPAD_BUTTON_A, InputConfig.stringToGamepadButton("button_a"));
        assertEquals(GLFW_GAMEPAD_BUTTON_A, InputConfig.stringToGamepadButton("BUTTON_A"));
        assertEquals(GLFW_GAMEPAD_AXIS_RIGHT_X, InputConfig.stringToGamepadAxis("right_stick_x"));
        assertEquals(GLFW_GAMEPAD_AXIS_RIGHT_X, InputConfig.stringToGamepadAxis("RIGHT_STICK_X"));
    }

    @Test
    void testAlternativeKeyNames() {
        // Test alternative key names
        assertEquals(GLFW_KEY_ESCAPE, InputConfig.stringToKeyCode("ESC"));
        assertEquals(GLFW_KEY_ESCAPE, InputConfig.stringToKeyCode("ESCAPE"));
        assertEquals(GLFW_KEY_ENTER, InputConfig.stringToKeyCode("ENTER"));
        assertEquals(GLFW_KEY_ENTER, InputConfig.stringToKeyCode("RETURN"));
        assertEquals(GLFW_KEY_LEFT_CONTROL, InputConfig.stringToKeyCode("CONTROL"));
        assertEquals(GLFW_KEY_LEFT_CONTROL, InputConfig.stringToKeyCode("CTRL"));
    }

    @Test
    void testAlternativeGamepadButtonNames() {
        // Test alternative button names
        assertEquals(GLFW_GAMEPAD_BUTTON_LEFT_BUMPER, InputConfig.stringToGamepadButton("LEFT_BUMPER"));
        assertEquals(GLFW_GAMEPAD_BUTTON_LEFT_BUMPER, InputConfig.stringToGamepadButton("LB"));
        assertEquals(GLFW_GAMEPAD_BUTTON_BACK, InputConfig.stringToGamepadButton("BACK"));
        assertEquals(GLFW_GAMEPAD_BUTTON_BACK, InputConfig.stringToGamepadButton("SELECT"));
        assertEquals(GLFW_GAMEPAD_BUTTON_GUIDE, InputConfig.stringToGamepadButton("GUIDE"));
        assertEquals(GLFW_GAMEPAD_BUTTON_GUIDE, InputConfig.stringToGamepadButton("HOME"));
    }

    @Test
    void testInvalidKeyNameFallback() {
        // Invalid key names should fall back to SPACE
        assertEquals(GLFW_KEY_SPACE, InputConfig.stringToKeyCode("INVALID_KEY"));
        assertEquals(GLFW_KEY_SPACE, InputConfig.stringToKeyCode(""));
        assertEquals(GLFW_KEY_SPACE, InputConfig.stringToKeyCode("123ABC"));
    }

    @Test
    void testInvalidGamepadNameFallback() {
        // Invalid button names should fall back to BUTTON_A
        assertEquals(GLFW_GAMEPAD_BUTTON_A, InputConfig.stringToGamepadButton("INVALID_BUTTON"));
        assertEquals(GLFW_GAMEPAD_BUTTON_A, InputConfig.stringToGamepadButton(""));
        
        // Invalid axis names should fall back to RIGHT_STICK_X
        assertEquals(GLFW_GAMEPAD_AXIS_RIGHT_X, InputConfig.stringToGamepadAxis("INVALID_AXIS"));
        assertEquals(GLFW_GAMEPAD_AXIS_RIGHT_X, InputConfig.stringToGamepadAxis(""));
    }
}
