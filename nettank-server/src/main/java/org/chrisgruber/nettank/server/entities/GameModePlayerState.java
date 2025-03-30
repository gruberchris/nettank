package org.chrisgruber.nettank.server.entities;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class GameModePlayerState {
    private static final Logger logger = LoggerFactory.getLogger(GameModePlayerState.class);

    private final int playerId;
    private int respawnsRemaining = 0;
    private int mainWeaponAmmoCount = 0;

    protected GameModePlayerState(Integer playerId) {
        this.playerId = playerId;
    }

    // Player ID
    public int getPlayerId() { return playerId; }

    // Respawns Remaining
    public int getRespawnsRemaining() { return respawnsRemaining; }
    public void setRespawnsRemaining(int respawnsRemaining) { this.respawnsRemaining = respawnsRemaining; }

    // Main Weapon Ammo Count
    public int getMainWeaponAmmoCount() { return mainWeaponAmmoCount; }
    public void setMainWeaponAmmoCount(int mainWeaponAmmoCount) { this.mainWeaponAmmoCount = mainWeaponAmmoCount; }
}
