package org.chrisgruber.nettank.server;

import org.chrisgruber.nettank.common.network.NetworkProtocol;
import org.joml.Vector3f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.Socket;
import java.net.SocketException;
import java.util.concurrent.BlockingQueue; // Import
import java.util.concurrent.LinkedBlockingQueue; // Import
import java.util.concurrent.TimeUnit; // Import

public class ClientHandler implements Runnable {
    private static final Logger logger = LoggerFactory.getLogger(ClientHandler.class);
    private Socket socket;
    private final GameServer server;
    private PrintWriter out;
    private BufferedReader in;
    private final Object connectionLock = new Object(); // Keep for initial setup & reader thread access
    private volatile boolean running = false;
    private volatile boolean shuttingDown = false;

    // Player specific info
    private int playerId = -1;
    private String playerName = null;
    private Vector3f playerColor = null;

    // --- Send Queue ---
    private final BlockingQueue<String> sendQueue = new LinkedBlockingQueue<>();
    private Thread senderThread;
    private static final String POISON_PILL = "///POISON_PILL///"; // Special message to stop sender

    public ClientHandler(Socket socket, GameServer server) {
        this.socket = socket;
        this.server = server;
    }

    public void setPlayerInfo(int id, String name, Vector3f color) {
        this.playerId = id;
        this.playerName = name;
        this.playerColor = color;
        // Set thread name now that we have player info
        Thread.currentThread().setName("ClientHandler-Reader-" + id + "-" + name);
        if (senderThread != null) {
            senderThread.setName("ClientHandler-Sender-" + id + "-" + name);
        }
    }

    // Reader Thread Logic (Original run method renamed)
    private void runReaderLoop() {
        // Name will be set in setPlayerInfo
        logger.info("Client handler reader loop started.");
        running = true; // Mark as running

        try {
            BufferedReader localIn; // Use local reference
            synchronized(connectionLock) {
                if (in == null) { // Check if already closed during setup
                    logger.error("Input stream is null at reader start for client {}", playerId);
                    return; // Cannot proceed
                }
                localIn = in;
            }

            String clientMessage;
            while (running && !Thread.currentThread().isInterrupted()) {
                clientMessage = null;
                try {
                    // Read OUTSIDE lock
                    clientMessage = localIn.readLine();

                    if (!running) break; // Check flag after potential block

                    if (clientMessage == null) {
                        logger.info("Client {} disconnected (end of stream).", playerId);
                        closeConnection("Client disconnected"); // Trigger cleanup
                        break;
                    }

                    logger.debug("Client {} received: {}", playerId, clientMessage);
                    parseClientMessage(clientMessage);

                } catch (SocketException e) {
                    if (running && !shuttingDown) {
                        logger.info("Socket error (read) for client {}: {}", playerId, e.getMessage());
                        closeConnection("Socket read error: " + e.getMessage()); // Trigger cleanup
                    } else {
                        logger.info("SocketException (read) for client {} during shutdown.", playerId);
                    }
                    break; // Exit loop
                } catch (IOException e) {
                    if (running && !shuttingDown) {
                        logger.error("I/O error (read) for client {}: {}", playerId, e.getMessage());
                        closeConnection("IO read error: " + e.getMessage()); // Trigger cleanup
                    } else {
                        logger.info("IOException (read) for client {} during shutdown.", playerId);
                    }
                    break; // Exit loop
                } catch (Exception e) {
                    if (running) {
                        logger.error("Unexpected error in client reader loop for client {}. Message: '{}'", playerId, clientMessage, e);
                        // Decide whether to close or try to continue? Close for safety.
                        closeConnection("Reader loop error: " + e.getMessage());
                        break;
                    }
                }
            }
        } catch(Exception e) {
            // Catch errors during initial setup/getting reader
            logger.error("Critical error starting client reader loop for player {}", playerId, e);
            closeConnection("Reader start error: " + e.getMessage());
        } finally {
            running = false; // Ensure flag is false
            logger.info("Client handler reader loop finished for player {}.", playerId);
            // Ensure sender thread is stopped if reader loop finishes
            stopSenderThread();
        }
    }

