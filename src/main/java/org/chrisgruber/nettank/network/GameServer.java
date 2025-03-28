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
import java.net.SocketException;
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
    private volatile int hostPlayerId = -1;

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

    private final List<Thread> clientHandlerThreads = new CopyOnWriteArrayList<>(); // Track handler threads


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

    public synchronized void requestShutdown(int requestorPlayerId) {
        if (requestorPlayerId == hostPlayerId && hostPlayerId != -1) { // Check against tracked host ID
            logger.info("Shutdown requested by host (Player {}). Initiating server stop...", requestorPlayerId);
            stop();
        } else {
            logger.warn("Shutdown requested by non-host client (Player {}) or host ID not set. Request ignored.", requestorPlayerId);
        }
    }

    public void startClientThread(ClientHandler handler) {
        Thread thread = new Thread(handler); // This thread now runs ClientHandler.run()
        int tempId = handler.getSocket().getPort();
        // Name is set/updated later in setPlayerInfo
        thread.setName("ClientHandler-Main-" + tempId);
        thread.setUncaughtExceptionHandler((t, e) -> {
            logger.error("Uncaught exception in thread {}: {}", t.getName(), e.getMessage(), e);
            // Handler itself should trigger closeConnection on error now
            // handler.closeConnection("Internal error: " + e.getMessage());
        });
        clientHandlerThreads.add(thread); // Track the thread
        thread.start();
        logger.info("Started client handler main thread: {}", thread.getName());
    }

    public void start() throws IOException {
        serverSocket = new ServerSocket(port, 50, InetAddress.getByName("0.0.0.0"));
        running = true;
        logger.info("Server started on {}:{}", serverSocket.getInetAddress().getHostAddress(), port);

        // Start game loop in a separate thread
        Thread gameLoopThread = new Thread(this::gameLoop);
        gameLoopThread.setName("GameLoop");
        gameLoopThread.start();

        logger.info("Waiting for client connections...");

        // Accept client connections
        while (running) {
            try {
                Socket clientSocket = serverSocket.accept();
                logger.info("Client connected: {}", clientSocket.getInetAddress().getHostAddress());

                // Handle client connection in a new thread
                ClientHandler clientHandler = new ClientHandler(clientSocket, this);
                startClientThread(clientHandler); // This starts the handler's main run method

                // Player registration happens after receiving CONNECT message

            } catch (SocketException e) {
                if (running) {
                    logger.error("Server socket accept error: {}", e.getMessage());
                } else {
                    logger.info("Server socket closed, accept loop terminating.");
                }
                // If running is false, the loop will exit
            } catch (IOException e) {
                if (running) {
                    logger.error("Error accepting client connection", e);
                }
            }
        }

        logger.info("Server accept loop finished.");
        // Wait for game loop thread to finish? Optional
        try {
            gameLoopThread.join(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public void stop() {
        logger.info("Server shutdown requested...");
        running = false;

        // Close server socket to stop accepting new connections
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
                logger.info("Server socket closed.");
            }
        } catch (IOException e) {
            logger.error("Error closing server socket.", e);
        }

        // Close existing client connections
        logger.info("Closing {} client connections...", clients.size());
        // Create a copy of values to avoid ConcurrentModificationException if closeConnection->removePlayer modifies 'clients'
        List<ClientHandler> handlersToClose = new ArrayList<>(clients.values());
        for (ClientHandler handler : handlersToClose) {
            if(handler != null) {
                handler.closeConnection("Server shutting down");
            }
        }
        // Wait a moment for handlers to close? Optional.
        try { Thread.sleep(500); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

        clients.clear();
        tanks.clear();
        bullets.clear();

        // Interrupt any remaining handler threads (reader/sender/main)
        logger.info("Interrupting any remaining client handler threads...");
        for (Thread t : clientHandlerThreads) {
            if (t != null && t.isAlive()) {
                logger.debug("Interrupting thread: {}", t.getName());
                t.interrupt();
            }
        }
        clientHandlerThreads.clear(); // Clear tracking list

        logger.info("Server stopped.");
    }

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
        boolean isFirstPlayer = false;

        // --- Assign Host Role ---
        if (hostPlayerId == -1 && clients.isEmpty()) { // Check if first player to register
            hostPlayerId = playerId; // Assign host ID
            isFirstPlayer = true;
            logger.info("Player ID {} ({}) assigned as HOST.", playerId, playerName);
        }

        Vector3f assignedColor = availableColors.removeFirst();
        Vector2f spawnPos = gameMap.getRandomSpawnPoint();
        Tank newTank = new Tank(playerId, spawnPos.x, spawnPos.y, assignedColor, playerName);
        newTank.setLives(currentGameState == GameState.PLAYING ? 0 : Tank.INITIAL_LIVES);

        handler.setPlayerInfo(playerId, playerName, assignedColor);
        clients.put(playerId, handler);
        tanks.put(playerId, newTank);

        logger.info("Player registered: ID={}, Name={}, Color={}, IsHost={}", playerId, playerName, assignedColor, isFirstPlayer);

        // 1. Send assigned ID, color, AND HOST STATUS to the new player
        handler.sendMessage(String.format("%s;%d;%f;%f;%f;%b", // Added %b for boolean
                NetworkProtocol.ASSIGN_ID,
                playerId,
                assignedColor.x, assignedColor.y, assignedColor.z,
                isFirstPlayer // Send true if host, false otherwise
        ));

        // 2. Send current game state and existing players/bullets to the new player
        handler.sendMessage(String.format("%s;%s;%d", NetworkProtocol.GAME_STATE, currentGameState.name(),
                // Calculate timeData based on state... (existing logic)
                (currentGameState == GameState.PLAYING ? roundStartTimeMillis : (currentGameState == GameState.COUNTDOWN ? stateChangeTime + COUNTDOWN_SECONDS * 1000 : 0))
        ));

        for (Tank tank : tanks.values()) {
            // Send NEW_PLAYER for all existing tanks (including self)
            handler.sendMessage(String.format("%s;%d;%f;%f;%f;%s;%f;%f;%f",
                    NetworkProtocol.NEW_PLAYER, tank.getPlayerId(), tank.getPosition().x, tank.getPosition().y, tank.getRotation(),
                    tank.getName(), tank.getColor().x, tank.getColor().y, tank.getColor().z));
            // Send current lives for all existing tanks
            handler.sendMessage(String.format("%s;%d;%d", NetworkProtocol.PLAYER_LIVES, tank.getPlayerId(), tank.getLives()));
        }
        // Send existing bullets (optional)

        // 3. Inform all *other* players about the new player
        broadcast(String.format("%s;%d;%f;%f;%f;%s;%f;%f;%f",
                NetworkProtocol.NEW_PLAYER, playerId, spawnPos.x, spawnPos.y, newTank.getRotation(),
                playerName, assignedColor.x, assignedColor.y, assignedColor.z), playerId); // Exclude new player

        // 4. Send lives of new player to others
        broadcast(String.format("%s;%d;%d", NetworkProtocol.PLAYER_LIVES, playerId, newTank.getLives()), playerId);


        // Update game state if needed
        checkGameStateTransition();
    }

    // Called by ClientHandler when connection is lost/closed
    public synchronized void removePlayer(int playerId) {
        ClientHandler handler = clients.remove(playerId);
        Tank tank = tanks.remove(playerId);

        // Remove thread from tracking list (best effort, find by handler if possible)
        // This part is tricky as threads might die before removal.
        // Maybe ClientHandler should notify server *before* thread truly exits?

        // --- Reset host if the host leaves ---
        if (playerId == hostPlayerId) {
            logger.info("Host (Player {}) disconnected. Resetting host ID.", playerId);
            hostPlayerId = -1;
            // Optional: Assign host to the next player ID 0 if they exist? Or just wait for new connection?
            // For simplicity, let's just reset it. The server might not be manageable until a new host joins.
            // Or: if (!clients.isEmpty()) { hostPlayerId = clients.keySet().iterator().next(); logger.info("Assigned new host: Player {}", hostPlayerId);}
        }

        if (handler != null && tank != null) {
            logger.info("Player removed: ID={}, Name={}", playerId, tank.getName());
            if (tank.getColor() != null) {
                availableColors.add(tank.getColor());
                Collections.shuffle(availableColors);
            }
            broadcast(NetworkProtocol.PLAYER_LEFT + ";" + playerId, -1);
            checkGameStateTransition();
        } else {
            logger.warn("Attempted to remove player {}, but handler or tank was already null.", playerId);
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
            //bullets.clear(); // No bullets outside of PLAYING state
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
        GameState previousState = currentGameState;

        int totalPlayers = tanks.size();

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
                    // Reset players for the new round
                    resetPlayersForNewRound();

                    roundStartTimeMillis = currentTime;
                    changeState(GameState.PLAYING, currentTime);
                } else {
                    // Announce countdown numbers (simple example, could be more robust)
                    long remainingMillis = (stateChangeTime + COUNTDOWN_SECONDS * 1000) - currentTime;
                    int remainingSeconds = (int)Math.ceil(remainingMillis / 1000.0);
                    // TODO: Send announcements more intelligently (e.g., only once per second)
                }
                break;
            case PLAYING:
                // CheckWinCondition handles transition out of PLAYING
                if (tanks.size() < MIN_PLAYERS_TO_START && totalPlayers > 1) {
                    // Optional: End round early if too many disconnect?
                    // logger.info("Not enough players left ({}), ending round early.", tanks.size());
                    // checkWinCondition(); // Force check which might lead to draw/win
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
        int totalPlayers = tanks.size();

        // If there's only 1 player total (or 0), the round shouldn't end based on alive count alone.
        // Let the game run indefinitely for testing/single-player or add other conditions later.
        if (totalPlayers <= 1) {
            logger.trace("Skipping win condition check: Only {} player(s) connected.", totalPlayers);
            return; // Don't end the round if only 0 or 1 player is connected.
        }

        // --- Count alive players ONLY if there were 2 or more players ---
        for (Tank tank : tanks.values()) {
            if (tank.isAlive()) {
                aliveCount++;
                lastAliveTank = tank;
            }
        }

        // --- End round only if 1 or 0 players are left alive AND the round was meaningful (>1 player started/present) ---
        if (aliveCount <= 1) {
            logger.info("Round over condition met (Alive: {}, Total: {}).", aliveCount, totalPlayers);
            long finalTime = System.currentTimeMillis() - roundStartTimeMillis;
            changeState(GameState.ROUND_OVER, System.currentTimeMillis());

            String winnerName = "NO ONE";
            int winnerId = -1;
            if (lastAliveTank != null) {
                winnerName = lastAliveTank.getName();
                winnerId = lastAliveTank.getPlayerId();
                logger.info("Winner: {} (ID: {}).", winnerName, winnerId);

                //broadcast(String.format("%s;%d;%s;%d", NetworkProtocol.ROUND_OVER, lastAliveTank.getPlayerId(), winnerName, finalTime), -1);
                //broadcastAnnouncement(winnerName + " WINS! FINAL TIME: " + formatTime(finalTime), -1);
            } else {
                logger.info("Round ended in a draw.");
                //broadcast(String.format("%s;-1;DRAW;%d", NetworkProtocol.ROUND_OVER, finalTime), -1); // -1 for winner ID indicates draw
                //broadcastAnnouncement("DRAW! FINAL TIME: " + formatTime(finalTime), -1);
            }

            // Broadcast winner/draw info
            broadcast(String.format("%s;%d;%s;%d", NetworkProtocol.ROUND_OVER, winnerId, winnerName, finalTime), -1);
            if (winnerId != -1) {
                broadcastAnnouncement(winnerName + " WINS! FINAL TIME: " + formatTime(finalTime), -1);
            } else {
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
