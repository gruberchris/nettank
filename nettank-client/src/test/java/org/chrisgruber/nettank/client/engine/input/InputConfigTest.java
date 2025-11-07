package org.chrisgruber.nettank.client.engine.input;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.glfw.GLFW.*;

class InputConfigTest {

    @TempDir
    Path tempDir;
    
    private Path configFile;
    private String originalUserHome;

    @BeforeEach
    void setUp() {
        // Save original user.home and set to temp directory for testing
        originalUserHome = System.getProperty("user.home");
        System.setProperty("user.home", tempDir.toString());
        configFile = tempDir.resolve(".nettank").resolve("input-config.json");
    }

    @AfterEach
    void tearDown() {
        // Restore original user.home
        System.setProperty("user.home", originalUserHome);
    }

    @Test
    void testDefaultConfigCreation() {
        // When config doesn't exist, load() should create it with defaults
        InputConfig config = InputConfig.load();
        
        assertNotNull(config);
        assertNotNull(config.keyboard);
        assertNotNull(config.gamepad);
        
        // Verify default keyboard settings
        assertEquals("W", config.keyboard.forward);
        assertEquals("S", config.keyboard.backward);
        assertEquals("A", config.keyboard.rotateLeft);
        assertEquals("D", config.keyboard.rotateRight);
        assertEquals("SPACE", config.keyboard.shoot);
        assertEquals("ESCAPE", config.keyboard.exit);
        
        // Verify default gamepad settings
        assertEquals("RIGHT_TRIGGER", config.gamepad.forward);
        assertEquals("LEFT_TRIGGER", config.gamepad.backward);
        assertEquals("RIGHT_STICK_X", config.gamepad.rotateAxis);
        assertEquals("BUTTON_A", config.gamepad.shoot);
        assertEquals(0.2f, config.gamepad.stickDeadzone, 0.001f);
        assertEquals(0.1f, config.gamepad.triggerThreshold, 0.001f);
        assertEquals(1.0f, config.gamepad.rotationSensitivity, 0.001f);
        
        // Verify file was created
        assertTrue(Files.exists(configFile));
    }

    @Test
    void testConfigSaveAndLoad() throws IOException {
        // Create and save a custom config
        InputConfig config = new InputConfig();
        config.keyboard.forward = "UP";
        config.keyboard.backward = "DOWN";
        config.keyboard.rotateLeft = "LEFT";
        config.keyboard.rotateRight = "RIGHT";
        config.gamepad.stickDeadzone = 0.3f;
        config.gamepad.rotationSensitivity = 1.5f;
        
        config.save();
        
        // Load the config back
        InputConfig loadedConfig = InputConfig.load();
        
        // Verify loaded values match saved values
        assertEquals("UP", loadedConfig.keyboard.forward);
        assertEquals("DOWN", loadedConfig.keyboard.backward);
        assertEquals("LEFT", loadedConfig.keyboard.rotateLeft);
        assertEquals("RIGHT", loadedConfig.keyboard.rotateRight);
        assertEquals(0.3f, loadedConfig.gamepad.stickDeadzone, 0.001f);
        assertEquals(1.5f, loadedConfig.gamepad.rotationSensitivity, 0.001f);
    }

    @Test
    void testStringToKeyCode() {
        // Test letter keys
        assertEquals(GLFW_KEY_W, InputConfig.stringToKeyCode("W"));
        assertEquals(GLFW_KEY_A, InputConfig.stringToKeyCode("A"));
        assertEquals(GLFW_KEY_S, InputConfig.stringToKeyCode("S"));
        assertEquals(GLFW_KEY_D, InputConfig.stringToKeyCode("D"));
        
        // Test case insensitivity
        assertEquals(GLFW_KEY_W, InputConfig.stringToKeyCode("w"));
        assertEquals(GLFW_KEY_A, InputConfig.stringToKeyCode("a"));
        
        // Test special keys
        assertEquals(GLFW_KEY_SPACE, InputConfig.stringToKeyCode("SPACE"));
        assertEquals(GLFW_KEY_ESCAPE, InputConfig.stringToKeyCode("ESCAPE"));
        assertEquals(GLFW_KEY_ESCAPE, InputConfig.stringToKeyCode("ESC"));
        assertEquals(GLFW_KEY_ENTER, InputConfig.stringToKeyCode("ENTER"));
        assertEquals(GLFW_KEY_ENTER, InputConfig.stringToKeyCode("RETURN"));
        
        // Test arrow keys
        assertEquals(GLFW_KEY_UP, InputConfig.stringToKeyCode("UP"));
        assertEquals(GLFW_KEY_DOWN, InputConfig.stringToKeyCode("DOWN"));
        assertEquals(GLFW_KEY_LEFT, InputConfig.stringToKeyCode("LEFT"));
        assertEquals(GLFW_KEY_RIGHT, InputConfig.stringToKeyCode("RIGHT"));
        
        // Test number keys
        assertEquals(GLFW_KEY_0, InputConfig.stringToKeyCode("0"));
        assertEquals(GLFW_KEY_1, InputConfig.stringToKeyCode("1"));
        assertEquals(GLFW_KEY_9, InputConfig.stringToKeyCode("9"));
        
        // Test unknown key (should default to SPACE)
        assertEquals(GLFW_KEY_SPACE, InputConfig.stringToKeyCode("UNKNOWN_KEY"));
    }

