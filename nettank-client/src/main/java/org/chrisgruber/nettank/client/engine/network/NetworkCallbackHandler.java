package org.chrisgruber.nettank.client.engine.network;

import org.chrisgruber.nettank.common.util.GameState;

public interface NetworkCallbackHandler {
    // Connection Status
    void connectionFailed(String reason);
    void disconnected();
    // Game State / Player ID
    void setLocalPlayerId(int id);
    void setGameState(GameState state, long timeData);
    void addAnnouncement(String message);
    // Entity/World Updates
    void addOrUpdateTank(int id, float x, float y, float rotation, String name, float r, float g, float b, int lives);
    void updateTankState(int id, float x, float y, float rotation);
    void removeTank(int id);
    void updatePlayerLives(int playerId, int lives);
    void spawnBullet(int ownerId, float x, float y, float dirX, float dirY);
    void handlePlayerHit(int targetId, int shooterId);
    void handlePlayerDestroyed(int targetId, int shooterId);
}