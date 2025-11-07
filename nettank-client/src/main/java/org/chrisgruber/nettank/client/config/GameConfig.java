package org.chrisgruber.nettank.client.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class GameConfig {
    private static final Logger logger = LoggerFactory.getLogger(GameConfig.class);
    private static final String CONFIG_DIR = ".nettank";
    private static final String CONFIG_FILE = "game-config.json";
    
    public String playerName = "Player";
    public DisplayConfig display = new DisplayConfig();
    
    public static class DisplayConfig {
        public int width = 1920;  // 1080p width
        public int height = 1080; // 1080p height
        public boolean fullscreen = false;
        public boolean vsync = true;
    }
    
    /**
     * Load configuration from the user's home directory, creating default if not exists
     */
    public static GameConfig load() {
        Path configPath = getConfigPath();
        
        if (!Files.exists(configPath)) {
            logger.info("Game config file not found, creating default at: {}", configPath);
            GameConfig defaultConfig = createDefault();
            defaultConfig.save();
            return defaultConfig;
        }
        
        try (Reader reader = Files.newBufferedReader(configPath)) {
            Gson gson = new Gson();
            GameConfig config = gson.fromJson(reader, GameConfig.class);
            
            // Validate loaded config
            if (config == null || config.display == null) {
                logger.warn("Invalid config file, using defaults");
                return createDefault();
            }
            
            // Ensure the player name is not empty
            if (config.playerName == null || config.playerName.trim().isEmpty()) {
                config.playerName = "Player";
            }
            
            // Validate resolution
            if (config.display.width < 800 || config.display.height < 600) {
                logger.warn("Resolution too small ({}x{}), using defaults", 
                           config.display.width, config.display.height);
                config.display.width = 1920;
                config.display.height = 1080;
            }
            
            // Validate resolution max (prevent absurd values, max 4K)
            if (config.display.width > 3840 || config.display.height > 2160) {
                logger.warn("Resolution too large ({}x{}), capping to 4K", 
                           config.display.width, config.display.height);
                config.display.width = Math.min(config.display.width, 3840);
                config.display.height = Math.min(config.display.height, 2160);
            }
            
            logger.info("Loaded game configuration from: {}", configPath);
            logger.info("Player name: {}, Resolution: {}x{}, Fullscreen: {}, VSync: {}", 
                       config.playerName, config.display.width, config.display.height,
                       config.display.fullscreen, config.display.vsync);
            return config;
        } catch (Exception e) {
            logger.error("Failed to load game config file, using defaults", e);
            return createDefault();
        }
    }
    
    /**
     * Save configuration to the user's home directory
     */
    public void save() {
        Path configPath = getConfigPath();
        
        try {
            // Create the directory if it doesn't exist
            Files.createDirectories(configPath.getParent());
            
            // Write a config file with pretty printing
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            try (Writer writer = Files.newBufferedWriter(configPath)) {
                gson.toJson(this, writer);
            }
            
            logger.info("Saved game configuration to: {}", configPath);
        } catch (Exception e) {
            logger.error("Failed to save game config file", e);
        }
    }
    
    /**
     * Create default configuration
     */
    private static GameConfig createDefault() {
        return new GameConfig();
    }
    
    /**
     * Get path to config file in user's home directory
     */
    private static Path getConfigPath() {
        String userHome = System.getProperty("user.home");
        return Paths.get(userHome, CONFIG_DIR, CONFIG_FILE);
    }
    
    /**
     * Validate and sanitize player name
     */
    public void setPlayerName(String name) {
        if (name == null || name.trim().isEmpty()) {
            this.playerName = "Player";
            return;
        }
        
        // Trim and limit length
        name = name.trim();
        if (name.length() > 20) {
            name = name.substring(0, 20);
        }
        
        // Remove any special characters that might cause issues
        name = name.replaceAll("[^a-zA-Z0-9_\\-\\s]", "");
        
        if (name.isEmpty()) {
            this.playerName = "Player";
        } else {
            this.playerName = name;
        }
    }
    
    /**
     * Set display resolution
     */
    public void setResolution(int width, int height) {
        // Clamp to reasonable values (max 4K for realistic GLFW window support)
        this.display.width = Math.max(800, Math.min(3840, width));
        this.display.height = Math.max(600, Math.min(2160, height));
    }
    
    /**
     * Common resolution presets
     */
    public enum ResolutionPreset {
        HD_720P(1280, 720),
        HD_1080P(1920, 1080),
        QHD_1440P(2560, 1440),
        UHD_4K(3840, 2160);  // Maximum realistic resolution for GLFW windows
        
        public final int width;
        public final int height;
        
        ResolutionPreset(int width, int height) {
            this.width = width;
            this.height = height;
        }
    }
    
    /**
     * Apply a resolution preset
     */
    public void applyResolutionPreset(ResolutionPreset preset) {
        this.display.width = preset.width;
        this.display.height = preset.height;
    }
}
