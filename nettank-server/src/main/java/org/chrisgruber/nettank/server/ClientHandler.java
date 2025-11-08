package org.chrisgruber.nettank.server;

import org.chrisgruber.nettank.common.network.NetworkProtocol;
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

    // Registration timeout
    private static final long REGISTRATION_TIMEOUT_MS = 5000; // 5 seconds
    private final long connectionStartTime;

    // Heartbeat timeout - configurable via system property or default to 30 seconds
    private static final long DEFAULT_HEARTBEAT_TIMEOUT_MS = 30000; // 30 seconds
    private final long heartbeatTimeoutMs;
    private volatile long lastActivityTime;

    // --- Send Queue ---
    private final BlockingQueue<String> sendQueue = new LinkedBlockingQueue<>();
    private Thread senderThread;
    private static final String POISON_PILL = "///POISON_PILL///"; // Special message to stop sender

    private static final String CLIENT_HANDLER_READER = "ClientHandler-Reader-";
    private static final String CLIENT_HANDLER_SENDER = "ClientHandler-Sender-";

    public ClientHandler(Socket socket, GameServer server) {
        this.socket = socket;
        this.server = server;
        this.connectionStartTime = System.currentTimeMillis();
        this.lastActivityTime = System.currentTimeMillis();
        
        // Allow configuring heartbeat timeout via system property
        long configuredTimeout = DEFAULT_HEARTBEAT_TIMEOUT_MS;
        String timeoutProperty = System.getProperty("nettank.heartbeat.timeout.ms");
        if (timeoutProperty != null) {
            try {
                configuredTimeout = Long.parseLong(timeoutProperty);
                logger.info("Using configured heartbeat timeout: {}ms", configuredTimeout);
            } catch (NumberFormatException e) {
                logger.warn("Invalid heartbeat timeout property, using default: {}ms", DEFAULT_HEARTBEAT_TIMEOUT_MS);
            }
        }
        this.heartbeatTimeoutMs = configuredTimeout;
    }

    public void setPlayerInfo(int id, String name) {
        this.playerId = id;
        this.playerName = name;

        // Set thread name now that we have player info
        Thread.currentThread().setName(CLIENT_HANDLER_READER + id + "-" + name);

        if (senderThread != null) {
            senderThread.setName(CLIENT_HANDLER_SENDER + id + "-" + name);
        }
    }

    // Reader Thread Logic (Original run method renamed)
    private void runReaderLoop() {
        // Name will be set in setPlayerInfo
        if (playerId == -1) {
            logger.debug("Client handler reader loop started.");
        } else {
            logger.info("Client handler reader loop started.");
        }
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
                long currentTime = System.currentTimeMillis();
                
                // Check registration timeout for unregistered clients
                if (playerId == -1 && (currentTime - connectionStartTime > REGISTRATION_TIMEOUT_MS)) {
                    logger.debug("Client failed to register within {}ms, closing connection", REGISTRATION_TIMEOUT_MS);
                    closeConnection("Registration timeout");
                    break;
                }
                
                // Check heartbeat timeout for registered players
                if (playerId != -1 && (currentTime - lastActivityTime > heartbeatTimeoutMs)) {
                    logger.warn("Player {} ({}ms) idle, no heartbeat received for {}ms, disconnecting", 
                            playerId, currentTime - lastActivityTime, heartbeatTimeoutMs);
                    closeConnection("Heartbeat timeout");
                    break;
                }

                clientMessage = null;
                try {
                    // Read OUTSIDE lock
                    clientMessage = localIn.readLine();

                    if (!running) break; // Check flag after a potential block

                    if (clientMessage == null) {
                        if (playerId == -1) {
                            logger.debug("Unregistered client disconnected (end of stream).");
                        } else {
                            logger.info("Client {} disconnected (end of stream).", playerId);
                        }
                        closeConnection("Client disconnected"); // Trigger cleanup
                        break;
                    }

                    logger.debug("Client {} received: {}", playerId, clientMessage);
                    
                    // Update last activity time on any message received
                    lastActivityTime = System.currentTimeMillis();
                    
                    parseClientMessage(clientMessage);

                } catch (SocketException e) {
                    if (running && !shuttingDown) {
                        if (playerId == -1) {
                            logger.debug("Socket error (read) for unregistered client: {}", e.getMessage());
                        } else {
                            logger.info("Socket error (read) for client {}: {}", playerId, e.getMessage());
                        }
                        closeConnection("Socket read error: " + e.getMessage()); // Trigger cleanup
                    } else {
                        if (playerId != -1) {
                            logger.info("SocketException (read) for client {} during shutdown.", playerId);
                        }
                    }
                    break; // Exit loop
                } catch (IOException e) {
                    if (running && !shuttingDown) {
                        if (playerId == -1) {
                            logger.debug("I/O error (read) for unregistered client: {}", e.getMessage());
                        } else {
                            logger.error("I/O error (read) for client {}: {}", playerId, e.getMessage());
                        }
                        closeConnection("IO read error: " + e.getMessage()); // Trigger cleanup
                    } else {
                        if (playerId != -1) {
                            logger.info("IOException (read) for client {} during shutdown.", playerId);
                        }
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
            running = false; // Ensure the flag is false
            if (playerId == -1) {
                logger.debug("Client handler reader loop finished for unregistered client.");
            } else {
                logger.info("Client handler reader loop finished for player {}.", playerId);
            }
            // Ensure the sender thread is stopped if the reader loop finishes
            stopSenderThread();
        }
    }

    // Sender Thread Logic
    private void runSenderLoop() {
        // Name might be set later by setPlayerInfo
        Thread.currentThread().setName(CLIENT_HANDLER_SENDER + playerId + "-" + (playerName != null ? playerName : "Connecting"));
        if (playerId == -1) {
            logger.debug("Client handler sender loop started for unregistered client.");
        } else {
            logger.info("Client handler sender loop started for player {}.", playerId);
        }

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
            if (playerId == -1) {
                logger.debug("Client handler sender loop finished for unregistered client.");
            } else {
                logger.info("Client handler sender loop finished for player {}.", playerId);
            }
            // If the sender stops, the connection is likely dead or closing. Trigger full close.
            if (running) { // Avoid double-closing if closeConnection was already called
                closeConnection("Sender loop finished unexpectedly");
            }
        }
    }

    @Override
    public void run() {
        String initialThreadName = "ClientHandler-Main-" + (socket != null ? socket.getPort() : "Unknown");
        Thread.currentThread().setName(initialThreadName);
        logger.debug("Client handler main thread started: {}", initialThreadName);
        running = true; // Set the running state here

        try {
            synchronized(connectionLock) {
                // Set up streams early - if this fails, we can't proceed
                out = new PrintWriter(socket.getOutputStream(), true);
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            }

            // Start the sender thread as virtual thread for I/O-bound sending
            senderThread = Thread.ofVirtual()
                .name(CLIENT_HANDLER_SENDER + playerId + "-Connecting")
                .start(this::runSenderLoop);

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
            if (playerId == -1) {
                logger.debug("Client handler main thread finished: {}", Thread.currentThread().getName());
            } else {
                logger.info("Client handler main thread finished: {}", Thread.currentThread().getName());
            }
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
        if (message == null) {
            logger.warn("Null message received from client {}", playerId);
            closeConnection("Null message received");
            return;
        }

        logger.debug("Received from client {}: {}", (playerId == -1 ? "UNKNOWN" : playerId), message);

        // Security check: message length
        final int maxMessageLength = 100;
        if (message.length() > maxMessageLength) {
            logger.warn("Message too long from client {}: {} chars", playerId, message.length());
            closeConnection("Message exceeds maximum length");
            return;
        }

        try {
            String[] parts = message.split(";");
            if (parts.length == 0) {
                logger.warn("Empty message received from client {}", playerId);
                return;
            }

            String command = parts[0];

            // Authenticate: Only allow CONNECT messages before player registration
            if (playerId == -1 && !NetworkProtocol.CONNECT.equals(command)) {
                logger.warn("Unauthorized message before registration: {}", message);
                closeConnection("Authentication required");
                return;
            }

            // Process based on command type
            switch (command) {
                case NetworkProtocol.CONNECT -> handleConnectMessage(parts);
                case NetworkProtocol.INPUT -> handleInputMessage(parts);
                case NetworkProtocol.SHOOT_CMD -> handleShootCommand();
                case NetworkProtocol.PING -> handlePingMessage();
                default -> {
                    logger.warn("Unknown command from client {}: {}", playerId, command);
                    sendMessage(NetworkProtocol.ERROR_MSG + ";Unknown command");
                }
            }
        } catch (Exception e) {
            logger.error("Error parsing message from client {} ('{}'): {}",
                    playerId, sanitizeLogMessage(message), e.getMessage(), e);
            closeConnection("Message parsing error");
        }
    }

    private String sanitizeLogMessage(String message) {
        // Prevent log injection by limiting length in logs
        return message.length() > 50 ? message.substring(0, 47) + "..." : message;
    }

    private void handleConnectMessage(String[] parts) {
        if (parts.length < 2) {
            logger.warn("Malformed CONNECT message from client {}", playerId);
            sendMessage(NetworkProtocol.ERROR_MSG + ";Invalid connection request");
            closeConnection("Malformed CONNECT message");
            return;
        }

        if (playerId != -1) {
            logger.warn("Client {} sent duplicate CONNECT message", playerId);
            return;
        }

        String name = parts[1];
        // Security check: Name validation
        if (!isValidPlayerName(name)) {
            logger.warn("Invalid player name received: '{}'", name);
            sendMessage(NetworkProtocol.ERROR_MSG + ";Invalid player name");
            closeConnection("Invalid player name");
            return;
        }

        logger.info("Registration request from client: {}", name);
        server.registerPlayer(this, name);
    }

    private boolean isValidPlayerName(String name) {
        return name != null && !name.isEmpty() && !name.contains(";") &&
                name.length() <= 16 && name.matches("^[\\w\\-\\s]+$");
    }

    private void handleInputMessage(String[] parts) {
        if (parts.length < 5) {
            logger.warn("Malformed INPUT message from client {}: missing parts", playerId);
            return;
        }

        try {
            boolean w = Boolean.parseBoolean(parts[1]);
            boolean s = Boolean.parseBoolean(parts[2]);
            boolean a = Boolean.parseBoolean(parts[3]);
            boolean d = Boolean.parseBoolean(parts[4]);
            server.handlePlayerMovementInput(playerId, w, s, a, d);
        } catch (Exception e) {
            logger.error("Error parsing INPUT parameters from client {}", playerId, e);
        }
    }

    private void handleShootCommand() {
        server.handlePlayerShootMainWeaponInput(playerId);
    }

    private void handlePingMessage() {
        // Heartbeat received - activity time already updated in the reader loop
        logger.trace("Heartbeat received from player {}", playerId);
        // Optionally send PONG response
        sendMessage(NetworkProtocol.PONG);
    }

    // Non-blocking: Adds a message to the queue for the sender thread
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
            sendQueue.clear();
            sendQueue.offer(POISON_PILL);
            senderThread.interrupt();

            try {
                // Wait briefly for the sender thread to die
                senderThread.join(500); // Wait max 500ms

                if (senderThread.isAlive()) {
                    if (playerId != -1) {
                        logger.warn("Sender thread for player {} did not terminate gracefully after interrupt and join.", playerId);
                    }
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

    public synchronized void closeConnection(String reason) {
        if (shuttingDown) {
            logger.trace("Handler for player {} already shutting down (Reason: {})", playerId, reason);
            return;
        }

        shuttingDown = true;
        running = false;
        if (playerId == -1) {
            logger.debug("Closing connection for unregistered client because: {}", reason);
        } else {
            logger.info("Closing connection for player {} ({}) because: {}", playerId, playerName, reason);
        }

        // Step 1: Stop sender thread
        stopSenderThread();

        // Step 2: Close socket (triggers SocketException in reader thread)
        closeSocketSafely();

        // Step 3: Close streams in correct order
        closeStreamsSafely();

        // Step 4: Notify server of player removal
        notifyServerOfRemoval();

        if (playerId == -1) {
            logger.debug("Finished closing connection procedures for unregistered client.");
        } else {
            logger.info("Finished closing connection procedures for player {}.", playerId);
        }
    }

    private void closeSocketSafely() {
        Socket socketToClose = this.socket;
        this.socket = null; // Prevent other threads from accessing

        if (socketToClose != null && !socketToClose.isClosed()) {
            try {
                logger.debug("Closing handler socket for player {}...", playerId);
                socketToClose.close();
                logger.debug("Handler socket closed for player {}.", playerId);
            } catch (IOException e) {
                logger.error("Error closing handler socket for player {}: {}", playerId, e.getMessage(), e);
            }
        } else {
            logger.debug("Handler socket for player {} was already null or closed.", playerId);
        }
    }

    private void closeStreamsSafely() {
        // Close input stream first (reading from closed socket is OK, writing is not)
        if (in != null) {
            try {
                in.close();
                logger.trace("Input stream closed for player {}", playerId);
            } catch (IOException e) {
                logger.debug("Error closing input stream for player {}: {}", playerId, e.getMessage());
            } finally {
                in = null;
            }
        }

        // Then close output stream
        if (out != null) {
            out.close(); // PrintWriter doesn't throw IOException
            out = null;
            logger.trace("Output stream closed for player {}", playerId);
        }
    }

    private void notifyServerOfRemoval() {
        if (playerId != -1) {
            try {
                server.removePlayer(playerId);
            } catch (Exception e) {
                logger.error("Error notifying server of player {} removal: {}",
                        playerId, e.getMessage(), e);
            }
        }
    }

    public int getPlayerId() {
        return playerId;
    }

    public Socket getSocket() {
        synchronized(connectionLock) {
            return socket;
        }
    }
}
