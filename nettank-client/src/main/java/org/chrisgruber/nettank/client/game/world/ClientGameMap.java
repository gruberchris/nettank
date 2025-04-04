package org.chrisgruber.nettank.client.game.world;

import org.chrisgruber.nettank.client.engine.graphics.Camera;
import org.chrisgruber.nettank.client.engine.graphics.Renderer;
import org.chrisgruber.nettank.client.engine.graphics.Shader;
import org.chrisgruber.nettank.client.engine.graphics.Texture;
import org.chrisgruber.nettank.common.world.GameMapData;
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
    private static final Random random = new Random();

    public ClientGameMap(int width, int height) {
        logger.debug("Creating ClientGameMap ({}x{})", width, height);
        this.mapData = new GameMapData(width, height);
        this.tiles = new TileType[width][height];
        generateSimpleMap();
    }

    private void generateSimpleMap() {
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

    // TODO: Delegate boundary check to common data object
    public boolean isOutOfBounds(float x, float y, float objectRadius) {
        return mapData.isOutOfBounds(x, y, objectRadius);
    }

    public float getWorldWidth() { return mapData.getWorldWidth(); }
    public float getWorldHeight() { return mapData.getWorldHeight(); }
}