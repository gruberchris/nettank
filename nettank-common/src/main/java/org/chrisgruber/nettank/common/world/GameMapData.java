package org.chrisgruber.nettank.common.world;

import org.chrisgruber.nettank.common.entities.Entity;
import org.joml.Vector2f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Random;

public class GameMapData {
    private static final Logger logger = LoggerFactory.getLogger(GameMapData.class);
    public static final float DEFAULT_TILE_SIZE = 32.0f;

    public final int widthTiles;
    public final int heightTiles;
    public final float tileSize;

    private final Random random = new Random();
    protected final TerrainTile[][] terrainGrid;

    public GameMapData(int widthTiles, int heightTiles) { 
        this(widthTiles, heightTiles, DEFAULT_TILE_SIZE); 
    }

    public GameMapData(int widthTiles, int heightTiles, float tileSize) {
        this.widthTiles = widthTiles;
        this.heightTiles = heightTiles;
        this.tileSize = tileSize;
        if (tileSize <= 0 || widthTiles <= 0 || heightTiles <= 0) {
            throw new IllegalArgumentException("Map dimensions and tile size must be positive.");
        }
        this.terrainGrid = new TerrainTile[widthTiles][heightTiles];
        initializeTerrainGrid();
    }

    protected void initializeTerrainGrid() {
        for (int y = 0; y < heightTiles; y++) {
            for (int x = 0; x < widthTiles; x++) {
                terrainGrid[x][y] = new TerrainTile(TerrainType.GRASS);
            }
        }
    }

    public void checkAndCorrectBoundaries(Entity entity) {
        float mapWidth = widthTiles * tileSize;
        float mapHeight = heightTiles * tileSize;

        // Get half the entity's size (assuming width and height are the same for tanks)
        // Or use the collision radius directly if that's more appropriate
        float radius = entity.getBoundingRadius();

        // Calculate the allowed boundaries for the CENTER of the entity
        float minAllowedX = radius;
        float maxAllowedX = mapWidth - radius;
        float minAllowedY = radius;
        float maxAllowedY = mapHeight - radius;

        boolean wasOutOfBounds = false;
        var tankPosition = entity.getPosition();
        float currentX = tankPosition.x();
        float currentY = tankPosition.y();

        // Create a corrected position starting with the current position
        float correctedX = currentX;
        float correctedY = currentY;

        // Check X boundaries against the allowed center range
        if (currentX < minAllowedX) {
            correctedX = minAllowedX; // Clamp center to the minimum allowed position
            wasOutOfBounds = true;
        } else if (currentX > maxAllowedX) {
            correctedX = maxAllowedX; // Clamp center to the maximum allowed position
            wasOutOfBounds = true;
        }

        // Check Y boundaries against the allowed center range
        if (currentY < minAllowedY) {
            correctedY = minAllowedY;
            wasOutOfBounds = true;
        } else if (currentY > maxAllowedY) {
            correctedY = maxAllowedY;
            wasOutOfBounds = true;
        }

        // If a player was out of bounds, log it and set to corrected position
        if (wasOutOfBounds) {
            // Log the original position that triggered the correction
            logger.debug("PlayerId: {} was out of bounds at x: {}, y: {}. Resetting to valid position at x: {}, y: {}",
                    entity.getPlayerId(), currentX, currentY, correctedX, correctedY);

            entity.setPosition(new Vector2f(correctedX, correctedY));
        }
    }

    public boolean isOutOfBounds(Entity entity) {
        float mapWidth = widthTiles * tileSize;
        float mapHeight = heightTiles * tileSize;

        float radius = entity.getBoundingRadius();

        return entity.getX() - radius < 0 ||
                entity.getX() + radius > mapWidth ||
                entity.getY() - radius < 0 ||
                entity.getY() + radius > mapHeight;
    }

    public Vector2f getRandomSpawnPoint() {
        float worldWidth = widthTiles * tileSize;
        float worldHeight = heightTiles * tileSize;
        float margin = tileSize * 2;
        float effectiveWidth = Math.max(0, worldWidth - 2 * margin);
        float effectiveHeight = Math.max(0, worldHeight - 2 * margin);

        int maxAttempts = 100;
        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            float spawnX = margin + random.nextFloat() * effectiveWidth;
            float spawnY = margin + random.nextFloat() * effectiveHeight;
            
            TerrainTile tile = getTileAt(spawnX, spawnY);
            if (tile != null && !tile.hasOverlay()) {
                return new Vector2f(spawnX, spawnY);
            }
        }
        
        logger.warn("Could not find clear spawn point after {} attempts, using fallback position", maxAttempts);
        return new Vector2f(margin + effectiveWidth / 2, margin + effectiveHeight / 2);
    }

    public int getWidthTiles() { return widthTiles; }
    public int getHeightTiles() { return heightTiles; }
    public float getTileSize() { return tileSize; }
    public float getWorldWidth() { return widthTiles * tileSize; }
    public float getWorldHeight() { return heightTiles * tileSize; }

    public boolean isValidTile(int x, int y) {
        return x >= 0 && x < widthTiles && y >= 0 && y < heightTiles;
    }

    public TerrainTile getTile(int x, int y) {
        if (!isValidTile(x, y)) {
            return null;
        }
        return terrainGrid[x][y];
    }

    public TerrainTile getTileAt(float worldX, float worldY) {
        int tileX = (int) (worldX / tileSize);
        int tileY = (int) (worldY / tileSize);
        return getTile(tileX, tileY);
    }

    public float getSpeedModifierAt(float worldX, float worldY) {
        TerrainTile tile = getTileAt(worldX, worldY);
        if (tile == null) {
            return 1.0f;
        }
        return tile.getEffectiveSpeedModifier();
    }

    public boolean isPassableAt(float worldX, float worldY) {
        TerrainTile tile = getTileAt(worldX, worldY);
        if (tile == null) {
            return false;
        }
        return tile.isPassable();
    }

    public boolean blocksBulletsAt(float worldX, float worldY) {
        TerrainTile tile = getTileAt(worldX, worldY);
        if (tile == null) {
            return false;
        }
        return tile.blocksBullets();
    }
}