    @Test
    void testStringToGamepadAxis() {
        assertEquals(GLFW_GAMEPAD_AXIS_LEFT_X, InputConfig.stringToGamepadAxis("LEFT_STICK_X"));
        assertEquals(GLFW_GAMEPAD_AXIS_LEFT_Y, InputConfig.stringToGamepadAxis("LEFT_STICK_Y"));
        assertEquals(GLFW_GAMEPAD_AXIS_RIGHT_X, InputConfig.stringToGamepadAxis("RIGHT_STICK_X"));
        assertEquals(GLFW_GAMEPAD_AXIS_RIGHT_Y, InputConfig.stringToGamepadAxis("RIGHT_STICK_Y"));
        assertEquals(GLFW_GAMEPAD_AXIS_LEFT_TRIGGER, InputConfig.stringToGamepadAxis("LEFT_TRIGGER"));
        assertEquals(GLFW_GAMEPAD_AXIS_RIGHT_TRIGGER, InputConfig.stringToGamepadAxis("RIGHT_TRIGGER"));
        
        // Test case insensitivity
        assertEquals(GLFW_GAMEPAD_AXIS_RIGHT_X, InputConfig.stringToGamepadAxis("right_stick_x"));
        
        // Test unknown axis (should default to RIGHT_STICK_X)
        assertEquals(GLFW_GAMEPAD_AXIS_RIGHT_X, InputConfig.stringToGamepadAxis("UNKNOWN_AXIS"));
    }

    @Test
    void testStringToGamepadButton() {
        // Test button names
        assertEquals(GLFW_GAMEPAD_BUTTON_A, InputConfig.stringToGamepadButton("BUTTON_A"));
        assertEquals(GLFW_GAMEPAD_BUTTON_A, InputConfig.stringToGamepadButton("A"));
        assertEquals(GLFW_GAMEPAD_BUTTON_B, InputConfig.stringToGamepadButton("BUTTON_B"));
        assertEquals(GLFW_GAMEPAD_BUTTON_B, InputConfig.stringToGamepadButton("B"));
        assertEquals(GLFW_GAMEPAD_BUTTON_X, InputConfig.stringToGamepadButton("BUTTON_X"));
        assertEquals(GLFW_GAMEPAD_BUTTON_X, InputConfig.stringToGamepadButton("X"));
        assertEquals(GLFW_GAMEPAD_BUTTON_Y, InputConfig.stringToGamepadButton("BUTTON_Y"));
        assertEquals(GLFW_GAMEPAD_BUTTON_Y, InputConfig.stringToGamepadButton("Y"));
        
        // Test bumpers
        assertEquals(GLFW_GAMEPAD_BUTTON_LEFT_BUMPER, InputConfig.stringToGamepadButton("LEFT_BUMPER"));
        assertEquals(GLFW_GAMEPAD_BUTTON_LEFT_BUMPER, InputConfig.stringToGamepadButton("LB"));
        assertEquals(GLFW_GAMEPAD_BUTTON_RIGHT_BUMPER, InputConfig.stringToGamepadButton("RIGHT_BUMPER"));
        assertEquals(GLFW_GAMEPAD_BUTTON_RIGHT_BUMPER, InputConfig.stringToGamepadButton("RB"));
        
        // Test D-pad
        assertEquals(GLFW_GAMEPAD_BUTTON_DPAD_UP, InputConfig.stringToGamepadButton("DPAD_UP"));
        assertEquals(GLFW_GAMEPAD_BUTTON_DPAD_DOWN, InputConfig.stringToGamepadButton("DPAD_DOWN"));
        assertEquals(GLFW_GAMEPAD_BUTTON_DPAD_LEFT, InputConfig.stringToGamepadButton("DPAD_LEFT"));
        assertEquals(GLFW_GAMEPAD_BUTTON_DPAD_RIGHT, InputConfig.stringToGamepadButton("DPAD_RIGHT"));
        
        // Test special buttons
        assertEquals(GLFW_GAMEPAD_BUTTON_START, InputConfig.stringToGamepadButton("START"));
        assertEquals(GLFW_GAMEPAD_BUTTON_BACK, InputConfig.stringToGamepadButton("BACK"));
        assertEquals(GLFW_GAMEPAD_BUTTON_BACK, InputConfig.stringToGamepadButton("SELECT"));
        assertEquals(GLFW_GAMEPAD_BUTTON_GUIDE, InputConfig.stringToGamepadButton("GUIDE"));
        assertEquals(GLFW_GAMEPAD_BUTTON_GUIDE, InputConfig.stringToGamepadButton("HOME"));
        
        // Test case insensitivity
        assertEquals(GLFW_GAMEPAD_BUTTON_A, InputConfig.stringToGamepadButton("button_a"));
        
        // Test unknown button (should default to BUTTON_A)
        assertEquals(GLFW_GAMEPAD_BUTTON_A, InputConfig.stringToGamepadButton("UNKNOWN_BUTTON"));
    }

