package org.chrisgruber.nettank.client.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class GameConfigTest {

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
    void testDefaultConfigCreation() {
        // When config doesn't exist, load() should create it with defaults
        GameConfig config = GameConfig.load();
        
        assertNotNull(config);
        assertNotNull(config.playerName);
        assertNotNull(config.display);
        
        // Verify default values
        assertEquals("Player", config.playerName);
        assertEquals(1920, config.display.width);
        assertEquals(1080, config.display.height);
        assertFalse(config.display.fullscreen);
        assertTrue(config.display.vsync);
        
        // Verify the file was created
        assertTrue(Files.exists(configFile));
    }

    @Test
    void testConfigSaveAndLoad() throws Exception {
        // Create and save a custom config
        GameConfig config = new GameConfig();
        config.playerName = "TestPlayer";
        config.display.width = 2560;
        config.display.height = 1440;
        config.display.fullscreen = true;
        config.display.vsync = false;
        
        config.save();
        
        // Load the config back
        GameConfig loadedConfig = GameConfig.load();
        
        // Verify loaded values match saved values
        assertEquals("TestPlayer", loadedConfig.playerName);
        assertEquals(2560, loadedConfig.display.width);
        assertEquals(1440, loadedConfig.display.height);
        assertTrue(loadedConfig.display.fullscreen);
        assertFalse(loadedConfig.display.vsync);
    }

    @Test
    void testPlayerNameValidation() {
        GameConfig config = new GameConfig();
        
        // Test normal name
        config.setPlayerName("John");
        assertEquals("John", config.playerName);
        
        // Test name with spaces
        config.setPlayerName("John Doe");
        assertEquals("John Doe", config.playerName);
        
        // Test name with numbers and special chars
        config.setPlayerName("Player_123");
        assertEquals("Player_123", config.playerName);
        
        // Test trimming
        config.setPlayerName("  Trimmed  ");
        assertEquals("Trimmed", config.playerName);
    }

    @Test
    void testPlayerNameLengthLimit() {
        GameConfig config = new GameConfig();
        
        // Test name too long (>20 chars)
        config.setPlayerName("ThisIsAVeryLongPlayerNameThatExceedsTheLimit");
        assertEquals(20, config.playerName.length());
        assertEquals("ThisIsAVeryLongPlaye", config.playerName);
    }

    @Test
    void testPlayerNameSpecialCharacterRemoval() {
        GameConfig config = new GameConfig();
        
        // Test special characters are removed
        config.setPlayerName("Player@#$%");
        assertEquals("Player", config.playerName);
        
        // Test only special characters (should default to "Player")
        config.setPlayerName("@#$%^&*");
        assertEquals("Player", config.playerName);
    }

    @Test
    void testPlayerNameEmpty() {
        GameConfig config = new GameConfig();
        
        // Test empty string
        config.setPlayerName("");
        assertEquals("Player", config.playerName);
        
        // Test null
        config.setPlayerName(null);
        assertEquals("Player", config.playerName);
        
        // Test whitespace only
        config.setPlayerName("   ");
        assertEquals("Player", config.playerName);
    }

    @Test
    void testResolutionValidation() {
        GameConfig config = new GameConfig();
        
        // Test normal resolution
        config.setResolution(1920, 1080);
        assertEquals(1920, config.display.width);
        assertEquals(1080, config.display.height);
        
        // Test 4K resolution
        config.setResolution(3840, 2160);
        assertEquals(3840, config.display.width);
        assertEquals(2160, config.display.height);
    }

    @Test
    void testResolutionMinimumClamping() {
        GameConfig config = new GameConfig();
        
        // Test resolution too small (should clamp to minimums)
        config.setResolution(640, 480);
        assertEquals(800, config.display.width);  // Clamped to min 800
        assertEquals(600, config.display.height); // Clamped to min 600
        
        // Test negative values
        config.setResolution(-100, -100);
        assertEquals(800, config.display.width);
        assertEquals(600, config.display.height);
    }

    @Test
    void testResolutionMaximumClamping() {
        GameConfig config = new GameConfig();
        
        // Test resolution too large (should clamp to maximums)
        config.setResolution(10000, 10000);
        assertEquals(3840, config.display.width);  // Clamped to max 4K width
        assertEquals(2160, config.display.height); // Clamped to max 4K height
    }

    @Test
    void testResolutionPresets() {
        GameConfig config = new GameConfig();
        
        // Test 720p preset
        config.applyResolutionPreset(GameConfig.ResolutionPreset.HD_720P);
        assertEquals(1280, config.display.width);
        assertEquals(720, config.display.height);
        
        // Test 1080p preset
        config.applyResolutionPreset(GameConfig.ResolutionPreset.HD_1080P);
        assertEquals(1920, config.display.width);
        assertEquals(1080, config.display.height);
        
        // Test 1440p preset
        config.applyResolutionPreset(GameConfig.ResolutionPreset.QHD_1440P);
        assertEquals(2560, config.display.width);
        assertEquals(1440, config.display.height);
        
        // Test 4K preset
        config.applyResolutionPreset(GameConfig.ResolutionPreset.UHD_4K);
        assertEquals(3840, config.display.width);
        assertEquals(2160, config.display.height);
    }

    @Test
    void testInvalidConfigLoadsDefaults() throws Exception {
        // Create directory
        Files.createDirectories(configFile.getParent());
        
        // Write invalid JSON
        Files.writeString(configFile, "{ invalid json }");
        
        // Load should fall back to defaults
        GameConfig config = GameConfig.load();
        
        assertNotNull(config);
        assertEquals("Player", config.playerName);
        assertEquals(1920, config.display.width);
        assertEquals(1080, config.display.height);
    }

    @Test
    void testLoadConfigWithMissingFields() throws Exception {
        // Create directory
        Files.createDirectories(configFile.getParent());
        
        // Write JSON with missing fields
        Files.writeString(configFile, "{ \"playerName\": \"TestUser\" }");
        
        // Load should create the default display config
        GameConfig config = GameConfig.load();
        
        assertNotNull(config);
        assertEquals("TestUser", config.playerName);
        assertNotNull(config.display);
    }

    @Test
    void testLoadConfigWithInvalidResolution() throws Exception {
        // Create directory
        Files.createDirectories(configFile.getParent());
        
        // Write config with resolution too small
        Files.writeString(configFile, 
            "{ \"playerName\": \"Test\", \"display\": { \"width\": 100, \"height\": 100 } }");
        
        // Load should fix resolution to defaults
        GameConfig config = GameConfig.load();
        
        assertEquals("Test", config.playerName);
        assertEquals(1920, config.display.width);  // Reset to default
        assertEquals(1080, config.display.height); // Reset to default
    }

    @Test
    void testLoadConfigWithOversizedResolution() throws Exception {
        // Create directory
        Files.createDirectories(configFile.getParent());
        
        // Write config with resolution too large
        Files.writeString(configFile, 
            "{ \"playerName\": \"Test\", \"display\": { \"width\": 10000, \"height\": 10000 } }");
        
        // Load should clamp resolution
        GameConfig config = GameConfig.load();
        
        assertEquals("Test", config.playerName);
        assertEquals(3840, config.display.width);  // Clamped to max 4K
        assertEquals(2160, config.display.height); // Clamped to max 4K
    }

    @Test
    void testLoadConfigWithEmptyPlayerName() throws Exception {
        // Create directory
        Files.createDirectories(configFile.getParent());
        
        // Write config with an empty player name
        Files.writeString(configFile, 
            "{ \"playerName\": \"\", \"display\": { \"width\": 1920, \"height\": 1080 } }");
        
        // Load should default empty name to "Player"
        GameConfig config = GameConfig.load();
        
        assertEquals("Player", config.playerName);
    }

    @Test
    void testFullscreenAndVSyncSettings() {
        GameConfig config = new GameConfig();
        
        // Test defaults
        assertFalse(config.display.fullscreen);
        assertTrue(config.display.vsync);
        
        // Test changing values
        config.display.fullscreen = true;
        config.display.vsync = false;
        config.save();
        
        // Reload and verify
        GameConfig loaded = GameConfig.load();
        assertTrue(loaded.display.fullscreen);
        assertFalse(loaded.display.vsync);
    }

    @Test
    void testConfigFileFormat() throws Exception {
        // Create and save config
        GameConfig config = new GameConfig();
        config.playerName = "FormatTest";
        config.save();
        
        // Read the file and verify it is valid JSON
        String json = Files.readString(configFile);
        assertNotNull(json);
        assertTrue(json.contains("\"playerName\""));
        assertTrue(json.contains("\"display\""));
        assertTrue(json.contains("\"width\""));
        assertTrue(json.contains("\"height\""));
        assertTrue(json.contains("\"fullscreen\""));
        assertTrue(json.contains("\"vsync\""));
    }

    @Test
    void testConfigDirectoryCreation() {
        // Delete the .nettank directory if it exists
        Path nettankDir = tempDir.resolve(".nettank");
        assertFalse(Files.exists(nettankDir));
        
        // Load config should create the directory
        GameConfig config = GameConfig.load();
        
        assertTrue(Files.exists(nettankDir));
        assertTrue(Files.isDirectory(nettankDir));
        assertTrue(Files.exists(configFile));
    }

    @Test
    void testMultipleSaveAndLoad() throws Exception {
        // Save config multiple times
        GameConfig config1 = new GameConfig();
        config1.playerName = "FirstSave";
        config1.save();
        
        GameConfig config2 = GameConfig.load();
        config2.playerName = "SecondSave";
        config2.display.width = 2560;
        config2.save();
        
        GameConfig config3 = GameConfig.load();
        config3.playerName = "ThirdSave";
        config3.display.height = 1440;
        config3.save();
        
        // The final load should have all the latest values
        GameConfig finalConfig = GameConfig.load();
        assertEquals("ThirdSave", finalConfig.playerName);
        assertEquals(2560, finalConfig.display.width);
        assertEquals(1440, finalConfig.display.height);
    }
}
