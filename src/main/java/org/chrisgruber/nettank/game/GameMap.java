package org.chrisgruber.nettank.game;

import org.chrisgruber.nettank.main.Game;
import org.chrisgruber.nettank.rendering.Renderer;
import org.chrisgruber.nettank.rendering.Shader;
import org.chrisgruber.nettank.rendering.Texture;
import org.joml.Vector2f;

import java.util.Random;

public class GameMap {

    private TileType[][] tiles;
    private int width;
    private int height;

    private static final float FOG_DARKNESS = 0.15f; // How dark unseen tiles are (0=black, 1=normal)

    public GameMap(int width, int height) {
        this.width = width;
        this.height = height;
        this.tiles = new TileType[width][height];
        generateSimpleMap();
    }

    private void generateSimpleMap() {
        Random random = new Random();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                // Simple random pattern
                tiles[x][y] = random.nextFloat() > 0.3f ? TileType.GRASS : TileType.DIRT;
            }
        }
    }

    public void render(Renderer renderer, Shader shader, Texture grassTexture, Texture dirtTexture,
                       float viewLeft, float viewRight, float viewBottom, float viewTop,
                       Vector2f playerPos, float viewRange) {

        shader.bind();
        float tileSize = Game.TILE_SIZE;
        float renderRangeSq = viewRange * viewRange;
        boolean isSpectating = (viewRange == Float.MAX_VALUE);

        // Calculate tile indices within the view + buffer for view range check
        int startX = Math.max(0, (int) (viewLeft / tileSize) - 1);
        int endX = Math.min(width, (int) (viewRight / tileSize) + 2);
        int startY = Math.max(0, (int) (viewBottom / tileSize) - 1);
        int endY = Math.min(height, (int) (viewTop / tileSize) + 2);


        for (int y = startY; y < endY; y++) {
            for (int x = startX; x < endX; x++) {
                float tileCenterX = (x + 0.5f) * tileSize;
                float tileCenterY = (y + 0.5f) * tileSize;

                float tint = 1.0f; // Default: fully visible
                if (!isSpectating && playerPos != null) {
                    float distSq = playerPos.distanceSquared(tileCenterX, tileCenterY);
                    if (distSq > renderRangeSq) {
                        tint = FOG_DARKNESS; // Apply fog effect
                    }
                    // Optional: Add a fade-out effect near the edge
                    // float fadeStartRangeSq = (viewRange * 0.8f) * (viewRange * 0.8f);
                    // if (distSq > fadeStartRangeSq && distSq <= renderRangeSq) {
                    //     tint = FOG_DARKNESS + (1.0f - FOG_DARKNESS) * (1.0f - (distSq - fadeStartRangeSq) / (renderRangeSq - fadeStartRangeSq));
                    // } else if (distSq > renderRangeSq) {
                    //     tint = FOG_DARKNESS;
                    // }
                }

                if(tint > 0.01f) { // Don't bother drawing fully black tiles
                    TileType type = tiles[x][y];
                    Texture texture = (type == TileType.GRASS) ? grassTexture : dirtTexture;

                    texture.bind();
                    shader.setUniform3f("u_tintColor", tint, tint, tint); // Apply fog tint
                    renderer.drawQuad(tileCenterX, tileCenterY, tileSize, tileSize, 0, shader);
                }
            }
        }
        // Reset tint after drawing map
        shader.setUniform3f("u_tintColor", 1.0f, 1.0f, 1.0f);
    }

    public TileType getTileAt(int x, int y) {
        if (x < 0 || x >= width || y < 0 || y >= height) {
            return null; // Or a specific 'boundary' tile type
        }
        return tiles[x][y];
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public Vector2f getRandomSpawnPoint() {
        Random random = new Random();
        float margin = Game.TILE_SIZE * 2; // Don't spawn too close to edges
        float spawnX = margin + random.nextFloat() * (width * Game.TILE_SIZE - 2 * margin);
        float spawnY = margin + random.nextFloat() * (height * Game.TILE_SIZE - 2 * margin);
        return new Vector2f(spawnX, spawnY);
    }

    // Basic check if a position is outside map bounds
    public boolean isOutOfBounds(float x, float y, float objectRadius) {
        float tileSize = Game.TILE_SIZE;
        return x - objectRadius < 0 || x + objectRadius > width * tileSize ||
                y - objectRadius < 0 || y + objectRadius > height * tileSize;
    }
}