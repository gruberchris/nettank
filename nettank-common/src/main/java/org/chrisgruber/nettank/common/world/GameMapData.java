package org.chrisgruber.nettank.common.world;

import org.joml.Vector2f;
import java.util.Random;

public class GameMapData {
    public final int widthTiles;
    public final int heightTiles;
    public final float tileSize;

    private final Random random = new Random();
    public static final float DEFAULT_TILE_SIZE = 32.0f;

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
    }

    public boolean isOutOfBounds(float x, float y, float radius) {
        float worldWidth = widthTiles * tileSize;
        float worldHeight = heightTiles * tileSize;
        return x - radius < 0 || x + radius > worldWidth ||
                y - radius < 0 || y + radius > worldHeight;
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