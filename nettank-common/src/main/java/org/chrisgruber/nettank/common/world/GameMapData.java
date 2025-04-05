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

        // Get half the entity's size (assuming width and height are the same for tanks)
        // Or use the collision radius directly if that's more appropriate
        float radius = entity.getCollisionRadius(); // Uses getSize() / 2.0f

        // Calculate the allowed boundaries for the CENTER of the entity
        float minAllowedX = radius;
        float maxAllowedX = mapWidth - radius;
        float minAllowedY = radius;
        float maxAllowedY = mapHeight - radius;

        boolean wasOutOfBounds = false;
        var tankPosition = entity.getPosition();
        float currentX = tankPosition.x();
        float currentY = tankPosition.y();

        // Create a corrected position starting with current position
        float correctedX = currentX;
        float correctedY = currentY;

        // Check X boundaries against allowed center range
        if (currentX < minAllowedX) {
            correctedX = minAllowedX; // Clamp center to the minimum allowed position
            wasOutOfBounds = true;
        } else if (currentX > maxAllowedX) {
            correctedX = maxAllowedX; // Clamp center to the maximum allowed position
            wasOutOfBounds = true;
        }

        // Check Y boundaries against allowed center range
        if (currentY < minAllowedY) {
            correctedY = minAllowedY;
            wasOutOfBounds = true;
        } else if (currentY > maxAllowedY) {
            correctedY = maxAllowedY;
            wasOutOfBounds = true;
        }

        // If player was out of bounds, log it and set to corrected position
        if (wasOutOfBounds) {
            // Log the original position that triggered the correction
            logger.debug("PlayerId: {} was out of bounds at x: {}, y: {}. Resetting to valid position at x: {}, y: {}",
                    entity.getPlayerId(), currentX, currentY, correctedX, correctedY);

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