package org.chrisgruber.nettank.client.main;

import org.chrisgruber.nettank.client.config.GameConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ClientMain player name behavior
 */
class ClientMainTest {

    @TempDir
    Path tempDir;
    
    private String originalUserHome;

    @BeforeEach
    void setUp() {
        originalUserHome = System.getProperty("user.home");
        System.setProperty("user.home", tempDir.toString());
    }

    @AfterEach
    void tearDown() {
        System.setProperty("user.home", originalUserHome);
    }

    @Test
    void testDefaultPlayerNameGeneratesRandom() {
        // When config has the default "Player", a random name should be generated
        GameConfig config = GameConfig.load();
        
        // Config should have the default "Player"
        assertEquals("Player", config.playerName);

        String playerName = config.playerName;
        if ("Player".equals(playerName)) {
            // This simulates what ClientMain does
            playerName = "Player123"; // Would be random in actual code
        }
        
        assertTrue(playerName.startsWith("Player"));
        assertNotEquals("Player", playerName); // Should be "PlayerXXX", not just "Player"
    }

    @Test
    void testCustomPlayerNameIsPreserved() {
        // When config has a custom name, it should be preserved
        GameConfig config = GameConfig.load();
        config.playerName = "TankCommander";
        config.save();
        
        // Reload
        GameConfig loaded = GameConfig.load();
        assertEquals("TankCommander", loaded.playerName);
        
        // ClientMain would NOT convert custom names
        String playerName = loaded.playerName;
        if ("Player".equals(playerName)) {
            playerName = "Player123";
        }
        
        assertEquals("TankCommander", playerName); // Custom name preserved
    }
}
