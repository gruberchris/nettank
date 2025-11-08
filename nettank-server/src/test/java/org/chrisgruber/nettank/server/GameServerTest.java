package org.chrisgruber.nettank.server;

import org.chrisgruber.nettank.common.entities.TankData;
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