    @Test
    void testConfigWithCustomKeyboardLayout() throws IOException {
        // Create ESDF layout config
        InputConfig config = new InputConfig();
        config.keyboard.forward = "E";
        config.keyboard.backward = "D";
        config.keyboard.rotateLeft = "S";
        config.keyboard.rotateRight = "F";
        config.keyboard.shoot = "SPACE";
        config.keyboard.exit = "ESCAPE";
        
        config.save();
        
        // Reload and verify
        InputConfig loaded = InputConfig.load();
        assertEquals("E", loaded.keyboard.forward);
        assertEquals("D", loaded.keyboard.backward);
        assertEquals("S", loaded.keyboard.rotateLeft);
        assertEquals("F", loaded.keyboard.rotateRight);
    }

    @Test
    void testConfigWithCustomGamepadSettings() throws IOException {
        // Create custom gamepad config
        InputConfig config = new InputConfig();
        config.gamepad.forward = "RIGHT_TRIGGER";
        config.gamepad.backward = "LEFT_TRIGGER";
        config.gamepad.rotateAxis = "LEFT_STICK_X";
        config.gamepad.shoot = "RIGHT_BUMPER";
        config.gamepad.stickDeadzone = 0.15f;
        config.gamepad.triggerThreshold = 0.2f;
        config.gamepad.rotationSensitivity = 1.5f;
        
        config.save();
        
        // Reload and verify
        InputConfig loaded = InputConfig.load();
        assertEquals("LEFT_STICK_X", loaded.gamepad.rotateAxis);
        assertEquals("RIGHT_BUMPER", loaded.gamepad.shoot);
        assertEquals(0.15f, loaded.gamepad.stickDeadzone, 0.001f);
        assertEquals(0.2f, loaded.gamepad.triggerThreshold, 0.001f);
        assertEquals(1.5f, loaded.gamepad.rotationSensitivity, 0.001f);
    }

    @Test
    void testConfigFileFormat() throws IOException {
        // Create and save config
        InputConfig config = new InputConfig();
        config.save();
        
        // Read the file and verify it's valid JSON
        String json = Files.readString(configFile);
        assertNotNull(json);
        assertTrue(json.contains("\"keyboard\""));
        assertTrue(json.contains("\"gamepad\""));
        assertTrue(json.contains("\"forward\""));
        
        // Verify it can be parsed back
        Gson gson = new Gson();
        InputConfig parsed = gson.fromJson(json, InputConfig.class);
        assertNotNull(parsed);
        assertNotNull(parsed.keyboard);
        assertNotNull(parsed.gamepad);
    }

    @Test
    void testConfigDirectoryCreation() {
        // Delete the .nettank directory if it exists
        Path nettankDir = tempDir.resolve(".nettank");
        assertFalse(Files.exists(nettankDir));
        
        // Load config should create the directory
        InputConfig config = InputConfig.load();
        
        assertTrue(Files.exists(nettankDir));
        assertTrue(Files.isDirectory(nettankDir));
        assertTrue(Files.exists(configFile));
    }

    @Test
    void testCorruptedConfigFallsBackToDefault() throws IOException {
        // Create directory
        Files.createDirectories(configFile.getParent());
        
        // Write invalid JSON
        Files.writeString(configFile, "{ this is not valid json }");
        
        // Load should fall back to defaults without crashing
        InputConfig config = InputConfig.load();
        
        assertNotNull(config);
        assertEquals("W", config.keyboard.forward); // Should have default values
    }

    @Test
    void testEmptyConfigFallsBackToDefault() throws IOException {
        // Create directory
        Files.createDirectories(configFile.getParent());
        
        // Write empty file (Gson can't parse empty string)
        Files.writeString(configFile, "{}");
        
        // Load should fall back to defaults (empty JSON object creates default config)
        InputConfig config = InputConfig.load();
        
        assertNotNull(config);
        // With empty JSON, Gson creates object but fields might be null, so defaults get applied
        assertNotNull(config.keyboard);
        assertNotNull(config.gamepad);
    }
}
