package org.chrisgruber.nettank.server.gamemode;

import org.chrisgruber.nettank.common.entities.TankData;
import org.chrisgruber.nettank.common.gamemode.GameModeRule;
import org.chrisgruber.nettank.common.gamemode.GameStartCondition;
import org.chrisgruber.nettank.common.gamemode.GameWinCondition;
import org.chrisgruber.nettank.common.util.GameState;
import org.chrisgruber.nettank.server.state.ServerContext;

public abstract class GameMode {

    protected boolean isInfiniteRespawns;
    protected int totalRespawnsAllowed;
    protected int minRequiredPlayers;
    protected int maxAllowedPlayers;
    protected boolean isMainWeaponAmmoLimited;
    protected int startingMainWeaponAmmoCount;
    protected int killCountToBroadcastKillStreak;

    protected GameModeRule gameModeRule;
    protected GameStartCondition gameStartCondition;
    protected GameWinCondition gameWinCondition;

    protected int gameStartOnCountdownInSeconds;
    protected int gameStartOnPlayerCountTotal;
    protected int gameEndsOnCountdownInMinutes;
    protected int gameEndsOnFirstPlayerToReachScore;
    protected int gameEndsOnTotalPlayersRemaining;

    protected GameMode() {
        this.isInfiniteRespawns = true;
        this.totalRespawnsAllowed = 3;
        this.minRequiredPlayers = 1;
        this.maxAllowedPlayers = 12;
        this.isMainWeaponAmmoLimited = false;
        this.startingMainWeaponAmmoCount = 0;
        this.killCountToBroadcastKillStreak = 3;

        this.gameModeRule = GameModeRule.FREE_FOR_ALL;
        this.gameStartCondition = GameStartCondition.IMMEDIATE;
        this.gameWinCondition = GameWinCondition.NONE;

        this.gameStartOnCountdownInSeconds = 0;
        this.gameStartOnPlayerCountTotal = 0;
        this.gameEndsOnCountdownInMinutes = 0;
        this.gameEndsOnFirstPlayerToReachScore = 0;
        this.gameEndsOnTotalPlayersRemaining = 0;
    }

    public abstract boolean checkIsVictoryConditionMet(ServerContext serverContext);
    public abstract void handleNewPlayerJoin(ServerContext serverContext, Integer playerId, String playerName, TankData tankData);
    public abstract void handleNewPlayerJoinWhileGameInProgress(ServerContext serverContext, Integer playerId, String playerName, TankData tankData);
    public abstract void handlePlayerLeaveWhileGameInProgress(ServerContext serverContext, Integer playerId, TankData tankData);

    // Implementations for implementing conditions to transition between game states
    public abstract GameState shouldTransitionFromWaiting(ServerContext serverContext, long currentTime);
    public abstract GameState shouldTransitionFromCountdown(ServerContext serverContext, long currentTime);
    public abstract GameState shouldTransitionFromPlaying(ServerContext serverContext, long currentTime);
    public abstract GameState shouldTransitionFromRoundOver(ServerContext serverContext, long currentTime);

    public boolean isInfiniteRespawns() {
        return isInfiniteRespawns;
    }

    public int getTotalRespawnsAllowed() {
        return totalRespawnsAllowed;
    }

    public int getMinRequiredPlayers() {
        return minRequiredPlayers;
    }

    public int getMaxAllowedPlayers() {
        return maxAllowedPlayers;
    }

    public boolean isMainWeaponAmmoLimited() {
        return isMainWeaponAmmoLimited;
    }

    public int getStartingMainWeaponAmmoCount() {
        return startingMainWeaponAmmoCount;
    }

    public GameModeRule getGameModeRule() {
        return gameModeRule;
    }

    public GameStartCondition getGameStartCondition() {
        return gameStartCondition;
    }

    public GameWinCondition getGameWinCondition() {
        return gameWinCondition;
    }

    public int getGameStartOnCountdownInSeconds() {
        return gameStartOnCountdownInSeconds;
    }

    public int getGameStartOnPlayerCountTotal() {
        return gameStartOnPlayerCountTotal;
    }

    public int getGameEndsOnCountdownInMinutes() {
        return gameEndsOnCountdownInMinutes;
    }

    public int getGameEndsOnFirstPlayerToReachScore() {
        return gameEndsOnFirstPlayerToReachScore;
    }

    public int getGameEndsOnTotalPlayersRemaining() {
        return gameEndsOnTotalPlayersRemaining;
    }

    public int getKillCountToBroadcastKillStreak() {
        return killCountToBroadcastKillStreak;
    }
}
