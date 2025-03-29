package org.chrisgruber.nettank.server.gamemode;

import org.chrisgruber.nettank.common.gamemode.GameModeRule;
import org.chrisgruber.nettank.common.gamemode.GameStartCondition;
import org.chrisgruber.nettank.common.gamemode.GameWinCondition;

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
    public boolean checkIsGameReadyToStart() {
        return this.minRequiredPlayers > 0 && this.maxAllowedPlayers > 0;
    }

    @Override
    public boolean checkIsGameConditionMet() {
        return false;
    }
}
