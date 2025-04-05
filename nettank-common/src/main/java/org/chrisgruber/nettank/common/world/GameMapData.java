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

    public GameMapData(int widthTiles, int heightTiles) { this(widthTiles, heightTiles, DEFAULT_TILE_SIZE); }

    public GameMapData(int widthTiles, int heightTiles, float tileSize) {
        this.widthTiles = widthTiles;
        this.heightTiles = heightTiles;
        this.tileSize = tileSize;
        if (tileSize <= 0 || widthTiles <= 0 || heightTiles <= 0) {
            throw new IllegalArgumentException("Map dimensions and tile size must be positive.");
        }
    }

    public void checkAndCorrectBoundaries(Entity entity) {
        float mapWidth = widthTiles * tileSize;
        float mapHeight = heightTiles * tileSize;

        boolean wasOutOfBounds = false;

        var tankPosition = entity.getPosition();

        // Create a corrected position starting with current position
        float correctedX = tankPosition.x();
        float correctedY = tankPosition.y();

        // Check X boundaries
        if (tankPosition.x() < 0) {
            correctedX = 0;
            wasOutOfBounds = true;
        } else if (tankPosition.x() > mapWidth) {
            correctedX = mapWidth;
            wasOutOfBounds = true;
        }

        // Check Y boundaries
        if (tankPosition.y() < 0) {
            correctedY = 0;
            wasOutOfBounds = true;
        } else if (tankPosition.y() > mapHeight) {
            correctedY = mapHeight;
            wasOutOfBounds = true;
        }

        // If player was out of bounds, log it and set to corrected position
        if (wasOutOfBounds) {
            logger.debug("PlayerId: {} is out of bounds at x: {}, y: {}. Resetting to valid position at x: {}, y: {}",
                    entity.getPlayerId(), tankPosition.x(), tankPosition.y(), correctedX, correctedY);

            entity.setPosition(correctedX, correctedY);
        }
    }

    public boolean isOutOfBounds(Entity entity) {
        float mapWidth = widthTiles * tileSize;
        float mapHeight = heightTiles * tileSize;

        float radius = entity.getCollisionRadius();

        return entity.getX() - radius < 0 ||
                entity.getX() + radius > mapWidth ||
                entity.getY() - radius < 0 ||
                entity.getY() + radius > mapHeight;
    }

    public Vector2f getRandomSpawnPoint() {
        float worldWidth = widthTiles * tileSize;
        float worldHeight = heightTiles * tileSize;
        float margin = tileSize * 2;
        // Ensure margin is not too large for map size
        float effectiveWidth = Math.max(0, worldWidth - 2 * margin);
        float effectiveHeight = Math.max(0, worldHeight - 2 * margin);

        float spawnX = margin + random.nextFloat() * effectiveWidth;
        float spawnY = margin + random.nextFloat() * effectiveHeight;
        return new Vector2f(spawnX, spawnY);
    }

    public int getWidthTiles() { return widthTiles; }
    public int getHeightTiles() { return heightTiles; }
    public float getTileSize() { return tileSize; }
    public float getWorldWidth() { return widthTiles * tileSize; }
    public float getWorldHeight() { return heightTiles * tileSize; }
}