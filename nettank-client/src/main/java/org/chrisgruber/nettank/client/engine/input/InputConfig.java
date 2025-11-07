package org.chrisgruber.nettank.client.engine.input;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.lwjgl.glfw.GLFW.*;

public class InputConfig {
    private static final Logger logger = LoggerFactory.getLogger(InputConfig.class);
    private static final String CONFIG_DIR = ".nettank";
    private static final String CONFIG_FILE = "input-config.json";
    
    public KeyboardConfig keyboard = new KeyboardConfig();
    public GamepadConfig gamepad = new GamepadConfig();
    
    public static class KeyboardConfig {
        public String forward = "W";
        public String backward = "S";
        public String rotateLeft = "A";
        public String rotateRight = "D";
        public String shoot = "SPACE";
        public String exit = "ESCAPE";
    }
    
    public static class GamepadConfig {
        public String forward = "RIGHT_TRIGGER";
        public String backward = "LEFT_TRIGGER";
        public String rotateAxis = "RIGHT_STICK_X";
        public String shoot = "BUTTON_A";
        public float stickDeadzone = 0.2f;
        public float triggerThreshold = 0.1f;
        public float rotationSensitivity = 1.0f;
    }
    
    /**
     * Load configuration from user's home directory, creating default if not exists
     */
    public static InputConfig load() {
        Path configPath = getConfigPath();
        
        if (!Files.exists(configPath)) {
            logger.info("Config file not found, creating default at: {}", configPath);
            InputConfig defaultConfig = createDefault();
            defaultConfig.save();
            return defaultConfig;
        }
        
        try (Reader reader = Files.newBufferedReader(configPath)) {
            Gson gson = new Gson();
            InputConfig config = gson.fromJson(reader, InputConfig.class);
            logger.info("Loaded input configuration from: {}", configPath);
            return config;
        } catch (Exception e) {
            logger.error("Failed to load config file, using defaults", e);
            return createDefault();
        }
    }
    
    /**
     * Save configuration to user's home directory
     */
    public void save() {
        Path configPath = getConfigPath();
        
        try {
            // Create directory if it doesn't exist
            Files.createDirectories(configPath.getParent());
            
            // Write config file with pretty printing
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            try (Writer writer = Files.newBufferedWriter(configPath)) {
                gson.toJson(this, writer);
            }
            
            logger.info("Saved input configuration to: {}", configPath);
        } catch (Exception e) {
            logger.error("Failed to save config file", e);
        }
    }
    
    /**
     * Create default configuration
     */
    private static InputConfig createDefault() {
        return new InputConfig();
    }
    
    /**
     * Get path to config file in user's home directory
     */
    private static Path getConfigPath() {
        String userHome = System.getProperty("user.home");
        return Paths.get(userHome, CONFIG_DIR, CONFIG_FILE);
    }
    
    /**
     * Convert key name string to GLFW key code
     */
    public static int stringToKeyCode(String keyName) {
        return switch (keyName.toUpperCase()) {
            case "A" -> GLFW_KEY_A;
            case "B" -> GLFW_KEY_B;
            case "C" -> GLFW_KEY_C;
            case "D" -> GLFW_KEY_D;
            case "E" -> GLFW_KEY_E;
            case "F" -> GLFW_KEY_F;
            case "G" -> GLFW_KEY_G;
            case "H" -> GLFW_KEY_H;
            case "I" -> GLFW_KEY_I;
            case "J" -> GLFW_KEY_J;
            case "K" -> GLFW_KEY_K;
            case "L" -> GLFW_KEY_L;
            case "M" -> GLFW_KEY_M;
            case "N" -> GLFW_KEY_N;
            case "O" -> GLFW_KEY_O;
            case "P" -> GLFW_KEY_P;
            case "Q" -> GLFW_KEY_Q;
            case "R" -> GLFW_KEY_R;
            case "S" -> GLFW_KEY_S;
            case "T" -> GLFW_KEY_T;
            case "U" -> GLFW_KEY_U;
            case "V" -> GLFW_KEY_V;
            case "W" -> GLFW_KEY_W;
            case "X" -> GLFW_KEY_X;
            case "Y" -> GLFW_KEY_Y;
            case "Z" -> GLFW_KEY_Z;
            case "SPACE" -> GLFW_KEY_SPACE;
            case "ESCAPE", "ESC" -> GLFW_KEY_ESCAPE;
            case "ENTER", "RETURN" -> GLFW_KEY_ENTER;
            case "TAB" -> GLFW_KEY_TAB;
            case "SHIFT" -> GLFW_KEY_LEFT_SHIFT;
            case "CONTROL", "CTRL" -> GLFW_KEY_LEFT_CONTROL;
            case "ALT" -> GLFW_KEY_LEFT_ALT;
            case "UP" -> GLFW_KEY_UP;
            case "DOWN" -> GLFW_KEY_DOWN;
            case "LEFT" -> GLFW_KEY_LEFT;
            case "RIGHT" -> GLFW_KEY_RIGHT;
            case "0" -> GLFW_KEY_0;
            case "1" -> GLFW_KEY_1;
            case "2" -> GLFW_KEY_2;
            case "3" -> GLFW_KEY_3;
            case "4" -> GLFW_KEY_4;
            case "5" -> GLFW_KEY_5;
            case "6" -> GLFW_KEY_6;
            case "7" -> GLFW_KEY_7;
            case "8" -> GLFW_KEY_8;
            case "9" -> GLFW_KEY_9;
            default -> {
                logger.warn("Unknown key name: {}, defaulting to SPACE", keyName);
                yield GLFW_KEY_SPACE;
            }
        };
    }
    
