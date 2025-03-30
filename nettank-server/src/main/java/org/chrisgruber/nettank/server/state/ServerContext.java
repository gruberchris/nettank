package org.chrisgruber.nettank.server.state; // Example new package

import org.chrisgruber.nettank.common.entities.BulletData;
import org.chrisgruber.nettank.common.entities.TankData;
import org.chrisgruber.nettank.common.util.GameState;
import org.chrisgruber.nettank.common.world.GameMapData;
import org.chrisgruber.nettank.server.ClientHandler; // Server specific
import org.chrisgruber.nettank.server.gamemode.GameMode;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

// Holds the mutable runtime state of the game server
public class ServerContext {

    // General Server State
    public volatile boolean running = false;
    public final AtomicBoolean stopping = new AtomicBoolean(false);

    // Player/Client Management
    public final Map<Integer, ClientHandler> clients = new ConcurrentHashMap<>();
    public final Map<Integer, TankData> tanks = new ConcurrentHashMap<>();
    public final AtomicInteger nextPlayerId = new AtomicInteger(0);

    // World State
    public final List<BulletData> bullets = new CopyOnWriteArrayList<>();
    public GameMapData gameMapData;
    public GameMode gameMode;

    // Game Flow State (volatile for thread safety)
    public volatile GameState currentGameState = GameState.WAITING;
    public volatile long roundStartTimeMillis = 0;      // When the round started
    public volatile long stateChangeTime;               // When the current state was entered
    public volatile int lastAnnouncedNumber = -1;       // Last number announced to clients

    // General Game Settings
    public volatile long tankRespawnDelayMillis = 3000;    // Config setting for how long to wait for destroyed tanks to respawn

    // --- Constructor ---
    public ServerContext() {
        this.gameMapData = null;
        this.stateChangeTime = System.currentTimeMillis(); // Initial state starts now
    }

    // Save this code until I decide not to mutate the game state in this class or leave it in GameServer
    /*
    public synchronized void changeGameState(GameState newState, long timeDataForState) {
        if (this.currentGameState == newState) return; // Avoid redundant changes

        // Log the change (maybe pass logger in?)
        // logger.info("Server changing state from {} to {}", currentGameState, newState);

        this.currentGameState = newState;
        this.stateChangeTime = System.currentTimeMillis(); // Record time of actual change

        // Update other state based on transition
        if (newState == GameState.PLAYING) {
            this.roundStartTimeMillis = timeDataForState; // Assume timeData is start time
            this.bullets.clear(); // Clear bullets on entering PLAYING
        } else if (newState == GameState.WAITING || newState == GameState.COUNTDOWN) {
            this.roundStartTimeMillis = 0;
            this.bullets.clear(); // Clear bullets when not playing
        }
    }
    */

    public int getPlayerCount() { return clients.size(); }
}