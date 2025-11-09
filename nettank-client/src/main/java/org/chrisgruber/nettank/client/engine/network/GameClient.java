package org.chrisgruber.nettank.client.engine.network;

import org.chrisgruber.nettank.common.network.NetworkProtocol;
import org.chrisgruber.nettank.common.util.GameState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.io.*;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.UUID;

public class GameClient implements Runnable {
    private static final Logger logger = LoggerFactory.getLogger(GameClient.class);

    private final String serverIp;
    private final int serverPort;
    private final String playerName;
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private volatile boolean running = false;
    private volatile boolean shuttingDown = false;
    private final Object connectionLock = new Object();
    private final NetworkCallbackHandler networkCallbackHandler;
    private boolean isSpectating = false;
    private long spectateEndTimeMillis = 0;
    private long lastInputSendTime = 0;
    private static final long INPUT_SEND_INTERVAL_MS = 50;
    
    // Heartbeat configuration - configurable via system property or default to 10 seconds
    private static final long DEFAULT_HEARTBEAT_INTERVAL_MS = 10000; // 10 seconds
    private final long heartbeatIntervalMs;
    private long lastHeartbeatTime = 0;

    public GameClient(String serverIp, int serverPort, String playerName, NetworkCallbackHandler networkCallbackHandler) {
        this.serverIp = serverIp;
        this.serverPort = serverPort;
        this.playerName = playerName;
        this.networkCallbackHandler = networkCallbackHandler;
        
        // Allow configuring a heartbeat interval via system property
        long configuredInterval = DEFAULT_HEARTBEAT_INTERVAL_MS;
        String intervalProperty = System.getProperty("nettank.heartbeat.interval.ms");
        if (intervalProperty != null) {
            try {
                configuredInterval = Long.parseLong(intervalProperty);
                logger.info("Using configured heartbeat interval: {}ms", configuredInterval);
            } catch (NumberFormatException e) {
                logger.warn("Invalid heartbeat interval property, using default: {}ms", DEFAULT_HEARTBEAT_INTERVAL_MS);
            }
        }
        this.heartbeatIntervalMs = configuredInterval;
    }

    @Override
    public void run() {
        Thread.currentThread().setName("GameClientThread");
        running = true;
        logger.info("NetworkClient thread started.");

        try {
            Socket localSocket;
            PrintWriter localOut;
            BufferedReader localIn;

            synchronized(connectionLock) {
                socket = new Socket(serverIp, serverPort);
                out = new PrintWriter(socket.getOutputStream(), true);
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                localSocket = socket;
                localOut = out;
                localIn = in;
            }

            logger.info("Connected to server: {}:{}", serverIp, serverPort);

            if (localOut != null) {
                sendMessage(NetworkProtocol.CONNECT + ";" + playerName);
                logger.info("Sent initial connect message to server: {}", playerName);
            }

            String serverMessage = null;

            while (running && !Thread.currentThread().isInterrupted()) {
                if (localIn == null) {
                    logger.warn("NetworkClient: Input stream null, breaking.");
                    break;
                }
                
                // Send periodic heartbeat to keep connection alive
                long currentTime = System.currentTimeMillis();
                if (currentTime - lastHeartbeatTime >= heartbeatIntervalMs) {
                    sendHeartbeat();
                    lastHeartbeatTime = currentTime;
                }

                try {
                    serverMessage = null;

                    logger.trace("NetworkClient: Waiting for server message...");

                    serverMessage = localIn.readLine();

                    logger.trace("NetworkClient: readLine() returned: {}", serverMessage == null ? "null" : "a message");

                    if (!running) {
                        logger.trace("NetworkClient: 'running' false after readLine, exiting.");
                        break;
                    }

                    if (serverMessage == null) {
                        logger.info("Server closed connection (end of stream).");
                        SwingUtilities.invokeLater(networkCallbackHandler::disconnected);
                        break;
                    }

                    logger.trace("Client received message from server: {}", serverMessage);

                    parseServerMessage(serverMessage);

                } catch (SocketException e) {
                    if (running && !shuttingDown) {
                        logger.error("NetworkClient: Socket exception in loop: {}", e.getMessage());
                        SwingUtilities.invokeLater(() -> networkCallbackHandler.connectionFailed("Network error: " + e.getMessage()));
                        running = false;
                    } else {
                        logger.info("NetworkClient: SocketException during shutdown (likely intended): {}", e.getMessage());
                    }
                    break;
                } catch (IOException e) {
                    if (running && !shuttingDown) {
                        logger.error("NetworkClient: I/O error in loop: {}", e.getMessage(), e);
                        SwingUtilities.invokeLater(() -> networkCallbackHandler.connectionFailed("I/O error: " + e.getMessage()));
                        running = false;
                    } else {
                        logger.debug("NetworkClient: IOException during shutdown or after stop: {}", e.getMessage());
                    }
                    break;
                } catch (Exception e) {
                    logger.error("NetworkClient: Unexpected exception processing message '{}'", serverMessage, e);
                    break;
                }
            }
        } catch (UnknownHostException e) {
            logger.error("##### NetworkClient: Unknown host {} #####", serverIp, e);
            SwingUtilities.invokeLater(() -> networkCallbackHandler.connectionFailed("Unknown host: " + serverIp));
        } catch (IOException e) {
            logger.error("##### NetworkClient: Failed to connect to {}:{} #####", serverIp, serverPort, e);
            SwingUtilities.invokeLater(() -> networkCallbackHandler.connectionFailed("Connection failed: " + e.getMessage()));
        } catch (Throwable t) {
            logger.error("##### NetworkClient: CRITICAL UNCAUGHT THROWABLE #####", t);
            if (running && !shuttingDown) {
                try {
                    SwingUtilities.invokeLater(() -> networkCallbackHandler.connectionFailed("Critical network thread error: " + t.getMessage()));
                }
                catch (Exception inner) {/* ignore */}
            }
        } finally {
            logger.info("NetworkClient: Shutting down network client thread resources...");

            if (!shuttingDown) {
                stop();
            }
            else {
                logger.debug("NetworkClient: Shutdown already initiated, ensuring resources closed in finally.");
            }

            logger.info("NetworkClient thread finished.");
        }
    }

