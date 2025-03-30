package org.chrisgruber.nettank.server.gamemode;

import org.chrisgruber.nettank.common.entities.TankData;
import org.chrisgruber.nettank.common.gamemode.GameModeRule;
import org.chrisgruber.nettank.common.gamemode.GameStartCondition;
import org.chrisgruber.nettank.common.gamemode.GameWinCondition;
import org.chrisgruber.nettank.common.util.GameState;
import org.chrisgruber.nettank.server.entities.FreeForAllPlayerState;
import org.chrisgruber.nettank.server.state.ServerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

public class FreeForAll extends GameMode {
    private static final Logger logger = LoggerFactory.getLogger(FreeForAll.class);

    // Game mode player state
    protected Map<Integer, FreeForAllPlayerState> playerStatesByPlayerId;

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

        // Game mode player state
        this.playerStatesByPlayerId = new java.util.HashMap<>();
    }

    @Override
    public long getCountdownStateLengthInSeconds() {
        long delayPerSecond = 2;
        return gameStartOnCountdownInSeconds * delayPerSecond;
    }

    @Override
    public boolean checkIsVictoryConditionMet(ServerContext serverContext) {
        // Since gameWinCondition is NONE, the game never will never end
        return false;
    }

    @Override
    public void handleNewPlayerJoin(ServerContext serverContext, Integer playerId, String playerName, TankData tankData) {
        logger.info("New player {} has joined the game.", playerName);
        var playerState = new FreeForAllPlayerState(playerId);
        playerState.setRespawnsRemaining(getTotalRespawnsAllowedOnStart());
        playerState.setMainWeaponAmmoCount(getStartingMainWeaponAmmoCount());
        playerStatesByPlayerId.put(playerId, playerState);
    }

    @Override
    public void handleNewPlayerJoinWhileGameInProgress(ServerContext serverContext, Integer playerId, String playerName, TankData tankData) {
        logger.info("New player {} has joined the game in progress.", playerName);
        var playerState = new FreeForAllPlayerState(playerId);
        playerState.setRespawnsRemaining(getTotalRespawnsAllowedOnStart());
        playerState.setMainWeaponAmmoCount(getStartingMainWeaponAmmoCount());
        playerStatesByPlayerId.put(playerId, playerState);
    }

    @Override
    public void handlePlayerLeaveWhileGameInProgress(ServerContext serverContext, Integer playerId, TankData tankData) {
        logger.info("Player {} has left the game.", playerId);
        playerStatesByPlayerId.remove(playerId);
    }

    @Override
    public GameState shouldTransitionFromWaiting(ServerContext serverContext, long currentTime) {
        var playerCount = serverContext.getPlayerCount();
        var minRequiredPlayers = getMinRequiredPlayers();

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
        var playerCount = serverContext.getPlayerCount();
        var minRequiredPlayers = getMinRequiredPlayers();

        logger.trace("Checking transition from COUNTDOWN to PLAYING. Current player count: {} Required player count: {}", playerCount, minRequiredPlayers);

        if (playerCount < minRequiredPlayers) {
            logger.trace("Not enough players to transition to PLAYING. Returning to WAITING state.");
            return GameState.WAITING;
        }

        // Get the total countdown duration and delay per step
        long totalCountdownDuration = getCountdownStateLengthInSeconds();
        double delayPerStep = (double)totalCountdownDuration / gameStartOnCountdownInSeconds;

        // Calculate which countdown number we're on (3,2,1)
        long secondsElapsed = (currentTime - serverContext.stateChangeTime) / 1000;
        int countdownStep = (int)(secondsElapsed / delayPerStep);
        int displayNumber = gameStartOnCountdownInSeconds - countdownStep;

        // Only announce when we reach a new countdown number
        if (displayNumber > 0 && displayNumber != serverContext.lastAnnouncedNumber) {
            logger.info("Countdown announcement: {} seconds until start.", displayNumber);
            serverContext.lastAnnouncedNumber = displayNumber;
        }


        if (currentTime >= serverContext.stateChangeTime + getCountdownStateLengthInSeconds() * 1000L) {
            logger.info("Countdown complete! Transitioning to PLAYING state");
            countdownTimeInSeconds = -1;    // Reset countdown state
            serverContext.lastAnnouncedNumber = -1; // Reset last announced number
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
    public void handlePlayerDeath(ServerContext serverContext, Integer playerId, TankData tankData) {
        logger.info("Player {} has died.", playerId);
        // Usually, decrementing the respawn count but this game mode allows unlimited respawns
    }

    @Override
    public Integer getRemainingRespawnsForPlayer(Integer playerId) {
        FreeForAllPlayerState playerState = playerStatesByPlayerId.get(playerId);
        if (playerState == null) {
            logger.error("Player {} has no game mode player state.", playerId);
            return 0;
        }
        return playerState.getRespawnsRemaining();
    }

    @Override
    public void handlePlayerRespawn(ServerContext serverContext, Integer playerId, TankData tankData) {
        FreeForAllPlayerState playerState = playerStatesByPlayerId.get(playerId);
        if (playerState == null) {
            logger.error("Player {} has no game mode player state.", playerId);
            return;
        }

        tankData.setForSpawn(
                serverContext.gameMapData.getRandomSpawnPoint(),
                0, // Reset rotation to default
                1, // Reset hit points
                0, // Reset death time
                0  // Reset last shot time
        );

        logger.info("Player {} has respawned at position {} with rotation {}.", playerId, tankData.getPosition(), tankData.getRotation());
    }
}
