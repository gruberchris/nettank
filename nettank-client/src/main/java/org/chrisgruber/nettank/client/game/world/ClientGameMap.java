package org.chrisgruber.nettank.client.game.world;

import org.chrisgruber.nettank.client.engine.graphics.Camera;
import org.chrisgruber.nettank.client.engine.graphics.Renderer;
import org.chrisgruber.nettank.client.engine.graphics.Shader;
import org.chrisgruber.nettank.client.engine.graphics.Texture;
import org.chrisgruber.nettank.client.game.entities.ClientEntity;
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
    private static final float FOG_FADE_DISTANCE = 3.5f; // Tiles for smooth transition (increased for smoother blur). Try 4.0-5.0 for even softer
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
    
    // Smoothstep function for smoother interpolation (eases in and out)
    private float smoothstep(float t) {
        t = Math.max(0.0f, Math.min(1.0f, t)); // Clamp to [0, 1]
        return t * t * (3.0f - 2.0f * t);
    }

    public void render(Renderer renderer, Shader shader, Texture grassTexture, Texture dirtTexture,
                       Camera camera, float viewRange, Vector2f fogCenter) {

        shader.bind();

        final float tileSize = GameMapData.DEFAULT_TILE_SIZE; // Use constant from common
        float renderRangeSq = viewRange * viewRange;
        boolean isSpectating = (viewRange == Float.MAX_VALUE);

        float viewLeft = camera.getViewLeft();
        float viewRight = camera.getViewRight();
        float viewBottom = camera.getViewBottom();
        float viewTop = camera.getViewTop();

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

                if (!isSpectating && fogCenter != null) {
                    // Sample multiple points around tile center for smooth blur effect
                    float tintSum = 0.0f;
                    int sampleCount = 0;
                    
                    // 3x3 sampling grid for blur effect
                    for (float dy = -0.33f; dy <= 0.33f; dy += 0.33f) {
                        for (float dx = -0.33f; dx <= 0.33f; dx += 0.33f) {
                            float sampleX = tileCenterX + (dx * tileSize);
                            float sampleY = tileCenterY + (dy * tileSize);
                            float dist = fogCenter.distance(sampleX, sampleY);
                            float fadeStart = viewRange - (FOG_FADE_DISTANCE * tileSize);
                            
                            float sampleTint = 1.0f;
                            if (dist > viewRange) {
                                sampleTint = FOG_DARKNESS;
                            } else if (dist > fadeStart) {
                                float fadeProgress = (dist - fadeStart) / (FOG_FADE_DISTANCE * tileSize);
                                fadeProgress = smoothstep(fadeProgress); // Apply smoothstep for even softer transition
                                sampleTint = 1.0f - (fadeProgress * (1.0f - FOG_DARKNESS));
                            }
                            
                            tintSum += sampleTint;
                            sampleCount++;
                        }
                    }
                    
                    tint = tintSum / sampleCount; // Average the samples for blur effect
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

   public boolean isOutOfBounds(ClientEntity clientEntity) {
        // TODO: this logic is duplicated from GameMapData.isOutOfBounds()

        float mapWidth = mapData.getWidthTiles() * mapData.getTileSize();
        float mapHeight = mapData.getHeightTiles() * mapData.getTileSize();

        float radius = clientEntity.getSize() / 2.0f;

        return clientEntity.getPosition().x() - radius < 0 ||
                clientEntity.getPosition().x()  + radius > mapWidth ||
                clientEntity.getPosition().y() - radius < 0 ||
                clientEntity.getPosition().y() + radius > mapHeight;
    }

    public int getWidthTiles() { return mapData.widthTiles; }
    public int getHeightTiles() { return mapData.heightTiles;
   }

    public float getWorldWidth() { return mapData.getWorldWidth(); }
    public float getWorldHeight() { return mapData.getWorldHeight(); }
}