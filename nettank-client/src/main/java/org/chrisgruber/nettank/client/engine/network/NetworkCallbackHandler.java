package org.chrisgruber.nettank.client.engine.network;

import org.chrisgruber.nettank.common.util.GameState;

import java.util.UUID;

public interface NetworkCallbackHandler {
    // Connection Status
    void connectionFailed(String reason);
    void disconnected();
    // Game State / Player ID
    void setLocalPlayerId(int id);
    void setGameState(GameState state, long timeData);
    void addAnnouncement(String message);
    // Entity/World Updates
    void addOrUpdateTank(int id, float x, float y, float rotation, String name, float r, float g, float b, float turretRotation, String tankType);
    void updateTankState(int id, float x, float y, float rotation, float turretRotation, boolean isRespawn);
    void updateTankVisibility(int playerId, boolean visible);
    void removeTank(int id);
    void updatePlayerLives(int playerId, int lives);
    void spawnBullet(UUID bulletId, int ownerId, float x, float y, float dirX, float dirY);
    void handlePlayerHit(int targetId, int shooterId, UUID bulletId, int damage, String side, boolean critical);
    void updateArmorStatus(int front, int left, int right, int rear, int hitPoints);
    void handlePlayerDestroyed(int targetId, int shooterId);
    void storeMapInfo(int widthTiles, int heightTiles, float tileSize);
    void receiveTerrainData(int width, int height, String encodedData);
    void updateShootCooldown(long cooldownRemainingMs);
    void updateAmmoCount(int playerId, int ammoCount);
    void terrainStateChanged(int tileX, int tileY, String stateName);
}