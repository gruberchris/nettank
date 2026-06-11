package org.chrisgruber.nettank.server;

import org.chrisgruber.nettank.common.entities.TankData;
import org.chrisgruber.nettank.common.entities.TankStats;
import org.chrisgruber.nettank.common.network.NetworkProtocol;
import org.chrisgruber.nettank.common.util.GameState;
import org.chrisgruber.nettank.server.gamemode.FreeForAll;
import org.chrisgruber.nettank.server.state.ServerContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.net.Socket;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@Timeout(value = 10, unit = TimeUnit.SECONDS)
class GameServerTest {

    private GameServer gameServer;
    private static final int TEST_PORT = 5556;
    private static final int TEST_NETWORK_HZ = 30;
    private static final int TEST_MAP_WIDTH = 50;
    private static final int TEST_MAP_HEIGHT = 50;

    @Mock
    private ClientHandler mockClientHandler;

    @Mock
    private Socket mockSocket;

    private AutoCloseable mocks;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        gameServer = new GameServer(TEST_PORT, TEST_NETWORK_HZ, TEST_MAP_WIDTH, TEST_MAP_HEIGHT);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (gameServer != null) {
            gameServer.stop();
        }
        if (mocks != null) {
            mocks.close();
        }
    }

    @Test
    void testConstructorInitializesCorrectly() {
        assertNotNull(gameServer);
    }

    @Test
    void testConstructorSetsMapDimensions() throws Exception {
        var contextField = GameServer.class.getDeclaredField("serverContext");
        contextField.setAccessible(true);
        ServerContext context = (ServerContext) contextField.get(gameServer);

        assertNotNull(context.gameMapData);
        assertEquals(TEST_MAP_WIDTH, context.gameMapData.getWidthTiles());
        assertEquals(TEST_MAP_HEIGHT, context.gameMapData.getHeightTiles());
    }

    @Test
    void testConstructorInitializesGameMode() throws Exception {
        var contextField = GameServer.class.getDeclaredField("serverContext");
        contextField.setAccessible(true);
        ServerContext context = (ServerContext) contextField.get(gameServer);

        assertNotNull(context.gameMode);
        assertInstanceOf(FreeForAll.class, context.gameMode);
    }

    @Test
    void testRegisterPlayerSuccessfully() throws Exception {
        when(mockClientHandler.getSocket()).thenReturn(mockSocket);
        when(mockSocket.getInetAddress()).thenReturn(java.net.InetAddress.getLocalHost());
        doNothing().when(mockClientHandler).sendMessage(anyString());
        doNothing().when(mockClientHandler).setPlayerInfo(anyInt(), anyString());

        var contextField = GameServer.class.getDeclaredField("serverContext");
        contextField.setAccessible(true);
        ServerContext context = (ServerContext) contextField.get(gameServer);

        gameServer.registerPlayer(mockClientHandler, "TestPlayer");

        assertEquals(1, context.clients.size());
        assertEquals(1, context.tanks.size());
        verify(mockClientHandler, atLeastOnce()).sendMessage(anyString());
    }

    @Test
    void testRegisterPlayerWhenServerFull() throws Exception {
        when(mockClientHandler.getSocket()).thenReturn(mockSocket);
        when(mockSocket.getInetAddress()).thenReturn(java.net.InetAddress.getLocalHost());
        doNothing().when(mockClientHandler).sendMessage(anyString());
        doNothing().when(mockClientHandler).closeConnection(anyString());

        var contextField = GameServer.class.getDeclaredField("serverContext");
        contextField.setAccessible(true);
        ServerContext context = (ServerContext) contextField.get(gameServer);

        int maxPlayers = context.gameMode.getMaxAllowedPlayers();

        // Fill server to max capacity
        for (int i = 0; i < maxPlayers; i++) {
            ClientHandler handler = mock(ClientHandler.class);
            when(handler.getSocket()).thenReturn(mock(Socket.class));
            when(handler.getSocket().getInetAddress()).thenReturn(java.net.InetAddress.getLocalHost());
            doNothing().when(handler).sendMessage(anyString());
            doNothing().when(handler).setPlayerInfo(anyInt(), anyString());
            gameServer.registerPlayer(handler, "Player" + i);
        }

        // Try to add one more player
        gameServer.registerPlayer(mockClientHandler, "OverflowPlayer");

        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(mockClientHandler).sendMessage(messageCaptor.capture());
        assertTrue(messageCaptor.getValue().contains(NetworkProtocol.ERROR_MSG));
        verify(mockClientHandler).closeConnection("Server full");
    }

    @Test
    void testRemovePlayer() throws Exception {
        when(mockClientHandler.getSocket()).thenReturn(mockSocket);
        when(mockSocket.getInetAddress()).thenReturn(java.net.InetAddress.getLocalHost());
        doNothing().when(mockClientHandler).sendMessage(anyString());
        doNothing().when(mockClientHandler).setPlayerInfo(anyInt(), anyString());

        var contextField = GameServer.class.getDeclaredField("serverContext");
        contextField.setAccessible(true);
        ServerContext context = (ServerContext) contextField.get(gameServer);

        gameServer.registerPlayer(mockClientHandler, "TestPlayer");
        int playerId = 0;

        assertEquals(1, context.clients.size());
        gameServer.removePlayer(playerId);

        assertEquals(0, context.clients.size());
        assertEquals(0, context.tanks.size());
    }

    @Test
    void testHandlePlayerMovementInput() throws Exception {
        when(mockClientHandler.getSocket()).thenReturn(mockSocket);
        when(mockSocket.getInetAddress()).thenReturn(java.net.InetAddress.getLocalHost());
        doNothing().when(mockClientHandler).sendMessage(anyString());
        doNothing().when(mockClientHandler).setPlayerInfo(anyInt(), anyString());

        var contextField = GameServer.class.getDeclaredField("serverContext");
        contextField.setAccessible(true);
        ServerContext context = (ServerContext) contextField.get(gameServer);
        context.currentGameState = GameState.PLAYING;

        gameServer.registerPlayer(mockClientHandler, "TestPlayer");
        int playerId = 0;

        gameServer.handlePlayerMovementInput(playerId, true, false, false, false);

        TankData tank = context.tanks.get(playerId);
        assertNotNull(tank);
        assertTrue(tank.isMovingForward());
        assertFalse(tank.isMovingBackward());
    }

    @Test
    void testHandlePlayerMovementInputWhenNotPlaying() throws Exception {
        when(mockClientHandler.getSocket()).thenReturn(mockSocket);
        when(mockSocket.getInetAddress()).thenReturn(java.net.InetAddress.getLocalHost());
        doNothing().when(mockClientHandler).sendMessage(anyString());
        doNothing().when(mockClientHandler).setPlayerInfo(anyInt(), anyString());

        var contextField = GameServer.class.getDeclaredField("serverContext");
        contextField.setAccessible(true);
        ServerContext context = (ServerContext) contextField.get(gameServer);
        context.currentGameState = GameState.WAITING;

        gameServer.registerPlayer(mockClientHandler, "TestPlayer");
        int playerId = 0;

        gameServer.handlePlayerMovementInput(playerId, true, false, false, false);

        TankData tank = context.tanks.get(playerId);
        assertNotNull(tank);
        assertFalse(tank.isMovingForward());
    }

    @Test
    void testHandlePlayerShootMainWeaponInput() throws Exception {
        when(mockClientHandler.getSocket()).thenReturn(mockSocket);
        when(mockSocket.getInetAddress()).thenReturn(java.net.InetAddress.getLocalHost());
        doNothing().when(mockClientHandler).sendMessage(anyString());
        doNothing().when(mockClientHandler).setPlayerInfo(anyInt(), anyString());

        var contextField = GameServer.class.getDeclaredField("serverContext");
        contextField.setAccessible(true);
        ServerContext context = (ServerContext) contextField.get(gameServer);
        context.currentGameState = GameState.PLAYING;

        gameServer.registerPlayer(mockClientHandler, "TestPlayer");
        int playerId = 0;

        int initialBulletCount = context.bullets.size();
        gameServer.handlePlayerShootMainWeaponInput(playerId);

        assertEquals(initialBulletCount + 1, context.bullets.size());
    }

    @Test
    void testHandlePlayerShootMainWeaponInputDuringCooldown() throws Exception {
        when(mockClientHandler.getSocket()).thenReturn(mockSocket);
        when(mockSocket.getInetAddress()).thenReturn(java.net.InetAddress.getLocalHost());
        doNothing().when(mockClientHandler).sendMessage(anyString());
        doNothing().when(mockClientHandler).setPlayerInfo(anyInt(), anyString());

        var contextField = GameServer.class.getDeclaredField("serverContext");
        contextField.setAccessible(true);
        ServerContext context = (ServerContext) contextField.get(gameServer);
        context.currentGameState = GameState.PLAYING;

        gameServer.registerPlayer(mockClientHandler, "TestPlayer");
        int playerId = 0;

        gameServer.handlePlayerShootMainWeaponInput(playerId);
        int bulletCountAfterFirstShot = context.bullets.size();

        // Try to shoot again immediately (should be blocked by cooldown)
        gameServer.handlePlayerShootMainWeaponInput(playerId);

        assertEquals(bulletCountAfterFirstShot, context.bullets.size());
    }

    @Test
    void testHandlePlayerShootMainWeaponInputWhenNotPlaying() throws Exception {
        when(mockClientHandler.getSocket()).thenReturn(mockSocket);
        when(mockSocket.getInetAddress()).thenReturn(java.net.InetAddress.getLocalHost());
        doNothing().when(mockClientHandler).sendMessage(anyString());
        doNothing().when(mockClientHandler).setPlayerInfo(anyInt(), anyString());

        var contextField = GameServer.class.getDeclaredField("serverContext");
        contextField.setAccessible(true);
        ServerContext context = (ServerContext) contextField.get(gameServer);
        context.currentGameState = GameState.WAITING;

        gameServer.registerPlayer(mockClientHandler, "TestPlayer");
        int playerId = 0;

        int initialBulletCount = context.bullets.size();
        gameServer.handlePlayerShootMainWeaponInput(playerId);

        assertEquals(initialBulletCount, context.bullets.size());
    }

    @Test
    void testBroadcastExcludesSpecifiedPlayer() throws Exception {
        ClientHandler handler1 = mock(ClientHandler.class);
        ClientHandler handler2 = mock(ClientHandler.class);

        when(handler1.getSocket()).thenReturn(mock(Socket.class));
        when(handler2.getSocket()).thenReturn(mock(Socket.class));
        when(handler1.getSocket().getInetAddress()).thenReturn(java.net.InetAddress.getLocalHost());
        when(handler2.getSocket().getInetAddress()).thenReturn(java.net.InetAddress.getLocalHost());
        when(handler1.getPlayerId()).thenReturn(0);
        when(handler2.getPlayerId()).thenReturn(1);

        doNothing().when(handler1).sendMessage(anyString());
        doNothing().when(handler2).sendMessage(anyString());
        doNothing().when(handler1).setPlayerInfo(anyInt(), anyString());
        doNothing().when(handler2).setPlayerInfo(anyInt(), anyString());

        gameServer.registerPlayer(handler1, "Player1");
        gameServer.registerPlayer(handler2, "Player2");

        gameServer.broadcast("Test message", 0);

        verify(handler1, never()).sendMessage("Test message");
        verify(handler2, times(1)).sendMessage("Test message");
    }

    @Test
    void testBroadcastAnnouncement() throws Exception {
        when(mockClientHandler.getSocket()).thenReturn(mockSocket);
        when(mockSocket.getInetAddress()).thenReturn(java.net.InetAddress.getLocalHost());
        when(mockClientHandler.getPlayerId()).thenReturn(0);
        doNothing().when(mockClientHandler).sendMessage(anyString());
        doNothing().when(mockClientHandler).setPlayerInfo(anyInt(), anyString());

        gameServer.registerPlayer(mockClientHandler, "TestPlayer");

        gameServer.broadcastAnnouncement("Test Announcement", -1);

        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(mockClientHandler, atLeastOnce()).sendMessage(messageCaptor.capture());

        boolean foundAnnouncement = messageCaptor.getAllValues().stream()
            .anyMatch(msg -> msg.contains(NetworkProtocol.ANNOUNCE) && msg.contains("Test Announcement"));
        assertTrue(foundAnnouncement);
    }

    @Test
    void testTankConstants() {
        assertEquals(100.0f, GameServer.TANK_MOVE_SPEED);
        assertEquals(50.0f, GameServer.TANK_TURN_SPEED);
        assertEquals(350.0f, GameServer.BULLET_SPEED);
        assertEquals(2000L, GameServer.BULLET_LIFETIME_MS);
        assertEquals(2000L, GameServer.TANK_SHOOT_COOLDOWN_MS);
    }

    @Test
    void testEffectiveStatsMatchLegacyConstants() {
        TankData tank = new TankData(0, new org.joml.Vector2f(0, 0), new org.joml.Vector2f(0, 0), 0f,
                new org.joml.Vector3f(1, 1, 1), "TestPlayer");

        TankStats stats = gameServer.getEffectiveStats(tank);

        assertEquals(TankData.MAX_HIT_POINTS, stats.maxHitPoints());
        assertEquals(GameServer.TANK_MOVE_SPEED, stats.moveSpeed());
        assertEquals(GameServer.TANK_TURN_SPEED, stats.turnSpeed());
        assertEquals(0.7f, stats.backwardSpeedFactor());
        assertEquals(GameServer.BULLET_SPEED, stats.bulletSpeed());
        assertEquals(GameServer.BULLET_LIFETIME_MS, stats.bulletLifetimeMs());
        assertEquals(1, stats.bulletDamage());
        assertEquals(GameServer.TANK_SHOOT_COOLDOWN_MS, stats.shootCooldownMs());
    }

    @Test
    void testHandleHitUsesBulletDamage() throws Exception {
        when(mockClientHandler.getSocket()).thenReturn(mockSocket);
        when(mockSocket.getInetAddress()).thenReturn(java.net.InetAddress.getLocalHost());
        doNothing().when(mockClientHandler).sendMessage(anyString());
        doNothing().when(mockClientHandler).setPlayerInfo(anyInt(), anyString());

        var contextField = GameServer.class.getDeclaredField("serverContext");
        contextField.setAccessible(true);
        ServerContext context = (ServerContext) contextField.get(gameServer);
        context.currentGameState = GameState.PLAYING;

        gameServer.registerPlayer(mockClientHandler, "TestPlayer");
        TankData tank = context.tanks.get(0);
        tank.setPosition(new org.joml.Vector2f(800, 800));
        tank.setRotation(0f);
        int initialHitPoints = tank.getHitPoints();

        // Impact from directly behind (rear hits always crit, so full damage reaches HP)
        var bullet = new org.chrisgruber.nettank.common.entities.BulletData(
                java.util.UUID.randomUUID(), 99, new org.joml.Vector2f(800, 750), new org.joml.Vector2f(0, 0),
                0f, System.currentTimeMillis(), false, 2);

        gameServer.handleHit(tank, bullet);

        assertEquals(initialHitPoints - 2, tank.getHitPoints());
    }

    @Test
    void testComputeHitSideMapsQuadrantsAcrossRotations() {
        TankData tank = new TankData(0, new org.joml.Vector2f(0, 0), new org.joml.Vector2f(0, 0), 0f,
                new org.joml.Vector3f(1, 1, 1), "T");

        // Hull facing +Y (rotation 0): direction convention is (-sin r, cos r)
        tank.setRotation(0f);
        assertEquals(org.chrisgruber.nettank.common.entities.ArmorSide.FRONT, GameServer.computeHitSide(tank, new org.joml.Vector2f(0, 10)));
        assertEquals(org.chrisgruber.nettank.common.entities.ArmorSide.REAR, GameServer.computeHitSide(tank, new org.joml.Vector2f(0, -10)));
        assertEquals(org.chrisgruber.nettank.common.entities.ArmorSide.LEFT, GameServer.computeHitSide(tank, new org.joml.Vector2f(-10, 0)));
        assertEquals(org.chrisgruber.nettank.common.entities.ArmorSide.RIGHT, GameServer.computeHitSide(tank, new org.joml.Vector2f(10, 0)));

        // Hull rotated 90 degrees CCW (facing -X)
        tank.setRotation(90f);
        assertEquals(org.chrisgruber.nettank.common.entities.ArmorSide.FRONT, GameServer.computeHitSide(tank, new org.joml.Vector2f(-10, 0)));
        assertEquals(org.chrisgruber.nettank.common.entities.ArmorSide.REAR, GameServer.computeHitSide(tank, new org.joml.Vector2f(10, 0)));
        assertEquals(org.chrisgruber.nettank.common.entities.ArmorSide.LEFT, GameServer.computeHitSide(tank, new org.joml.Vector2f(0, -10)));
        assertEquals(org.chrisgruber.nettank.common.entities.ArmorSide.RIGHT, GameServer.computeHitSide(tank, new org.joml.Vector2f(0, 10)));

        // Hull rotated 180 degrees (facing -Y)
        tank.setRotation(180f);
        assertEquals(org.chrisgruber.nettank.common.entities.ArmorSide.FRONT, GameServer.computeHitSide(tank, new org.joml.Vector2f(0, -10)));
        assertEquals(org.chrisgruber.nettank.common.entities.ArmorSide.REAR, GameServer.computeHitSide(tank, new org.joml.Vector2f(0, 10)));
    }

    @Test
    void testNormalHitDepletesArmorBeforeHitPoints() throws Exception {
        when(mockClientHandler.getSocket()).thenReturn(mockSocket);
        when(mockSocket.getInetAddress()).thenReturn(java.net.InetAddress.getLocalHost());
        doNothing().when(mockClientHandler).sendMessage(anyString());
        doNothing().when(mockClientHandler).setPlayerInfo(anyInt(), anyString());

        var contextField = GameServer.class.getDeclaredField("serverContext");
        contextField.setAccessible(true);
        ServerContext context = (ServerContext) contextField.get(gameServer);
        context.currentGameState = GameState.PLAYING;

        gameServer.critRandom = new java.util.Random() {
            @Override public float nextFloat() { return 0.99f; } // never crit on non-rear sides
        };

        gameServer.registerPlayer(mockClientHandler, "TestPlayer");
        TankData tank = context.tanks.get(0);
        tank.setPosition(new org.joml.Vector2f(800, 800));
        tank.setRotation(0f);

        int initialHitPoints = tank.getHitPoints();
        var front = org.chrisgruber.nettank.common.entities.ArmorSide.FRONT;
        assertEquals(2, tank.getArmor(front)); // STANDARD front armor

        // Frontal hit for 1: absorbed entirely by armor
        var bullet1 = new org.chrisgruber.nettank.common.entities.BulletData(
                java.util.UUID.randomUUID(), 99, new org.joml.Vector2f(800, 850), new org.joml.Vector2f(0, 0),
                0f, System.currentTimeMillis(), false, 1);
        gameServer.handleHit(tank, bullet1);
        assertEquals(1, tank.getArmor(front));
        assertEquals(initialHitPoints, tank.getHitPoints());

        // Frontal hit for 3: 1 absorbed by remaining armor, 2 spill into HP
        var bullet2 = new org.chrisgruber.nettank.common.entities.BulletData(
                java.util.UUID.randomUUID(), 99, new org.joml.Vector2f(800, 850), new org.joml.Vector2f(0, 0),
                0f, System.currentTimeMillis(), false, 3);
        gameServer.handleHit(tank, bullet2);
        assertEquals(0, tank.getArmor(front));
        assertEquals(initialHitPoints - 2, tank.getHitPoints());
    }

    @Test
    void testCriticalHitDamagesArmorAndHitPoints() throws Exception {
        when(mockClientHandler.getSocket()).thenReturn(mockSocket);
        when(mockSocket.getInetAddress()).thenReturn(java.net.InetAddress.getLocalHost());
        doNothing().when(mockClientHandler).sendMessage(anyString());
        doNothing().when(mockClientHandler).setPlayerInfo(anyInt(), anyString());

        var contextField = GameServer.class.getDeclaredField("serverContext");
        contextField.setAccessible(true);
        ServerContext context = (ServerContext) contextField.get(gameServer);
        context.currentGameState = GameState.PLAYING;

        gameServer.critRandom = new java.util.Random() {
            @Override public float nextFloat() { return 0.05f; } // always crit (below 10%)
        };

        gameServer.registerPlayer(mockClientHandler, "TestPlayer");
        TankData tank = context.tanks.get(0);
        tank.setPosition(new org.joml.Vector2f(800, 800));
        tank.setRotation(0f);
        int initialHitPoints = tank.getHitPoints();

        // Frontal crit for 1: armor loses 1 AND hitpoints lose 1
        var bullet = new org.chrisgruber.nettank.common.entities.BulletData(
                java.util.UUID.randomUUID(), 99, new org.joml.Vector2f(800, 850), new org.joml.Vector2f(0, 0),
                0f, System.currentTimeMillis(), false, 1);
        gameServer.handleHit(tank, bullet);

        assertEquals(1, tank.getArmor(org.chrisgruber.nettank.common.entities.ArmorSide.FRONT));
        assertEquals(initialHitPoints - 1, tank.getHitPoints());
    }

    @Test
    void testArmorStatusSentOnlyToOwner() throws Exception {
        ClientHandler handler1 = mock(ClientHandler.class);
        ClientHandler handler2 = mock(ClientHandler.class);

        when(handler1.getSocket()).thenReturn(mock(Socket.class));
        when(handler2.getSocket()).thenReturn(mock(Socket.class));
        when(handler1.getSocket().getInetAddress()).thenReturn(java.net.InetAddress.getLocalHost());
        when(handler2.getSocket().getInetAddress()).thenReturn(java.net.InetAddress.getLocalHost());
        when(handler1.getPlayerId()).thenReturn(0);
        when(handler2.getPlayerId()).thenReturn(1);

        var contextField = GameServer.class.getDeclaredField("serverContext");
        contextField.setAccessible(true);
        ServerContext context = (ServerContext) contextField.get(gameServer);
        context.currentGameState = GameState.PLAYING;

        gameServer.registerPlayer(handler1, "Target");
        gameServer.registerPlayer(handler2, "Shooter");
        TankData target = context.tanks.get(0);
        target.setPosition(new org.joml.Vector2f(800, 800));
        target.setRotation(0f);

        // Both players got their own ARM at registration; only post-hit traffic matters here
        clearInvocations(handler1, handler2);

        var bullet = new org.chrisgruber.nettank.common.entities.BulletData(
                java.util.UUID.randomUUID(), 1, new org.joml.Vector2f(800, 850), new org.joml.Vector2f(0, 0),
                0f, System.currentTimeMillis(), false, 1);
        gameServer.handleHit(target, bullet);

        ArgumentCaptor<String> captor1 = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> captor2 = ArgumentCaptor.forClass(String.class);
        verify(handler1, atLeastOnce()).sendMessage(captor1.capture());
        verify(handler2, atLeastOnce()).sendMessage(captor2.capture());

        assertTrue(captor1.getAllValues().stream().anyMatch(m -> m.startsWith(NetworkProtocol.ARMOR_STATUS + ";")));
        assertTrue(captor2.getAllValues().stream().noneMatch(m -> m.startsWith(NetworkProtocol.ARMOR_STATUS + ";")));
        // Both clients see the public HIT with side and crit fields
        assertTrue(captor2.getAllValues().stream().anyMatch(m -> m.startsWith(NetworkProtocol.HIT + ";0;1;")));
    }

    @Test
    void testRespawnRestoresPerTypeArmor() throws Exception {
        when(mockClientHandler.getSocket()).thenReturn(mockSocket);
        when(mockSocket.getInetAddress()).thenReturn(java.net.InetAddress.getLocalHost());
        doNothing().when(mockClientHandler).sendMessage(anyString());
        doNothing().when(mockClientHandler).setPlayerInfo(anyInt(), anyString());

        var contextField = GameServer.class.getDeclaredField("serverContext");
        contextField.setAccessible(true);
        ServerContext context = (ServerContext) contextField.get(gameServer);

        gameServer.registerPlayer(mockClientHandler, "HeavyPlayer", org.chrisgruber.nettank.common.entities.TankType.HEAVY);
        TankData tank = context.tanks.get(0);

        tank.setArmor(org.chrisgruber.nettank.common.entities.ArmorSide.FRONT, 0);
        tank.setArmor(org.chrisgruber.nettank.common.entities.ArmorSide.REAR, 0);

        context.gameMode.handlePlayerRespawn(context, 0, tank);

        assertEquals(3, tank.getArmor(org.chrisgruber.nettank.common.entities.ArmorSide.FRONT));
        assertEquals(2, tank.getArmor(org.chrisgruber.nettank.common.entities.ArmorSide.LEFT));
        assertEquals(2, tank.getArmor(org.chrisgruber.nettank.common.entities.ArmorSide.RIGHT));
        assertEquals(1, tank.getArmor(org.chrisgruber.nettank.common.entities.ArmorSide.REAR));
        assertEquals(6, tank.getHitPoints());
    }

    @Test
    void testAmmoIsDecrementedAndEnforcedForFiniteAmmoMode() throws Exception {
        when(mockClientHandler.getSocket()).thenReturn(mockSocket);
        when(mockSocket.getInetAddress()).thenReturn(java.net.InetAddress.getLocalHost());
        doNothing().when(mockClientHandler).sendMessage(anyString());
        doNothing().when(mockClientHandler).setPlayerInfo(anyInt(), anyString());

        var contextField = GameServer.class.getDeclaredField("serverContext");
        contextField.setAccessible(true);
        ServerContext context = (ServerContext) contextField.get(gameServer);

        // Anonymous mode variant with 2 rounds of finite ammo
        context.gameMode = new FreeForAll() {
            { this.startingMainWeaponAmmoCount = 2; }
        };
        context.currentGameState = GameState.PLAYING;

        gameServer.registerPlayer(mockClientHandler, "TestPlayer");
        int playerId = 0;
        TankData tank = context.tanks.get(playerId);

        gameServer.handlePlayerShootMainWeaponInput(playerId);
        assertEquals(1, context.bullets.size());
        assertEquals(1, context.gameMode.getMainWeaponAmmoForPlayer(playerId));

        tank.setLastShotTime(0); // bypass cooldown
        gameServer.handlePlayerShootMainWeaponInput(playerId);
        assertEquals(2, context.bullets.size());
        assertEquals(0, context.gameMode.getMainWeaponAmmoForPlayer(playerId));

        tank.setLastShotTime(0);
        gameServer.handlePlayerShootMainWeaponInput(playerId);
        assertEquals(2, context.bullets.size()); // refused: out of ammo

        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(mockClientHandler, atLeastOnce()).sendMessage(messageCaptor.capture());
        assertTrue(messageCaptor.getAllValues().stream()
                .anyMatch(msg -> msg.startsWith(NetworkProtocol.AMMO_COUNT + ";" + playerId + ";")));
    }

    @Test
    void testTankOnMudMovesAtReducedSpeed() throws Exception {
        when(mockClientHandler.getSocket()).thenReturn(mockSocket);
        when(mockSocket.getInetAddress()).thenReturn(java.net.InetAddress.getLocalHost());
        doNothing().when(mockClientHandler).sendMessage(anyString());
        doNothing().when(mockClientHandler).setPlayerInfo(anyInt(), anyString());

        var contextField = GameServer.class.getDeclaredField("serverContext");
        contextField.setAccessible(true);
        ServerContext context = (ServerContext) contextField.get(gameServer);
        context.currentGameState = GameState.PLAYING;

        gameServer.registerPlayer(mockClientHandler, "TestPlayer");
        TankData tank = context.tanks.get(0);

        // MUD everywhere (0.6x speed), no overlays
        for (int y = 0; y < context.gameMapData.getHeightTiles(); y++) {
            for (int x = 0; x < context.gameMapData.getWidthTiles(); x++) {
                var tile = context.gameMapData.getTile(x, y);
                tile.setBaseType(org.chrisgruber.nettank.common.world.TerrainType.MUD);
                tile.setOverlayType(null);
            }
        }

        tank.setPosition(new org.joml.Vector2f(800, 800));
        gameServer.handlePlayerMovementInput(0, true, false, false, false);

        org.joml.Vector2f before = new org.joml.Vector2f(tank.getPosition());
        gameServer.updateGameLogic(1.0f); // one simulated second

        float distance = tank.getPosition().distance(before);
        assertEquals(100.0f * 0.6f, distance, 0.01f);
    }

    @Test
    void testTurnRateScalesWithTerrainSpeedModifier() throws Exception {
        when(mockClientHandler.getSocket()).thenReturn(mockSocket);
        when(mockSocket.getInetAddress()).thenReturn(java.net.InetAddress.getLocalHost());
        doNothing().when(mockClientHandler).sendMessage(anyString());
        doNothing().when(mockClientHandler).setPlayerInfo(anyInt(), anyString());

        var contextField = GameServer.class.getDeclaredField("serverContext");
        contextField.setAccessible(true);
        ServerContext context = (ServerContext) contextField.get(gameServer);
        context.currentGameState = GameState.PLAYING;

        gameServer.registerPlayer(mockClientHandler, "TestPlayer");
        TankData tank = context.tanks.get(0);

        tank.setPosition(new org.joml.Vector2f(800, 800));
        tank.setRotation(0f); // avoid 360-degree wraparound in the assertion
        var tile = context.gameMapData.getTileAt(800, 800);
        tile.setBaseType(org.chrisgruber.nettank.common.world.TerrainType.MUD);
        tile.setOverlayType(null);

        gameServer.handlePlayerMovementInput(0, false, false, true, false); // turn left only
        float rotationBefore = tank.getRotation();
        gameServer.updateGameLogic(1.0f);

        float turned = tank.getRotation() - rotationBefore;
        assertEquals(50.0f * 0.6f, turned, 0.01f); // turn modifier = max(0.6, 0.5)
    }

    @Test
    void testBurningTileDamagesOccupyingTankOncePerInterval() throws Exception {
        when(mockClientHandler.getSocket()).thenReturn(mockSocket);
        when(mockSocket.getInetAddress()).thenReturn(java.net.InetAddress.getLocalHost());
        doNothing().when(mockClientHandler).sendMessage(anyString());
        doNothing().when(mockClientHandler).setPlayerInfo(anyInt(), anyString());

        var contextField = GameServer.class.getDeclaredField("serverContext");
        contextField.setAccessible(true);
        ServerContext context = (ServerContext) contextField.get(gameServer);
        context.currentGameState = GameState.PLAYING;

        gameServer.registerPlayer(mockClientHandler, "TestPlayer");
        TankData tank = context.tanks.get(0);

        tank.setPosition(new org.joml.Vector2f(800, 800));
        var tile = context.gameMapData.getTileAt(800, 800);
        tile.setCurrentState(org.chrisgruber.nettank.common.world.TerrainState.BURNING);

        int initialHitPoints = tank.getHitPoints();

        gameServer.updateGameLogic(1.0f / 60.0f);
        assertEquals(initialHitPoints - 1, tank.getHitPoints());

        // Immediately after, the 2-second damage interval has not elapsed
        gameServer.updateGameLogic(1.0f / 60.0f);
        assertEquals(initialHitPoints - 1, tank.getHitPoints());
    }

    @Test
    void testTurretRotatesIndependentlyOfHull() throws Exception {
        when(mockClientHandler.getSocket()).thenReturn(mockSocket);
        when(mockSocket.getInetAddress()).thenReturn(java.net.InetAddress.getLocalHost());
        doNothing().when(mockClientHandler).sendMessage(anyString());
        doNothing().when(mockClientHandler).setPlayerInfo(anyInt(), anyString());

        var contextField = GameServer.class.getDeclaredField("serverContext");
        contextField.setAccessible(true);
        ServerContext context = (ServerContext) contextField.get(gameServer);
        context.currentGameState = GameState.PLAYING;

        gameServer.registerPlayer(mockClientHandler, "TestPlayer");
        TankData tank = context.tanks.get(0);
        tank.setPosition(new org.joml.Vector2f(800, 800));
        tank.setRotation(0f);
        tank.setTurretRotation(0f);

        // pin plain terrain so procedural speed modifiers don't affect the turn assertion
        var tile = context.gameMapData.getTileAt(800, 800);
        tile.setBaseType(org.chrisgruber.nettank.common.world.TerrainType.GRASS);
        tile.setOverlayType(null);

        // Turret input only: +1.0 turns the turret right (clockwise) at 90 deg/s
        gameServer.handlePlayerMovementInput(0, false, false, false, false, 1.0f);
        gameServer.updateGameLogic(1.0f);

        assertEquals(0f, tank.getRotation(), 0.01f); // hull unchanged
        assertEquals(270f, tank.getTurretRotation(), 0.01f); // 0 - 90, normalized

        // Hull turn input only: turret keeps its rotation
        gameServer.handlePlayerMovementInput(0, false, false, true, false, 0.0f);
        gameServer.updateGameLogic(1.0f);

        assertEquals(50f, tank.getRotation(), 0.01f);
        assertEquals(270f, tank.getTurretRotation(), 0.01f);
    }

    @Test
    void testBulletFiresAlongTurretRotation() throws Exception {
        when(mockClientHandler.getSocket()).thenReturn(mockSocket);
        when(mockSocket.getInetAddress()).thenReturn(java.net.InetAddress.getLocalHost());
        doNothing().when(mockClientHandler).sendMessage(anyString());
        doNothing().when(mockClientHandler).setPlayerInfo(anyInt(), anyString());

        var contextField = GameServer.class.getDeclaredField("serverContext");
        contextField.setAccessible(true);
        ServerContext context = (ServerContext) contextField.get(gameServer);
        context.currentGameState = GameState.PLAYING;

        gameServer.registerPlayer(mockClientHandler, "TestPlayer");
        TankData tank = context.tanks.get(0);
        tank.setRotation(0f);
        tank.setTurretRotation(90f); // hull facing +Y, turret facing -X

        gameServer.handlePlayerShootMainWeaponInput(0);

        assertEquals(1, context.bullets.size());
        var bullet = context.bullets.get(0);
        // direction for 90 deg: dirX = -sin(90) = -1, dirY = cos(90) = 0
        assertEquals(-GameServer.BULLET_SPEED, bullet.getXVelocity(), 0.01f);
        assertEquals(0f, bullet.getYVelocity(), 0.01f);
    }

    @Test
    void testTankTypeStatsApplied() throws Exception {
        when(mockClientHandler.getSocket()).thenReturn(mockSocket);
        when(mockSocket.getInetAddress()).thenReturn(java.net.InetAddress.getLocalHost());
        doNothing().when(mockClientHandler).sendMessage(anyString());
        doNothing().when(mockClientHandler).setPlayerInfo(anyInt(), anyString());

        var contextField = GameServer.class.getDeclaredField("serverContext");
        contextField.setAccessible(true);
        ServerContext context = (ServerContext) contextField.get(gameServer);

        gameServer.registerPlayer(mockClientHandler, "HeavyPlayer", org.chrisgruber.nettank.common.entities.TankType.HEAVY);
        TankData tank = context.tanks.get(0);

        assertEquals(org.chrisgruber.nettank.common.entities.TankType.HEAVY, tank.getTankType());
        assertEquals(6, tank.getHitPoints()); // HEAVY spawns with 6 HP

        TankStats stats = gameServer.getEffectiveStats(tank);
        assertEquals(70.0f, stats.moveSpeed());
        assertEquals(2, stats.bulletDamage());
        assertEquals(2800L, stats.shootCooldownMs());
    }

    @Test
    void testTankTypeSelectionOnlyValidBeforeRoundStarts() throws Exception {
        when(mockClientHandler.getSocket()).thenReturn(mockSocket);
        when(mockSocket.getInetAddress()).thenReturn(java.net.InetAddress.getLocalHost());
        doNothing().when(mockClientHandler).sendMessage(anyString());
        doNothing().when(mockClientHandler).setPlayerInfo(anyInt(), anyString());

        var contextField = GameServer.class.getDeclaredField("serverContext");
        contextField.setAccessible(true);
        ServerContext context = (ServerContext) contextField.get(gameServer);
        context.currentGameState = GameState.WAITING;

        gameServer.registerPlayer(mockClientHandler, "TestPlayer");
        TankData tank = context.tanks.get(0);

        gameServer.handleTankTypeSelection(0, "LIGHT");
        assertEquals(org.chrisgruber.nettank.common.entities.TankType.LIGHT, tank.getTankType());
        assertEquals(3, tank.getHitPoints());

        context.currentGameState = GameState.PLAYING;
        gameServer.handleTankTypeSelection(0, "HEAVY");
        assertEquals(org.chrisgruber.nettank.common.entities.TankType.LIGHT, tank.getTankType()); // rejected
    }

    @Test
    void testStealthTankCloaksWhenIdleAndDecloaksOnMovement() throws Exception {
        when(mockClientHandler.getSocket()).thenReturn(mockSocket);
        when(mockSocket.getInetAddress()).thenReturn(java.net.InetAddress.getLocalHost());
        doNothing().when(mockClientHandler).sendMessage(anyString());
        doNothing().when(mockClientHandler).setPlayerInfo(anyInt(), anyString());

        var contextField = GameServer.class.getDeclaredField("serverContext");
        contextField.setAccessible(true);
        ServerContext context = (ServerContext) contextField.get(gameServer);
        context.currentGameState = GameState.PLAYING;

        gameServer.registerPlayer(mockClientHandler, "Sneaky", org.chrisgruber.nettank.common.entities.TankType.STEALTH);
        TankData tank = context.tanks.get(0);
        tank.setPosition(new org.joml.Vector2f(800, 800));
        tank.setLastShotTime(0);

        // ensure the tank is on plain terrain (no concealment shortcut)
        var tile = context.gameMapData.getTileAt(800, 800);
        tile.setBaseType(org.chrisgruber.nettank.common.world.TerrainType.GRASS);
        tile.setOverlayType(null);

        long now = System.currentTimeMillis();
        assertTrue(gameServer.computeShouldCloak(tank, now)); // idle since "0" -> cloaked

        tank.setInputState(true, false, false, false);
        assertFalse(gameServer.computeShouldCloak(tank, now)); // moving decloaks immediately

        tank.setInputState(false, false, false, false);
        tank.setLastShotTime(now - 500);
        assertFalse(gameServer.computeShouldCloak(tank, now)); // recent shot blocks cloak
        assertTrue(gameServer.computeShouldCloak(tank, now + 2500)); // recloaks after delay
    }

    @Test
    void testNonStealthTankNeverCloaks() throws Exception {
        when(mockClientHandler.getSocket()).thenReturn(mockSocket);
        when(mockSocket.getInetAddress()).thenReturn(java.net.InetAddress.getLocalHost());
        doNothing().when(mockClientHandler).sendMessage(anyString());
        doNothing().when(mockClientHandler).setPlayerInfo(anyInt(), anyString());

        var contextField = GameServer.class.getDeclaredField("serverContext");
        contextField.setAccessible(true);
        ServerContext context = (ServerContext) contextField.get(gameServer);
        context.currentGameState = GameState.PLAYING;

        gameServer.registerPlayer(mockClientHandler, "Regular");
        TankData tank = context.tanks.get(0);
        tank.setLastShotTime(0);

        assertFalse(gameServer.computeShouldCloak(tank, System.currentTimeMillis()));
    }

    @Test
    void testBroadcastStateSkipsCloakedTankForOtherPlayers() throws Exception {
        ClientHandler handler1 = mock(ClientHandler.class);
        ClientHandler handler2 = mock(ClientHandler.class);

        when(handler1.getSocket()).thenReturn(mock(Socket.class));
        when(handler2.getSocket()).thenReturn(mock(Socket.class));
        when(handler1.getSocket().getInetAddress()).thenReturn(java.net.InetAddress.getLocalHost());
        when(handler2.getSocket().getInetAddress()).thenReturn(java.net.InetAddress.getLocalHost());
        when(handler1.getPlayerId()).thenReturn(0);
        when(handler2.getPlayerId()).thenReturn(1);

        var contextField = GameServer.class.getDeclaredField("serverContext");
        contextField.setAccessible(true);
        ServerContext context = (ServerContext) contextField.get(gameServer);
        context.currentGameState = GameState.PLAYING;

        gameServer.registerPlayer(handler1, "Cloaker");
        gameServer.registerPlayer(handler2, "Watcher");

        gameServer.cloakedPlayerIds.add(0);
        gameServer.broadcastState();

        String updPrefixCloaked = NetworkProtocol.PLAYER_UPDATE + ";0;";
        String updPrefixVisible = NetworkProtocol.PLAYER_UPDATE + ";1;";

        ArgumentCaptor<String> captor1 = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> captor2 = ArgumentCaptor.forClass(String.class);
        verify(handler1, atLeastOnce()).sendMessage(captor1.capture());
        verify(handler2, atLeastOnce()).sendMessage(captor2.capture());

        // The cloaked tank's owner still receives their own UPD
        assertTrue(captor1.getAllValues().stream().anyMatch(m -> m.startsWith(updPrefixCloaked)));
        // The other player gets no UPD for the cloaked tank but sees the visible one
        assertTrue(captor2.getAllValues().stream().noneMatch(m -> m.startsWith(updPrefixCloaked)));
        assertTrue(captor2.getAllValues().stream().anyMatch(m -> m.startsWith(updPrefixVisible)));
    }

    @Test
    void testUnlimitedAmmoModeSendsNoAmmoMessages() throws Exception {
        when(mockClientHandler.getSocket()).thenReturn(mockSocket);
        when(mockSocket.getInetAddress()).thenReturn(java.net.InetAddress.getLocalHost());
        doNothing().when(mockClientHandler).sendMessage(anyString());
        doNothing().when(mockClientHandler).setPlayerInfo(anyInt(), anyString());

        var contextField = GameServer.class.getDeclaredField("serverContext");
        contextField.setAccessible(true);
        ServerContext context = (ServerContext) contextField.get(gameServer);
        context.currentGameState = GameState.PLAYING;

        gameServer.registerPlayer(mockClientHandler, "TestPlayer");
        int playerId = 0;

        gameServer.handlePlayerShootMainWeaponInput(playerId);
        assertEquals(1, context.bullets.size());
        assertEquals(-1, context.gameMode.getMainWeaponAmmoForPlayer(playerId));

        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(mockClientHandler, atLeastOnce()).sendMessage(messageCaptor.capture());
        assertTrue(messageCaptor.getAllValues().stream()
                .noneMatch(msg -> msg.startsWith(NetworkProtocol.AMMO_COUNT + ";")));
    }

    @Test
    void testStopServerIdempotent() {
        gameServer.stop();
        gameServer.stop(); // Should not throw exception
    }

    @Test
    void testHandlePlayerMovementInputForInvalidPlayerId() throws Exception {
        var contextField = GameServer.class.getDeclaredField("serverContext");
        contextField.setAccessible(true);
        ServerContext context = (ServerContext) contextField.get(gameServer);
        context.currentGameState = GameState.PLAYING;

        gameServer.handlePlayerMovementInput(999, true, false, false, false);
        // Should not throw exception
    }

    @Test
    void testHandlePlayerShootMainWeaponInputForInvalidPlayerId() throws Exception {
        var contextField = GameServer.class.getDeclaredField("serverContext");
        contextField.setAccessible(true);
        ServerContext context = (ServerContext) contextField.get(gameServer);
        context.currentGameState = GameState.PLAYING;

        int initialBulletCount = context.bullets.size();
        gameServer.handlePlayerShootMainWeaponInput(999);

        assertEquals(initialBulletCount, context.bullets.size());
    }

    @Test
    void testRemovePlayerWhenPlayerDoesNotExist() {
        gameServer.removePlayer(999);
        // Should not throw exception
    }

    @Test
    void testRegisterPlayerAssignsUniqueIds() throws Exception {
        ClientHandler handler1 = mock(ClientHandler.class);
        ClientHandler handler2 = mock(ClientHandler.class);

        when(handler1.getSocket()).thenReturn(mock(Socket.class));
        when(handler2.getSocket()).thenReturn(mock(Socket.class));
        when(handler1.getSocket().getInetAddress()).thenReturn(java.net.InetAddress.getLocalHost());
        when(handler2.getSocket().getInetAddress()).thenReturn(java.net.InetAddress.getLocalHost());

        ArgumentCaptor<Integer> id1Captor = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<Integer> id2Captor = ArgumentCaptor.forClass(Integer.class);

        gameServer.registerPlayer(handler1, "Player1");
        gameServer.registerPlayer(handler2, "Player2");

        verify(handler1).setPlayerInfo(id1Captor.capture(), eq("Player1"));
        verify(handler2).setPlayerInfo(id2Captor.capture(), eq("Player2"));

        assertNotEquals(id1Captor.getValue(), id2Captor.getValue());
    }

    @Test
    void testRegisterPlayerAssignsUniqueColors() throws Exception {
        ClientHandler handler1 = mock(ClientHandler.class);
        ClientHandler handler2 = mock(ClientHandler.class);

        when(handler1.getSocket()).thenReturn(mock(Socket.class));
        when(handler2.getSocket()).thenReturn(mock(Socket.class));
        when(handler1.getSocket().getInetAddress()).thenReturn(java.net.InetAddress.getLocalHost());
        when(handler2.getSocket().getInetAddress()).thenReturn(java.net.InetAddress.getLocalHost());
        doNothing().when(handler1).sendMessage(anyString());
        doNothing().when(handler2).sendMessage(anyString());
        doNothing().when(handler1).setPlayerInfo(anyInt(), anyString());
        doNothing().when(handler2).setPlayerInfo(anyInt(), anyString());

        gameServer.registerPlayer(handler1, "Player1");
        gameServer.registerPlayer(handler2, "Player2");

        var contextField = GameServer.class.getDeclaredField("serverContext");
        contextField.setAccessible(true);
        ServerContext context = (ServerContext) contextField.get(gameServer);

        TankData tank1 = context.tanks.get(0);
        TankData tank2 = context.tanks.get(1);

        assertNotNull(tank1);
        assertNotNull(tank2);
        assertNotNull(tank1.getColor());
        assertNotNull(tank2.getColor());
        assertNotEquals(tank1.getColor(), tank2.getColor());
    }

    @Test
    void testHandlePlayerMovementInputWhenTankDestroyed() throws Exception {
        when(mockClientHandler.getSocket()).thenReturn(mockSocket);
        when(mockSocket.getInetAddress()).thenReturn(java.net.InetAddress.getLocalHost());
        doNothing().when(mockClientHandler).sendMessage(anyString());
        doNothing().when(mockClientHandler).setPlayerInfo(anyInt(), anyString());

        var contextField = GameServer.class.getDeclaredField("serverContext");
        contextField.setAccessible(true);
        ServerContext context = (ServerContext) contextField.get(gameServer);
        context.currentGameState = GameState.PLAYING;

        gameServer.registerPlayer(mockClientHandler, "TestPlayer");
        int playerId = 0;

        TankData tank = context.tanks.get(playerId);
        tank.takeHit(10); // Destroy tank

        gameServer.handlePlayerMovementInput(playerId, true, false, false, false);

        assertFalse(tank.isMovingForward());
    }

    @Test
    void testHandlePlayerShootMainWeaponInputWhenTankDestroyed() throws Exception {
        when(mockClientHandler.getSocket()).thenReturn(mockSocket);
        when(mockSocket.getInetAddress()).thenReturn(java.net.InetAddress.getLocalHost());
        doNothing().when(mockClientHandler).sendMessage(anyString());
        doNothing().when(mockClientHandler).setPlayerInfo(anyInt(), anyString());

        var contextField = GameServer.class.getDeclaredField("serverContext");
        contextField.setAccessible(true);
        ServerContext context = (ServerContext) contextField.get(gameServer);
        context.currentGameState = GameState.PLAYING;

        gameServer.registerPlayer(mockClientHandler, "TestPlayer");
        int playerId = 0;

        TankData tank = context.tanks.get(playerId);
        tank.takeHit(10); // Destroy tank

        int initialBulletCount = context.bullets.size();
        gameServer.handlePlayerShootMainWeaponInput(playerId);

        assertEquals(initialBulletCount, context.bullets.size());
    }

    @Test
    void testBroadcastWithEmptyClientList() throws Exception {
        var contextField = GameServer.class.getDeclaredField("serverContext");
        contextField.setAccessible(true);
        ServerContext context = (ServerContext) contextField.get(gameServer);

        assertTrue(context.clients.isEmpty());
        gameServer.broadcast("Test message", -1);
        // Should not throw exception
    }
}
