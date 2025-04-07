package org.chrisgruber.nettank.server.gamemode;

import org.chrisgruber.nettank.common.entities.TankData;
import org.chrisgruber.nettank.common.gamemode.GameModeRule;
import org.chrisgruber.nettank.common.gamemode.GameStartCondition;
import org.chrisgruber.nettank.common.gamemode.GameWinCondition;
import org.chrisgruber.nettank.common.util.GameState;
import org.chrisgruber.nettank.server.entities.FreeForAllPlayerState;
import org.chrisgruber.nettank.server.state.ServerContext;
import org.joml.Vector2f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

public class FreeForAll extends GameMode {
    private static final Logger logger = LoggerFactory.getLogger(FreeForAll.class);

    // Game mode player state
    protected final Map<Integer, FreeForAllPlayerState> playerStatesByPlayerId =
            new java.util.concurrent.ConcurrentHashMap<>();

    public FreeForAll() {
        super();
        this.maxAllowedPlayers = 12;
        this.killCountToBroadcastKillStreak = 3;
        this.startingMainWeaponAmmoCount = -1; // -1 for unlimited ammo allowed

        // Round starting countdown state and configuration
        this.gameStartOnCountdownInSeconds = 3;
        this.countdownTimeInSeconds = -1;

        this.gameModeRule = GameModeRule.FREE_FOR_ALL;
        this.gameStartCondition = GameStartCondition.IMMEDIATE;
        this.gameWinCondition = GameWinCondition.NONE;
    }

    @Override
    public long getCountdownStateLengthInSeconds() {
        long delayPerSecond = 2;
        synchronized (stateLock) {
            return gameStartOnCountdownInSeconds * delayPerSecond;
        }
    }

    @Override
    public boolean checkIsVictoryConditionMet(ServerContext serverContext) {
        // Since gameWinCondition is NONE, the game never will never end
        return false;
    }

    @Override
    public void handleNewPlayerJoin(ServerContext serverContext, int playerId, String playerName, TankData tankData) {
        logger.info("New player {} has joined the game.", playerName);
        var playerState = new FreeForAllPlayerState(playerId);

        synchronized (stateLock) {
            playerState.setRespawnsRemaining(getTotalRespawnsAllowedOnStart());
            playerState.setMainWeaponAmmoCount(getStartingMainWeaponAmmoCount());
            playerStatesByPlayerId.put(playerId, playerState);
            tankData.setForSpawn(
                    serverContext.gameMapData.getRandomSpawnPoint(),
                    this.random.nextFloat(0f, 359f), // Random rotation
                    1, // Hit points
                    0, // Death time
                    0  // Last shot time
            );
        }
    }

    @Override
    public void handleNewPlayerJoinWhileGameInProgress(ServerContext serverContext, int playerId, String playerName, TankData tankData) {
        logger.info("New player {} has joined the game in progress.", playerName);
        var playerState = new FreeForAllPlayerState(playerId);

        synchronized (stateLock) {
            playerState.setRespawnsRemaining(getTotalRespawnsAllowedOnStart());
            playerState.setMainWeaponAmmoCount(getStartingMainWeaponAmmoCount());
            playerStatesByPlayerId.put(playerId, playerState);
            tankData.setForSpawn(
                    serverContext.gameMapData.getRandomSpawnPoint(),
                    this.random.nextFloat(0f, 359f), // Random rotation
                    1, // Hit points
                    0, // Death time
                    0  // Last shot time
            );
        }
    }

    @Override
    public void handlePlayerLeaveWhileGameInProgress(ServerContext serverContext, int playerId, TankData tankData) {
        logger.info("Player {} has left the game.", playerId);

        synchronized (stateLock) {
            playerStatesByPlayerId.remove(playerId);
        }
    }

    @Override
    public GameState shouldTransitionFromWaiting(ServerContext serverContext, long currentTime) {
        var playerCount = serverContext.getPlayerCount();   // getting player count is thread-safe as it's a volatile field and synchronized with an Object lock
        int minRequiredPlayers;

        synchronized (stateLock) {
            minRequiredPlayers = getMinRequiredPlayers();
        }

        logger.trace("Checking transition from WAITING to COUNTDOWN. Current player count: {} Required player count: {}", playerCount, minRequiredPlayers);

        if (playerCount >= minRequiredPlayers) {
            logger.trace("Transitioning to COUNTDOWN state.");
            return GameState.COUNTDOWN;
        }

        logger.trace("Not enough players to transition to COUNTDOWN. Waiting for more players.");

        return GameState.WAITING;
    }

