package org.chrisgruber.nettank.server;

// Imports using common package
import org.chrisgruber.nettank.common.entities.BulletData;
import org.chrisgruber.nettank.common.entities.TankData;
import org.chrisgruber.nettank.common.network.NetworkProtocol;
import org.chrisgruber.nettank.common.world.GameMapData;
import org.chrisgruber.nettank.common.util.Colors;
import org.chrisgruber.nettank.common.util.GameState;

import org.chrisgruber.nettank.server.gamemode.FreeForAll;
import org.chrisgruber.nettank.server.state.ServerContext;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class GameServer {
    private static final Logger logger = LoggerFactory.getLogger(GameServer.class);
    private final int port;
    private ServerSocket serverSocket;
    private Thread gameLoopThread;

    // Server-specific Constants
    public static final float TANK_MOVE_SPEED = 100.0f;
    public static final float TANK_TURN_SPEED = 100.0f;
    public static final float BULLET_SPEED = 350.0f;
    public static final long BULLET_LIFETIME_MS = 2000;
    public static final long TANK_SHOOT_COOLDOWN_MS = 500;
    private static final long STARTING_COUNTDOWN_SECONDS = 3;
    private static final long ROUND_END_DELAY_MS = 5000;

    private final List<Vector3f> availableColors;

    private final List<Thread> clientHandlerThreads = new CopyOnWriteArrayList<>();

    // Server context holds mutable state
    private final ServerContext serverContext = new ServerContext();

    public GameServer(int port) {
        this.port = port;
        this.serverContext.gameMode = new FreeForAll();
        this.serverContext.gameMapData = new GameMapData(50, 50, GameMapData.DEFAULT_TILE_SIZE);
        availableColors = Colors.generateDistinctColors(serverContext.gameMode.getMaxAllowedPlayers());
        Collections.shuffle(availableColors);
        Runtime.getRuntime().addShutdownHook(new Thread(this::stop, "ServerShutdownHook"));
    }

    public static void main(String[] args) {
        int port = 5555; // Default port
        if (args.length >= 1) {
            try { port = Integer.parseInt(args[0]); }
            catch (NumberFormatException e) { System.err.println("Invalid port: " + args[0] + ". Using default " + port); }
        }

        try {
            logger.info("Attempting to start server on port {}...", port);
            GameServer server = new GameServer(port);
            logger.info("Server object created.");
            server.start(); // Blocks until server stops
            logger.info("GameServer.main() finished after server.start() returned.");
        } catch (IOException e) {
            logger.error("!!! Server failed to start on port {} !!!", port, e);
            System.err.println("FATAL: Server failed to start - " + e.getMessage());
            System.exit(1);
        } catch (Exception e) {
            logger.error("!!! Unexpected error during server startup !!!", e);
            System.err.println("FATAL: Unexpected server error - " + e.getMessage());
            System.exit(1);
        }
        logger.info("GameServer.main() is exiting naturally.");
        // Removed System.exit(0) for natural JVM exit
    }

    public void start() throws IOException {
        serverSocket = new ServerSocket(port, 50, InetAddress.getByName("0.0.0.0"));
        serverContext.running = true;

        logger.info("Server started on {}:{}", serverSocket.getInetAddress().getHostAddress(), port);

        gameLoopThread = new Thread(this::gameLoop);
        gameLoopThread.setName("GameLoop");
        gameLoopThread.setDaemon(true); // Game loop shouldn't prevent exit
        gameLoopThread.start();

        logger.info("Waiting for client connections...");

        try {
            while (serverContext.running) {
                try {
                    Socket clientSocket = serverSocket.accept(); // Blocks
                    if (!serverContext.running) break; // Check flag after unblocking
                    logger.info("Client connected: {}", clientSocket.getInetAddress().getHostAddress());
                    ClientHandler clientHandler = new ClientHandler(clientSocket, this);
                    startClientThread(clientHandler);
                } catch (SocketException e) {
                    if (serverContext.running) { logger.error("Server socket accept error: {}", e.getMessage());}
                    else { logger.info("Server socket closed, accept loop terminating."); }
                    // Loop condition (running) handles exit
                } catch (IOException e) {
                    if (serverContext.running) { logger.error("Error accepting client connection", e); }
                } catch (Exception e){
                    if(serverContext.running) { logger.error("Unexpected error in accept loop", e); }
                }
            }
        } finally {
            logger.info("Server accept loop finished.");
            // Wait for game loop thread (moved from stop() to ensure it happens before main exits)
            if (gameLoopThread != null && gameLoopThread.isAlive()) {
                logger.info("Waiting for GameLoop thread to finish...");
                try {
                    gameLoopThread.join(2000);
                    if (gameLoopThread.isAlive()) { logger.warn("GameLoop thread did not finish gracefully after 2 seconds."); }
                    else { logger.info("GameLoop thread finished."); }
                } catch (InterruptedException e) {
                    logger.warn("Interrupted while waiting for GameLoop thread.");
                    Thread.currentThread().interrupt();
                }
            }
        }

        logger.info("Server main thread exiting start() method.");
    }

    // --- Server Shutdown ---
    // This method is called by the shutdown hook.
    public void stop() {
        if (!serverContext.stopping.compareAndSet(false, true)) {
            logger.info("Server stop() already in progress or completed.");
            return;
        }
        logger.info("Server stop() sequence initiated...");
        serverContext.running = false;

        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                logger.debug("Closing server socket...");
                serverSocket.close();
                logger.info("Server socket closed.");
            }
        } catch (IOException e) { logger.error("Error closing server socket.", e); }

        // Interrupt game loop first to stop updates quickly
        if (gameLoopThread != null && gameLoopThread.isAlive()) {
            logger.debug("Interrupting GameLoop thread...");
            gameLoopThread.interrupt();
            // Join moved to start() finally block
        }

        logger.info("Closing client connections...");
        List<ClientHandler> handlersToClose = new ArrayList<>(serverContext.clients.values());
        logger.info("Found {} handlers to close.", handlersToClose.size());
        for (ClientHandler handler : handlersToClose) {
            if (handler != null) { handler.closeConnection("Server shutting down"); }
        }

        // Clear state
        serverContext.clients.clear();
        serverContext.tanks.clear();
        serverContext.bullets.clear();
        serverContext.nextPlayerId.set(0);

        logger.info("Interrupting any potentially lingering client handler main threads...");
        List<Thread> threadsToInterrupt = new ArrayList<>(clientHandlerThreads);
        for (Thread t : threadsToInterrupt) {
            if (t != null && t.isAlive()) {
                logger.debug("Interrupting client handler main thread: {}", t.getName());
                t.interrupt();
            }
        }
        clientHandlerThreads.clear();

        logger.info("Server stop() sequence finished.");
    }


    // --- Client Thread Management ---
    // This method is called by the start() method to start a new client handler thread for a new player connecting.
    private void startClientThread(ClientHandler handler) {
        Thread thread = new Thread(handler);
        int tempId = handler.getSocket().getPort();
        thread.setName("ClientHandler-Main-" + tempId); // Initial name
        thread.setDaemon(true); // Client handlers shouldn't prevent exit
        thread.setUncaughtExceptionHandler((t, e) -> logger.error("Uncaught exception in thread {}: {}", t.getName(), e.getMessage(), e));
        clientHandlerThreads.add(thread);
        thread.start();
        logger.info("Started client handler main thread: {}", thread.getName());
    }

    // --- Registration & Removal ---

    public synchronized void registerPlayer(ClientHandler handler, String playerName) {
        if (serverContext.clients.size() >= serverContext.gameMode.getMaxAllowedPlayers()) {
            handler.sendMessage(NetworkProtocol.ERROR_MSG + ";Server full");
            handler.closeConnection("Server full"); return;
        }
        if (availableColors.isEmpty()) {
            handler.sendMessage(NetworkProtocol.ERROR_MSG + ";No colors available");
            handler.closeConnection("No colors available"); return;
        }

        int playerId = serverContext.nextPlayerId.getAndIncrement();

        Vector3f assignedColor = availableColors.removeFirst();

        handler.setPlayerInfo(playerId, playerName, assignedColor);

        Vector2f spawnPos = serverContext.gameMapData.getRandomSpawnPoint();

        TankData newTankData = new TankData(playerId, spawnPos.x, spawnPos.y, assignedColor, playerName);
        newTankData.setLives(TankData.INITIAL_LIVES);
        serverContext.clients.put(playerId, handler);
        serverContext.tanks.put(playerId, newTankData);

        logger.info("Player registered: ID={}, Name={}, Color={}", playerId, playerName, assignedColor);

        // Send ASSIGN_ID (5 parts now)
        handler.sendMessage(String.format("%s;%d;%f;%f;%f",
                NetworkProtocol.ASSIGN_ID, playerId, assignedColor.x, assignedColor.y, assignedColor.z));

        logger.info("Sent ASSIGN_ID to player ID {}: {}", playerId, handler.getSocket().getInetAddress().getHostAddress());

        handler.sendMessage(String.format("%s;%s;%d",
                NetworkProtocol.GAME_STATE,
                serverContext.currentGameState.name(),
                getTimeDataForGameState(serverContext.currentGameState)));

        logger.info("Sent GAME_STATE to player ID {}: {}", playerId, handler.getSocket().getInetAddress().getHostAddress());

        if (serverContext.currentGameState == GameState.PLAYING) {
            serverContext.gameMode.handleNewPlayerJoinWhileGameInProgress(serverContext, playerId, playerName, newTankData);
        }

        // Send all tanks and their lives to new player
        for (TankData tankData : serverContext.tanks.values()) {
            handler.sendMessage(String.format("%s;%d;%f;%f;%f;%s;%f;%f;%f",
                    NetworkProtocol.NEW_PLAYER, tankData.playerId, tankData.position.x, tankData.position.y, tankData.rotation,
                    tankData.name, tankData.color.x, tankData.color.y, tankData.color.z));
            handler.sendMessage(String.format("%s;%d;%d", NetworkProtocol.PLAYER_LIVES, tankData.playerId, tankData.lives));
        }

        logger.info("Sent existing player's tanks to new player ID {}: {}", playerId, handler.getSocket().getInetAddress().getHostAddress());

        // Inform others about new player & lives
        String newPlayerMsg = String.format("%s;%d;%f;%f;%f;%s;%f;%f;%f",
                NetworkProtocol.NEW_PLAYER, newTankData.playerId, newTankData.position.x, newTankData.position.y, newTankData.rotation,
                newTankData.name, newTankData.color.x, newTankData.color.y, newTankData.color.z);
        String livesMsg = String.format("%s;%d;%d", NetworkProtocol.PLAYER_LIVES, newTankData.playerId, newTankData.lives);
        broadcast(newPlayerMsg, playerId);
        broadcast(livesMsg, playerId);

        logger.info("Broadcasted new player info to others: ID={}, Name={}", playerId, playerName);

        checkGameStateTransition();

        logger.info("Checked game state transition after player registration.");
    }

    private long getTimeDataForGameState(GameState state) {
        return switch (state) {
            case PLAYING -> serverContext.roundStartTimeMillis;
            case COUNTDOWN -> serverContext.stateChangeTime + STARTING_COUNTDOWN_SECONDS * 1000;
            default -> 0;
        };
    }

    public synchronized void removePlayer(int playerId) {
        ClientHandler handler = serverContext.clients.remove(playerId);
        TankData tankData = serverContext.tanks.remove(playerId);

        if (handler != null && tankData != null) {
            logger.info("Player removed: ID={}, Name={}", playerId, tankData.name);
            if (tankData.color != null) { availableColors.add(tankData.color); Collections.shuffle(availableColors); }
            broadcast(NetworkProtocol.PLAYER_LEFT + ";" + playerId, -1);
            checkGameStateTransition();
        } else {
            // logger.warn("Attempted to remove player {}, but handler or tankData was already null.", playerId); // Reduce noise
        }

        if (serverContext.getPlayerCount() == 0) {
            serverContext.currentGameState = GameState.WAITING; // Reset state if no players left
        }
    }

    // --- Game Loop & Logic ---

    // The game loop runs at a fixed time step (60 FPS) with sleep and handles game logic updates.
    private void gameLoop() {
        long lastTime = System.nanoTime();
        double nsPerTick = 1_000_000_000.0 / 60.0;  // 60 ticks per second
        double delta = 0;   // Time accumulator for game updates

        logger.trace("GameLoop thread started.");

        while (serverContext.running) {
            long now = System.nanoTime();
            delta += Math.min((now - lastTime) / nsPerTick, 10.0);
            lastTime = now;

            delta = processGameUpdates(delta);

            try {
                if (!serverContext.running) break;
                // sleep for 1ms to yield CPU time
                Thread.sleep(1);
            } catch (InterruptedException e) {
                logger.info("Game loop interrupted (likely server shutdown).");
                Thread.currentThread().interrupt(); // Preserve interrupt status
            } catch (Exception e) {
                if (serverContext.running) { logger.error("Unexpected error in GameLoop sleep/wait phase", e); }
            }
        }

        logger.trace("GameLoop thread finished.");
    }

    // Manages when and how often the game logic is mutated, ensuring a consistent experience regardless of the frame rate.
    private synchronized double processGameUpdates(double delta) {
        // "delta" is the amount of accumulated time since the last update and is measured in "ticks". 1 tick = one full update cycle
        boolean updated = false;

        // While delta is >= 1.0, call updateGameLogic() to process game logic
        while (delta >= 1.0) {
            // Call updateGameLogic() with fixed time step. Each call represents a game tick, 1/60th of a second of game time.
            updateGameLogic(1.0f / 60.0f);
            delta -= 1.0;
            updated = true;
        }

        if (updated) {
            // Broadcast state to all players after calling updateGameLogic() changes state
            broadcastState();
        }

        return delta;
    }

    private synchronized void updateGameLogic(float deltaTime) {
        long currentTime = System.currentTimeMillis();

        logger.trace("Processing game state transitions. Current state: {}, Time: {}", serverContext.currentGameState, currentTime);

        handleGameStateTransitions(currentTime);

        logger.trace("Game state transitions processed. Current state: {}, Time: {}", serverContext.currentGameState, currentTime);

        if (serverContext.currentGameState != GameState.PLAYING) return;    // Only update game state if in PLAYING state

        logger.trace("Game is in PLAYING state. Updating game logic. Time: {}", currentTime);

        // Update Tanks
        for (TankData tankData : serverContext.tanks.values()) {
            if (!tankData.alive) continue;  // No need to update dead tanks
            Vector2f oldPos = new Vector2f(tankData.position);
            // Movement Logic
            float turnAmount = 0;
            if (tankData.turningLeft) turnAmount += TANK_TURN_SPEED * deltaTime;
            if (tankData.turningRight) turnAmount -= TANK_TURN_SPEED * deltaTime;
            tankData.rotation += turnAmount;
            tankData.rotation = (tankData.rotation % 360.0f + 360.0f) % 360.0f;

            float moveAmount = 0;
            if (tankData.movingForward) moveAmount = TANK_MOVE_SPEED * deltaTime;
            else if (tankData.movingBackward) moveAmount = -TANK_MOVE_SPEED * deltaTime * 0.7f;

            if (moveAmount != 0) {
                float angleRad = (float) Math.toRadians(tankData.rotation);
                float dx = (float) -Math.sin(angleRad) * moveAmount;
                float dy = (float) Math.cos(angleRad) * moveAmount; // Assuming 0=UP, +Y=UP (adjust if needed)
                tankData.addPosition(dx, dy);
            }

            // Bounds Check
            if (serverContext.gameMapData.isOutOfBounds(tankData.position.x, tankData.position.y, TankData.COLLISION_RADIUS)) {
                tankData.setPosition(oldPos.x, oldPos.y);
                logger.warn("PlayerId: {}. Player tank moved out of map bounds and was reset to previous position.", tankData.playerId);
            }

            checkGameStateTransition();
        }

        logger.trace("Tanks updated. Current state: {}, Time: {}", serverContext.currentGameState, currentTime);

        // Update Bullets
        List<BulletData> bulletsToRemove = new ArrayList<>();
        for (BulletData bulletData : serverContext.bullets) {
            bulletData.position.add(bulletData.velocity.x * deltaTime, bulletData.velocity.y * deltaTime);
            boolean expired = (currentTime - bulletData.spawnTime) >= BULLET_LIFETIME_MS;
            if (expired || serverContext.gameMapData.isOutOfBounds(bulletData.position.x, bulletData.position.y, BulletData.SIZE / 2.0f)) {
                bulletsToRemove.add(bulletData);
            }
        }

        logger.trace("Bullets updated. Current state: {}, Time: {}", serverContext.currentGameState, currentTime);

        // Collision Check
        for (BulletData bulletData : serverContext.bullets) {
            if (bulletsToRemove.contains(bulletData)) continue; // Skip if already marked for removal
            for (TankData tankData : serverContext.tanks.values()) {
                // if (!tankData.alive || tankData.playerId == bulletData.ownerId) continue; // Skip if tank is dead or bullet owner is the same as tank
                if (bulletData.position.distanceSquared(tankData.position) < Math.pow(TankData.COLLISION_RADIUS + BulletData.SIZE / 2.0f, 2)) {
                    handleHit(tankData, bulletData);
                    bulletsToRemove.add(bulletData);
                    break;
                }
            }
        }
        serverContext.bullets.removeAll(bulletsToRemove);

        logger.trace("Bullets collisions processed. Bullets removed: {} Current state: {}, Time: {}", bulletsToRemove.size(), serverContext.currentGameState, currentTime);

        checkWinCondition();

        logger.trace("Game logic update complete. Current state: {}, Time: {}", serverContext.currentGameState, currentTime);
    }

    private synchronized void handleHit(TankData target, BulletData bulletData) {
        TankData shooter = serverContext.tanks.get(bulletData.ownerId);
        String shooterName = (shooter != null) ? shooter.name : "Unknown";
        String targetName = target.name;
        logger.info("Hit registered: {} -> {}", shooterName, targetName);

        target.takeHit();
        broadcast(String.format("%s;%d;%d", NetworkProtocol.HIT, target.playerId, bulletData.ownerId), -1);
        broadcast(String.format("%s;%d;%d", NetworkProtocol.PLAYER_LIVES, target.playerId, target.lives), -1);
        broadcastAnnouncement(shooterName + " KILLED " + targetName, -1);

        if (!target.alive) {
            logger.info("{} was defeated.", targetName);
            broadcast(String.format("%s;%d;%d", NetworkProtocol.DESTROYED, target.playerId, bulletData.ownerId), -1);
            broadcastAnnouncement(targetName + " HAS BEEN DEFEATED!", -1);
        }
    }

    // Process player movement input and set the tank's movement state
    public synchronized void handlePlayerMovementInput(int playerId, boolean w, boolean s, boolean a, boolean d) {
        if (serverContext.currentGameState != GameState.PLAYING) {
            logger.warn("Unable to process tank movement input for playerId: {} because the game is not in PLAYING state.", playerId);
            return;
        }

        TankData tankData = serverContext.tanks.get(playerId);

        if (tankData == null) {
            logger.error("Unable to process tank movement input for playerId: {} because no tank data was found.", playerId);
            return;
        }

        if (!tankData.isAlive()) {
            logger.warn("Unable to process tank movement input for playerId: {} because the tank is not alive.", playerId);
            return;
        }

        tankData.setInputState(w, s, a, d);

        logger.debug("Processed movement input for PlayerId: {} input controls state was -> w:{}, s:{}, a:{}, d:{}", playerId, w, s, a, d);
    }

    // Process player main weapon shoot input and shoot a bullet if possible
    public synchronized void handlePlayerShootMainWeaponInput(int playerId) {
        if (serverContext.currentGameState != GameState.PLAYING) {
            logger.warn("Unable to process shoot input for playerId: {} because the game is not in PLAYING state.", playerId);
            return;
        }

        TankData tankData = serverContext.tanks.get(playerId);

        if (tankData == null) {
            logger.error("Unable to process shoot input for playerId: {} because no tank data was found.", playerId);
            return;
        }

        if (!tankData.isAlive()) {
            logger.warn("Unable to process shoot input for playerId: {} because the tank is not alive.", playerId);
            return;
        }

        long currentTime = System.currentTimeMillis();

        // Check main weapon cooldown to see if the tank can shoot
        boolean hasCooledDown = currentTime - tankData.lastShotTime >= TANK_SHOOT_COOLDOWN_MS;

        if (!hasCooledDown) {
            var cooldownTimeRemainingInMilliseconds = TANK_SHOOT_COOLDOWN_MS - (currentTime - tankData.lastShotTime);
            var timeSinceLastShotInMilliseconds = currentTime - tankData.lastShotTime;
            logger.warn("PlayerId: {} attempted to shoot but the weapon is still cooling down. Time since last shot: {}ms. Cooldown time remaining: {}ms", playerId, timeSinceLastShotInMilliseconds, cooldownTimeRemainingInMilliseconds);
            return;
        }

        tankData.recordShot(currentTime);

        float angleRad = (float) Math.toRadians(tankData.rotation);
        float dirX = (float) -Math.sin(angleRad);
        float dirY = (float) Math.cos(angleRad);

        float spawnDist = TankData.SIZE / 2.0f + BulletData.SIZE / 2.0f; // Spawn slightly ahead
        float startX = tankData.position.x + dirX * spawnDist;
        float startY = tankData.position.y + dirY * spawnDist;

        Vector2f velocity = new Vector2f(dirX, dirY).normalize().mul(BULLET_SPEED);

        // Create BulletData object
        BulletData bullet = new BulletData(playerId, startX, startY, velocity.x, velocity.y, currentTime);
        serverContext.bullets.add(bullet);

        logger.debug("PlayerId: {} shot a bullet at position ({}, {}) with direction ({}, {})", playerId, startX, startY, dirX, dirY);

        // Broadcast with calculated dirX, dirY
        broadcast(String.format("%s;%d;%f;%f;%f;%f", NetworkProtocol.SHOOT, playerId, startX, startY, dirX, dirY), -1);
    }

    // Processes game state transitions based on game state conditions
    private synchronized void handleGameStateTransitions(long currentTime) {
        int totalPlayers = serverContext.getPlayerCount();
        int minPlayers = serverContext.gameMode.getMinRequiredPlayers();

        logger.trace("Handling game state change: state={}, time={}, players={}",
                serverContext.currentGameState, currentTime, totalPlayers);

        switch (serverContext.currentGameState) {
            case WAITING:
                logger.trace("In WAITING state: current={}, min={}, max={}",
                        totalPlayers, minPlayers, serverContext.gameMode.getMaxAllowedPlayers());

                if (totalPlayers >= minPlayers) {
                    logger.debug("Transitioning to COUNTDOWN: player count >= min required");
                    changeState(GameState.COUNTDOWN, currentTime);
                }
                break;

            case COUNTDOWN:
                if (totalPlayers < minPlayers) {
                    logger.info("Transitioning from COUNTDOWN to WAITING: because player count: {}  < min required: {}", totalPlayers, minPlayers);
                    changeState(GameState.WAITING, currentTime);
                } else if (currentTime >= serverContext.stateChangeTime + STARTING_COUNTDOWN_SECONDS * 1000) {
                    resetPlayersForNewRound();
                    serverContext.roundStartTimeMillis = currentTime;
                    changeState(GameState.PLAYING, currentTime);
                }
                break;

            case PLAYING:
                // checkWinCondition handles transition out of PLAYING game state
                break;

            case ROUND_OVER:
                boolean roundEndTimeReached = currentTime >= serverContext.stateChangeTime + ROUND_END_DELAY_MS;
                if (roundEndTimeReached) {
                    GameState nextState = (totalPlayers >= minPlayers) ? GameState.COUNTDOWN : GameState.WAITING;
                    changeState(nextState, currentTime);
                }
                break;

            default:
                logger.warn("Unhandled game state: {}", serverContext.currentGameState);
                break;
        }
    }

    // Handles the state change and broadcasts the new state to all clients
    private synchronized void changeState(GameState newState, long timeData) {
        // Early return if state isn't changing
        if (serverContext.currentGameState == newState) {
            return;
        }

        logger.info("Server changing state from {} to {}", serverContext.currentGameState, newState);

        // Update server context state
        serverContext.currentGameState = newState;
        serverContext.stateChangeTime = System.currentTimeMillis();

        // Calculate appropriate time data for client notification
        long broadcastTimeData = calculateBroadcastTimeData(newState, timeData);

        // Broadcast state change to all clients
        broadcast(String.format("%s;%s;%d",
                NetworkProtocol.GAME_STATE, newState.name(), broadcastTimeData), -1);

        // Send appropriate announcements based on new state
        sendStateAnnouncement(newState);
    }

    // Helper method to calculate the appropriate time data to broadcast
    private long calculateBroadcastTimeData(GameState state, long timeData) {
        return switch (state) {
            case PLAYING -> timeData; // Send start time
            case COUNTDOWN -> serverContext.stateChangeTime + STARTING_COUNTDOWN_SECONDS * 1000; // Send target end time
            case ROUND_OVER -> timeData; // For ROUND_OVER, timeData should be duration
            default -> 0;
        };
    }

    // Helper method to send state-specific announcements
    private void sendStateAnnouncement(GameState state) {
        switch (state) {
            case WAITING -> broadcastAnnouncement("WAITING FOR PLAYERS...", -1);
            case COUNTDOWN -> broadcastAnnouncement("ROUND STARTING IN " + STARTING_COUNTDOWN_SECONDS + "...", -1);
            case PLAYING -> {
                // No announcement needed for PLAYING state
                logger.info("PLAYING state reached, no announcement to broadcast.");
            }
            case ROUND_OVER -> {
                // Announcement for round over is handled in checkWinCondition
                logger.info("ROUND_OVER state reached, no announcement to broadcast.");
            }
            default -> throw new IllegalStateException("Unexpected value: " + state);
        }
    }

    // Resets player state for a new round
    private void resetPlayersForNewRound() {
        logger.info("Resetting players for new round.");

        serverContext.bullets.clear();

        for(TankData tankData : serverContext.tanks.values()) {
            Vector2f spawnPos = serverContext.gameMapData.getRandomSpawnPoint();
            tankData.setPosition(spawnPos.x, spawnPos.y);
            tankData.setRotation(0);
            tankData.setLives(serverContext.gameMode.getTotalRespawnsAllowed());
            tankData.setInputState(false, false, false, false);
            tankData.lastShotTime = 0; // Reset shot timer
            broadcast(String.format("%s;%d;%f;%f", NetworkProtocol.RESPAWN, tankData.playerId, spawnPos.x, spawnPos.y), -1);
            broadcast(String.format("%s;%d;%d", NetworkProtocol.PLAYER_LIVES, tankData.playerId, tankData.lives), -1);
        }
    }

    // Save this code for making a future game mode that ends the round when only one player is left
    /*
    private synchronized void checkWinCondition() {
        if (serverContext.currentGameState != GameState.PLAYING) return;

        int aliveCount = 0;
        TankData lastAliveTank = null;
        int totalPlayers = serverContext.getPlayerCount();

        // --- Revised Logic ---
        // If MIN_PLAYERS_TO_START is 1, and only 1 player is connected, NEVER end the round by elimination.
        if (totalPlayers == 1 && serverContext.gameMode.getMinRequiredPlayers() == 1) {
            logger.trace("Skipping win condition check: Single player mode active.");
            return;
        }

        // If MIN_PLAYERS_TO_START is >= 2, only check if at least that many players are present.
        if (totalPlayers < serverContext.gameMode.getMinRequiredPlayers()) {
            logger.trace("Skipping win condition check: Fewer than minimum players connected ({}/{})",
                    totalPlayers, serverContext.gameMode.getMinRequiredPlayers());
            return;
        }

        // --- Count alive players (only if we passed the initial checks) ---
        for (TankData tankData : serverContext.tanks.values()) {
            if (tankData.alive) {
                aliveCount++;
                lastAliveTank = tankData;
            }
        }

        // --- End round based on elimination (only if >= MIN_PLAYERS_TO_START were present) ---
        if (aliveCount <= 1) {
            logger.info("Round over condition met (Alive: {}, Total: {}).", aliveCount, totalPlayers);
            long finalTime = System.currentTimeMillis() - serverContext.roundStartTimeMillis;
            changeState(GameState.ROUND_OVER, finalTime); // Pass duration

            String winnerName = "NO ONE"; int winnerId = -1;
            if (lastAliveTank != null) { winnerName = lastAliveTank.name; winnerId = lastAliveTank.playerId; logger.info("Winner: {} (ID: {}).", winnerName, winnerId); }
            else { logger.info("Round ended in a draw."); }

            broadcast(String.format("%s;%d;%s;%d", NetworkProtocol.ROUND_OVER, winnerId, winnerName, finalTime), -1);
            broadcastAnnouncement( (winnerId != -1 ? winnerName + " WINS!" : "DRAW!") + " FINAL TIME: " + formatTime(finalTime), -1);
        }
    }
    */

    // Check if the win condition is met and handle the end of the round
    private synchronized void checkWinCondition() {
        if (serverContext.currentGameState != GameState.PLAYING) {
            logger.warn("Skipping win condition check: Game state is {} not PLAYING.", serverContext.currentGameState);
            return;
        }

        var isVictory = serverContext.gameMode.checkIsGameConditionMet(serverContext);

        if (isVictory) {
            logger.info("Round met win condition.");
        }
    }

    private void checkGameStateTransition() {
        long currentTime = System.currentTimeMillis();

        if (serverContext.currentGameState == GameState.WAITING || serverContext.currentGameState == GameState.COUNTDOWN) {
            handleGameStateTransitions(currentTime);
        }
        else if (serverContext.currentGameState == GameState.PLAYING) {
            checkWinCondition();
        }
    }

    // Broadcasts the current game state to all players
    private void broadcastState() {
        if (serverContext.currentGameState != GameState.PLAYING) {
            // Do not broadcast state if not in PLAYING state
            logger.trace("Skipping broadcastState: Game state is {} not PLAYING.", serverContext.currentGameState);
            return;
        }

        for (TankData tankData : serverContext.tanks.values()) {
            if (tankData == null) {
                logger.error("Skipping broadcastState: tankData is null");
                continue;
            }

            // Save this code and decide if a dead player should still see game state as a spectator, if spectator feature allowed
            /*
            if (!tankData.isAlive()) {
                logger.debug("Skipping broadcastState: tankData is not alive for playerId {}", tankData.playerId);
                continue;
            }
            */

            broadcast(String.format("%s;%d;%f;%f;%f",
                    NetworkProtocol.PLAYER_UPDATE, tankData.playerId, tankData.position.x, tankData.position.y, tankData.rotation), -1);
        }
    }

    // Broadcasts a message to all players, excluding the specified player IDs
    public void broadcast(String message, List<Integer> excludedPlayerIds) {
        for (var excludePlayerId : excludedPlayerIds) {
            broadcast(message, excludePlayerId);
        }
    }

    // Broadcasts a message to all players, excluding the specified player ID
    public void broadcast(String message, int excludePlayerId) {
        logger.trace("Broadcasting (exclude {}): {}", excludePlayerId, message);

        if (serverContext.clients.isEmpty()) {
            logger.warn("Skipping broadcast message: no clients to send to.");
            return;
        }

        for (ClientHandler handler : serverContext.clients.values()) {
            if (handler == null) {
                logger.error("Skipping broadcast message: handler is null for playerId {}", excludePlayerId);
                continue;
            }

            if (handler.getPlayerId() == excludePlayerId) {
                logger.trace("Skipping broadcast message to excluded player ID {}: {}", excludePlayerId, message);
                continue;
            }

            logger.trace("Sending to client ID {}: {}", handler.getPlayerId(), message);

            handler.sendMessage(message);
        }

        logger.trace("Broadcast message sent to all clients except player ID {}: {}", excludePlayerId, message);
    }

    // Broadcasts an announcement to all players, excluding the specified player IDs
    public void broadcastAnnouncement(String announcement, List<Integer> excludePlayerIds) {
        for (var excludePlayerId : excludePlayerIds) {
            broadcast(announcement, excludePlayerId);
        }
    }

    // Broadcasts an announcement to all players, excluding the specified player ID
    public void broadcastAnnouncement(String announcement, int excludePlayerId) {
        broadcast(NetworkProtocol.ANNOUNCE + ";" + announcement, excludePlayerId);
    }

    private String formatTime(long millis) {
        long seconds = (millis / 1000) % 60;
        long minutes = (millis / (1000 * 60)) % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }
}