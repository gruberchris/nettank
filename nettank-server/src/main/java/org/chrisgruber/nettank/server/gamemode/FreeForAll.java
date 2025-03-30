package org.chrisgruber.nettank.server.gamemode;

import org.chrisgruber.nettank.common.entities.TankData;
import org.chrisgruber.nettank.common.gamemode.GameModeRule;
import org.chrisgruber.nettank.common.gamemode.GameStartCondition;
import org.chrisgruber.nettank.common.gamemode.GameWinCondition;
import org.chrisgruber.nettank.common.util.GameState;
import org.chrisgruber.nettank.server.state.ServerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FreeForAll extends GameMode {
    private static final Logger logger = LoggerFactory.getLogger(FreeForAll.class);

    public FreeForAll() {
        super();
        this.isInfiniteRespawns = true;
        this.maxAllowedPlayers = 12;
        this.isMainWeaponAmmoLimited = false;
        this.killCountToBroadcastKillStreak = 3;
        this.gameStartOnCountdownInSeconds = 3;
        this.countdownTimeInSeconds = -1;

        this.gameModeRule = GameModeRule.FREE_FOR_ALL;
        this.gameStartCondition = GameStartCondition.IMMEDIATE;
        this.gameWinCondition = GameWinCondition.NONE;
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
        tankData.setInfiniteLivesAllowed(true);
    }

    @Override
    public void handleNewPlayerJoinWhileGameInProgress(ServerContext serverContext, Integer playerId, String playerName, TankData tankData) {
        logger.info("New player {} has joined the game in progress.", playerName);
        tankData.setInfiniteLivesAllowed(true);
    }

    @Override
    public void handlePlayerLeaveWhileGameInProgress(ServerContext serverContext, Integer playerId, TankData tankData) {
        logger.info("Player {} has left the game.", playerId);
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
}