    // Sender Thread Logic
    private void runSenderLoop() {
        // Name might be set later by setPlayerInfo
        Thread.currentThread().setName("ClientHandler-Sender-" + playerId + "-" + (playerName != null ? playerName : "Connecting"));
        logger.info("Client handler sender loop started for player {}.", playerId);

        PrintWriter localOut;
        Socket localSocket; // Needed for checking output shutdown status

        try {
            synchronized(connectionLock) {
                if (out == null || socket == null) {
                    logger.error("Output stream or socket is null at sender start for client {}", playerId);
                    return; // Cannot proceed
                }
                localOut = out;
                localSocket = socket;
            }

            while (!Thread.currentThread().isInterrupted()) {
                String message = null;
                try {
                    // Block until a message is available or interrupted
                    message = sendQueue.poll(1, TimeUnit.SECONDS); // Poll with timeout

                    if (message == null) {
                        // Timeout occurred, check if still running
                        if (!running && sendQueue.isEmpty()) {
                            logger.debug("Sender loop for {} timed out and not running, exiting.", playerId);
                            break;
                        }
                        continue; // Continue polling
                    }


                    if (POISON_PILL.equals(message)) {
                        logger.debug("Sender loop for {} received poison pill. Exiting.", playerId);
                        break; // Exit signal
                    }

                    // Perform blocking IO outside any lock shared with reader/main server thread
                    logger.trace("Sender loop for {} sending message: {}", playerId, message);
                    localOut.println(message);
                    localOut.flush(); // Ensure it's sent

                    if (localOut.checkError()) {
                        logger.error("PrintWriter error flag set for client {} after sending/flushing message: {}", playerId, message);
                        closeConnection("PrintWriter error"); // Trigger cleanup
                        break; // Exit sender loop
                    }
                    logger.trace("Sender loop for {} flushed message.", playerId);

                } catch (InterruptedException e) {
                    logger.info("Sender loop for {} interrupted. Exiting.", playerId);
                    Thread.currentThread().interrupt(); // Preserve interrupt status
                    break;
                } catch (Exception e) {
                    if (running && !shuttingDown) {
                        logger.error("Exception in sender loop for client {} processing message '{}'", playerId, message, e);
                        closeConnection("Sender loop error: " + e.getMessage()); // Trigger cleanup
                    } else {
                        logger.info("Exception in sender loop for client {} during shutdown.", playerId);
                    }
                    break; // Exit sender loop
                }
            }

        } catch (Exception e) {
            logger.error("Critical error starting or running client sender loop for player {}", playerId, e);
            closeConnection("Sender start/run error: " + e.getMessage());
        } finally {
            logger.info("Client handler sender loop finished for player {}.", playerId);
            // If sender stops, the connection is likely dead or closing. Trigger full close.
            if (running) { // Avoid double-closing if closeConnection was already called
                closeConnection("Sender loop finished unexpectedly");
            }
        }
    }

    @Override
    public void run() {
        String initialThreadName = "ClientHandler-Main-" + (socket != null ? socket.getPort() : "Unknown");
        Thread.currentThread().setName(initialThreadName);
        logger.info("Client handler main thread started: {}", initialThreadName);
        running = true; // Set running state here

        try {
            synchronized(connectionLock) {
                // Set up streams early - if this fails, we can't proceed
                out = new PrintWriter(socket.getOutputStream(), true);
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            }

            // Start the sender thread (now set as daemon in startClientThread's target Thread)
            senderThread = new Thread(this::runSenderLoop);
            senderThread.setName("ClientHandler-Sender-" + playerId + "-Connecting"); // Temporary name
            senderThread.setDaemon(true); // Explicitly set daemon here too
            senderThread.start();

            // Run the reader loop in the current thread (already set as daemon by GameServer)
            runReaderLoop();

        } catch (IOException e) {
            logger.error("Failed to set up streams for client handler {}: {}", initialThreadName, e.getMessage());
            closeConnection("Stream setup failed");
        } catch (Exception e) {
            logger.error("Unexpected error in ClientHandler main run method for {}", initialThreadName, e);
            closeConnection("Handler run error");
        } finally {
            // Ensure cleanup happens when run() exits (e.g., reader loop finishes)
            logger.info("Client handler main thread finished: {}", Thread.currentThread().getName());
            // Close connection if not already shutting down (e.g., reader exited normally)
            if(!shuttingDown) {
                closeConnection("Handler main thread finished");
            }
            // Explicitly nullify to help GC, though closeConnection should do it
            in = null;
            out = null;
            socket = null;
        }
    }


