package org.chrisgruber.nettank.network;

import org.chrisgruber.nettank.main.Game;
import org.joml.Vector3f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.Socket;
import java.net.SocketException;

public class ClientHandler implements Runnable {
    private static final Logger logger = LoggerFactory.getLogger(ClientHandler.class);
    private Socket socket;
    private GameServer server;
    private PrintWriter out;
    private BufferedReader in;
    private final Object connectionLock = new Object();
    private volatile boolean running = false;
    private volatile boolean shuttingDown = false;

    // Player specific info - set after successful registration
    private int playerId = -1;
    private String playerName = null;
    private Vector3f playerColor = null;


    public ClientHandler(Socket socket, GameServer server) {
        this.socket = socket;
        this.server = server;
    }

    // Called by GameServer after successful registration
    public void setPlayerInfo(int id, String name, Vector3f color) {
        this.playerId = id;
        this.playerName = name;
        this.playerColor = color;
    }

    @Override
    public void run() {
        String threadName = "ServerClientHandler-" + socket.getPort();
        Thread.currentThread().setName(threadName);
        logger.info("Client handler thread started: {}", threadName);

        running = true;
        try {
            synchronized(connectionLock) {
                out = new PrintWriter(socket.getOutputStream(), true);
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            }

            String clientMessage;
            while (running && !Thread.currentThread().isInterrupted()) {
                clientMessage = null;
                synchronized(connectionLock) {
                    if (in != null) {
                        clientMessage = in.readLine();
                    }
                }

                if (clientMessage == null) {
                    if (running) {
                        logger.info("Client disconnected (end of stream)");
                        closeConnection("Client disconnected");
                    }
                    break;
                }

                logger.info("Client received: {}", clientMessage);
                parseClientMessage(clientMessage);
            }
        } catch (SocketException e) {
            if (running && !shuttingDown) {
                logger.info("Socket error for client {}: {}", playerId, e.getMessage());
            }
        } catch (IOException e) {
            if (running && !shuttingDown) {
                logger.error("I/O error for client {}: {}", playerId, e.getMessage());
            }
        } catch (Exception e) {
            if (running) {
                logger.error("Unexpected error in client handler", e);
            }
        } finally {
            closeConnection("Connection closed");
            logger.info("Client handler thread finished: {}", threadName);
        }
    }

    /*
    private void parseClientMessage(String message) {
        // System.out.println("Received from client " + playerId + ": " + message); // DEBUG
        try {
            String[] parts = message.split(";");
            if (parts.length == 0) return;

            String command = parts[0];

            // Handle messages only AFTER player ID is assigned, except for CONNECT
            if (playerId == -1 && !command.equals(NetworkProtocol.CONNECT)) {
                System.err.println("Received message before registration: " + message);
                return; // Ignore messages until registered
            }


            switch (command) {
                case NetworkProtocol.CONNECT:
                    if (parts.length >= 2 && playerId == -1) { // Allow only once before registered
                        String name = parts[1];
                        // Validate name length/characters?
                        server.registerPlayer(this, name);
                    }
                    break;

                case NetworkProtocol.INPUT:
                    if (parts.length >= 5) {
                        boolean w = Boolean.parseBoolean(parts[1]);
                        boolean s = Boolean.parseBoolean(parts[2]);
                        boolean a = Boolean.parseBoolean(parts[3]);
                        boolean d = Boolean.parseBoolean(parts[4]);
                        server.handlePlayerInput(playerId, w, s, a, d);
                    }
                    break;

                case NetworkProtocol.SHOOT_CMD:
                    server.handlePlayerShoot(playerId);
                    break;

                // Handle PING later if needed
                // case NetworkProtocol.PING:
                //     sendMessage(NetworkProtocol.PONG);
                //     break;

                default:
                    System.out.println("Unknown message from client " + playerId + ": " + message);
            }
        } catch (Exception e) { // Catch parsing errors
            System.err.println("Error parsing client message from " + playerId + " ('" + message + "'): " + e.getMessage());
        }
    }

     */

