package org.chrisgruber.nettank.network;

import org.chrisgruber.nettank.entities.Bullet;
import org.chrisgruber.nettank.entities.Tank;
import org.chrisgruber.nettank.game.GameMap;
import org.chrisgruber.nettank.util.Colors;
import org.chrisgruber.nettank.util.GameState;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

public class GameServer {
    private static final Logger logger = LoggerFactory.getLogger(GameServer.class);
    private final int port;
    private ServerSocket serverSocket;
    private boolean running = false;
    private final AtomicInteger nextPlayerId = new AtomicInteger(0);

    // Game State
    private final GameMap gameMap;
    private final Map<Integer, ClientHandler> clients = new ConcurrentHashMap<>();
    private final Map<Integer, Tank> tanks = new ConcurrentHashMap<>();
    private final List<Bullet> bullets = new CopyOnWriteArrayList<>();
    private final List<Vector3f> availableColors = new CopyOnWriteArrayList<>(Colors.TANK_COLORS);

    // Timing & Game Flow
    private volatile GameState currentGameState = GameState.WAITING;
    private long roundStartTimeMillis = 0;
    private long stateChangeTime = 0; // Time the current state started
    private static final long COUNTDOWN_SECONDS = 3;
    private static final long ROUND_END_DELAY_MS = 5000; // Time to show winner before new round
    public static final int MIN_PLAYERS_TO_START = 1;
    public static final int MAX_PLAYERS = 6;


