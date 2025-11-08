package org.chrisgruber.nettank.client.game;

import org.chrisgruber.nettank.client.game.entities.ClientBullet;
import org.chrisgruber.nettank.client.game.entities.ClientTank;
import org.chrisgruber.nettank.common.util.GameState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for TankBattleGame logic (non-rendering aspects)
 * Note: These tests require LWJGL/OpenGL context - disabled for now
 */
@org.junit.jupiter.api.Disabled("Requires LWJGL/OpenGL context - enable for local testing only")
class TankBattleGameTest {

    private TankBattleGame game;
    
    // Note: We can't fully initialize the game without LWJGL context,
    // so we'll test individual methods that don't require rendering

    @BeforeEach
    void setUp() {
        // Create a game instance (constructor doesn't initialize rendering)
        // Tests will use reflection to test internal logic
    }

    // ========== NetworkCallbackHandler Implementation Tests ==========

    @Test
    void testSetLocalPlayerId() throws Exception {
        game = createGameInstance();
        
        game.setLocalPlayerId(42);
        
        Field field = TankBattleGame.class.getDeclaredField("localPlayerId");
        field.setAccessible(true);
        int playerId = (int) field.get(game);
        
        assertEquals(42, playerId);
    }

    @Test
    void testAddOrUpdateTank_NewTank() throws Exception {
        game = createGameInstance();
        
        game.addOrUpdateTank(1, 100.0f, 200.0f, 1.57f, "Player1", 1.0f, 0.5f, 0.0f);
        
        Field tanksField = TankBattleGame.class.getDeclaredField("tanks");
        tanksField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<Integer, ClientTank> tanks = (Map<Integer, ClientTank>) tanksField.get(game);
        
        assertTrue(tanks.containsKey(1));
        ClientTank tank = tanks.get(1);
        assertNotNull(tank);
        assertEquals("Player1", tank.getName());
    }

    @Test
    void testRemoveTank() throws Exception {
        game = createGameInstance();
        
        // Add a tank first
        game.addOrUpdateTank(1, 100.0f, 200.0f, 1.57f, "Player1", 1.0f, 0.5f, 0.0f);
        
        // Remove it
        game.removeTank(1);
        
        Field tanksField = TankBattleGame.class.getDeclaredField("tanks");
        tanksField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<Integer, ClientTank> tanks = (Map<Integer, ClientTank>) tanksField.get(game);
        
        assertFalse(tanks.containsKey(1));
    }

    @Test
    void testUpdateTankState() throws Exception {
        game = createGameInstance();
        
        // Add a tank first
        game.addOrUpdateTank(1, 100.0f, 200.0f, 1.57f, "Player1", 1.0f, 0.5f, 0.0f);
        
        // Update its state
        game.updateTankState(1, 150.0f, 250.0f, 3.14f, false);
        
        Field tanksField = TankBattleGame.class.getDeclaredField("tanks");
        tanksField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<Integer, ClientTank> tanks = (Map<Integer, ClientTank>) tanksField.get(game);
        
        ClientTank tank = tanks.get(1);
        assertNotNull(tank);
        assertEquals(150.0f, tank.getPosition().x(), 0.001f);
        assertEquals(250.0f, tank.getPosition().y(), 0.001f);
        assertEquals(3.14f, tank.getRotation(), 0.001f);
    }

    @Test
    void testSpawnBullet() throws Exception {
        game = createGameInstance();
        
        UUID bulletId = UUID.randomUUID();
        game.spawnBullet(bulletId, 1, 100.0f, 200.0f, 5.0f, 3.0f);
        
        Field bulletsField = TankBattleGame.class.getDeclaredField("bullets");
        bulletsField.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<ClientBullet> bullets = (List<ClientBullet>) bulletsField.get(game);
        
        assertEquals(1, bullets.size());
        ClientBullet bullet = bullets.getFirst();
        assertEquals(bulletId, bullet.getId());
    }

    @Test
    void testHandlePlayerHit() throws Exception {
        game = createGameInstance();
        
        // Add a bullet first
        UUID bulletId = UUID.randomUUID();
        game.spawnBullet(bulletId, 1, 100.0f, 200.0f, 5.0f, 3.0f);
        
        // Handle hit
        game.handlePlayerHit(2, 1, bulletId, 25);
        
        Field bulletsField = TankBattleGame.class.getDeclaredField("bullets");
        bulletsField.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<ClientBullet> bullets = (List<ClientBullet>) bulletsField.get(game);
        
        // Bullet should be removed after hit
        assertTrue(bullets.isEmpty());
    }

    @Test
    void testHandlePlayerDestroyed() throws Exception {
        game = createGameInstance();
        
        // Set local player ID
        game.setLocalPlayerId(1);
        
        // Player 1 destroyed player 2
        game.handlePlayerDestroyed(2, 1);
        
        Field killsField = TankBattleGame.class.getDeclaredField("playerKills");
        killsField.setAccessible(true);
        int kills = (int) killsField.get(game);
        
        assertEquals(1, kills);
    }