    private void parseClientMessage(String message) {
        logger.debug("Received from client {}: {}", (playerId == -1 ? "UNKNOWN" : playerId), message); // Use Debug level
        try {
            String[] parts = message.split(";");
            if (parts.length == 0) return;

            String command = parts[0];

            // Handle messages only AFTER player ID is assigned, except for CONNECT
            if (playerId == -1 && !command.equals(NetworkProtocol.CONNECT)) {
                logger.warn("Received message before registration: {}", message);
                // Maybe close connection if protocol violated? For now, just ignore.
                return;
            }

            switch (command) {
                case NetworkProtocol.CONNECT:
                    if (parts.length >= 2) {
                        if (playerId != -1) {
                            logger.warn("Client {} sent duplicate CONNECT message.", playerId);
                            return; // Ignore duplicate attempts
                        }
                        String name = parts[1];
                        // Basic name validation (prevent protocol injection)
                        if (name.isEmpty() || name.contains(";") || name.length() > 16) {
                            logger.warn("Invalid player name received: '{}'", name);
                            sendMessage(NetworkProtocol.ERROR_MSG + ";Invalid player name");
                            closeConnection("Invalid player name");
                            return;
                        }
                        logger.info("Registration request from client: {}", name);
                        server.registerPlayer(this, name);
                    } else {
                        logger.warn("Malformed CONNECT message: {}", message);
                    }
                    break;

                // <<< ADD THESE CASES BACK >>>
                case NetworkProtocol.INPUT:
                    if (parts.length >= 5 && playerId != -1) {
                        try {
                            boolean w = Boolean.parseBoolean(parts[1]);
                            boolean s = Boolean.parseBoolean(parts[2]);
                            boolean a = Boolean.parseBoolean(parts[3]);
                            boolean d = Boolean.parseBoolean(parts[4]);
                            server.handlePlayerInput(playerId, w, s, a, d);
                        } catch (Exception e) {
                            logger.error("Error parsing INPUT message parts: {}", message, e);
                        }
                    } else if (playerId == -1) {
                        logger.warn("Received INPUT before registration: {}", message);
                    } else {
                        logger.warn("Malformed INPUT message: {}", message);
                    }
                    break;

                case NetworkProtocol.SHOOT_CMD:
                    if (playerId != -1) {
                        server.handlePlayerShoot(playerId);
                    } else {
                        logger.warn("Received SHOOT_CMD before registration: {}", message);
                    }
                    break;
                // <<< END OF ADDED CASES >>>

                // Handle PING later if needed
                // case NetworkProtocol.PING:
                //     sendMessage(NetworkProtocol.PONG);
                //     break;

                default:
                    logger.warn("Unknown message from client {}: {}", playerId, message);
            }
        } catch (Exception e) { // Catch broader parsing errors
            logger.error("Error parsing client message from {} ('{}'): {}", playerId, message, e.getMessage(), e);
        }
    }

    public void sendMessage(String message) {
        // Use trace level for high-frequency sending potentially
        logger.trace("Attempting to send message to client {}: {}", playerId, message);
        PrintWriter localOut;
        synchronized(connectionLock) {
            // Check shuttingDown flag here too
            if (out == null || shuttingDown) {
                logger.warn("Cannot send message to client {}: PrintWriter is null or shutting down.", playerId);
                return;
            }
            localOut = out; // Get reference under lock
        }
        // Perform potentially blocking IO outside the lock
        try {
            // Ensure output stream isn't closed before sending
            if (socket == null || socket.isOutputShutdown()) {
                logger.warn("Cannot send message to client {}: Output stream is shut down.", playerId);
                return;
            }

            logger.trace("Sending message via PrintWriter for client {}", playerId);
            localOut.println(message);
            // Check for potential errors during write/flush
            if (localOut.checkError()) {
                // This flag indicates an IOException occurred previously.
                logger.error("PrintWriter error flag set for client {} potentially before/during sending message: {}", playerId, message);
                // Force connection closure as the stream is likely broken.
                closeConnection("PrintWriter error");
            } else {
                logger.trace("Message sent via println for client {}. Flushing...", playerId);
                localOut.flush(); // Ensure data is sent immediately
                if (localOut.checkError()) {
                    logger.error("PrintWriter error flag set for client {} after flush for message: {}", playerId, message);
                    closeConnection("PrintWriter error after flush");
                } else {
                    logger.trace("Message flushed for client {}.", playerId);
                }
            }
        } catch (Exception e) {
            // Catch unexpected errors during send/flush outside the lock
            logger.error("Exception during sendMessage for client {}: {}", playerId, e.getMessage(), e);
            closeConnection("Exception during sendMessage: " + e.getMessage());
        }
    }

    public synchronized void closeConnection(String reason) {
        if (shuttingDown) {
            logger.debug("Handler for player {} already shutting down, reason: {}", playerId, reason);
            return; // Already being closed
        }
        shuttingDown = true; // Mark that shutdown has started
        running = false; // Signal the loop to stop
        logger.info("Closing connection for player {} ({}) because: {}", playerId, playerName, reason);

        // Close socket first
        Socket socketToClose = null;
        synchronized(connectionLock) {
            socketToClose = this.socket;
            this.socket = null;
        }
        if (socketToClose != null) {
            try {
                logger.debug("Closing handler socket for player {}...", playerId);
                socketToClose.close();
                logger.debug("Handler socket closed for player {}.", playerId);
            } catch (IOException e) {
                logger.error("Error closing handler socket for player {}", playerId, e);
            }
        }

        // Close streams
        synchronized(connectionLock) {
            try {
                if (out != null) { out.close(); }
            } catch (Exception e) { /* Ignore */ } finally { out = null; }
            try {
                if (in != null) { in.close(); }
            } catch (Exception e) { /* Ignore */ } finally { in = null; }
        }

        // Notify server about disconnection *after* attempting resource cleanup
        if (playerId != -1) {
            server.removePlayer(playerId); // This needs to be robust to being called during server shutdown too
        }

        logger.info("Finished closing connection for player {}", playerId);
    }

    public int getPlayerId() {
        return playerId;
    }

    public String getPlayerName() {
        return playerName;
    }

    public Socket getSocket() {
        return socket;
    }
}
