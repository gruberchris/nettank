package org.chrisgruber.nettank.server.world;

import org.chrisgruber.nettank.common.world.GameMapData;
import org.chrisgruber.nettank.common.world.TerrainTile;
import org.chrisgruber.nettank.common.world.TerrainState;
import org.chrisgruber.nettank.common.world.Flammability;
import org.joml.Vector2f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class FireManager {
    private static final Logger logger = LoggerFactory.getLogger(FireManager.class);

    private final GameMapData gameMapData;
    private final Map<TilePosition, Long> burningTiles = new ConcurrentHashMap<>();
    private final Random random = new Random();

    private static final float EXPLOSION_IGNITION_RADIUS_TILES = 2.5f;

    public FireManager(GameMapData gameMapData) {
        this.gameMapData = gameMapData;
    }

    public void onExplosion(Vector2f position, float radius) {
        int tileX = (int) (position.x / gameMapData.getTileSize());
        int tileY = (int) (position.y / gameMapData.getTileSize());

        int radiusTiles = (int) Math.ceil(EXPLOSION_IGNITION_RADIUS_TILES);

        logger.debug("Explosion at world ({}, {}) = tile ({}, {}) with radius {} tiles",
                position.x, position.y, tileX, tileY, radiusTiles);

        for (int y = tileY - radiusTiles; y <= tileY + radiusTiles; y++) {
            for (int x = tileX - radiusTiles; x <= tileX + radiusTiles; x++) {
                if (!gameMapData.isValidTile(x, y)) continue;

                float tileCenterX = (x + 0.5f) * gameMapData.getTileSize();
                float tileCenterY = (y + 0.5f) * gameMapData.getTileSize();
                float distToExplosion = position.distance(tileCenterX, tileCenterY);

                float tileRadius = EXPLOSION_IGNITION_RADIUS_TILES * gameMapData.getTileSize();
                if (distToExplosion <= tileRadius) {
                    attemptIgnition(x, y, 1.0f);
                }
            }
        }
    }

    public boolean attemptIgnition(int tileX, int tileY, float chanceMultiplier) {
        TerrainTile tile = gameMapData.getTile(tileX, tileY);
        if (tile == null) return false;

        if (tile.getCurrentState().hasVisualEffect() ||
            tile.getBaseType().getFlammability() == Flammability.NONE) {
            return false;
        }

        if (tile.getCurrentState() == TerrainState.FLOODED) {
            return false;
        }

        float ignitionChance = tile.getBaseType().getFlammability().getIgnitionChance();
        if (random.nextFloat() > ignitionChance * chanceMultiplier) {
            return false;
        }

        tile.setCurrentState(TerrainState.IGNITING);
        tile.setStateChangeTime(System.currentTimeMillis());
        tile.setFireDuration(tile.getBaseType().getBurnDuration());

        TilePosition pos = new TilePosition(tileX, tileY);
        burningTiles.put(pos, System.currentTimeMillis());

        logger.debug("Tile ({}, {}) ignited, will burn for {} ms",
                tileX, tileY, tile.getFireDuration());

        return true;
    }

    public void update(long currentTime) {
        Iterator<Map.Entry<TilePosition, Long>> iterator = burningTiles.entrySet().iterator();

        while (iterator.hasNext()) {
            Map.Entry<TilePosition, Long> entry = iterator.next();
            TilePosition pos = entry.getKey();
            TerrainTile tile = gameMapData.getTile(pos.x, pos.y);

            if (tile == null) {
                iterator.remove();
                continue;
            }

            long timeBurning = currentTime - tile.getStateChangeTime();
            long burnDuration = tile.getFireDuration();

            if (timeBurning < 2000) {
                if (tile.getCurrentState() != TerrainState.IGNITING) {
                    tile.setCurrentState(TerrainState.IGNITING);
                }
            } else if (timeBurning < burnDuration - 3000) {
                if (tile.getCurrentState() != TerrainState.BURNING) {
                    tile.setCurrentState(TerrainState.BURNING);
                    logger.debug("Tile ({}, {}) transitioned to BURNING", pos.x, pos.y);
                }
            } else if (timeBurning < burnDuration) {
                if (tile.getCurrentState() != TerrainState.SMOLDERING) {
                    tile.setCurrentState(TerrainState.SMOLDERING);
                    logger.debug("Tile ({}, {}) transitioned to SMOLDERING", pos.x, pos.y);
                }
            } else {
                tile.setCurrentState(TerrainState.SCORCHED);
                logger.debug("Tile ({}, {}) burned out, now SCORCHED", pos.x, pos.y);
                iterator.remove();
            }
        }
    }

    public List<TileStateChange> getBurningTiles() {
        List<TileStateChange> changes = new ArrayList<>();
        for (TilePosition pos : burningTiles.keySet()) {
            TerrainTile tile = gameMapData.getTile(pos.x, pos.y);
            if (tile != null) {
                changes.add(new TileStateChange(pos.x, pos.y, tile.getCurrentState()));
            }
        }
        return changes;
    }

    public static class TilePosition {
        public final int x;
        public final int y;

        public TilePosition(int x, int y) {
            this.x = x;
            this.y = y;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            TilePosition that = (TilePosition) o;
            return x == that.x && y == that.y;
        }

        @Override
        public int hashCode() {
            return Objects.hash(x, y);
        }
    }

    public static class TileStateChange {
        public final int x;
        public final int y;
        public final TerrainState state;

        public TileStateChange(int x, int y, TerrainState state) {
            this.x = x;
            this.y = y;
            this.state = state;
        }
    }
}
