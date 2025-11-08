package org.chrisgruber.nettank.client.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for GameConfig to ensure it works end-to-end
 */
class GameConfigIntegrationTest {

    @TempDir
    Path tempDir;
    
    private Path configFile;
    private String originalUserHome;

    @BeforeEach
    void setUp() {
        // Save original user.home and set to temp directory for testing
        originalUserHome = System.getProperty("user.home");
        System.setProperty("user.home", tempDir.toString());
        configFile = tempDir.resolve(".nettank").resolve("game-config.json");
    }

    @AfterEach
    void tearDown() {
        // Restore original user.home
        System.setProperty("user.home", originalUserHome);
    }

    @Test
    void testDefaultConfigCreatedOnFirstLoad() {
        // The first load should create config with defaults
        GameConfig config = GameConfig.load();
        
        assertNotNull(config);
        assertEquals("Player", config.playerName);
        assertEquals(1920, config.display.width);
        assertEquals(1080, config.display.height);
        assertFalse(config.display.fullscreen);
        assertTrue(config.display.vsync);
        
        // Verify the file was created
        assertTrue(Files.exists(configFile));
    }

    @Test
    void testConfigPersistsAcrossLoads() {
        // Create and save custom config
        GameConfig config1 = GameConfig.load();
        config1.playerName = "CustomPlayer";
        config1.setResolution(2560, 1440);
        config1.display.fullscreen = true;
        config1.save();
        
        // Load again and verify persistence
        GameConfig config2 = GameConfig.load();
        assertEquals("CustomPlayer", config2.playerName);
        assertEquals(2560, config2.display.width);
        assertEquals(1440, config2.display.height);
        assertTrue(config2.display.fullscreen);
    }

    @Test
    void testResolutionClampingOnLoad() throws Exception {
        // Create config with invalid resolution
        Files.createDirectories(configFile.getParent());
        String invalidConfig = """
            {
              "playerName": "TestUser",
              "display": {
                "width": 50000,
                "height": 50000,
                "fullscreen": false,
                "vsync": true
              }
            }
            """;
        Files.writeString(configFile, invalidConfig);
        
        // Load should clamp to max 4k resolution
        GameConfig config = GameConfig.load();
        assertEquals("TestUser", config.playerName);
        assertEquals(3840, config.display.width);  // Clamped to 4k resolution
        assertEquals(2160, config.display.height); // Clamped to 4k resolution
    }

    @Test
    void testPlayerNameSanitizationOnLoad() throws Exception {
        // Create config with an empty player name
        Files.createDirectories(configFile.getParent());
        String invalidConfig = """
            {
              "playerName": "",
              "display": {
                "width": 1920,
                "height": 1080,
                "fullscreen": false,
                "vsync": true
              }
            }
            """;
        Files.writeString(configFile, invalidConfig);
        
        // Load should default empty name
        GameConfig config = GameConfig.load();
        assertEquals("Player", config.playerName);
    }

    @Test
    void testMultipleConfigsCoexist() {
        // Game config should be independent of input config
        GameConfig gameConfig = GameConfig.load();
        gameConfig.playerName = "GameUser";
        gameConfig.save();
        
        // Verify game config exists
        assertTrue(Files.exists(tempDir.resolve(".nettank").resolve("game-config.json")));
        
        // Both configs should be in the same directory
        Path nettankDir = tempDir.resolve(".nettank");
        assertTrue(Files.exists(nettankDir));
        assertTrue(Files.isDirectory(nettankDir));
    }

    @Test
    void testConfigWithAllResolutionPresets() {
        GameConfig config = GameConfig.load();
        
        // Test all presets
        for (GameConfig.ResolutionPreset preset : GameConfig.ResolutionPreset.values()) {
            config.applyResolutionPreset(preset);
            config.save();
            
            GameConfig reloaded = GameConfig.load();
            assertEquals(preset.width, reloaded.display.width, 
                        "Failed for preset: " + preset.name());
            assertEquals(preset.height, reloaded.display.height, 
                        "Failed for preset: " + preset.name());
        }
    }

    @Test
    void testConfigRealisticUsageScenario() {
        // Simulate a user's typical workflow
        
        // 1. First launch - default config created
        GameConfig config = GameConfig.load();
        assertEquals("Player", config.playerName);
        
        // 2. User changes player name
        config.setPlayerName("TankMaster");
        config.save();
        
        // 3. Game restarts, config persists
        GameConfig config2 = GameConfig.load();
        assertEquals("TankMaster", config2.playerName);
        
        // 4. User changes resolution to 4k resolution and enables fullscreen
        config2.applyResolutionPreset(GameConfig.ResolutionPreset.UHD_4K);
        config2.display.fullscreen = true;
        config2.save();
        
        // 5. Game restarts again, everything persists
        GameConfig config3 = GameConfig.load();
        assertEquals("TankMaster", config3.playerName);
        assertEquals(3840, config3.display.width);
        assertEquals(2160, config3.display.height);
        assertTrue(config3.display.fullscreen);
    }
}