    private void parseClientMessage(String message) {
        logger.debug("Received from client {}: {}", (playerId == -1 ? "UNKNOWN" : playerId), message);

        int maxMessageLength = 100; // Define a maximum message length

        if (message.length() > maxMessageLength) {
            logger.warn("Received message size too long from client {}: {}", playerId, message);
            closeConnection("Threat detected. IP logged. Missile launched. __!__");
            return;
        }

        try {
            String[] parts = message.split(";");
            if (parts.length == 0) return;

            String command = parts[0];

            if (playerId == -1 && !command.equals(NetworkProtocol.CONNECT)) {
                logger.warn("Received message before registration: {}", message);
                return;
            }

            switch (command) {
                case NetworkProtocol.CONNECT:
                    if (parts.length >= 2) {
                        if (playerId != -1) {
                            logger.warn("Client {} sent duplicate CONNECT message.", playerId);
                            return;
                        }
                        String name = parts[1];
                        if (name.isEmpty() || name.contains(";") || name.length() > 16) {
                            logger.warn("Invalid player name received: '{}'", name);
                            sendMessage(NetworkProtocol.ERROR_MSG + ";Invalid player name"); // Add to queue
                            closeConnection("Invalid player name");
                            return;
                        }
                        logger.info("Registration request from client: {}", name);
                        // server.registerPlayer MUST be synchronized or handle concurrency carefully
                        server.registerPlayer(this, name);
                    } else {
                        logger.warn("Malformed CONNECT message: {}", message);
                    }
                    break;

                case NetworkProtocol.INPUT:
                    if (parts.length >= 5) {
                        try {
                            boolean w = Boolean.parseBoolean(parts[1]);
                            boolean s = Boolean.parseBoolean(parts[2]);
                            boolean a = Boolean.parseBoolean(parts[3]);
                            boolean d = Boolean.parseBoolean(parts[4]);
                            server.handlePlayerMovementInput(playerId, w, s, a, d);
                        } catch (Exception e) {
                            logger.error("Error parsing INPUT message parts: {}", message, e);
                        }
                    } else {
                        logger.warn("Received INPUT before registration or malformed INPUT message: {}", message);
                    }
                    break;

                case NetworkProtocol.SHOOT_CMD:
                    // server.handlePlayerShootMainWeaponInput might need synchronization
                    server.handlePlayerShootMainWeaponInput(playerId);
                    break;

                default:
                    logger.warn("Unknown message from client {}: {}", playerId, message);
            }
        } catch (Exception e) {
            logger.error("Error parsing client message from {} ('{}'): {}", playerId, message, e.getMessage(), e);
        }
    }

    // Non-blocking: Adds message to the queue for the sender thread
    public void sendMessage(String message) {
        if (!running || shuttingDown) {
            logger.warn("Attempted to queue message for {} but handler not running or shutting down: {}", playerId, message);
            return;
        }
        try {
            logger.trace("Queuing message for client {}: {}", playerId, message);
            // Offer might be better if queue could be bounded, but LinkedBlockingQueue is effectively unbounded
            sendQueue.put(message);
        } catch (InterruptedException e) {
            logger.warn("Interrupted while queuing message for client {}: {}", playerId, message);
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            // Should generally not happen with LinkedBlockingQueue unless maybe out of memory
            logger.error("Error queuing message for client {}: {}", playerId, message, e);
        }
    }

    // Stops the sender thread gracefully
    private void stopSenderThread() {
        logger.debug("Stopping sender thread for player {}...", playerId);
        if (senderThread != null && senderThread.isAlive()) {
            // Add poison pill to ensure sender loop exits even if interrupted exception is missed
            sendQueue.clear(); // Clear pending messages first? Optional.
            sendQueue.offer(POISON_PILL); // Use offer, don't block if queue is full (shouldn't be)
            senderThread.interrupt(); // Interrupt potentially blocked poll() or write()
            try {
                // Wait briefly for the sender thread to die
                senderThread.join(500); // Wait max 500ms
                if (senderThread.isAlive()) {
                    logger.warn("Sender thread for player {} did not terminate gracefully after interrupt and join.", playerId);
                } else {
                    logger.debug("Sender thread for player {} joined successfully.", playerId);
                }
            } catch (InterruptedException e) {
                logger.warn("Interrupted while joining sender thread for player {}.", playerId);
                Thread.currentThread().interrupt();
            }
        } else {
            logger.debug("Sender thread for player {} was null or not alive.", playerId);
        }
        senderThread = null;
    }


    // Close connection - needs to coordinate with reader and sender
    public synchronized void closeConnection(String reason) {
        if (shuttingDown) {
            logger.trace("Handler for player {} already shutting down (Reason: {})", playerId, reason); // Use Trace
            return;
        }
        shuttingDown = true;
        running = false;
        logger.info("Closing connection for player {} ({}) because: {}", playerId, playerName, reason);

        stopSenderThread(); // Stop sender

        // Close socket (reader thread will get SocketException)
        Socket socketToClose = this.socket; // Get ref before nullifying
        this.socket = null; // Nullify early
        if (socketToClose != null) {
            try {
                if (!socketToClose.isClosed()) { // Check if already closed
                    logger.debug("Closing handler socket for player {}...", playerId);
                    socketToClose.close();
                    logger.debug("Handler socket closed for player {}.", playerId);
                } else {
                    logger.debug("Handler socket for player {} was already closed.", playerId);
                }
            } catch (IOException e) { logger.error("Error closing handler socket for player {}", playerId, e); }
        }

        // Clean up streams (best effort)
        try { if (out != null) out.close(); } catch (Exception e) {} finally { out = null; }
        try { if (in != null) in.close(); } catch (Exception e) {} finally { in = null; }

        // Notify server - must be synchronized on GameServer if it modifies shared state directly
        if (playerId != -1) {
            server.removePlayer(playerId); // GameServer.removePlayer is synchronized
        }

        logger.info("Finished closing connection procedures for player {}.", playerId);
    }


    public int getPlayerId() {
        return playerId;
    }

    public String getPlayerName() {
        return playerName;
    }

    public Socket getSocket() {
        synchronized(connectionLock) {
            return socket;
        }
    }
}