    /**
     * Convert gamepad axis name to GLFW axis constant
     */
    public static int stringToGamepadAxis(String axisName) {
        return switch (axisName.toUpperCase()) {
            case "LEFT_STICK_X" -> GLFW_GAMEPAD_AXIS_LEFT_X;
            case "LEFT_STICK_Y" -> GLFW_GAMEPAD_AXIS_LEFT_Y;
            case "RIGHT_STICK_X" -> GLFW_GAMEPAD_AXIS_RIGHT_X;
            case "RIGHT_STICK_Y" -> GLFW_GAMEPAD_AXIS_RIGHT_Y;
            case "LEFT_TRIGGER" -> GLFW_GAMEPAD_AXIS_LEFT_TRIGGER;
            case "RIGHT_TRIGGER" -> GLFW_GAMEPAD_AXIS_RIGHT_TRIGGER;
            default -> {
                logger.warn("Unknown gamepad axis: {}, defaulting to RIGHT_STICK_X", axisName);
                yield GLFW_GAMEPAD_AXIS_RIGHT_X;
            }
        };
    }
    
    /**
     * Convert gamepad button name to GLFW button constant
     */
    public static int stringToGamepadButton(String buttonName) {
        return switch (buttonName.toUpperCase()) {
            case "BUTTON_A", "A" -> GLFW_GAMEPAD_BUTTON_A;
            case "BUTTON_B", "B" -> GLFW_GAMEPAD_BUTTON_B;
            case "BUTTON_X", "X" -> GLFW_GAMEPAD_BUTTON_X;
            case "BUTTON_Y", "Y" -> GLFW_GAMEPAD_BUTTON_Y;
            case "LEFT_BUMPER", "LB" -> GLFW_GAMEPAD_BUTTON_LEFT_BUMPER;
            case "RIGHT_BUMPER", "RB" -> GLFW_GAMEPAD_BUTTON_RIGHT_BUMPER;
            case "BACK", "SELECT" -> GLFW_GAMEPAD_BUTTON_BACK;
            case "START" -> GLFW_GAMEPAD_BUTTON_START;
            case "GUIDE", "HOME" -> GLFW_GAMEPAD_BUTTON_GUIDE;
            case "LEFT_THUMB" -> GLFW_GAMEPAD_BUTTON_LEFT_THUMB;
            case "RIGHT_THUMB" -> GLFW_GAMEPAD_BUTTON_RIGHT_THUMB;
            case "DPAD_UP" -> GLFW_GAMEPAD_BUTTON_DPAD_UP;
            case "DPAD_RIGHT" -> GLFW_GAMEPAD_BUTTON_DPAD_RIGHT;
            case "DPAD_DOWN" -> GLFW_GAMEPAD_BUTTON_DPAD_DOWN;
            case "DPAD_LEFT" -> GLFW_GAMEPAD_BUTTON_DPAD_LEFT;
            default -> {
                logger.warn("Unknown gamepad button: {}, defaulting to BUTTON_A", buttonName);
                yield GLFW_GAMEPAD_BUTTON_A;
            }
        };
    }
}