    private void parseServerMessage(String message) {
        logger.trace("Parsing server message: {}", message);

        try {
            String[] parts = message.split(";");
            if (parts.length == 0) return;

            String command = parts[0];

            switch (command) {
                case NetworkProtocol.ASSIGN_ID -> {
                    try {
                        var msg = NetworkMessage.PlayerId.parse(parts);
                        networkCallbackHandler.setLocalPlayerId(msg.id());
                    } catch (IllegalArgumentException e) {
                        logger.error("Malformed ASSIGN_ID message: {}", e.getMessage());
                    }
                }
                case NetworkProtocol.NEW_PLAYER -> {
                    try {
                        var msg = NetworkMessage.NewPlayer.parse(parts);
                        networkCallbackHandler.addOrUpdateTank(
                            msg.id(), msg.x(), msg.y(), msg.rotation(),
                            msg.name(), msg.colorR(), msg.colorG(), msg.colorB()
                        );
                    } catch (IllegalArgumentException e) {
                        logger.error("Malformed NEW_PLAYER message: {}", e.getMessage());
                    }
                }
                case NetworkProtocol.PLAYER_UPDATE -> {
                    try {
                        var msg = NetworkMessage.PlayerUpdate.parse(parts);
                        networkCallbackHandler.updateTankState(
                            msg.id(), msg.x(), msg.y(), msg.rotation(), false
                        );
                    } catch (IllegalArgumentException e) {
                        logger.error("Malformed PLAYER_UPDATE message: {}", e.getMessage());
                    }
                }
                case NetworkProtocol.PLAYER_LEFT -> {
                    try {
                        var msg = NetworkMessage.PlayerLeft.parse(parts);
                        networkCallbackHandler.removeTank(msg.id());
                    } catch (IllegalArgumentException e) {
                        logger.error("Malformed PLAYER_LEFT message: {}", e.getMessage());
                    }
                }
                case NetworkProtocol.SHOOT -> {
                    try {
                        var msg = NetworkMessage.Shoot.parse(parts);
                        networkCallbackHandler.spawnBullet(
                            msg.bulletId(), msg.ownerId(),
                            msg.x(), msg.y(), msg.dirX(), msg.dirY()
                        );
                    } catch (IllegalArgumentException e) {
                        logger.error("Malformed SHOOT message: {}", e.getMessage());
                    }
                }
                case NetworkProtocol.HIT -> {
                    try {
                        var msg = NetworkMessage.Hit.parse(parts);
                        networkCallbackHandler.handlePlayerHit(
                            msg.targetId(), msg.shooterId(),
                            msg.bulletId(), msg.damage()
                        );
                    } catch (IllegalArgumentException e) {
                        logger.error("Malformed HIT message: {}", e.getMessage());
                    }
                }
                case NetworkProtocol.DESTROYED -> {
                    try {
                        var msg = NetworkMessage.Destroyed.parse(parts);
                        networkCallbackHandler.handlePlayerDestroyed(
                            msg.targetId(), msg.shooterId()
                        );
                    } catch (IllegalArgumentException e) {
                        logger.error("Malformed DESTROYED message: {}", e.getMessage());
                    }
                }
                case NetworkProtocol.PLAYER_LIVES -> {
                    try {
                        var msg = NetworkMessage.PlayerLives.parse(parts);
                        networkCallbackHandler.updatePlayerLives(msg.playerId(), msg.lives());
                    } catch (IllegalArgumentException e) {
                        logger.error("Malformed PLAYER_LIVES message: {}", e.getMessage());
                    }
                }
                case NetworkProtocol.GAME_STATE -> {
                    try {
                        var msg = NetworkMessage.GameStateMessage.parse(parts);
                        try {
                            networkCallbackHandler.setGameState(
                                GameState.valueOf(msg.stateName()),
                                msg.timeData()
                            );
                        } catch (IllegalArgumentException e) {
                            logger.error("Received invalid game state: {}", msg.stateName(), e);
                        }
                    } catch (IllegalArgumentException e) {
                        logger.error("Malformed GAME_STATE message: {}", e.getMessage());
                    }
                }
                case NetworkProtocol.ANNOUNCE -> {
                    try {
                        var msg = NetworkMessage.Announcement.parse(parts);
                        networkCallbackHandler.addAnnouncement(msg.message());
                    } catch (IllegalArgumentException e) {
                        logger.error("Malformed ANNOUNCE message: {}", e.getMessage());
                    }
                }
                case NetworkProtocol.ROUND_OVER -> {
                    try {
                        var msg = NetworkMessage.RoundOver.parse(parts);
                        logger.trace("Received ROUND_OVER: winner={}, time={}ms", 
                                   msg.winnerName(), msg.finalTimeMillis());
                    } catch (IllegalArgumentException e) {
                        logger.error("Malformed ROUND_OVER message: {}", e.getMessage());
                    }
                }
                case NetworkProtocol.RESPAWN -> {
                    try {
                        var msg = NetworkMessage.Respawn.parse(parts);
                        logger.debug("Received RESPAWN for player {}", msg.id());
                        networkCallbackHandler.updateTankState(
                            msg.id(), msg.x(), msg.y(), msg.rotation(), true
                        );
                    } catch (IllegalArgumentException e) {
                        logger.error("Malformed RESPAWN message: {}", e.getMessage());
                    }
                }
                case NetworkProtocol.SPECTATE_START -> {
                    try {
                        var msg = NetworkMessage.SpectatorMode.parse(parts);
                        spectateEndTimeMillis = msg.durationMs();
                        setSpectatorMode(true);
                    } catch (IllegalArgumentException e) {
                        logger.error("Malformed SPECTATE_START message: {}", e.getMessage());
                    }
                }
                case NetworkProtocol.SPECTATE_END -> setSpectatorMode(false);
                case NetworkProtocol.SPECTATE_PERMANENT -> {
                    setSpectatorMode(true);
                    spectateEndTimeMillis = -1;
                }
                case NetworkProtocol.MAP_INFO -> {
                    try {
                        var msg = NetworkMessage.MapInfo.parse(parts);
                        // Note: MapInfo record doesn't store tileSize, using parts[3] directly
                        networkCallbackHandler.storeMapInfo(
                            msg.width(), msg.height(),
                            parts.length >= 4 ? Float.parseFloat(parts[3]) : 1.0f
                        );
                    } catch (IllegalArgumentException e) {
                        logger.error("Malformed MAP_INFO message: {}", e.getMessage());
                    }
                }
                case NetworkProtocol.TERRAIN_DATA -> {
                    try {
                        var msg = NetworkMessage.TerrainData.parse(parts);
                        networkCallbackHandler.receiveTerrainData(msg.width(), msg.height(), msg.encodedData());
                        logger.info("Received TERRAIN_DATA: {}x{} tiles, {} bytes", 
                            msg.width(), msg.height(), msg.encodedData().length());
                    } catch (IllegalArgumentException e) {
                        logger.error("Malformed TERRAIN_DATA message: {}", e.getMessage());
                    }
                }
                case NetworkProtocol.ERROR_MSG -> {
                    try {
                        var msg = NetworkMessage.ErrorMessage.parse(parts);
                        logger.error("Received error message from server: {}", msg.errorText());
                        networkCallbackHandler.connectionFailed(msg.errorText());
                        stop();
                    } catch (IllegalArgumentException e) {
                        logger.error("Malformed ERROR_MSG message: {}", e.getMessage());
                    }
                }
                case NetworkProtocol.SHOOT_COOLDOWN -> {
                    try {
                        var msg = NetworkMessage.ShootCooldown.parse(parts);
                        networkCallbackHandler.updateShootCooldown(msg.cooldownMs());
                    } catch (IllegalArgumentException e) {
                        logger.error("Malformed SHOOT_COOLDOWN message: {}", e.getMessage());
                    }
                }
                default -> logger.warn("Unknown message command from server: {}", command);
            }
        } catch (NumberFormatException e) {
            logger.error("Failed to parse number in message: {}", message, e);
        } catch (ArrayIndexOutOfBoundsException e) {
            logger.error("Malformed message received (ArrayIndexOutOfBounds): {}", message, e);
        } catch (Exception e) {
            logger.error("Error parsing server message {}", message, e);
        } finally {
            logger.trace("Finished parsing server message.");
        }
    }

