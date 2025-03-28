package org.chrisgruber.nettank.client.game.world;

import org.chrisgruber.nettank.client.engine.graphics.Camera;
import org.chrisgruber.nettank.client.engine.graphics.Renderer;
import org.chrisgruber.nettank.client.engine.graphics.Shader;
import org.chrisgruber.nettank.client.engine.graphics.Texture;
import org.chrisgruber.nettank.common.world.GameMapData; // Use common data
import org.joml.Vector2f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Random;

public class ClientGameMap {
    private static final Logger logger = LoggerFactory.getLogger(ClientGameMap.class);

    public enum TileType { GRASS, DIRT, WALL }

    private final GameMapData mapData; // Holds common dimensions/utils
    private final TileType[][] tiles; // Client-specific tile grid for rendering

    private static final float FOG_DARKNESS = 0.15f;

    // Constructor uses GameMapData constants implicitly
    public ClientGameMap(int width, int height) {
        logger.debug("Creating ClientGameMap ({}x{})", width, height);
        // Create GameMapData using its constructor which uses the DEFAULT_TILE_SIZE constant
        this.mapData = new GameMapData(width, height);
        this.tiles = new TileType[width][height];
        generateSimpleMap();
    }

    private void generateSimpleMap() {
        Random random = new Random();
        logger.debug("Generating simple map pattern...");
        for (int y = 0; y < mapData.getHeightTiles(); y++) {
            for (int x = 0; x < mapData.getWidthTiles(); x++) {
                tiles[x][y] = random.nextFloat() > 0.3f ? TileType.GRASS : TileType.DIRT;
            }
        }
    }

    public void render(Renderer renderer, Shader shader, Texture grassTexture, Texture dirtTexture,
                       Camera camera, float viewRange) {

        shader.bind();
        final float tileSize = GameMapData.DEFAULT_TILE_SIZE; // Use constant from common
        float renderRangeSq = viewRange * viewRange;
        boolean isSpectating = (viewRange == Float.MAX_VALUE);

        float viewLeft = camera.getViewLeft();
        float viewRight = camera.getViewRight();
        float viewBottom = camera.getViewBottom();
        float viewTop = camera.getViewTop();
        Vector2f playerPos = camera.getPosition();

        int startX = Math.max(0, (int) Math.floor(viewLeft / tileSize) - 1);
        int endX = Math.min(mapData.widthTiles, (int) Math.ceil(viewRight / tileSize) + 1);
        int startY = Math.max(0, (int) Math.floor(viewBottom / tileSize) - 1);
        int endY = Math.min(mapData.heightTiles, (int) Math.ceil(viewTop / tileSize) + 1);

        for (int y = startY; y < endY; y++) {
            for (int x = startX; x < endX; x++) {
                if (x < 0 || x >= mapData.widthTiles || y < 0 || y >= mapData.heightTiles) continue;

                float tileCenterX = (x + 0.5f) * tileSize;
                float tileCenterY = (y + 0.5f) * tileSize;
                float tint = 1.0f;

                if (!isSpectating && playerPos != null) {
                    float distSq = playerPos.distanceSquared(tileCenterX, tileCenterY);
                    if (distSq > renderRangeSq) { tint = FOG_DARKNESS; }
                }

                if (tint > FOG_DARKNESS - 0.01f) {
                    TileType type = tiles[x][y];
                    Texture texture = (type == TileType.GRASS) ? grassTexture : dirtTexture;
                    if (texture == null) continue;

                    texture.bind();
                    shader.setUniform3f("u_tintColor", tint, tint, tint);
                    renderer.drawQuad(tileCenterX, tileCenterY, tileSize, tileSize, 0, shader);
                }
            }
        }
        shader.setUniform3f("u_tintColor", 1.0f, 1.0f, 1.0f); // Reset tint
    }

    // Delegate boundary check to common data object
    public boolean isOutOfBounds(float x, float y, float objectRadius) {
        return mapData.isOutOfBounds(x, y, objectRadius);
    }

    // --- Getters for dimensions ---
    public int getWidthTiles() { return mapData.getWidthTiles(); }
    public int getHeightTiles() { return mapData.getHeightTiles(); }
    public float getTileSize() { return GameMapData.DEFAULT_TILE_SIZE; } // Return the constant
    public float getWorldWidth() { return mapData.getWorldWidth(); }
    public float getWorldHeight() { return mapData.getWorldHeight(); }

    // --- Example method - Note: Spawn logic is primarily server-side ---
    // public Vector2f getRandomSpawnPoint() {
    //    return mapData.getRandomSpawnPoint(); // Delegate if needed client-side
    // }

    public TileType getTileAt(int x, int y) {
        if (x < 0 || x >= mapData.widthTiles || y < 0 || y >= mapData.heightTiles) {
            return null;
        }
        return tiles[x][y];
    }

    public Vector2f getRandomSpawnPoint() {
        // This logic should primarily be server-side, but if needed client-side:
        Random random = new Random();
        // Use the constant from GameMapData
        float margin = GameMapData.DEFAULT_TILE_SIZE * 2;
        float spawnX = margin + random.nextFloat() * (mapData.getWorldWidth() - 2 * margin);
        float spawnY = margin + random.nextFloat() * (mapData.getWorldHeight() - 2 * margin);
        return new Vector2f(spawnX, spawnY);
    }
}