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

    public GameClient(String serverIp, int serverPort, String playerName, NetworkCallbackHandler networkCallbackHandler) {
        this.serverIp = serverIp;
        this.serverPort = serverPort;
        this.playerName = playerName;
        this.networkCallbackHandler = networkCallbackHandler;
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
        logger.trace("Parsing server message: {}", message); // Use Trace

        try {
            String[] parts = message.split(";");

            if (parts.length == 0) return;

            String command = parts[0];

            switch (command) {
                case NetworkProtocol.ASSIGN_ID:
                    // AID;<yourId>;<colorR>;<colorG>;<colorB> (5 parts)
                    if (parts.length >= 5) {
                        int id = Integer.parseInt(parts[1]);    // Ignore color parts r,g,b (parts[2],[3],[4]) here - they come with NEW_PLAYER
                        networkCallbackHandler.setLocalPlayerId(id);
                    } else {
                        logger.error("Malformed ASSIGN_ID message: Expected 5 parts, got {}", parts.length);
                    }
                    break;
                case NetworkProtocol.NEW_PLAYER:
                    // NEW;<id>;<x>;<y>;<rot>;<name>;<r>;<g>;<b> (9 parts)
                    // Lives info comes separately via PLAYER_LIVES
                    if (parts.length >= 9) {
                        int id = Integer.parseInt(parts[1]);
                        float x = Float.parseFloat(parts[2]);
                        float y = Float.parseFloat(parts[3]);
                        float rot = Float.parseFloat(parts[4]);
                        String name = parts[5];
                        float r = Float.parseFloat(parts[6]);
                        float g = Float.parseFloat(parts[7]);
                        float b = Float.parseFloat(parts[8]);
                        networkCallbackHandler.addOrUpdateTank(id, x, y, rot, name, r, g, b);
                    } else {
                        logger.error("Malformed NEW_PLAYER message: Expected 9 parts, got {}", parts.length);
                    }
                    break;
                case NetworkProtocol.PLAYER_UPDATE:
                    // UPD;<id>;<x>;<y>;<rot> (5 parts)
                    if (parts.length >= 5) {
                        int id = Integer.parseInt(parts[1]);
                        float x = Float.parseFloat(parts[2]);
                        float y = Float.parseFloat(parts[3]);
                        float rot = Float.parseFloat(parts[4]);
                        networkCallbackHandler.updateTankState(id, x, y, rot, false);
                    } else {
                        logger.error("Malformed PLAYER_UPDATE message: Expected 5 parts, got {}", parts.length);
                    }
                    break;
                case NetworkProtocol.PLAYER_LEFT:
                    // LEF;<id> (2 parts)
                    if (parts.length >= 2) {
                        int id = Integer.parseInt(parts[1]);
                        networkCallbackHandler.removeTank(id);
                    }
                    else {
                        logger.error("Malformed PLAYER_LEFT message: Expected 2 parts, got {}", parts.length);
                    }
                    break;
                case NetworkProtocol.SHOOT:
                    // SHO;<bulletId>;<ownerId>;<x>;<y>;<dirX>;<dirY> (6 parts now)
                    if (parts.length >= 7) {
                        UUID bulletId = UUID.fromString(parts[1]);
                        int ownerId = Integer.parseInt(parts[2]);
                        float x = Float.parseFloat(parts[3]);
                        float y = Float.parseFloat(parts[4]);
                        float dirX = Float.parseFloat(parts[5]);
                        float dirY = Float.parseFloat(parts[6]);
                        networkCallbackHandler.spawnBullet(bulletId, ownerId, x, y, dirX, dirY);
                    } else {
                        logger.error("Malformed SHOOT message: Expected 7 parts, got {}", parts.length);
                    }
                    break;
                case NetworkProtocol.HIT:
                    // HIT;<targetId>;<shooterId>;<bulletId>;<damage> (5 parts)
                    if (parts.length >= 5) {
                        int t = Integer.parseInt(parts[1]);
                        int s = Integer.parseInt(parts[2]);
                        UUID uuid = UUID.fromString(parts[3]);
                        int damage = Integer.parseInt(parts[4]);
                        networkCallbackHandler.handlePlayerHit(t, s, uuid, damage);
                    }
                    else {
                        logger.error("Malformed HIT message: Expected 5 parts, got {}", parts.length);
                    }
                    break;
                case NetworkProtocol.DESTROYED:
                    // DES;<targetId>;<shooterId> (3 parts)
                    if (parts.length >= 3) {
                        int t = Integer.parseInt(parts[1]);
                        int s = Integer.parseInt(parts[2]);
                        networkCallbackHandler.handlePlayerDestroyed(t, s);
                    }
                    else {
                        logger.error("Malformed DESTROYED message: Expected 3 parts, got {}", parts.length);
                    }
                    break;
                case NetworkProtocol.PLAYER_LIVES:
                    // LIV;<id>;<lives> (3 parts)
                    if (parts.length >= 3) {
                        int id = Integer.parseInt(parts[1]);
                        int l = Integer.parseInt(parts[2]);
                        networkCallbackHandler.updatePlayerLives(id, l);
                    }
                    else {
                        logger.error("Malformed PLAYER_LIVES message: Expected 3 parts, got {}", parts.length);
                    }
                    break;
                case NetworkProtocol.GAME_STATE:
                    // GST;<stateName>;<timeData> (3 parts)
                    if (parts.length >= 3) {
                        try {
                            GameState s = GameState.valueOf(parts[1]);
                            long t = Long.parseLong(parts[2]);
                            networkCallbackHandler.setGameState(s, t);
                        }
                        catch (IllegalArgumentException e) {
                            logger.error("Received invalid game state: {}", parts[1], e);
                        }
                    } else { logger.error("Malformed GAME_STATE message: Expected 3 parts, got {}", parts.length); }
                    break;
                case NetworkProtocol.ANNOUNCE:
                    // ANN;<messageText> (2+ parts)
                    if (parts.length >= 2) {
                        networkCallbackHandler.addAnnouncement(parts[1]);
                    }
                    else {
                        logger.error("Malformed ANNOUNCE message: Expected 2+ parts, got {}", parts.length);
                    }
                    break;
                case NetworkProtocol.ROUND_OVER:
                    // ROV;<winnerId>;<winnerName>;<finalTimeMillis> (4 parts)
                    // Client doesn't need to parse this directly, info is in announcement now
                    if (parts.length >= 4) {
                        logger.trace("Received ROUND_OVER message (parsed by announcement)");
                    }
                    else {
                        logger.error("Malformed ROUND_OVER message: Expected 4 parts, got {}", parts.length);
                    }
                    break;
                case NetworkProtocol.RESPAWN:
                    // RSP;<id>;<x>;<y>;<rotation> (5 parts)
                    if (parts.length >= 5) {
                        int id = Integer.parseInt(parts[1]);
                        float x = Float.parseFloat(parts[2]);
                        float y = Float.parseFloat(parts[3]);
                        float rotation = Float.parseFloat(parts[4]);
                        logger.debug("Received RESPAWN for player {}", id);
                        networkCallbackHandler.updateTankState(id, x, y, rotation, true);
                        // Server will send PLAYER_LIVES shortly after
                    } else {
                        logger.error("Malformed RESPAWN message: Expected 5 parts, got {}", parts.length);
                    }
                    break;
                case NetworkProtocol.SPECTATE_START:
                    if (parts.length >= 2) {
                        spectateEndTimeMillis = Long.parseLong(parts[1]);
                        setSpectatorMode(true);
                        // TODO: Maybe set camera to follow the player that killed you?
                    }
                    break;
                case NetworkProtocol.SPECTATE_END:
                    setSpectatorMode(false);
                    break;
                case NetworkProtocol.SPECTATE_PERMANENT:
                    setSpectatorMode(true);
                    spectateEndTimeMillis = -1; // Permanent spectating
                    break;
                case NetworkProtocol.MAP_INFO:
                    if (parts.length == 4) {
                        int widthTiles = Integer.parseInt(parts[1]);
                        int heightTiles = Integer.parseInt(parts[2]);
                        float tileSize = Float.parseFloat(parts[3]);
                        networkCallbackHandler.storeMapInfo(widthTiles, heightTiles, tileSize);
                    } else {
                        logger.error("Received malformed MAP_INFO: Expected 4 parts got {} message was: {}", parts.length, message);
                    }
                    break;
                case NetworkProtocol.ERROR_MSG:
                    // ERR;<errorMessage> (2+ parts)
                    if(parts.length >= 2) {
                        String errorMsg = parts[1];
                        logger.error("Received error message from server: {}", errorMsg);
                        networkCallbackHandler.connectionFailed(errorMsg); // Notify game logic
                        stop(); // Stop this client
                    } else {
                        logger.error("Malformed ERROR_MSG message: Expected 2+ parts, got {}", parts.length);
                    }
                    break;
                default:
                    logger.warn("Unknown message command from server: {}", command); // Changed from error to warn
            }
        } catch (NumberFormatException e) {
            logger.error("Failed to parse number in message: {}", message, e);
        } catch (ArrayIndexOutOfBoundsException e) { // Catch specific parsing errors
            logger.error("Malformed message received (ArrayIndexOutOfBounds): {}", message, e);
        } catch (Exception e) {
            logger.error("Error parsing server message {}", message, e);
        } finally {
            logger.trace("Finished parsing server message."); // Use Trace
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