    private void setSpectatorMode(boolean spectating) {
        this.isSpectating = spectating;
        // TODO: Update UI to show spectator mode
        if (spectating) {
            // TODO: Show spectator UI, maybe show a countdown timer until respawn
            logger.info("Entered spectator mode");
        } else {
            // TODO: Hide spectator UI
            logger.info("Exited spectator mode");
        }
    }

    public void sendMessage(String message) {
        synchronized(connectionLock) {
            if (out != null && running && !shuttingDown) {
                out.println(message);
                // Add immediate flush to ensure messages are sent
                out.flush();
            }
        }
    }

    // Send movement input state
    public void sendInput(boolean w, boolean s, boolean a, boolean d) {
        long now = System.currentTimeMillis();

        if (now - lastInputSendTime >= INPUT_SEND_INTERVAL_MS) {
            sendMessage(String.format("%s;%b;%b;%b;%b", NetworkProtocol.INPUT, w, s, a, d));
            lastInputSendTime = now;
        }
    }

    // Send shoot command
    public void sendShoot() {
        sendMessage(NetworkProtocol.SHOOT_CMD);
    }
    
    // Send heartbeat to keep connection alive
    private void sendHeartbeat() {
        logger.trace("Sending heartbeat to server");
        sendMessage(NetworkProtocol.PING);
    }

