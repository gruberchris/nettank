package org.chrisgruber.nettank.server;

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
    public static final float TANK_TURN_SPEED = 50.0f;
    public static final float BULLET_SPEED = 350.0f;
    public static final long BULLET_LIFETIME_MS = 2000;
    public static final long TANK_SHOOT_COOLDOWN_MS = 2000;

    // Game World Map
    public static final int MAP_WIDTH = 50;
    public static final int MAP_HEIGHT = 50;

    // Configure and set network protocol rates
    private static final int DEFAULT_NETWORK_HZ = 30; // Default updates per second
    private final long networkUpdateIntervalMillis; // Made non-final
    private long lastNetworkUpdateTimeMillis = 0; // Tracks when the last update was sent

    private final List<Vector3f> availableColors;
    private final List<Thread> clientHandlerThreads = new CopyOnWriteArrayList<>();
    private final ServerContext serverContext = new ServerContext();

    public GameServer(int port, int networkHz) throws IOException {
        this.port = port;

        // Set network update rate
        this.networkUpdateIntervalMillis = 1000L / networkHz;
        logger.info("Configuring server for network update rate: {} Hz ({} ms interval)",
                networkHz, this.networkUpdateIntervalMillis);

        // Initialize server context
        this.serverContext.gameMode = new FreeForAll();
        this.serverContext.gameMapData = new GameMapData(MAP_WIDTH, MAP_HEIGHT, GameMapData.DEFAULT_TILE_SIZE);

        // Make and shuffle colors to assign to players
        availableColors = Colors.generateDistinctColors(serverContext.gameMode.getMaxAllowedPlayers());
        Collections.shuffle(availableColors);

        // Set up the server shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(this::stop, "ServerShutdownHook"));
    }

    public static void main(String[] args) {
        int port = 5555; // Default port

        int networkHz = DEFAULT_NETWORK_HZ; // Default network rate

        if (args.length >= 1) {
            try { port = Integer.parseInt(args[0]); }
            catch (NumberFormatException e) { System.err.println("Invalid port: " + args[0] + ". Using default " + port); }
        }

        if (args.length >= 2) {
            try { networkHz = Integer.parseInt(args[1]); }
            catch (NumberFormatException e) { System.err.println("Invalid network Hz: " + args[1] + ". Using default " + networkHz); }
        }

        try {
            logger.info("Attempting to start server on port {}...", port);
            GameServer server = new GameServer(port, networkHz);
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
        Vector2f velocity = new Vector2f(0, 0);
        // TODO: random init rotation?
        float rotation = 0.0f;

        TankData newTankData = new TankData(playerId, spawnPos, velocity, rotation, assignedColor, playerName);
        serverContext.clients.put(playerId, handler);
        serverContext.tanks.put(playerId, newTankData);

        logger.info("Player registered: ID={}, Name={}, Color={}", playerId, playerName, assignedColor);

        // Send ASSIGN_ID
        handler.sendMessage(String.format("%s;%d;%f;%f;%f",
                NetworkProtocol.ASSIGN_ID, playerId, assignedColor.x, assignedColor.y, assignedColor.z));

        logger.info("Sent ASSIGN_ID to player ID {}: {}", playerId, handler.getSocket().getInetAddress().getHostAddress());

        GameMapData mapData = serverContext.gameMapData; // Get the authoritative map data
        handler.sendMessage(String.format("%s;%d;%d;%f",
                NetworkProtocol.MAP_INFO,
                mapData.getWidthTiles(),
                mapData.getHeightTiles(),
                mapData.getTileSize()));

        logger.info("Sent MAP_INFO ({};{};{}) to player ID {}: {}", mapData.getWidthTiles(), mapData.getHeightTiles(), mapData.getTileSize(), playerId, handler.getSocket().getInetAddress().getHostAddress());

        handler.sendMessage(String.format("%s;%s;%d",
                NetworkProtocol.GAME_STATE,
                serverContext.currentGameState.name(),
                getTimeDataForGameState(serverContext.currentGameState)));

        logger.info("Sent GAME_STATE to player ID {}: {}", playerId, handler.getSocket().getInetAddress().getHostAddress());

        if (serverContext.currentGameState == GameState.PLAYING) {
            serverContext.gameMode.handleNewPlayerJoinWhileGameInProgress(serverContext, playerId, playerName, newTankData);
        }

        serverContext.gameMode.handleNewPlayerJoin(serverContext, playerId, playerName, newTankData);

        var totalRespawnsAllowed = serverContext.gameMode.getTotalRespawnsAllowedOnStart();

        // Send all tanks and their lives to new player
        for (TankData tankData : serverContext.tanks.values()) {
            var tankColor = tankData.getColor();
            handler.sendMessage(String.format("%s;%d;%f;%f;%f;%s;%f;%f;%f",
                    NetworkProtocol.NEW_PLAYER, tankData.getPlayerId(), tankData.getX(), tankData.getY(), tankData.getRotation(),
                    tankData.getPlayerName(), tankColor.x(), tankColor.y(), tankColor.z()));
            handler.sendMessage(String.format("%s;%d;%d", NetworkProtocol.PLAYER_LIVES, tankData.getPlayerId(), totalRespawnsAllowed));
        }

        logger.info("Sent existing player's tanks to new player ID {}: {}", playerId, handler.getSocket().getInetAddress().getHostAddress());

        // Inform others about new player & lives
        String newPlayerMsg = String.format("%s;%d;%f;%f;%f;%s;%f;%f;%f",
                NetworkProtocol.NEW_PLAYER, newTankData.getPlayerId(), newTankData.getX(), newTankData.getY(), newTankData.getRotation(),
                newTankData.getPlayerName(), newTankData.getColor().x(), newTankData.getColor().y(), newTankData.getColor().z());
        String livesMsg = String.format("%s;%d;%d", NetworkProtocol.PLAYER_LIVES, newTankData.getPlayerId(), totalRespawnsAllowed);
        broadcast(newPlayerMsg, playerId);
        broadcast(livesMsg, playerId);

        logger.info("Broadcast new player info to others: ID={}, Name={}", playerId, playerName);
    }

    // Returns the time data for the new connected player based on the current game state when they connected - invoked from registerPlayer()
    private long getTimeDataForGameState(GameState state) {
        long gameStartOnCountdownInSeconds = serverContext.gameMode.getCountdownStateLengthInSeconds();

        long fallbackTime = System.currentTimeMillis();

        if (serverContext.roundStartTimeMillis == 0) {
            logger.warn("Round start time is zero for game state: {}", state);
            serverContext.roundStartTimeMillis = fallbackTime;
        }

        return switch (state) {
            case PLAYING -> serverContext.roundStartTimeMillis;
            case COUNTDOWN -> serverContext.stateChangeTime + gameStartOnCountdownInSeconds * 1000L;
            default -> 0;
        };
    }

    public synchronized void removePlayer(int playerId) {
        ClientHandler handler = serverContext.clients.remove(playerId);
        TankData tankData = serverContext.tanks.remove(playerId);

        if (handler != null && tankData != null) {
            logger.info("Player removed: ID={}, Name={}", playerId, tankData.getPlayerName());
            if (tankData.getColor() != null) { availableColors.add(tankData.getColor()); Collections.shuffle(availableColors); }
            broadcast(NetworkProtocol.PLAYER_LEFT + ";" + playerId, -1);
        }

        if (serverContext.currentGameState == GameState.PLAYING) {
            serverContext.gameMode.handlePlayerLeaveWhileGameInProgress(serverContext, playerId, tankData);
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

        lastNetworkUpdateTimeMillis = System.currentTimeMillis();

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
        boolean tankStateUpdated = false;

        // While delta is >= 1.0, call updateGameLogic() to process game logic
        while (delta >= 1.0) {
            // Call updateGameLogic() with fixed time step. Each call represents a game tick, 1/60th of a second of game time.
            tankStateUpdated = updateGameLogic(1.0f / 60.0f);
            delta -= 1.0;
        }

        long currentTimeMillis = System.currentTimeMillis();

        // Check if it's time to send AND if the game is in a state where updates are needed
        if (serverContext.currentGameState == GameState.PLAYING && tankStateUpdated &&
                (currentTimeMillis - lastNetworkUpdateTimeMillis >= networkUpdateIntervalMillis))
        {
            logger.trace("Network update interval triggered ({} ms). Broadcasting state.", networkUpdateIntervalMillis);

            broadcastState();

            lastNetworkUpdateTimeMillis = currentTimeMillis; // IMPORTANT: Reset the timer
        }

        return delta;
    }

    private synchronized boolean updateGameLogic(float deltaTime) {
        boolean stateChangedThisTick = false;
        long currentTime = System.currentTimeMillis();

        logger.trace("Processing game state transitions. Current state: {}, Time: {}", serverContext.currentGameState, currentTime);

        handleGameStateTransitions(currentTime);

        logger.trace("Game state transitions processed. Current state: {}, Time: {}", serverContext.currentGameState, currentTime);

        if (serverContext.currentGameState != GameState.PLAYING) return false;    // Only update game state if in PLAYING state

        logger.trace("Game is in PLAYING state. Updating game logic. Time: {}", currentTime);

        // Update Tanks
        for (TankData tankData : serverContext.tanks.values()) {
            if (tankData.isDestroyed()) continue;  // No need to update destroyed tanks

            // Movement Logic
            float turnAmount = 0;
            if (tankData.isTurningLeft()) turnAmount += TANK_TURN_SPEED * deltaTime;
            if (tankData.isTurningRight()) turnAmount -= TANK_TURN_SPEED * deltaTime;

            if (turnAmount != 0) {
                tankData.setRotation(tankData.getRotation() + turnAmount);
                stateChangedThisTick = true;

                if (tankData.isTurningLeft() || tankData.isTurningRight()) {
                    logger.debug("Updated tank rotation for playerId {}: new heading is {} degrees. Player turned {} by {} degrees", tankData.getPlayerId(), tankData.getRotation(), tankData.isTurningLeft() ? "left" : "right", turnAmount);
                }
            }

            float moveAmount = 0;
            if (tankData.isMovingForward()) moveAmount = TANK_MOVE_SPEED * deltaTime;
            else if (tankData.isMovingBackward()) moveAmount = -TANK_MOVE_SPEED * deltaTime * 0.7f;

            if (moveAmount != 0) {
                float angleRad = (float) Math.toRadians(tankData.getRotation());
                float dx = (float) -Math.sin(angleRad) * moveAmount;
                float dy = (float) Math.cos(angleRad) * moveAmount; // Assuming 0=UP, +Y=UP (adjust if needed)
                tankData.addPosition(dx, dy);

                // Check bounds
                serverContext.gameMapData.checkAndCorrectBoundaries(tankData);

                stateChangedThisTick = true;
            }
        }

        logger.trace("Tanks updated. Current state: {}, Time: {}", serverContext.currentGameState, currentTime);

        // Update Bullets
        List<BulletData> bulletsToRemove = new ArrayList<>();
        for (BulletData bulletData : serverContext.bullets) {
            bulletData.getPosition().add(bulletData.getXVelocity() * deltaTime, bulletData.getYVelocity() * deltaTime);
            boolean expired = (currentTime - bulletData.getSpawnTime()) >= BULLET_LIFETIME_MS;
            if (expired || serverContext.gameMapData.isOutOfBounds(bulletData)) {
                bulletsToRemove.add(bulletData);
            }
        }

        logger.trace("Bullets updated. Current state: {}, Time: {}", serverContext.currentGameState, currentTime);

        // Collision Check
        for (BulletData bulletData : serverContext.bullets) {
            if (bulletsToRemove.contains(bulletData)) continue; // Skip if already marked for removal
            for (TankData tankData : serverContext.tanks.values()) {
                if (bulletData.getPosition().distanceSquared(tankData.getPosition()) < Math.pow(TankData.COLLISION_RADIUS + BulletData.SIZE / 2.0f, 2)) {
                    handleHit(tankData, bulletData);
                    bulletsToRemove.add(bulletData);
                    break;
                }
            }
        }
        serverContext.bullets.removeAll(bulletsToRemove);

        logger.trace("Bullets collisions processed. Bullets removed: {} Current state: {}, Time: {}", bulletsToRemove.size(), serverContext.currentGameState, currentTime);

        // Check if any destroyed tanks can respawn
        for (TankData tankData : serverContext.tanks.values()) {
            if (tankData.isDestroyed() && tankData.getDeathTimeMillis() > 0) {
                long timeSinceDeath = currentTime - tankData.getDeathTimeMillis();

                if (timeSinceDeath >= serverContext.tankRespawnDelayMillis && serverContext.gameMode.getRemainingRespawnsForPlayer(tankData.getPlayerId()) > 0) {
                    serverContext.gameMode.handlePlayerRespawn(serverContext, tankData.getPlayerId(), tankData);

                    Vector2f spawnPos = tankData.getPosition();
                    broadcast(String.format("%s;%d;%f;%f", NetworkProtocol.RESPAWN, tankData.getPlayerId(), spawnPos.x, spawnPos.y), -1);

                    int respawnsRemaining = serverContext.gameMode.getRemainingRespawnsForPlayer(tankData.getPlayerId());
                    broadcast(String.format("%s;%d;%d", NetworkProtocol.PLAYER_LIVES, tankData.getPlayerId(), respawnsRemaining), -1);

                    sendSpectatorEndMessage(tankData.getPlayerId());

                    stateChangedThisTick = true;
                }
            }
        }

        checkWinCondition();

        logger.trace("Game logic update complete. Current state: {}, Time: {}", serverContext.currentGameState, currentTime);

        return stateChangedThisTick;
    }

    private synchronized void handleHit(TankData target, BulletData bulletData) {
        TankData shooter = serverContext.tanks.get(bulletData.getPlayerId());
        String shooterName = (shooter != null) ? shooter.getPlayerName() : "Unknown";
        String targetName = target.getPlayerName();
        logger.info("Hit registered: {} -> {}", shooterName, targetName);

        if (target.isDestroyed()) {
            logger.debug("Hit ignored: {} is already destroyed.", targetName);
            return;
        }

        int weaponDamage = 1;
        target.takeHit(weaponDamage);

        if (target.isDestroyed()) {
            target.setInputState(false, false, false, false);   // Stop movement to prevent "ghosting" after death
            int respawnsRemaining = serverContext.gameMode.getRemainingRespawnsForPlayer(target.getPlayerId());
            broadcast(String.format("%s;%d;%d", NetworkProtocol.HIT, target.getPlayerId(), bulletData.getPlayerId()), -1);
            broadcast(String.format("%s;%d;%d", NetworkProtocol.PLAYER_LIVES, target.getPlayerId(), respawnsRemaining), -1);
            broadcast(String.format("%s;%d;%d", NetworkProtocol.DESTROYED, target.getPlayerId(), bulletData.getPlayerId()), -1);
            sendSpectatorStartMessage(target.getPlayerId(), target);

            if (respawnsRemaining <= 0) {
                logger.info("{} was eliminated from the round.", targetName);
                broadcastAnnouncement(targetName + " HAS BEEN ELIMINATED!", -1);
                sendSpectatePermanentMessage(target.getPlayerId());
            }
        }
    }

    private synchronized void sendSpectatorStartMessage(int playerId, TankData tankData) {
        ClientHandler targetHandler = serverContext.clients.get(playerId);

        if (targetHandler == null) {
            logger.error("Unable to send spectator start message to playerId: {} because no handler was found.", playerId);
            return;
        }

        long respawnTime = tankData.getDeathTimeMillis() + serverContext.tankRespawnDelayMillis;
        targetHandler.sendMessage(String.format("%s;%d", NetworkProtocol.SPECTATE_START, respawnTime));

        logger.debug("Spectator started message sent to playerId: {} respawnTime: {}", playerId, respawnTime);
    }

    private synchronized void sendSpectatorEndMessage(int playerId) {
        ClientHandler targetHandler = serverContext.clients.get(playerId);

        if (targetHandler == null) {
            logger.error("Unable to send spectator end message to playerId: {} because no handler was found.", playerId);
            return;
        }

        targetHandler.sendMessage(NetworkProtocol.SPECTATE_END);

        logger.debug("Spectator ended message sent to playerId: {}", playerId);
    }

    private synchronized void sendSpectatePermanentMessage(int playerId) {
        ClientHandler targetHandler = serverContext.clients.get(playerId);

        if (targetHandler == null) {
            logger.error("Unable to send spectate permanent message to playerId: {} because no handler was found.", playerId);
            return;
        }

        targetHandler.sendMessage(NetworkProtocol.SPECTATE_PERMANENT);

        logger.debug("Spectate permanently message sent to playerId: {}", playerId);
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

        if (tankData.isDestroyed()) {
            logger.warn("Unable to process tank movement input for playerId: {} because the tank is destroyed.", playerId);
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

        if (tankData.isDestroyed()) {
            logger.warn("Unable to process shoot input for playerId: {} because the tank is destroyed.", playerId);
            return;
        }

        long currentTime = System.currentTimeMillis();

        // Check main weapon cooldown to see if the tank can shoot
        boolean hasCooledDown = currentTime - tankData.getLastShotTime() >= TANK_SHOOT_COOLDOWN_MS;

        if (!hasCooledDown) {
            var cooldownTimeRemainingInMilliseconds = TANK_SHOOT_COOLDOWN_MS - (currentTime - tankData.getLastShotTime());
            var timeSinceLastShotInMilliseconds = currentTime - tankData.getLastShotTime();
            logger.warn("PlayerId: {} attempted to shoot but the weapon is still cooling down. Time since last shot: {}ms. Cooldown time remaining: {}ms", playerId, timeSinceLastShotInMilliseconds, cooldownTimeRemainingInMilliseconds);
            return;
        }

        tankData.recordShot(currentTime);

        float angleRad = (float) Math.toRadians(tankData.getRotation());
        float dirX = (float) -Math.sin(angleRad);
        float dirY = (float) Math.cos(angleRad);

        float spawnDist = TankData.SIZE / 2.0f + BulletData.SIZE / 2.0f; // Spawn slightly ahead
        float startX = tankData.getX() + dirX * spawnDist;
        float startY = tankData.getY() + dirY * spawnDist;

        Vector2f position = new Vector2f(startX, startY);
        Vector2f velocity = new Vector2f(dirX, dirY).normalize().mul(BULLET_SPEED);

        // TODO: where is bullet rotation set and should it be here?
        float rotation = 0.0f;

        // Create BulletData object
        BulletData bullet = new BulletData(playerId, position, velocity, rotation, currentTime);
        serverContext.bullets.add(bullet);

        logger.debug("PlayerId: {} shot a bullet at position ({}, {}) with direction ({}, {})", playerId, startX, startY, dirX, dirY);

        // Broadcast with calculated dirX, dirY
        broadcast(String.format("%s;%d;%f;%f;%f;%f", NetworkProtocol.SHOOT, playerId, startX, startY, dirX, dirY), -1);
    }

    // Processes game state transitions based on game state conditions
    private synchronized void handleGameStateTransitions(long currentTime) {
        logger.trace("Handling game state change: state={}, time={}, players={}",
                serverContext.currentGameState, currentTime, serverContext.getPlayerCount());

        GameState nextState;

        switch (serverContext.currentGameState) {
            case WAITING:
                nextState = serverContext.gameMode.shouldTransitionFromWaiting(serverContext, currentTime);
                if (nextState != GameState.WAITING) {
                    changeState(nextState, currentTime);
                }
                break;

            case COUNTDOWN:
                nextState = serverContext.gameMode.shouldTransitionFromCountdown(serverContext, currentTime);
                if (nextState != GameState.COUNTDOWN) {
                    changeState(nextState, currentTime);
                }
                break;

            case PLAYING:
                nextState = serverContext.gameMode.shouldTransitionFromPlaying(serverContext, currentTime);
                if (nextState != GameState.PLAYING) {
                    changeState(nextState, currentTime);
                }
                break;

            case ROUND_OVER:
                nextState = serverContext.gameMode.shouldTransitionFromRoundOver(serverContext, currentTime);
                if (nextState != GameState.ROUND_OVER) {
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

        // Set roundStartTimeMillis when entering PLAYING state
        if (newState == GameState.PLAYING) {
            serverContext.roundStartTimeMillis = System.currentTimeMillis();
        }

        // Calculate appropriate time data for client notification
        long broadcastTimeData = calculateBroadcastTimeData(newState, timeData);

        // Broadcast state change to all clients
        broadcast(String.format("%s;%s;%d",
                NetworkProtocol.GAME_STATE, newState.name(), broadcastTimeData), -1);

        // Send appropriate announcements based on new state
        sendStateAnnouncement(newState);

        if (newState == GameState.PLAYING) {
            resetPlayersForNewRound();
        }
    }

    // Helper method to calculate the appropriate time data to broadcast - invoked from changeState()
    private long calculateBroadcastTimeData(GameState state, long timeData) {
        var countdownStateLengthInSeconds = serverContext.gameMode.getCountdownStateLengthInSeconds();

        return switch (state) {
            case PLAYING -> timeData;       // returns the current system time from System.currentTimeMillis()
            case COUNTDOWN -> serverContext.stateChangeTime + countdownStateLengthInSeconds * 1000L; // returns the countdown timer
            case ROUND_OVER -> timeData;    // returns the current system time from System.currentTimeMillis()
            default -> 0;
        };
    }

    // Helper method to send state-specific announcements - invoked from changeState()
    private void sendStateAnnouncement(GameState state) {
        var countdownStateLengthInSeconds = serverContext.gameMode.getCountdownStateLengthInSeconds();

        switch (state) {
            case WAITING -> broadcastAnnouncement("WAITING FOR PLAYERS...", -1);
            case COUNTDOWN -> broadcastAnnouncement("ROUND STARTING IN " + countdownStateLengthInSeconds + " SECONDS.", -1);
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
    // TODO: this logic can get moved to the game mode implementation
    private void resetPlayersForNewRound() {
        logger.info("Resetting players for new round.");

        serverContext.bullets.clear();

        int totalRespawnsAllowed = serverContext.gameMode.getTotalRespawnsAllowedOnStart();

        for(TankData tankData : serverContext.tanks.values()) {
            Vector2f spawnPos = serverContext.gameMapData.getRandomSpawnPoint();
            tankData.setPosition(spawnPos.x, spawnPos.y);
            tankData.setInputState(false, false, false, false);
            tankData.setLastShotTime(0);
            broadcast(String.format("%s;%d;%f;%f", NetworkProtocol.RESPAWN, tankData.getPlayerId(), spawnPos.x, spawnPos.y), -1);
            broadcast(String.format("%s;%d;%d", NetworkProtocol.PLAYER_LIVES, tankData.getPlayerId(), totalRespawnsAllowed), -1);
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

        var isVictory = serverContext.gameMode.checkIsVictoryConditionMet(serverContext);

        if (isVictory) {
            long finalTime = System.currentTimeMillis() - serverContext.roundStartTimeMillis;
            changeState(GameState.ROUND_OVER, finalTime);
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

            broadcast(String.format("%s;%d;%f;%f;%f",
                    NetworkProtocol.PLAYER_UPDATE, tankData.getPlayerId(), tankData.getX(), tankData.getY(), tankData.getRotation()), -1);
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

        logger.info("Broadcast message sent to all clients except player ID {}: {}", excludePlayerId, message);
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