    @Test
    void testHandlePlayerDestroyed_NotLocalPlayer() throws Exception {
        game = createGameInstance();
        
        // Set local player ID
        game.setLocalPlayerId(1);
        
        // Player 3 destroyed player 2 (not local player)
        game.handlePlayerDestroyed(2, 3);
        
        Field killsField = TankBattleGame.class.getDeclaredField("playerKills");
        killsField.setAccessible(true);
        int kills = (int) killsField.get(game);
        
        assertEquals(0, kills);
    }

    @Test
    void testUpdatePlayerLives() throws Exception {
        game = createGameInstance();
        
        // Add a tank first
        game.addOrUpdateTank(1, 100.0f, 200.0f, 1.57f, "Player1", 1.0f, 0.5f, 0.0f);
        
        // Update lives
        game.updatePlayerLives(1, 3);
        
        Field tanksField = TankBattleGame.class.getDeclaredField("tanks");
        tanksField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<Integer, ClientTank> tanks = (Map<Integer, ClientTank>) tanksField.get(game);
        
        ClientTank tank = tanks.get(1);
        assertNotNull(tank);
        // Lives are tracked internally, test that tank still exists
    }

    @Test
    void testSetGameState() throws Exception {
        game = createGameInstance();
        
        game.setGameState(GameState.PLAYING, 1000L);
        
        Field stateField = TankBattleGame.class.getDeclaredField("currentGameState");
        stateField.setAccessible(true);
        GameState state = (GameState) stateField.get(game);
        
        assertEquals(GameState.PLAYING, state);
        
        Field timeField = TankBattleGame.class.getDeclaredField("roundStartTimeMillis");
        timeField.setAccessible(true);
        long time = (long) timeField.get(game);
        
        assertTrue(time > 0);
    }

    @Test
    void testAddAnnouncement() throws Exception {
        game = createGameInstance();
        
        game.addAnnouncement("Test announcement");
        
        Field announcementsField = TankBattleGame.class.getDeclaredField("announcements");
        announcementsField.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<String> announcements = (List<String>) announcementsField.get(game);
        
        assertEquals(1, announcements.size());
        assertEquals("Test announcement", announcements.getFirst());
    }

    @Test
    void testStoreMapInfo() throws Exception {
        game = createGameInstance();
        
        game.storeMapInfo(100, 80, 32.0f);
        
        Field widthField = TankBattleGame.class.getDeclaredField("mapWidthTiles");
        widthField.setAccessible(true);
        int width = (int) widthField.get(game);
        
        Field heightField = TankBattleGame.class.getDeclaredField("mapHeightTiles");
        heightField.setAccessible(true);
        int height = (int) heightField.get(game);
        
        Field tileSizeField = TankBattleGame.class.getDeclaredField("mapTileSize");
        tileSizeField.setAccessible(true);
        float tileSize = (float) tileSizeField.get(game);
        
        assertEquals(100, width);
        assertEquals(80, height);
        assertEquals(32.0f, tileSize, 0.001f);
    }

    @Test
    void testUpdateShootCooldown() throws Exception {
        game = createGameInstance();
        
        // Set local player ID and add local tank
        game.setLocalPlayerId(1);
        game.addOrUpdateTank(1, 100.0f, 200.0f, 1.57f, "Player1", 1.0f, 0.5f, 0.0f);
        
        // Update cooldown
        game.updateShootCooldown(1500L);
        
        Field localTankField = TankBattleGame.class.getDeclaredField("localTank");
        localTankField.setAccessible(true);
        ClientTank localTank = (ClientTank) localTankField.get(game);
        
        assertNotNull(localTank);
        assertTrue(localTank.getCooldownRemaining() > 0);
    }

    @Test
    void testConnectionFailed() throws Exception {
        game = createGameInstance();
        
        // Should not throw exception
        assertDoesNotThrow(() -> game.connectionFailed("Connection failed"));
    }

    // ========== Helper Methods ==========

    private TankBattleGame createGameInstance() throws Exception {
        // Create an instance without initializing LWJGL
        return new TankBattleGame("127.0.0.1", 5555, "TestPlayer", "Test Game", 800, 600);
    }

    // ========== Edge Cases ==========

    @Test
    void testUpdateTankState_NonExistentTank() throws Exception {
        game = createGameInstance();
        
        // Try to update a tank that doesn't exist
        assertDoesNotThrow(() -> game.updateTankState(999, 150.0f, 250.0f, 3.14f, false));
    }

    @Test
    void testRemoveTank_NonExistentTank() throws Exception {
        game = createGameInstance();
        
        // Try to remove a tank that doesn't exist
        assertDoesNotThrow(() -> game.removeTank(999));
    }

    @Test
    void testUpdatePlayerLives_NonExistentTank() throws Exception {
        game = createGameInstance();
        
        // Try to update lives of a tank that doesn't exist
        assertDoesNotThrow(() -> game.updatePlayerLives(999, 3));
    }

