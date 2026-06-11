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
    private final Map<TilePosition, Long> lastSpreadAttemptTimes = new HashMap<>();
    private final List<TileStateChange> pendingStateChanges = new ArrayList<>();
    private final Random random;

    private static final float EXPLOSION_IGNITION_RADIUS_TILES = 2.5f;
    private static final long SPREAD_ATTEMPT_INTERVAL_MS = 2500;

    public FireManager(GameMapData gameMapData) {
        this(gameMapData, new Random());
    }

    // Random is injectable so tests can drive ignition and spread deterministically
    public FireManager(GameMapData gameMapData, Random random) {
        this.gameMapData = gameMapData;
        this.random = random;
    }

    public void onExplosion(Vector2f position, float radius, long currentTime) {
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
                    attemptIgnition(x, y, 1.0f, currentTime);
                }
            }
        }
    }

    public boolean attemptIgnition(int tileX, int tileY, float chanceMultiplier, long currentTime) {
        TerrainTile tile = gameMapData.getTile(tileX, tileY);
        if (tile == null || !canIgnite(tile)) return false;

        float ignitionChance = tile.getEffectiveType().getFlammability().getIgnitionChance();
        if (random.nextFloat() > ignitionChance * chanceMultiplier) {
            return false;
        }

        igniteTile(tileX, tileY, tile, currentTime);

        return true;
    }

    private boolean canIgnite(TerrainTile tile) {
        return !tile.getCurrentState().hasVisualEffect() &&
                tile.getCurrentState() != TerrainState.SCORCHED &&
                tile.getCurrentState() != TerrainState.FLOODED &&
                tile.getEffectiveType().getFlammability() != Flammability.NONE;
    }

    private void igniteTile(int tileX, int tileY, TerrainTile tile, long currentTime) {
        tile.setCurrentState(TerrainState.IGNITING);
        tile.setStateChangeTime(currentTime);
        tile.setFireDuration(tile.getEffectiveType().getBurnDuration());

        TilePosition pos = new TilePosition(tileX, tileY);
        burningTiles.put(pos, currentTime);
        recordStateChange(tileX, tileY, TerrainState.IGNITING);

        logger.debug("Tile ({}, {}) ignited, will burn for {} ms",
                tileX, tileY, tile.getFireDuration());
    }

    public void update(long currentTime) {
        Iterator<Map.Entry<TilePosition, Long>> iterator = burningTiles.entrySet().iterator();
        List<TilePosition> spreadSources = new ArrayList<>();

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
                    recordStateChange(pos.x, pos.y, TerrainState.IGNITING);
                }
            } else if (timeBurning < burnDuration - 3000) {
                if (tile.getCurrentState() != TerrainState.BURNING) {
                    tile.setCurrentState(TerrainState.BURNING);
                    recordStateChange(pos.x, pos.y, TerrainState.BURNING);
                    logger.debug("Tile ({}, {}) transitioned to BURNING", pos.x, pos.y);
                }
                spreadSources.add(pos);
            } else if (timeBurning < burnDuration) {
                if (tile.getCurrentState() != TerrainState.SMOLDERING) {
                    tile.setCurrentState(TerrainState.SMOLDERING);
                    recordStateChange(pos.x, pos.y, TerrainState.SMOLDERING);
                    logger.debug("Tile ({}, {}) transitioned to SMOLDERING", pos.x, pos.y);
                }
            } else {
                tile.setCurrentState(TerrainState.SCORCHED);

                // A burned-out flammable overlay (e.g. FOREST) is destroyed: the tree is
                // gone, leaving drivable scorched base terrain. Clients mirror this rule
                // on receiving the SCORCHED state (see ClientGameMap.onTerrainStateChanged).
                if (tile.hasOverlay() && tile.getOverlayType().getFlammability() != Flammability.NONE) {
                    logger.debug("Overlay {} at ({}, {}) burned down", tile.getOverlayType(), pos.x, pos.y);
                    tile.setOverlayType(null);
                }

                recordStateChange(pos.x, pos.y, TerrainState.SCORCHED);
                logger.debug("Tile ({}, {}) burned out, now SCORCHED", pos.x, pos.y);
                lastSpreadAttemptTimes.remove(pos);
                iterator.remove();
            }
        }

        for (TilePosition pos : spreadSources) {
            attemptSpread(pos, currentTime);
        }
    }

    // Fire spreads from BURNING tiles to flammable 4-neighbors, rolled once per SPREAD_ATTEMPT_INTERVAL_MS per tile
    private void attemptSpread(TilePosition pos, long currentTime) {
        Long lastAttempt = lastSpreadAttemptTimes.get(pos);
        if (lastAttempt != null && currentTime - lastAttempt < SPREAD_ATTEMPT_INTERVAL_MS) {
            return;
        }
        lastSpreadAttemptTimes.put(pos, currentTime);

        int[][] neighbors = {{0, 1}, {0, -1}, {1, 0}, {-1, 0}};
        for (int[] n : neighbors) {
            int nx = pos.x + n[0];
            int ny = pos.y + n[1];

            TerrainTile neighborTile = gameMapData.getTile(nx, ny);
            if (neighborTile == null || !canIgnite(neighborTile)) continue;

            float spreadChance = neighborTile.getEffectiveType().getFlammability().getSpreadChance();
            if (random.nextFloat() <= spreadChance) {
                igniteTile(nx, ny, neighborTile, currentTime);
            }
        }
    }

    private void recordStateChange(int x, int y, TerrainState state) {
        pendingStateChanges.add(new TileStateChange(x, y, state));
    }

    // Returns state changes accumulated since the last drain (for TST broadcasting) and clears them
    public List<TileStateChange> drainStateChanges() {
        if (pendingStateChanges.isEmpty()) return Collections.emptyList();
        List<TileStateChange> drained = new ArrayList<>(pendingStateChanges);
        pendingStateChanges.clear();
        return drained;
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