    public GameServer(int port) throws IOException {
        this.port = port;
        this.gameMap = new GameMap(50, 50); // Same size as client
        Collections.shuffle(availableColors); // Shuffle colors initially
        Runtime.getRuntime().addShutdownHook(new Thread(this::stop));
    }

    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Usage: GameServer <port>");
            System.exit(1);
        }

        int port = Integer.parseInt(args[0]);
        try {
            GameServer server = new GameServer(port);
            System.out.println("Server started on 0.0.0.0:" + port);
            server.start();
        } catch (IOException e) {
            System.err.println("Server failed: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    public void startClientThread(ClientHandler handler) {
        Thread thread = new Thread(handler);
        // Use socket port as identifier until player is registered
        int tempId = handler.getSocket().getPort();
        thread.setName("ServerClientHandler-" + tempId);
        thread.setUncaughtExceptionHandler((t, e) -> {
            logger.error("Uncaught exception in thread {}: {}", t.getName(), e.getMessage(), e);
            handler.closeConnection("Internal error: " + e.getMessage());
        });
        thread.start();
        logger.info("Started client handler thread: {}", thread.getName());
    }

    public void start() throws IOException {
        serverSocket = new ServerSocket(port, 50, InetAddress.getByName("0.0.0.0"));
        running = true;
        logger.info("Server started on {}:{}", serverSocket.getInetAddress().getHostAddress(), port);
        logger.info("Waiting for client threads to start...");

        // Start game loop in a separate thread
        Thread thread = new Thread(this::gameLoop);
        thread.setName("GameLoop");
        thread.start();

        // Accept client connections
        while (running) {
            try {
                Socket clientSocket = serverSocket.accept();
                logger.info("Client connected: {}", clientSocket.getInetAddress().getHostAddress());

                // Handle client connection in a new thread
                ClientHandler clientHandler = new ClientHandler(clientSocket, this);
                startClientThread(clientHandler);
                // Player registration happens after receiving CONNECT message

            } catch (IOException e) {
                if (running) {
                    logger.error("Error accepting client connection", e);
                } else {
                    logger.info("Server socket closed.");
                }
            }
        }
    }

    public void stop() {
        running = false;
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
            for (ClientHandler handler : clients.values()) {
                handler.closeConnection("Server shutting down");
            }
            clients.clear();
            tanks.clear();
            bullets.clear();
            logger.info("Server stopped.");
        } catch (IOException e) {
            logger.error("Error stopping server.", e);
        }
    }

    // Called by ClientHandler when a CONNECT message is received
    public synchronized void registerPlayer(ClientHandler handler, String playerName) {
        if (clients.size() >= MAX_PLAYERS) {
            handler.sendMessage(NetworkProtocol.ERROR_MSG + ";Server full");
            handler.closeConnection("Server full");
            return;
        }
        if (availableColors.isEmpty()) {
            handler.sendMessage(NetworkProtocol.ERROR_MSG + ";No colors available");
            handler.closeConnection("No colors available");
            return;
        }

        int playerId = nextPlayerId.getAndIncrement();
        Vector3f assignedColor = availableColors.remove(0); // Take the first available color

        Vector2f spawnPos = gameMap.getRandomSpawnPoint();
        Tank newTank = new Tank(playerId, spawnPos.x, spawnPos.y, assignedColor, playerName);
        newTank.setLives(currentGameState == GameState.PLAYING ? 0 : Tank.INITIAL_LIVES); // Join dead if game in progress

        handler.setPlayerInfo(playerId, playerName, assignedColor);
        clients.put(playerId, handler);
        tanks.put(playerId, newTank);

        logger.info("Player registered: ID={}, Name={}, Color={}", playerId, playerName, assignedColor);

        // 1. Send assigned ID and color to the new player
        handler.sendMessage(String.format("%s;%d;%f;%f;%f", NetworkProtocol.ASSIGN_ID, playerId, assignedColor.x, assignedColor.y, assignedColor.z));

        // 2. Send current game state and existing players/bullets to the new player
        handler.sendMessage(String.format("%s;%s;%d", NetworkProtocol.GAME_STATE, currentGameState.name(),
                currentGameState == GameState.PLAYING ? roundStartTimeMillis : (currentGameState == GameState.COUNTDOWN ? stateChangeTime + COUNTDOWN_SECONDS * 1000 : 0) ));

        for (Tank tank : tanks.values()) {
            handler.sendMessage(String.format("%s;%d;%f;%f;%f;%s;%f;%f;%f",
                    NetworkProtocol.NEW_PLAYER, tank.getPlayerId(), tank.getPosition().x, tank.getPosition().y, tank.getRotation(),
                    tank.getName(), tank.getColor().x, tank.getColor().y, tank.getColor().z));
            handler.sendMessage(String.format("%s;%d;%d", NetworkProtocol.PLAYER_LIVES, tank.getPlayerId(), tank.getLives()));
        }
        // Send existing bullets (less critical, could be skipped)
        // for (Bullet bullet : bullets) { handler.sendMessage(...); }


        // 3. Inform all *other* players about the new player
        broadcast(String.format("%s;%d;%f;%f;%f;%s;%f;%f;%f",
                NetworkProtocol.NEW_PLAYER, playerId, spawnPos.x, spawnPos.y, newTank.getRotation(),
                playerName, assignedColor.x, assignedColor.y, assignedColor.z), playerId); // Exclude new player

        // Update game state if needed (e.g., trigger countdown if enough players)
        checkGameStateTransition();
    }

    // Called by ClientHandler when connection is lost/closed
    public synchronized void removePlayer(int playerId) {
        ClientHandler handler = clients.remove(playerId);
        Tank tank = tanks.remove(playerId);

        if (handler != null && tank != null) {
            logger.info("Player removed: ID={}, Name={}", playerId, tank.getName());
            // Return color to the pool
            availableColors.add(tank.getColor());
            Collections.shuffle(availableColors); // Re-shuffle

            broadcast(NetworkProtocol.PLAYER_LEFT + ";" + playerId, -1); // Inform everyone

            // Check if game needs to end or state changes
            checkGameStateTransition();
        }
    }

    public boolean isRunning() {
        return serverSocket != null && !serverSocket.isClosed();
    }

    private void gameLoop() {
        long lastTime = System.nanoTime();
        double nsPerTick = 1_000_000_000.0 / 60.0; // Target 60 updates per second
        double delta = 0;

        while (running) {
            long now = System.nanoTime();
            delta += (now - lastTime) / nsPerTick;
            lastTime = now;

            boolean shouldRender = false; // Server doesn't render, but we use this for update rate

            while (delta >= 1) {
                update((float) (1.0 / 60.0)); // Fixed time step for physics/logic
                delta -= 1;
                shouldRender = true; // Indicates an update happened
            }

            if (shouldRender) {
                // Send state updates after logic update
                broadcastState();
            }


            // Avoid busy-waiting
            try {
                Thread.sleep(1); // Sleep briefly
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.error("Game loop interrupted.", e);
            }
        }
    }

    private synchronized void update(float deltaTime) {
        long currentTime = System.currentTimeMillis();

        // --- State Machine ---
        handleGameStateTransitions(currentTime);

        if (currentGameState != GameState.PLAYING) {
            bullets.clear(); // No bullets outside of PLAYING state
            return; // No game logic updates needed if not playing
        }

        // --- Update Tanks ---
        for (Tank tank : tanks.values()) {
            if (!tank.isAlive()) continue; // Skip dead tanks

            Vector2f oldPos = new Vector2f(tank.getPosition());
            tank.update(deltaTime); // Apply movement based on input state

            // Collision with map boundaries
            if (gameMap.isOutOfBounds(tank.getPosition().x, tank.getPosition().y, Tank.COLLISION_RADIUS)) {
                tank.setPosition(oldPos.x, oldPos.y); // Revert position if out of bounds
            }
            // TODO: Add Tank-Tank collision? (Can be complex/jittery)
        }

        // --- Update Bullets ---
        List<Bullet> bulletsToRemove = new ArrayList<>();
        for (Bullet bullet : bullets) {
            bullet.update(deltaTime);
            if (bullet.isExpired() || gameMap.isOutOfBounds(bullet.getPosition().x, bullet.getPosition().y, Bullet.SIZE / 2.0f)) {
                bulletsToRemove.add(bullet);
            }
        }

        // --- Collision Detection (Bullet <-> Tank) ---
        for (Bullet bullet : bullets) {
            if (bulletsToRemove.contains(bullet)) continue; // Skip already marked for removal

            for (Tank tank : tanks.values()) {
                if (!tank.isAlive() || tank.getPlayerId() == bullet.getOwnerId()) continue; // Can't shoot self, skip dead tanks

                // Simple distance-based collision check
                if (bullet.getPosition().distanceSquared(tank.getPosition()) < (Tank.COLLISION_RADIUS + Bullet.SIZE/2.0f) * (Tank.COLLISION_RADIUS + Bullet.SIZE/2.0f)) {
                    handleHit(tank, bullet);
                    bulletsToRemove.add(bullet); // Remove bullet on hit
                    break; // Bullet can only hit one tank
                }
            }
        }

        bullets.removeAll(bulletsToRemove);

        // Check win condition after handling hits
        checkWinCondition();
    }

    private synchronized void handleGameStateTransitions(long currentTime) {
        switch (currentGameState) {
            case WAITING:
                if (tanks.size() >= MIN_PLAYERS_TO_START) {
                    // Start countdown
                    changeState(GameState.COUNTDOWN, currentTime);
                }
                break;
            case COUNTDOWN:
                if (tanks.size() < MIN_PLAYERS_TO_START) {
                    // Not enough players anymore, return to waiting
                    changeState(GameState.WAITING, currentTime);
                } else if (currentTime >= stateChangeTime + COUNTDOWN_SECONDS * 1000) {
                    // Countdown finished, start playing
                    changeState(GameState.PLAYING, currentTime);
                    roundStartTimeMillis = currentTime;
                    // Reset players for the new round
                    resetPlayersForNewRound();
                } else {
                    // Announce countdown numbers (simple example, could be more robust)
                    long remainingMillis = (stateChangeTime + COUNTDOWN_SECONDS * 1000) - currentTime;
                    int remainingSeconds = (int)Math.ceil(remainingMillis / 1000.0);
                    // TODO: Send announcements more intelligently (e.g., only once per second)
                }
                break;
            case PLAYING:
                // CheckWinCondition handles transition out of PLAYING
                if (tanks.size() < MIN_PLAYERS_TO_START) {
                    // If not enough players left during game (e.g., disconnects), end round prematurely? Or wait?
                    // For now, let the win condition handle it if only one is left.
                }
                break;
            case ROUND_OVER:
                if (currentTime >= stateChangeTime + ROUND_END_DELAY_MS) {
                    // Delay finished, go back to waiting (or countdown if enough players)
                    if (tanks.size() >= MIN_PLAYERS_TO_START) {
                        changeState(GameState.COUNTDOWN, currentTime);
                    } else {
                        changeState(GameState.WAITING, currentTime);
                    }
                }
                break;
        }
    }

    private void changeState(GameState newState, long currentTime) {
        if (currentGameState != newState) {
            logger.info("Server changing state from {} to {}", currentGameState, newState);
            currentGameState = newState;
            stateChangeTime = currentTime;

            long timeData = 0;
            if (newState == GameState.PLAYING) timeData = currentTime; // Start time
            if (newState == GameState.COUNTDOWN) timeData = stateChangeTime + COUNTDOWN_SECONDS * 1000; // End time

            broadcast(String.format("%s;%s;%d", NetworkProtocol.GAME_STATE, newState.name(), timeData), -1);

            if (newState == GameState.WAITING) {
                broadcastAnnouncement("WAITING FOR PLAYERS...", -1);
            } else if (newState == GameState.COUNTDOWN) {
                // Could send initial countdown announcement here
                broadcastAnnouncement("ROUND STARTING IN " + COUNTDOWN_SECONDS + "...", -1);
            }
        }
    }

    private void resetPlayersForNewRound() {
        logger.info("Resetting players for new round.");
        bullets.clear();
        for(Tank tank : tanks.values()) {
            Vector2f spawnPos = gameMap.getRandomSpawnPoint();
            tank.setPosition(spawnPos.x, spawnPos.y);
            tank.setRotation(0); // Reset rotation
            tank.setLives(Tank.INITIAL_LIVES);
            tank.setInputState(false, false, false, false); // Reset input

            // Send respawn message for each player
            broadcast(String.format("%s;%d;%f;%f", NetworkProtocol.RESPAWN, tank.getPlayerId(), spawnPos.x, spawnPos.y), -1);
            broadcast(String.format("%s;%d;%d", NetworkProtocol.PLAYER_LIVES, tank.getPlayerId(), tank.getLives()), -1);
        }
    }


    private synchronized void handleHit(Tank target, Bullet bullet) {
        Tank shooter = tanks.get(bullet.getOwnerId());
        String shooterName = (shooter != null) ? shooter.getName() : "Unknown";
        String targetName = target.getName();

        logger.info("Hit registered: {} -> {}", shooterName, targetName);

        target.takeHit();
        broadcast(String.format("%s;%d;%d", NetworkProtocol.HIT, target.getPlayerId(), bullet.getOwnerId()), -1);
        broadcast(String.format("%s;%d;%d", NetworkProtocol.PLAYER_LIVES, target.getPlayerId(), target.getLives()), -1);

        // Announce kill
        broadcastAnnouncement(shooterName + " KILLED " + targetName, -1);


        if (!target.isAlive()) {
            logger.info("{} was defeated.", targetName);
            broadcast(String.format("%s;%d;%d", NetworkProtocol.DESTROYED, target.getPlayerId(), bullet.getOwnerId()), -1);
            broadcastAnnouncement(targetName + " HAS BEEN DEFEATED!", -1);

            // Respawn logic is now handled by round reset or potentially later if implementing mid-round respawns
            // For last-man-standing, defeated players just stay dead until next round.
        }
    }

    private synchronized void checkWinCondition() {
        if (currentGameState != GameState.PLAYING) return;

        int aliveCount = 0;
        Tank lastAliveTank = null;
        for (Tank tank : tanks.values()) {
            if (tank.isAlive()) {
                aliveCount++;
                lastAliveTank = tank;
            }
        }

        // Win condition: 1 player left alive, OR 0 players left alive (draw?), OR only 1 player connected total
        if (aliveCount <= 1 && tanks.size() >= 1) { // Allow win even if only 1 player started
            logger.info("Round over condition met.");
            long finalTime = System.currentTimeMillis() - roundStartTimeMillis;
            changeState(GameState.ROUND_OVER, System.currentTimeMillis());

            String winnerName = "NO ONE";
            if (lastAliveTank != null) {
                winnerName = lastAliveTank.getName();
                logger.info("Winner: {}.", winnerName);
                broadcast(String.format("%s;%d;%s;%d", NetworkProtocol.ROUND_OVER, lastAliveTank.getPlayerId(), winnerName, finalTime), -1);
                broadcastAnnouncement(winnerName + " WINS! FINAL TIME: " + formatTime(finalTime), -1);
            } else {
                logger.info("Round ended in a draw.");
                broadcast(String.format("%s;-1;DRAW;%d", NetworkProtocol.ROUND_OVER, finalTime), -1); // -1 for winner ID indicates draw
                broadcastAnnouncement("DRAW! FINAL TIME: " + formatTime(finalTime), -1);
            }
        }
    }

    private void checkGameStateTransition() {
        // Called when players join/leave to potentially change state
        long currentTime = System.currentTimeMillis();
        if (currentGameState == GameState.WAITING || currentGameState == GameState.COUNTDOWN) {
            handleGameStateTransitions(currentTime); // Re-evaluate conditions
        } else if (currentGameState == GameState.PLAYING) {
            checkWinCondition(); // Check if the leaving player causes a win
        }
    }


    // Called by ClientHandler with player input
    public void handlePlayerInput(int playerId, boolean w, boolean s, boolean a, boolean d) {
        Tank tank = tanks.get(playerId);
        if (tank != null && tank.isAlive() && currentGameState == GameState.PLAYING) {
            tank.setInputState(w, s, a, d);
        }
    }

    // Called by ClientHandler when player shoots
    public void handlePlayerShoot(int playerId) {
        Tank tank = tanks.get(playerId);
        long currentTime = System.currentTimeMillis();
        if (tank != null && tank.isAlive() && currentGameState == GameState.PLAYING && tank.canShoot(currentTime)) {
            tank.recordShot(currentTime);

            // Calculate bullet start position and velocity
            float angleRad = (float) Math.toRadians(tank.getRotation());
            float dirX = (float) Math.sin(angleRad);
            float dirY = (float) Math.cos(angleRad);

            // Spawn bullet slightly in front of the tank
            float spawnDist = Tank.SIZE / 2.0f + Bullet.SIZE;
            float startX = tank.getPosition().x + dirX * spawnDist;
            float startY = tank.getPosition().y + dirY * spawnDist;

            Vector2f velocity = new Vector2f(dirX, dirY).normalize().mul(Bullet.SPEED);
            Bullet bullet = new Bullet(playerId, startX, startY, velocity);
            bullets.add(bullet);

            // Broadcast shoot event
            broadcast(String.format("%s;%d;%f;%f;%f;%f", NetworkProtocol.SHOOT, playerId, startX, startY, dirX, dirY), -1);
        }
    }


    // Broadcasts regular state updates (positions primarily)
    private void broadcastState() {
        if (currentGameState != GameState.PLAYING) return; // Only broadcast positions during gameplay

        for (Tank tank : tanks.values()) {
            if (tank.isAlive()) { // Only broadcast alive tanks' updates frequently
                broadcast(String.format("%s;%d;%f;%f;%f",
                        NetworkProtocol.PLAYER_UPDATE, tank.getPlayerId(), tank.getPosition().x, tank.getPosition().y, tank.getRotation()), -1);
            }
        }
        // Broadcasting bullet updates is often too bandwidth-intensive for many bullets.
        // Clients predict bullets based on the initial SHOOT message.
        // Server handles authoritative collision.
    }

    // Send a message to all connected clients
    public void broadcast(String message, int excludePlayerId) {
        logger.debug("Broadcasting (exclude {}): {}", excludePlayerId, message); // Log the broadcast attempt
        if (clients.isEmpty()) {
            logger.warn("Broadcast requested but no clients connected.");
            return;
        }
        // Use ConcurrentHashMap's entrySet for potentially safer iteration if needed,
        // though values() is generally okay for read-only iteration.
        for (ClientHandler handler : clients.values()) {
            if (handler == null) continue; // Shouldn't happen, but safe check
            if (handler.getPlayerId() != excludePlayerId) {
                // Add logging inside the loop for specific handler sends
                logger.debug("Sending to client ID {}: {}", handler.getPlayerId(), message);
                handler.sendMessage(message);
            } else {
                logger.debug("Excluding client ID {} from broadcast.", excludePlayerId);
            }
        }
    }

    // Send an announcement message to all clients
    public void broadcastAnnouncement(String announcement, int excludePlayerId) {
        broadcast(NetworkProtocol.ANNOUNCE + ";" + announcement, excludePlayerId);
    }

    private String formatTime(long millis) {
        long seconds = (millis / 1000) % 60;
        long minutes = (millis / (1000 * 60)) % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }

    public GameMap getGameMap() {
        return gameMap;
    }
}
