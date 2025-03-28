package org.chrisgruber.nettank.server;

// Imports using common package
import org.chrisgruber.nettank.common.entities.BulletData;
import org.chrisgruber.nettank.common.entities.TankData;
import org.chrisgruber.nettank.common.network.NetworkProtocol;
import org.chrisgruber.nettank.common.world.GameMapData;
import org.chrisgruber.nettank.common.util.Colors;
import org.chrisgruber.nettank.common.util.GameState;

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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class GameServer {
    private static final Logger logger = LoggerFactory.getLogger(GameServer.class);
    private final int port;
    private ServerSocket serverSocket;
    private volatile boolean running = false;
    private final AtomicInteger nextPlayerId = new AtomicInteger(0);
    private volatile int hostPlayerId = -1;
    private final AtomicBoolean stopping = new AtomicBoolean(false);
    private Thread gameLoopThread;

    // Server-specific Constants
    public static final float TANK_MOVE_SPEED = 100.0f;
    public static final float TANK_TURN_SPEED = 100.0f;
    public static final float BULLET_SPEED = 350.0f;
    public static final long BULLET_LIFETIME_MS = 2000;
    public static final long TANK_SHOOT_COOLDOWN_MS = 500;
    private static final long COUNTDOWN_SECONDS = 3;
    private static final long ROUND_END_DELAY_MS = 5000;
    public static final int MIN_PLAYERS_TO_START = 1; // Set to 1 for testing
    public static final int MAX_PLAYERS = 6;

    // Game State
    private final GameMapData gameMapData;
    private final Map<Integer, ClientHandler> clients = new ConcurrentHashMap<>();
    private final Map<Integer, TankData> tanks = new ConcurrentHashMap<>();
    private final List<BulletData> bullets = new CopyOnWriteArrayList<>();
    private final List<Vector3f> availableColors = new CopyOnWriteArrayList<>(Colors.TANK_COLORS); // Use common Colors

    private volatile GameState currentGameState = GameState.WAITING;
    private long roundStartTimeMillis = 0;
    private long stateChangeTime = 0;

    private final List<Thread> clientHandlerThreads = new CopyOnWriteArrayList<>();

    public GameServer(int port) throws IOException {
        this.port = port;
        this.gameMapData = new GameMapData(50, 50, GameMapData.DEFAULT_TILE_SIZE);
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
        running = true; // Set running before starting threads
        logger.info("Server started on {}:{}", serverSocket.getInetAddress().getHostAddress(), port);

        gameLoopThread = new Thread(this::gameLoop);
        gameLoopThread.setName("GameLoop");
        gameLoopThread.setDaemon(true); // Game loop shouldn't prevent exit
        gameLoopThread.start();

        logger.info("Waiting for client connections...");
        try {
            while (running) {
                try {
                    Socket clientSocket = serverSocket.accept(); // Blocks
                    if (!running) break; // Check flag after unblocking
                    logger.info("Client connected: {}", clientSocket.getInetAddress().getHostAddress());
                    ClientHandler clientHandler = new ClientHandler(clientSocket, this);
                    startClientThread(clientHandler);
                } catch (SocketException e) {
                    if (running) { logger.error("Server socket accept error: {}", e.getMessage());}
                    else { logger.info("Server socket closed, accept loop terminating."); }
                    // Loop condition (running) handles exit
                } catch (IOException e) {
                    if (running) { logger.error("Error accepting client connection", e); }
                } catch (Exception e){
                    if(running) { logger.error("Unexpected error in accept loop", e); }
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

    public void stop() {
        if (!stopping.compareAndSet(false, true)) {
            logger.info("Server stop() already in progress or completed.");
            return;
        }
        logger.info("Server stop() sequence initiated...");
        running = false;

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
        List<ClientHandler> handlersToClose = new ArrayList<>(clients.values());
        logger.info("Found {} handlers to close.", handlersToClose.size());
        for (ClientHandler handler : handlersToClose) {
            if (handler != null) { handler.closeConnection("Server shutting down"); }
        }

        // Clear state
        clients.clear();
        tanks.clear();
        bullets.clear();
        availableColors.clear();
        availableColors.addAll(Colors.TANK_COLORS);
        Collections.shuffle(availableColors);
        nextPlayerId.set(0);
        hostPlayerId = -1;

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


    public void startClientThread(ClientHandler handler) {
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
        if (clients.size() >= MAX_PLAYERS) {
            handler.sendMessage(NetworkProtocol.ERROR_MSG + ";Server full");
            handler.closeConnection("Server full"); return;
        }
        if (availableColors.isEmpty()) {
            handler.sendMessage(NetworkProtocol.ERROR_MSG + ";No colors available");
            handler.closeConnection("No colors available"); return;
        }

        int playerId = nextPlayerId.getAndIncrement();
        boolean isFirstPlayer = false;
        if (hostPlayerId == -1 && clients.isEmpty()) {
            hostPlayerId = playerId; isFirstPlayer = true;
            logger.info("Player ID {} ({}) assigned as HOST.", playerId, playerName);
        }

        Vector3f assignedColor = availableColors.removeFirst();
        Vector2f spawnPos = gameMapData.getRandomSpawnPoint();

        TankData newTankData = new TankData(playerId, spawnPos.x, spawnPos.y, assignedColor, playerName);
        newTankData.setLives(currentGameState == GameState.PLAYING ? 0 : TankData.INITIAL_LIVES);

        handler.setPlayerInfo(playerId, playerName, assignedColor);
        clients.put(playerId, handler);
        tanks.put(playerId, newTankData);

        logger.info("Player registered: ID={}, Name={}, Color={}, IsHost={}", playerId, playerName, assignedColor, isFirstPlayer);

        // Send ASSIGN_ID (5 parts now)
        handler.sendMessage(String.format("%s;%d;%f;%f;%f",
                NetworkProtocol.ASSIGN_ID, playerId, assignedColor.x, assignedColor.y, assignedColor.z));

        handler.sendMessage(String.format("%s;%s;%d", NetworkProtocol.GAME_STATE, currentGameState.name(),
                (currentGameState == GameState.PLAYING ? roundStartTimeMillis : (currentGameState == GameState.COUNTDOWN ? stateChangeTime + COUNTDOWN_SECONDS * 1000 : 0)) ));

        // Send all tanks and their lives to new player
        for (TankData tankData : tanks.values()) {
            handler.sendMessage(String.format("%s;%d;%f;%f;%f;%s;%f;%f;%f",
                    NetworkProtocol.NEW_PLAYER, tankData.playerId, tankData.position.x, tankData.position.y, tankData.rotation,
                    tankData.name, tankData.color.x, tankData.color.y, tankData.color.z));
            handler.sendMessage(String.format("%s;%d;%d", NetworkProtocol.PLAYER_LIVES, tankData.playerId, tankData.lives));
        }

        // Inform others about new player & lives
        String newPlayerMsg = String.format("%s;%d;%f;%f;%f;%s;%f;%f;%f",
                NetworkProtocol.NEW_PLAYER, newTankData.playerId, newTankData.position.x, newTankData.position.y, newTankData.rotation,
                newTankData.name, newTankData.color.x, newTankData.color.y, newTankData.color.z);
        String livesMsg = String.format("%s;%d;%d", NetworkProtocol.PLAYER_LIVES, newTankData.playerId, newTankData.lives);
        broadcast(newPlayerMsg, playerId);
        broadcast(livesMsg, playerId);

        checkGameStateTransition();
    }

    public synchronized void removePlayer(int playerId) {
        ClientHandler handler = clients.remove(playerId);
        TankData tankData = tanks.remove(playerId);

        if (playerId == hostPlayerId) {
            logger.info("Host (Player {}) disconnected. Resetting host ID.", playerId);
            hostPlayerId = -1;
        }

        if (handler != null && tankData != null) {
            logger.info("Player removed: ID={}, Name={}", playerId, tankData.name);
            if (tankData.color != null) { availableColors.add(tankData.color); Collections.shuffle(availableColors); }
            broadcast(NetworkProtocol.PLAYER_LEFT + ";" + playerId, -1);
            checkGameStateTransition();
        } else {
            // logger.warn("Attempted to remove player {}, but handler or tankData was already null.", playerId); // Reduce noise
        }
    }

    // Called by ClientHandler on SSD command
    public synchronized void requestShutdown(int requestorPlayerId) {
        // Check against tracked host ID
        if (requestorPlayerId == hostPlayerId && hostPlayerId != -1) {
            logger.info("Shutdown requested by host (Player {}). Initiating server stop...", requestorPlayerId);
            stop();
        } else {
            logger.warn("Shutdown requested by non-host client (Player {}) or host ID not set. Request ignored.", requestorPlayerId);
            // Optionally send error back
            ClientHandler reqHandler = clients.get(requestorPlayerId);
            if(reqHandler != null) {
                reqHandler.sendMessage(NetworkProtocol.ERROR_MSG + ";Only host can shut down server");
            }
        }
    }


    // --- Game Loop & Logic ---

    private void gameLoop() {
        long lastTime = System.nanoTime();
        double nsPerTick = 1_000_000_000.0 / 60.0;
        double delta = 0; // Fixed declaration
        logger.info("GameLoop thread started.");
        while (running) {
            long now = System.nanoTime();
            delta += Math.min((now - lastTime) / nsPerTick, 10.0); // Capped delta time
            lastTime = now;
            boolean updated = false;
            while (delta >= 1.0) {
                updateGameLogic(1.0f / 60.0f);
                delta -= 1.0;
                updated = true;
            }
            if (updated) { broadcastState(); }
            try {
                if (!running) break;
                Thread.sleep(1);
            } catch (InterruptedException e) {
                logger.info("Game loop interrupted (likely server shutdown).");
                Thread.currentThread().interrupt(); // Preserve interrupt status
            } catch (Exception e) {
                if (running) { logger.error("Unexpected error in GameLoop sleep/wait phase", e); }
            }
        }
        logger.info("GameLoop thread finished.");
    }

    private synchronized void updateGameLogic(float deltaTime) {
        long currentTime = System.currentTimeMillis();
        handleGameStateTransitions(currentTime);
        if (currentGameState != GameState.PLAYING) { return; }

        // Update Tanks
        for (TankData tankData : tanks.values()) {
            if (!tankData.alive) continue;
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
            if (gameMapData.isOutOfBounds(tankData.position.x, tankData.position.y, TankData.COLLISION_RADIUS)) {
                tankData.setPosition(oldPos.x, oldPos.y);
            }

            checkGameStateTransition();
        }

        // Update Bullets
        List<BulletData> bulletsToRemove = new ArrayList<>();
        for (BulletData bulletData : bullets) {
            bulletData.position.add(bulletData.velocity.x * deltaTime, bulletData.velocity.y * deltaTime);
            boolean expired = (currentTime - bulletData.spawnTime) >= BULLET_LIFETIME_MS;
            if (expired || gameMapData.isOutOfBounds(bulletData.position.x, bulletData.position.y, BulletData.SIZE / 2.0f)) {
                bulletsToRemove.add(bulletData);
            }
        }

        // Collision Check
        for (BulletData bulletData : bullets) {
            if (bulletsToRemove.contains(bulletData)) continue;
            for (TankData tankData : tanks.values()) {
                if (!tankData.alive || tankData.playerId == bulletData.ownerId) continue;
                if (bulletData.position.distanceSquared(tankData.position) < Math.pow(TankData.COLLISION_RADIUS + BulletData.SIZE / 2.0f, 2)) {
                    handleHit(tankData, bulletData);
                    bulletsToRemove.add(bulletData);
                    break;
                }
            }
        }
        bullets.removeAll(bulletsToRemove);
        checkWinCondition();
    }

    private synchronized void handleHit(TankData target, BulletData bulletData) {
        TankData shooter = tanks.get(bulletData.ownerId);
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

    public void handlePlayerInput(int playerId, boolean w, boolean s, boolean a, boolean d) {
        TankData tankData = tanks.get(playerId);
        if (tankData != null && tankData.alive && currentGameState == GameState.PLAYING) {
            tankData.setInputState(w, s, a, d);
        }
    }

    public void handlePlayerShoot(int playerId) {
        TankData tankData = tanks.get(playerId);
        long currentTime = System.currentTimeMillis();
        // Check cooldown using server constant and data field
        boolean hasCooledDown = tankData != null && tankData.alive && (currentTime - tankData.lastShotTime >= TANK_SHOOT_COOLDOWN_MS);

        if (hasCooledDown && currentGameState == GameState.PLAYING) {
            tankData.recordShot(currentTime);

            float angleRad = (float) Math.toRadians(tankData.rotation);
            float dirX = (float) -Math.sin(angleRad);
            float dirY = (float) Math.cos(angleRad); // Assuming 0=UP, +Y=UP

            float spawnDist = TankData.SIZE / 2.0f + BulletData.SIZE / 2.0f; // Spawn slightly ahead
            float startX = tankData.position.x + dirX * spawnDist;
            float startY = tankData.position.y + dirY * spawnDist;

            Vector2f velocity = new Vector2f(dirX, dirY).normalize().mul(BULLET_SPEED);

            // Create BulletData (constructor now uses currentTime from server)
            BulletData bullet = new BulletData(playerId, startX, startY, velocity.x, velocity.y, currentTime);
            bullets.add(bullet);

            // Broadcast with calculated dirX, dirY
            broadcast(String.format("%s;%d;%f;%f;%f;%f", NetworkProtocol.SHOOT, playerId, startX, startY, dirX, dirY), -1);
        }
    }

    // --- State Transitions & Broadcasting --- (Largely unchanged logic, using TankData)
    private synchronized void handleGameStateTransitions(long currentTime) {
        int totalPlayers = tanks.size();
        switch (currentGameState) {
            case WAITING: if (totalPlayers >= MIN_PLAYERS_TO_START) { changeState(GameState.COUNTDOWN, currentTime); } break;
            case COUNTDOWN:
                if (totalPlayers < MIN_PLAYERS_TO_START) { changeState(GameState.WAITING, currentTime); }
                else if (currentTime >= stateChangeTime + COUNTDOWN_SECONDS * 1000) { resetPlayersForNewRound(); roundStartTimeMillis = currentTime; changeState(GameState.PLAYING, currentTime); }
                else { /* Countdown announce logic */ } break;
            case PLAYING: /* checkWinCondition handles transition */ break;
            case ROUND_OVER:
                if (currentTime >= stateChangeTime + ROUND_END_DELAY_MS) {
                    if (totalPlayers >= MIN_PLAYERS_TO_START) { changeState(GameState.COUNTDOWN, currentTime); }
                    else { changeState(GameState.WAITING, currentTime); }
                } break;
            default: break; // Added default
        }
    }

    private void changeState(GameState newState, long timeData) { // timeData meaning depends on state
        if (currentGameState == newState) return;
        logger.info("Server changing state from {} to {}", currentGameState, newState);
        currentGameState = newState;
        stateChangeTime = System.currentTimeMillis(); // Record when the state *actually* changed

        // Adjust timeData based on state for clarity
        long broadcastTimeData = 0;
        if (newState == GameState.PLAYING) {
            broadcastTimeData = timeData; // Send start time
        } else if (newState == GameState.COUNTDOWN) {
            broadcastTimeData = stateChangeTime + COUNTDOWN_SECONDS * 1000; // Send target end time
        } else if (newState == GameState.ROUND_OVER) {
            // In checkWinCondition, we called changeState with System.currentTimeMillis().
            // Let's use the calculated finalTime duration instead.
            // We need to slightly refactor checkWinCondition or pass duration here.
            // Simpler: Let timeData for ROUND_OVER be 0 for now, announcement has duration.
            broadcastTimeData = 0;
        }

        broadcast(String.format("%s;%s;%d", NetworkProtocol.GAME_STATE, newState.name(), broadcastTimeData), -1);

        if (newState == GameState.WAITING) { broadcastAnnouncement("WAITING FOR PLAYERS...", -1); }
        else if (newState == GameState.COUNTDOWN) { broadcastAnnouncement("ROUND STARTING IN " + COUNTDOWN_SECONDS + "...", -1); }
        // Announcement for round over is handled in checkWinCondition
    }

    private void resetPlayersForNewRound() {
        logger.info("Resetting players for new round.");
        bullets.clear();
        for(TankData tankData : tanks.values()) {
            Vector2f spawnPos = gameMapData.getRandomSpawnPoint();
            tankData.setPosition(spawnPos.x, spawnPos.y);
            tankData.setRotation(0);
            tankData.setLives(TankData.INITIAL_LIVES);
            tankData.setInputState(false, false, false, false);
            tankData.lastShotTime = 0; // Reset shot timer
            broadcast(String.format("%s;%d;%f;%f", NetworkProtocol.RESPAWN, tankData.playerId, spawnPos.x, spawnPos.y), -1);
            broadcast(String.format("%s;%d;%d", NetworkProtocol.PLAYER_LIVES, tankData.playerId, tankData.lives), -1);
        }
    }

    // In nettank-server/src/main/java/org/chrisgruber/nettank/server/GameServer.java

    // In nettank-server/src/main/java/org/chrisgruber/nettank/server/GameServer.java

    private synchronized void checkWinCondition() {
        if (currentGameState != GameState.PLAYING) return;

        int aliveCount = 0;
        TankData lastAliveTank = null;
        int totalPlayersInMap = tanks.size();

        // --- Revised Logic ---
        // If MIN_PLAYERS_TO_START is 1, and only 1 player is connected, NEVER end the round by elimination.
        if (totalPlayersInMap == 1 && MIN_PLAYERS_TO_START == 1) {
            logger.trace("Skipping win condition check: Single player mode active.");
            return;
        }

        // If MIN_PLAYERS_TO_START is >= 2, only check if at least that many players are present.
        if (totalPlayersInMap < MIN_PLAYERS_TO_START) {
            logger.trace("Skipping win condition check: Fewer than minimum players connected ({}/{})",
                    totalPlayersInMap, MIN_PLAYERS_TO_START);
            return;
        }

        // --- Count alive players (only if we passed the initial checks) ---
        for (TankData tankData : tanks.values()) {
            if (tankData.alive) {
                aliveCount++;
                lastAliveTank = tankData;
            }
        }

        // --- End round based on elimination (only if >= MIN_PLAYERS_TO_START were present) ---
        if (aliveCount <= 1) {
            logger.info("Round over condition met (Alive: {}, Total: {}).", aliveCount, totalPlayersInMap);
            long finalTime = System.currentTimeMillis() - roundStartTimeMillis;
            changeState(GameState.ROUND_OVER, finalTime); // Pass duration

            String winnerName = "NO ONE"; int winnerId = -1;
            if (lastAliveTank != null) { winnerName = lastAliveTank.name; winnerId = lastAliveTank.playerId; logger.info("Winner: {} (ID: {}).", winnerName, winnerId); }
            else { logger.info("Round ended in a draw."); }

            broadcast(String.format("%s;%d;%s;%d", NetworkProtocol.ROUND_OVER, winnerId, winnerName, finalTime), -1);
            broadcastAnnouncement( (winnerId != -1 ? winnerName + " WINS!" : "DRAW!") + " FINAL TIME: " + formatTime(finalTime), -1);
        }
    }

    private void checkGameStateTransition() {
        long currentTime = System.currentTimeMillis();
        if (currentGameState == GameState.WAITING || currentGameState == GameState.COUNTDOWN) { handleGameStateTransitions(currentTime); }
        else if (currentGameState == GameState.PLAYING) { checkWinCondition(); }
    }

    private void broadcastState() {
        if (currentGameState != GameState.PLAYING) return;
        for (TankData tankData : tanks.values()) {
            if (tankData.alive) {
                broadcast(String.format("%s;%d;%f;%f;%f",
                        NetworkProtocol.PLAYER_UPDATE, tankData.playerId, tankData.position.x, tankData.position.y, tankData.rotation), -1);
            }
        }
    }

    public void broadcast(String message, int excludePlayerId) {
        logger.trace("Broadcasting (exclude {}): {}", excludePlayerId, message);
        if (clients.isEmpty()) { return; }
        for (ClientHandler handler : clients.values()) {
            if (handler != null && handler.getPlayerId() != excludePlayerId) {
                logger.trace("Sending to client ID {}: {}", handler.getPlayerId(), message);
                handler.sendMessage(message);
            }
        }
    }

    public void broadcastAnnouncement(String announcement, int excludePlayerId) {
        broadcast(NetworkProtocol.ANNOUNCE + ";" + announcement, excludePlayerId);
    }

    private String formatTime(long millis) {
        long seconds = (millis / 1000) % 60;
        long minutes = (millis / (1000 * 60)) % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }
}