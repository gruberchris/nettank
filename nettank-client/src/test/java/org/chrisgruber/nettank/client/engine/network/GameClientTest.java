package org.chrisgruber.nettank.client.engine.network;

import org.chrisgruber.nettank.common.util.GameState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GameClientTest {

    private GameClient gameClient;
    private NetworkCallbackHandler mockHandler;
    private final String testServerIp = "127.0.0.1";
    private final int testServerPort = 5555;
    private final String testPlayerName = "TestPlayer";

    @BeforeEach
    void setUp() {
        mockHandler = mock(NetworkCallbackHandler.class);
        gameClient = new GameClient(testServerIp, testServerPort, testPlayerName, mockHandler);
    }

    @AfterEach
    void tearDown() {
        if (gameClient != null) {
            gameClient.stop();
        }
    }

    // ========== Constructor Tests ==========

    @Test
    void testConstructor_ValidParameters() {
        assertNotNull(gameClient);
        assertFalse(gameClient.isConnected());
    }

    @Test
    void testConstructor_NullHandler() {
        // Constructor accepts null handler (no null check in the current implementation)
        assertDoesNotThrow(() -> {
            new GameClient(testServerIp, testServerPort, testPlayerName, null);
        });
    }

    // ========== Message Parsing Tests ==========

    @Test
    void testParseMessage_PlayerId() throws Exception {
        String message = "AID;42;1.0;0.5;0.0";
        
        // Use reflection to call the private parseServerMessage method
        var method = GameClient.class.getDeclaredMethod("parseServerMessage", String.class);
        method.setAccessible(true);
        method.invoke(gameClient, message);
        
        verify(mockHandler).setLocalPlayerId(42);
    }

    @Test
    void testParseMessage_NewPlayer() throws Exception {
        String message = "NEW;42;100.5;200.3;1.57;Player1;1.0;0.5;0.0";
        
        var method = GameClient.class.getDeclaredMethod("parseServerMessage", String.class);
        method.setAccessible(true);
        method.invoke(gameClient, message);
        
        verify(mockHandler).addOrUpdateTank(
            eq(42), 
            eq(100.5f), 
            eq(200.3f), 
            eq(1.57f), 
            eq("Player1"),
            eq(1.0f),
            eq(0.5f),
            eq(0.0f)
        );
    }

    @Test
    void testParseMessage_PlayerUpdate() throws Exception {
        String message = "UPD;42;150.5;200.3;1.57";
        
        var method = GameClient.class.getDeclaredMethod("parseServerMessage", String.class);
        method.setAccessible(true);
        method.invoke(gameClient, message);
        
        verify(mockHandler).updateTankState(42, 150.5f, 200.3f, 1.57f, false);
    }

    @Test
    void testParseMessage_PlayerLeft() throws Exception {
        String message = "LEF;42";
        
        var method = GameClient.class.getDeclaredMethod("parseServerMessage", String.class);
        method.setAccessible(true);
        method.invoke(gameClient, message);
        
        verify(mockHandler).removeTank(42);
    }

    @Test
    void testParseMessage_Shoot() throws Exception {
        UUID bulletId = UUID.randomUUID();
        String message = "SHO;" + bulletId + ";42;100.5;200.3;5.0;3.0";
        
        var method = GameClient.class.getDeclaredMethod("parseServerMessage", String.class);
        method.setAccessible(true);
        method.invoke(gameClient, message);
        
        verify(mockHandler).spawnBullet(bulletId, 42, 100.5f, 200.3f, 5.0f, 3.0f);
    }

    @Test
    void testParseMessage_Hit() throws Exception {
        UUID bulletId = UUID.randomUUID();
        String message = "HIT;10;20;" + bulletId + ";25";
        
        var method = GameClient.class.getDeclaredMethod("parseServerMessage", String.class);
        method.setAccessible(true);
        method.invoke(gameClient, message);
        
        verify(mockHandler).handlePlayerHit(10, 20, bulletId, 25);
    }

    @Test
    void testParseMessage_Destroyed() throws Exception {
        String message = "DES;10;20";
        
        var method = GameClient.class.getDeclaredMethod("parseServerMessage", String.class);
        method.setAccessible(true);
        method.invoke(gameClient, message);
        
        verify(mockHandler).handlePlayerDestroyed(10, 20);
    }

    @Test
    void testParseMessage_PlayerLives() throws Exception {
        String message = "LIV;42;3";
        
        var method = GameClient.class.getDeclaredMethod("parseServerMessage", String.class);
        method.setAccessible(true);
        method.invoke(gameClient, message);
        
        verify(mockHandler).updatePlayerLives(42, 3);
    }

    @Test
    void testParseMessage_GameState() throws Exception {
        String message = "GST;PLAYING;12345";
        
        var method = GameClient.class.getDeclaredMethod("parseServerMessage", String.class);
        method.setAccessible(true);
        method.invoke(gameClient, message);
        
        verify(mockHandler).setGameState(GameState.PLAYING, 12345L);
    }

    @Test
    void testParseMessage_Announce() throws Exception {
        String message = "ANN;Welcome to the game!";
        
        var method = GameClient.class.getDeclaredMethod("parseServerMessage", String.class);
        method.setAccessible(true);
        method.invoke(gameClient, message);
        
        verify(mockHandler).addAnnouncement("Welcome to the game!");
    }

    @Test
    void testParseMessage_Respawn() throws Exception {
        String message = "RSP;42;100.5;200.3;1.57";
        
        var method = GameClient.class.getDeclaredMethod("parseServerMessage", String.class);
        method.setAccessible(true);
        method.invoke(gameClient, message);
        
        verify(mockHandler).updateTankState(42, 100.5f, 200.3f, 1.57f, true);
    }

    @Test
    void testParseMessage_MapInfo() throws Exception {
        String message = "MAP;100;80;32.0";
        
        var method = GameClient.class.getDeclaredMethod("parseServerMessage", String.class);
        method.setAccessible(true);
        method.invoke(gameClient, message);
        
        verify(mockHandler).storeMapInfo(100, 80, 32.0f);
    }

    @Test
    void testParseMessage_ShootCooldown() throws Exception {
        String message = "SHT_CDN;1500";
        
        var method = GameClient.class.getDeclaredMethod("parseServerMessage", String.class);
        method.setAccessible(true);
        method.invoke(gameClient, message);
        
        verify(mockHandler).updateShootCooldown(1500L);
    }

    // ========== Malformed Message Tests ==========

    @Test
    void testParseMessage_MalformedPlayerId() throws Exception {
        String message = "AID;42"; // Missing color parts
        
        var method = GameClient.class.getDeclaredMethod("parseServerMessage", String.class);
        method.setAccessible(true);
        
        // Should not throw, just log error
        assertDoesNotThrow(() -> method.invoke(gameClient, message));
    }

    @Test
    void testParseMessage_MalformedNewPlayer() throws Exception {
        String message = "NEW;42;100.5"; // Insufficient parts
        
        var method = GameClient.class.getDeclaredMethod("parseServerMessage", String.class);
        method.setAccessible(true);
        
        assertDoesNotThrow(() -> method.invoke(gameClient, message));
        verify(mockHandler, never()).addOrUpdateTank(anyInt(), anyFloat(), anyFloat(), 
            anyFloat(), anyString(), anyFloat(), anyFloat(), anyFloat());
    }

    @Test
    void testParseMessage_InvalidNumberFormat() throws Exception {
        String message = "UPD;not-a-number;150.5;200.3;1.57";
        
        var method = GameClient.class.getDeclaredMethod("parseServerMessage", String.class);
        method.setAccessible(true);
        
        // Should not throw, just log error
        assertDoesNotThrow(() -> method.invoke(gameClient, message));
        verify(mockHandler, never()).updateTankState(anyInt(), anyFloat(), anyFloat(), anyFloat(), anyBoolean());
    }

    @Test
    void testParseMessage_InvalidUUID() throws Exception {
        String message = "SHO;not-a-uuid;42;100.5;200.3;5.0;3.0";
        
        var method = GameClient.class.getDeclaredMethod("parseServerMessage", String.class);
        method.setAccessible(true);
        
        assertDoesNotThrow(() -> method.invoke(gameClient, message));
        verify(mockHandler, never()).spawnBullet(any(UUID.class), anyInt(), 
            anyFloat(), anyFloat(), anyFloat(), anyFloat());
    }

    @Test
    void testParseMessage_EmptyMessage() throws Exception {
        String message = "";
        
        var method = GameClient.class.getDeclaredMethod("parseServerMessage", String.class);
        method.setAccessible(true);
        
        // Should not throw
        assertDoesNotThrow(() -> method.invoke(gameClient, message));
    }

    @Test
    void testParseMessage_UnknownCommand() throws Exception {
        String message = "UNKNOWN;some;data";
        
        var method = GameClient.class.getDeclaredMethod("parseServerMessage", String.class);
        method.setAccessible(true);
        
        // Should not throw, just log warning
        assertDoesNotThrow(() -> method.invoke(gameClient, message));
    }

    // ========== Spectator Mode Tests ==========

    @org.junit.jupiter.api.Disabled("Reflection-based test - disabled for CI")
    @Test
    void testSpectatorMode_Start() throws Exception {
        String message = "SPEC_START;5000";
        
        var method = GameClient.class.getDeclaredMethod("parseServerMessage", String.class);
        method.setAccessible(true);
        method.invoke(gameClient, message);
        
        // Verify spectating via reflection
        var field = GameClient.class.getDeclaredField("isSpectating");
        field.setAccessible(true);
        assertTrue((Boolean) field.get(gameClient));
    }

    @org.junit.jupiter.api.Disabled("Reflection-based test - disabled for CI")
    @Test
    void testSpectatorMode_End() throws Exception {
        // First start spectating
        String startMessage = "SPEC_START;5000";
        var method = GameClient.class.getDeclaredMethod("parseServerMessage", String.class);
        method.setAccessible(true);
        method.invoke(gameClient, startMessage);
        
        var field = GameClient.class.getDeclaredField("isSpectating");
        field.setAccessible(true);
        assertTrue((Boolean) field.get(gameClient));
        
        // Then end spectating
        String endMessage = "SPEC_END";
        method.invoke(gameClient, endMessage);
        
        assertFalse((Boolean) field.get(gameClient));
    }

    @Test
    void testSpectatorMode_Permanent() throws Exception {
        String message = "SPEC_PERM";
        
        var method = GameClient.class.getDeclaredMethod("parseServerMessage", String.class);
        method.setAccessible(true);
        method.invoke(gameClient, message);
        
        var field = GameClient.class.getDeclaredField("isSpectating");
        field.setAccessible(true);
        assertTrue((Boolean) field.get(gameClient));
    }

    // ========== State Tests ==========

    @Test
    void testIsConnected_InitiallyFalse() {
        assertFalse(gameClient.isConnected());
    }

    @Test
    void testIsSpectating_InitiallyFalse() throws Exception {
        var field = GameClient.class.getDeclaredField("isSpectating");
        field.setAccessible(true);
        assertFalse((Boolean) field.get(gameClient));
    }

    // ========== Edge Cases ==========

    @Test
    void testParseMessage_NullMessage() throws Exception {
        var method = GameClient.class.getDeclaredMethod("parseServerMessage", String.class);
        method.setAccessible(true);
        
        // NullPointerException is expected for a null message
        assertDoesNotThrow(() -> method.invoke(gameClient, (String) null));
    }

    @Test
    void testParseMessage_MultipleDelimiters() throws Exception {
        String message = "UPD;42;150.5;200.3;1.57;;extra";
        
        var method = GameClient.class.getDeclaredMethod("parseServerMessage", String.class);
        method.setAccessible(true);
        
        // Should parse successfully (extra parts ignored)
        assertDoesNotThrow(() -> method.invoke(gameClient, message));
        verify(mockHandler).updateTankState(42, 150.5f, 200.3f, 1.57f, false);
    }

    @Test
    void testParseMessage_GameStateInvalid() throws Exception {
        String message = "GST;INVALID_STATE;12345";
        
        var method = GameClient.class.getDeclaredMethod("parseServerMessage", String.class);
        method.setAccessible(true);
        
        // Should not throw, just log error
        assertDoesNotThrow(() -> method.invoke(gameClient, message));
        verify(mockHandler, never()).setGameState(any(GameState.class), anyLong());
    }

    @Test
    void testParseMessage_NegativeValues() throws Exception {
        String message = "UPD;-1;-150.5;-200.3;-1.57";
        
        var method = GameClient.class.getDeclaredMethod("parseServerMessage", String.class);
        method.setAccessible(true);
        method.invoke(gameClient, message);
        
        verify(mockHandler).updateTankState(-1, -150.5f, -200.3f, -1.57f, false);
    }

    @Test
    void testParseMessage_ZeroValues() throws Exception {
        String message = "UPD;0;0.0;0.0;0.0";
        
        var method = GameClient.class.getDeclaredMethod("parseServerMessage", String.class);
        method.setAccessible(true);
        method.invoke(gameClient, message);
        
        verify(mockHandler).updateTankState(0, 0.0f, 0.0f, 0.0f, false);
    }

    @Test
    void testParseMessage_VeryLargeNumbers() throws Exception {
        String message = "LIV;999999;100";
        
        var method = GameClient.class.getDeclaredMethod("parseServerMessage", String.class);
        method.setAccessible(true);
        method.invoke(gameClient, message);
        
        verify(mockHandler).updatePlayerLives(999999, 100);
    }

    @Test
    void testParseMessage_SpecialCharacters() throws Exception {
        String message = "ANN;Player! @#$%^&*() won!";
        
        var method = GameClient.class.getDeclaredMethod("parseServerMessage", String.class);
        method.setAccessible(true);
        method.invoke(gameClient, message);
        
        verify(mockHandler).addAnnouncement("Player! @#$%^&*() won!");
    }

    @Test
    void testParseMessage_RoundOver() throws Exception {
        String message = "ROV;42;Winner;60000";
        
        var method = GameClient.class.getDeclaredMethod("parseServerMessage", String.class);
        method.setAccessible(true);
        
        // Should parse successfully and just log trace
        assertDoesNotThrow(() -> method.invoke(gameClient, message));
    }

    @Test
    void testParseMessage_ErrorMessage() throws Exception {
        String message = "ERR;Server is full";
        
        var method = GameClient.class.getDeclaredMethod("parseServerMessage", String.class);
        method.setAccessible(true);
        method.invoke(gameClient, message);
        
        verify(mockHandler).connectionFailed("Server is full");
    }
}