    @Override
    public GameState shouldTransitionFromCountdown(ServerContext serverContext, long currentTime) {
        var playerCount = serverContext.getPlayerCount();   // getting player count is thread-safe as it's a volatile field and synchronized with an Object lock
        int minRequiredPlayers;

        synchronized (stateLock) {
            minRequiredPlayers = getMinRequiredPlayers();
        }

        logger.trace("Checking transition from COUNTDOWN to PLAYING. Current player count: {} Required player count: {}", playerCount, minRequiredPlayers);

        if (playerCount < minRequiredPlayers) {
            logger.trace("Not enough players to transition to PLAYING. Returning to WAITING state.");
            return GameState.WAITING;
        }

        long totalCountdownDuration;
        int gameStartCountdown;

        synchronized (stateLock) {
            totalCountdownDuration = getCountdownStateLengthInSeconds();
            gameStartCountdown = getGameStartOnCountdownInSeconds();
        }

        double delayPerStep = (double)totalCountdownDuration / gameStartCountdown;
        long secondsElapsed = (currentTime - serverContext.stateChangeTime) / 1000;
        int countdownStep = (int)(secondsElapsed / delayPerStep);
        int displayNumber = gameStartCountdown - countdownStep;
        int lastAnnounced = serverContext.lastAnnouncedNumber;
        boolean shouldTransition = currentTime >= serverContext.stateChangeTime + totalCountdownDuration * 1000L;

        if (displayNumber > 0 && displayNumber != lastAnnounced) {
            logger.info("Countdown announcement: {} seconds until start.", displayNumber);
            serverContext.lastAnnouncedNumber = displayNumber;
        }

        if (shouldTransition) {
            logger.info("Countdown complete! Transitioning to PLAYING state");

            synchronized (stateLock) {
                countdownTimeInSeconds = -1;    // Reset countdown state
                serverContext.lastAnnouncedNumber = -1; // Reset last announced number
            }

            return GameState.PLAYING;
        }

        logger.trace("Still in COUNTDOWN state. Waiting for countdown to finish.");

        return GameState.COUNTDOWN;
    }

    @Override
    public GameState shouldTransitionFromPlaying(ServerContext serverContext, long currentTime) {
        logger.trace("FreeForAll: Checking transition from PLAYING to PLAYING.");
        return GameState.PLAYING;
    }

    @Override
    public GameState shouldTransitionFromRoundOver(ServerContext serverContext, long currentTime) {
        logger.trace("FreeForAll: Checking transition from ROUND_OVER to WAITING.");
        return GameState.ROUND_OVER;
    }

    @Override
    public void handlePlayerDeath(ServerContext serverContext, int playerId, TankData tankData) {
        logger.info("Player {} has died.", playerId);
        // Usually, decrementing the respawn count but this game mode allows unlimited respawns
    }

    @Override
    public int getRemainingRespawnsForPlayer(int playerId) {
        FreeForAllPlayerState playerState;

        synchronized (stateLock) {
            playerState = playerStatesByPlayerId.get(playerId);
        }

        if (playerState == null) {
            logger.error("Player {} has no game mode player state.", playerId);

            return 0;
        }

        return playerState.getRespawnsRemaining();
    }

    @Override
    public void handlePlayerRespawn(ServerContext serverContext, int playerId, TankData tankData) {
        Vector2f tankPosition;
        float tankRotation;

        synchronized (stateLock) {
            FreeForAllPlayerState playerState = playerStatesByPlayerId.get(playerId);

            if (playerState == null) {
                logger.error("Player {} has no game mode player state.", playerId);

                return;
            }

            tankData.setForSpawn(
                    serverContext.gameMapData.getRandomSpawnPoint(),
                    this.random.nextFloat(0f, 359f), // Reset rotation to default
                    1, // Reset hit points
                    0, // Reset death time
                    0  // Reset last shot time
            );

            tankData.setInputState(false, false, false, false);

            tankPosition = tankData.getPosition();
            tankRotation = tankData.getRotation();
        }

        logger.info("Player {} has respawned at position {} with rotation {}.", playerId, tankPosition, tankRotation);
    }
}