    @Test
    void testHandlePlayerHit_NonExistentBullet() throws Exception {
        game = createGameInstance();
        
        UUID bulletId = UUID.randomUUID();
        
        // Try to handle hit with a bullet that doesn't exist
        assertDoesNotThrow(() -> game.handlePlayerHit(2, 1, bulletId, 25));
    }

    @Test
    void testAddOrUpdateTank_NegativeCoordinates() throws Exception {
        game = createGameInstance();
        
        game.addOrUpdateTank(1, -100.0f, -200.0f, -1.57f, "Player1", 1.0f, 0.5f, 0.0f);
        
        Field tanksField = TankBattleGame.class.getDeclaredField("tanks");
        tanksField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<Integer, ClientTank> tanks = (Map<Integer, ClientTank>) tanksField.get(game);
        
        ClientTank tank = tanks.get(1);
        assertNotNull(tank);
        assertEquals(-100.0f, tank.getPosition().x(), 0.001f);
        assertEquals(-200.0f, tank.getPosition().y(), 0.001f);
    }

    @Test
    void testAddOrUpdateTank_ZeroValues() throws Exception {
        game = createGameInstance();
        
        game.addOrUpdateTank(0, 0.0f, 0.0f, 0.0f, "Player0", 0.0f, 0.0f, 0.0f);
        
        Field tanksField = TankBattleGame.class.getDeclaredField("tanks");
        tanksField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<Integer, ClientTank> tanks = (Map<Integer, ClientTank>) tanksField.get(game);
        
        ClientTank tank = tanks.get(0);
        assertNotNull(tank);
    }

    @Test
    void testAddOrUpdateTank_EmptyName() throws Exception {
        game = createGameInstance();
        
        game.addOrUpdateTank(1, 100.0f, 200.0f, 1.57f, "", 1.0f, 0.5f, 0.0f);
        
        Field tanksField = TankBattleGame.class.getDeclaredField("tanks");
        tanksField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<Integer, ClientTank> tanks = (Map<Integer, ClientTank>) tanksField.get(game);
        
        ClientTank tank = tanks.get(1);
        assertNotNull(tank);
        assertEquals("", tank.getName());
    }

    @Test
    void testSpawnBullet_MultipleWithSameOwner() throws Exception {
        game = createGameInstance();
        
        UUID bulletId1 = UUID.randomUUID();
        UUID bulletId2 = UUID.randomUUID();
        
        game.spawnBullet(bulletId1, 1, 100.0f, 200.0f, 5.0f, 3.0f);
        game.spawnBullet(bulletId2, 1, 110.0f, 210.0f, 5.0f, 3.0f);
        
        Field bulletsField = TankBattleGame.class.getDeclaredField("bullets");
        bulletsField.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<ClientBullet> bullets = (List<ClientBullet>) bulletsField.get(game);
        
        assertEquals(2, bullets.size());
    }

    @Test
    void testHandlePlayerDestroyed_MultipleKills() throws Exception {
        game = createGameInstance();
        
        // Set local player ID
        game.setLocalPlayerId(1);
        
        // Local player gets multiple kills
        game.handlePlayerDestroyed(2, 1);
        game.handlePlayerDestroyed(3, 1);
        game.handlePlayerDestroyed(4, 1);
        
        Field killsField = TankBattleGame.class.getDeclaredField("playerKills");
        killsField.setAccessible(true);
        int kills = (int) killsField.get(game);
        
        assertEquals(3, kills);
    }

    @Test
    void testAddAnnouncement_Multiple() throws Exception {
        game = createGameInstance();
        
        game.addAnnouncement("Announcement 1");
        game.addAnnouncement("Announcement 2");
        game.addAnnouncement("Announcement 3");
        
        Field announcementsField = TankBattleGame.class.getDeclaredField("announcements");
        announcementsField.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<String> announcements = (List<String>) announcementsField.get(game);
        
        assertEquals(3, announcements.size());
        assertEquals("Announcement 1", announcements.get(0));
        assertEquals("Announcement 2", announcements.get(1));
        assertEquals("Announcement 3", announcements.get(2));
    }

    @Test
    void testSetGameState_DifferentStates() throws Exception {
        game = createGameInstance();
        
        game.setGameState(GameState.CONNECTING, 0L);
        Field stateField = TankBattleGame.class.getDeclaredField("currentGameState");
        stateField.setAccessible(true);
        assertEquals(GameState.CONNECTING, stateField.get(game));
        
        game.setGameState(GameState.WAITING, 0L);
        assertEquals(GameState.WAITING, stateField.get(game));
        
        game.setGameState(GameState.PLAYING, 1000L);
        assertEquals(GameState.PLAYING, stateField.get(game));
        
        game.setGameState(GameState.ROUND_OVER, 0L);
        assertEquals(GameState.ROUND_OVER, stateField.get(game));
    }
}
