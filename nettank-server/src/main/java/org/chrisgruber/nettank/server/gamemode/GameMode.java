package org.chrisgruber.nettank.server.gamemode;

import org.chrisgruber.nettank.common.entities.TankData;
import org.chrisgruber.nettank.common.gamemode.GameModeRule;
import org.chrisgruber.nettank.common.gamemode.GameStartCondition;
import org.chrisgruber.nettank.common.gamemode.GameWinCondition;
import org.chrisgruber.nettank.common.util.GameState;
import org.chrisgruber.nettank.server.state.ServerContext;

public abstract class GameMode {

    protected int totalRespawnsAllowedOnStart;
    protected int minRequiredPlayers;
    protected int maxAllowedPlayers;
    protected int startingMainWeaponAmmoCount;  // -1 for unlimited ammo allowed
    protected int killCountToBroadcastKillStreak;

    protected GameModeRule gameModeRule;
    protected GameStartCondition gameStartCondition;
    protected GameWinCondition gameWinCondition;

    protected int gameStartOnCountdownInSeconds;
    protected int gameStartOnPlayerCountTotal;
    protected int gameEndsOnCountdownInMinutes;
    protected int gameEndsOnFirstPlayerToReachScore;
    protected int gameEndsOnTotalPlayersRemaining;

    protected int countdownTimeInSeconds;

    protected GameMode() {
        this.totalRespawnsAllowedOnStart = Integer.MAX_VALUE;
        this.minRequiredPlayers = 1;
        this.maxAllowedPlayers = 12;
        this.startingMainWeaponAmmoCount = -1; // -1 for unlimited ammo allowed
        this.killCountToBroadcastKillStreak = 3;

        this.gameModeRule = GameModeRule.FREE_FOR_ALL;
        this.gameStartCondition = GameStartCondition.IMMEDIATE;
        this.gameWinCondition = GameWinCondition.NONE;

        this.gameStartOnCountdownInSeconds = 0;
        this.gameStartOnPlayerCountTotal = 0;
        this.gameEndsOnCountdownInMinutes = 0;
        this.gameEndsOnFirstPlayerToReachScore = 0;
        this.gameEndsOnTotalPlayersRemaining = 0;

        this.countdownTimeInSeconds = 0;
    }

    public abstract boolean checkIsVictoryConditionMet(ServerContext serverContext);
    public abstract void handleNewPlayerJoin(ServerContext serverContext, int playerId, String playerName, TankData tankData);
    public abstract void handleNewPlayerJoinWhileGameInProgress(ServerContext serverContext, int playerId, String playerName, TankData tankData);
    public abstract void handlePlayerLeaveWhileGameInProgress(ServerContext serverContext, int playerId, TankData tankData);
    public abstract long getCountdownStateLengthInSeconds();
    public abstract void handlePlayerDeath(ServerContext serverContext, int playerId, TankData tankData);
    public abstract int getRemainingRespawnsForPlayer(int playerId);
    public abstract void handlePlayerRespawn(ServerContext serverContext, int playerId, TankData tankData);

    // Implementations for implementing conditions to transition between game states
    public abstract GameState shouldTransitionFromWaiting(ServerContext serverContext, long currentTime);
    public abstract GameState shouldTransitionFromCountdown(ServerContext serverContext, long currentTime);
    public abstract GameState shouldTransitionFromPlaying(ServerContext serverContext, long currentTime);
    public abstract GameState shouldTransitionFromRoundOver(ServerContext serverContext, long currentTime);

    public synchronized int getTotalRespawnsAllowedOnStart() {
        return totalRespawnsAllowedOnStart;
    }

    public synchronized int getMinRequiredPlayers() {
        return minRequiredPlayers;
    }

    public synchronized int getMaxAllowedPlayers() {
        return maxAllowedPlayers;
    }

    public synchronized int getStartingMainWeaponAmmoCount() {
        return startingMainWeaponAmmoCount;
    }

    public synchronized GameModeRule getGameModeRule() {
        return gameModeRule;
    }

    public synchronized GameStartCondition getGameStartCondition() {
        return gameStartCondition;
    }

    public synchronized GameWinCondition getGameWinCondition() {
        return gameWinCondition;
    }

    public synchronized int getGameStartOnCountdownInSeconds() {
        return gameStartOnCountdownInSeconds;
    }

    public synchronized int getGameStartOnPlayerCountTotal() {
        return gameStartOnPlayerCountTotal;
    }

    public synchronized int getGameEndsOnCountdownInMinutes() {
        return gameEndsOnCountdownInMinutes;
    }

    public synchronized int getGameEndsOnFirstPlayerToReachScore() {
        return gameEndsOnFirstPlayerToReachScore;
    }

    public synchronized int getGameEndsOnTotalPlayersRemaining() {
        return gameEndsOnTotalPlayersRemaining;
    }

    public synchronized int getKillCountToBroadcastKillStreak() {
        return killCountToBroadcastKillStreak;
    }

    public synchronized int getCountdownTimeInSeconds() { return countdownTimeInSeconds; }
}
