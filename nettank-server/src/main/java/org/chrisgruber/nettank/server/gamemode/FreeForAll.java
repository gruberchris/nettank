package org.chrisgruber.nettank.server.gamemode;

import org.chrisgruber.nettank.common.entities.TankData;
import org.chrisgruber.nettank.common.gamemode.GameModeRule;
import org.chrisgruber.nettank.common.gamemode.GameStartCondition;
import org.chrisgruber.nettank.common.gamemode.GameWinCondition;
import org.chrisgruber.nettank.server.state.ServerContext;

public class FreeForAll extends GameMode {
    public FreeForAll() {
        super();
        this.isInfiniteRespawns = true;
        this.maxAllowedPlayers = 12;
        this.isMainWeaponAmmoLimited = false;
        this.killCountToBroadcastKillStreak = 3;

        this.gameModeRule = GameModeRule.FREE_FOR_ALL;
        this.gameStartCondition = GameStartCondition.IMMEDIATE;
        this.gameWinCondition = GameWinCondition.NONE;
    }

    @Override
    public boolean checkIsGameReadyToStart(ServerContext serverContext) {
        int playerCount = serverContext.getPlayerCount();
        return playerCount >= minRequiredPlayers && playerCount <= maxAllowedPlayers;
    }

    @Override
    public boolean checkIsGameConditionMet(ServerContext serverContext) {
        // Since gameWinCondition is NONE, the game never will never end
        return false;
    }

    @Override
    public void handleNewPlayerJoinWhileGameInProgress(ServerContext serverContext, Integer playerId, String playerName, TankData tankData) {
        tankData.setLives(1000); // TODO: Should be infinite respawns. Have to fix the code in GameServer to support that.
    }

    @Override
    public void handleRoundOver(ServerContext serverContext) {
        // TODO:
    }
}
