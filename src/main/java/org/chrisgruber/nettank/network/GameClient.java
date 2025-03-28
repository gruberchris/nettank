package org.chrisgruber.nettank.network;

import org.chrisgruber.nettank.main.Game;
import org.chrisgruber.nettank.util.GameState;
import org.joml.Vector3f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.io.*;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;

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
    private final Game game; // Reference to the main game class for callbacks

    private long lastInputSendTime = 0;
    private static final long INPUT_SEND_INTERVAL_MS = 50; // Send input updates roughly 20 times/sec

    public GameClient(String serverIp, int serverPort, String playerName, Game game) {
        this.serverIp = serverIp;
        this.serverPort = serverPort;
        this.playerName = playerName;
        this.game = game;
    }

    @Override
    public void run() {
        Thread.currentThread().setName("NetworkClient");
        running = true;
        logger.info("NetworkClient thread started.");

        try {
            // Socket setup remains the same...
            Socket localSocket; // Use local vars within the try block scope
            PrintWriter localOut;
            BufferedReader localIn;

            synchronized(connectionLock) {
                socket = new Socket(serverIp, serverPort);
                out = new PrintWriter(socket.getOutputStream(), true);
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                // Assign to local variables *inside* the lock
                localSocket = socket;
                localOut = out;
                localIn = in;
            }

            logger.info("Connected to server: {}:{}", serverIp, serverPort);

            // Send initial connect message (using localOut reference)
            if (localOut != null) {
                // Use synchronized sendMessage method which handles locking internally now
                sendMessage(NetworkProtocol.CONNECT + ";" + playerName);
                logger.info("Sent initial connect message to server: {}", playerName);
            }


            String serverMessage = null;
            while (running && !Thread.currentThread().isInterrupted()) {
                // Reference localIn directly. It won't change unless stop() is called,
                // and stop() will interrupt this thread by closing the socket.
                if (localIn == null) {
                    logger.warn("NetworkClient: Input stream reference is null, breaking loop.");
                    break;
                }

                try { // Inner try for readLine and parse
                    serverMessage = null; // Reset before read
                    logger.debug("NetworkClient: Waiting for server message...");

                    // *** CRITICAL CHANGE: Read OUTSIDE the lock ***
                    serverMessage = localIn.readLine();

                    logger.debug("NetworkClient: readLine() returned: {}", serverMessage == null ? "null" : "a message");

                    // Check running flag *after* readLine potentially returns null or throws Exception
                    if (!running) {
                        logger.debug("NetworkClient: 'running' flag is false after readLine, exiting loop.");
                        break;
                    }

                    if (serverMessage == null) {
                        logger.info("Server closed connection (end of stream).");
                        // Use SwingUtilities for safety if game might interact with UI immediately
                        SwingUtilities.invokeLater(game::disconnected);
                        break; // Exit loop on null message
                    }

                    logger.info("Client received message from server: {}", serverMessage);
                    parseServerMessage(serverMessage);

                } catch (SocketException e) {
                    // Check running/shuttingDown flags more carefully here
                    if (running && !shuttingDown) {
                        logger.error("NetworkClient: Socket exception in loop: {}", e.getMessage());
                        SwingUtilities.invokeLater(() -> game.connectionFailed("Network error: " + e.getMessage()));
                        running = false; // Stop loop on error
                    } else {
                        // This is expected if stop() closed the socket
                        logger.info("NetworkClient: SocketException during shutdown (likely intended): {}", e.getMessage());
                    }
                    break; // Exit loop on socket exception
                } catch (IOException e) {
                    if (running && !shuttingDown) {
                        logger.error("NetworkClient: I/O error in loop: {}", e.getMessage(), e);
                        SwingUtilities.invokeLater(() -> game.connectionFailed("I/O error: " + e.getMessage()));
                        running = false; // Stop loop on error
                    } else {
                        logger.debug("NetworkClient: IOException during shutdown or after stop: {}", e.getMessage());
                    }
                    break; // Exit loop on IO exception
                } catch (Exception e) {
                    logger.error("NetworkClient: Unexpected exception in loop processing message '{}'", serverMessage, e);
                    // Maybe try to continue? Or stop? For now, just break.
                    break;
                }
            } // end while loop

        } catch (UnknownHostException e) {
            logger.error("##### NetworkClient: Unknown host {} #####", serverIp, e);
            SwingUtilities.invokeLater(() -> game.connectionFailed("Unknown host: " + serverIp));
        } catch (IOException e) { // Catch connection errors
            logger.error("##### NetworkClient: Failed to connect to {}:{} #####", serverIp, serverPort, e);
            SwingUtilities.invokeLater(() -> game.connectionFailed("Connection failed: " + e.getMessage()));
        } catch (Throwable t) { // Broadest catch
            logger.error("##### NetworkClient: CRITICAL UNCAUGHT THROWABLE #####", t);
            if (running && !shuttingDown) {
                try {
                    SwingUtilities.invokeLater(() -> game.connectionFailed("Critical network thread error: " + t.getMessage()));
                } catch (Exception inner) {/* ignore */}
            }
        } finally {
            logger.info("NetworkClient: Shutting down network client thread resources...");
            if (!shuttingDown) {
                stop();
            } else {
                // Ensure stream/socket cleanup happens even if stop() was already called,
                // in case the loop broke before stop() finished. Best effort.
                logger.debug("NetworkClient: Shutdown already initiated, ensuring resources closed in finally.");
                // Simplified cleanup directly here, as stop() might be called recursively otherwise
                Socket s = null;
                PrintWriter o = null;
                BufferedReader i = null;
                synchronized (connectionLock) {
                    s = socket; socket = null;
                    o = out; out = null;
                    i = in; in = null;
                }
                try { if (o != null) o.close(); } catch (Exception e) {}
                try { if (i != null) i.close(); } catch (Exception e) {}
                try { if (s != null && !s.isClosed()) s.close(); } catch (Exception e) {}
            }
            logger.info("NetworkClient thread finished.");
        }
    }

    private void parseServerMessage(String message) {
        //System.out.println("Received: " + message); // DEBUG
        try {
            String[] parts = message.split(";");
            if (parts.length == 0) return;

            String command = parts[0];

            switch (command) {
                case NetworkProtocol.ASSIGN_ID:
                    if (parts.length >= 6) {
                        int id = Integer.parseInt(parts[1]);
                        float r = Float.parseFloat(parts[2]);
                        float g = Float.parseFloat(parts[3]);
                        float b = Float.parseFloat(parts[4]);
                        boolean isHost = Boolean.parseBoolean(parts[5]);
                        game.setLocalPlayerId(id, r, g, b);
                        game.setHostStatus(isHost);
                    } else {
                        logger.error("Malformed ASSIGN_ID message: Expected 6 parts, got {}", parts.length);
                    }
                    break;
                case NetworkProtocol.NEW_PLAYER:
                    if (parts.length >= 9) {
                        int id = Integer.parseInt(parts[1]);
                        float x = Float.parseFloat(parts[2]);
                        float y = Float.parseFloat(parts[3]);
                        float rot = Float.parseFloat(parts[4]);
                        String name = parts[5];
                        float r = Float.parseFloat(parts[6]);
                        float g = Float.parseFloat(parts[7]);
                        float b = Float.parseFloat(parts[8]);
                        game.addOrUpdateTank(id, x, y, rot, name, r, g, b);
                    }
                    break;
                case NetworkProtocol.PLAYER_UPDATE:
                    if (parts.length >= 5) {
                        int id = Integer.parseInt(parts[1]);
                        float x = Float.parseFloat(parts[2]);
                        float y = Float.parseFloat(parts[3]);
                        float rot = Float.parseFloat(parts[4]);
                        game.updateTankState(id, x, y, rot);
                    }
                    break;
                case NetworkProtocol.PLAYER_LEFT:
                    if (parts.length >= 2) {
                        int id = Integer.parseInt(parts[1]);
                        game.removeTank(id);
                    }
                    break;
                case NetworkProtocol.SHOOT:
                    if (parts.length >= 7) {
                        int ownerId = Integer.parseInt(parts[1]);
                        float x = Float.parseFloat(parts[2]);
                        float y = Float.parseFloat(parts[3]);
                        float dirX = Float.parseFloat(parts[4]);
                        float dirY = Float.parseFloat(parts[5]);
                        // Ignore accuracy param parts[6] for now
                        game.spawnBullet(ownerId, x, y, dirX, dirY);
                    }
                    break;
                case NetworkProtocol.HIT:
                    if (parts.length >= 3) {
                        int targetId = Integer.parseInt(parts[1]);
                        int shooterId = Integer.parseInt(parts[2]);
                        game.handlePlayerHit(targetId, shooterId);
                    }
                    break;
                case NetworkProtocol.DESTROYED:
                    if (parts.length >= 3) {
                        int targetId = Integer.parseInt(parts[1]);
                        int shooterId = Integer.parseInt(parts[2]);
                        game.handlePlayerDestroyed(targetId, shooterId);
                    }
                    break;
                case NetworkProtocol.RESPAWN:
                    if (parts.length >= 4) {
                        int id = Integer.parseInt(parts[1]);
                        float x = Float.parseFloat(parts[2]);
                        float y = Float.parseFloat(parts[3]);
                        // Handled implicitly by NEW_PLAYER or PLAYER_UPDATE in simple model
                        // game.handleRespawn(id, x, y); // Could be explicit if needed
                    }
                    break;
                case NetworkProtocol.PLAYER_LIVES:
                    if(parts.length >= 3) {
                        int id = Integer.parseInt(parts[1]);
                        int lives = Integer.parseInt(parts[2]);
                        game.updatePlayerLives(id, lives);
                    }
                    break;
                case NetworkProtocol.GAME_STATE:
                    if(parts.length >= 3) {
                        try {
                            GameState state = GameState.valueOf(parts[1]);
                            long timeData = Long.parseLong(parts[2]);
                            game.setGameState(state, timeData);
                        } catch (IllegalArgumentException e) {
                            logger.error("Received invalid game state: {}", parts[1], e);
                        }
                    }
                    break;
                case NetworkProtocol.ANNOUNCE:
                    if(parts.length >= 2) {
                        game.addAnnouncement(parts[1]);
                    }
                    break;
                case NetworkProtocol.ROUND_OVER: // Sent alongside announcement, contains winner ID
                    if(parts.length >= 4) {
                        // int winnerId = Integer.parseInt(parts[1]);
                        // String winnerName = parts[2];
                        // long finalTime = Long.parseLong(parts[3]);
                        // This data is now primarily in the announcement message for simplicity
                    }
                    break;
                case NetworkProtocol.ERROR_MSG:
                    if(parts.length >= 2) {
                        logger.error("Received error message from server: {}", parts[1]);
                        game.connectionFailed(parts[1]); // Show error from server
                        stop(); // Stop the client connection
                        logger.info("Game disconnected due to error message from server.");
                    }
                    break;
                // Add PING/PONG later if needed for connection checking
                default:
                    logger.error("Unknown message from server: {}", message);
            }
        } catch (NumberFormatException e) {
            logger.error("Failed to parse number in message: {}", message, e);
        } catch (ArrayIndexOutOfBoundsException e) {
            logger.error("Malformed message received: {}", message, e);
        } catch (Exception e) { // Catch broader exceptions during parsing
            logger.error("Error parsing server message {}", message, e);
        } finally {
            logger.debug("Finished parsing server message."); // Log exit
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
        // Throttle input sending
        if (now - lastInputSendTime >= INPUT_SEND_INTERVAL_MS) {
            sendMessage(String.format("%s;%b;%b;%b;%b", NetworkProtocol.INPUT, w, s, a, d));
            lastInputSendTime = now;
        }
    }

    // Send shoot command
    public void sendShoot() {
        sendMessage(NetworkProtocol.SHOOT_CMD); // Simple command
    }


    public synchronized void stop() { // Keep synchronized on the method for shutdown flag integrity
        if (shuttingDown) {
            logger.debug("Client already in shutdown process, ignoring duplicate stop call");
            return;
        }
        logger.info("Attempting to stop client network connection..."); // Changed log message
        shuttingDown = true; // Mark that shutdown has started
        running = false; // Signal the loop to stop

        // --- Socket closing ---
        Socket socketToClose = null;
        logger.debug("Preparing to close socket...");
        // Minimize time holding the lock, just get the socket reference
        synchronized(connectionLock) {
            socketToClose = this.socket;
            this.socket = null; // Nullify under lock to prevent reuse
        }
        logger.debug("Socket reference obtained (lock released). Closing socket...");

        if (socketToClose != null) {
            try {
                // Close socket *outside* the connectionLock to avoid holding lock during potentially blocking IO
                socketToClose.close();
                logger.debug("Client socket closed successfully.");
            } catch (IOException e) {
                logger.error("Error closing client socket", e);
            }
        } else {
            logger.debug("Socket was already null before closing.");
        }
        logger.debug("Socket closing attempt finished.");

        // --- Stream closing ---
        // Also minimize lock time here, though less critical than socket
        PrintWriter outToClose = null;
        BufferedReader inToClose = null;
        logger.debug("Preparing to close streams...");
        synchronized(connectionLock) {
            outToClose = this.out;
            inToClose = this.in;
            this.out = null;
            this.in = null;
        }
        logger.debug("Stream references obtained (lock released). Closing streams...");

        try {
            if (outToClose != null) outToClose.close();
        } catch (Exception e) { /* Ignore */ }
        try {
            if (inToClose != null) inToClose.close();
        } catch (Exception e) { /* Ignore */ }
        logger.debug("Streams closed.");

        logger.info("Client network connection resources released."); // Final message
    }

    public boolean isConnected() {
        return running && socket != null && socket.isConnected() && !socket.isClosed();
    }

    public void sendShutdownServer() {
        sendMessage(NetworkProtocol.SHUTDOWN_SERVER);
    }
}