    public synchronized void stop() {
        if (shuttingDown) {
            logger.trace("Client stop() already called.");
            return;

        }
        logger.info("Attempting to stop client network connection...");

        shuttingDown = true;
        running = false;

        Socket socketToClose = null;
        PrintWriter outToClose = null;
        BufferedReader inToClose = null;

        logger.debug("Preparing to close socket/streams...");

        synchronized(connectionLock) {
            socketToClose = this.socket;
            this.socket = null;
            outToClose = this.out;
            this.out = null;
            inToClose = this.in;
            this.in = null;
        }

        logger.debug("Socket/stream references obtained. Closing...");

        // Close resources outside the lock
        try { if (outToClose != null) outToClose.close(); } catch (Exception e) {/* ignore */}
        try { if (inToClose != null) inToClose.close(); } catch (Exception e) {/* ignore */}

        try {
            if (socketToClose != null && !socketToClose.isClosed()) {
                socketToClose.close();
            }

            logger.debug("Client socket closed.");
        } catch (IOException e) {
            logger.error("Error closing client socket", e);
        }

        logger.info("Client network connection resources released.");
    }

    public boolean isConnected() {
        Socket socketRef;

        synchronized(connectionLock) {
            socketRef = this.socket;
        }

        // TRUE if the socket is connected and not closed
        return running &&
                !shuttingDown &&
                socketRef != null &&
                socketRef.isConnected() &&
                !socketRef.isClosed();
    }
}