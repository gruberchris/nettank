package org.chrisgruber.nettank.client.game.world;

import org.chrisgruber.nettank.client.engine.graphics.Camera;
import org.chrisgruber.nettank.client.engine.graphics.Renderer;
import org.chrisgruber.nettank.client.engine.graphics.Shader;
import org.chrisgruber.nettank.client.engine.graphics.Texture;
import org.chrisgruber.nettank.common.world.GameMapData;
import org.joml.Vector2f;

import java.util.Random;

public class ClientGameMap {

    public enum TileType { GRASS, DIRT, WALL }
    private TileType[][] tiles;
    private int width;
    private int height;
    private final GameMapData mapData;
    private float tileSize;

    private static final float FOG_DARKNESS = 0.15f; // How dark unseen tiles are (0=black, 1=normal)

    public ClientGameMap(int width, int height, float tileSize) {
        this.width = width;
        this.height = height;
        this.tileSize = tileSize;
        this.mapData = new GameMapData(width, height, tileSize);
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
                       Camera camera, float viewRange) {

        shader.bind();
        float renderRangeSq = viewRange * viewRange;
        boolean isSpectating = (viewRange == Float.MAX_VALUE);

        // Get view bounds from camera
        float viewLeft = camera.getViewLeft();
        float viewRight = camera.getViewRight();
        float viewBottom = camera.getViewBottom();
        float viewTop = camera.getViewTop();
        Vector2f playerPos = camera.getPosition(); // Use camera position as player pos for culling/fog

        // Calculate tile indices to render
        int startX = Math.max(0, (int) Math.floor(viewLeft / tileSize) - 1);
        int endX = Math.min(mapData.widthTiles, (int) Math.ceil(viewRight / tileSize) + 1);
        int startY = Math.max(0, (int) Math.floor(viewBottom / tileSize) - 1);
        int endY = Math.min(mapData.heightTiles, (int) Math.ceil(viewTop / tileSize) + 1);

        for (int y = startY; y < endY; y++) {
            for (int x = startX; x < endX; x++) {
                float tileCenterX = (x + 0.5f) * tileSize;
                float tileCenterY = (y + 0.5f) * tileSize;

                float tint = 1.0f;
                if (!isSpectating && playerPos != null) {
                    float distSq = playerPos.distanceSquared(tileCenterX, tileCenterY);
                    if (distSq > renderRangeSq) {
                        tint = FOG_DARKNESS;
                    }
                    // Optional fade logic could go here
                }

                if(tint > FOG_DARKNESS - 0.01f) { // Render slightly into the fog edge
                    TileType type = tiles[x][y];
                    Texture texture = (type == TileType.GRASS) ? grassTexture : dirtTexture;
                    if (texture == null) continue; // Skip if texture missing

                    texture.bind();
                    shader.setUniform3f("u_tintColor", tint, tint, tint);
                    renderer.drawQuad(tileCenterX, tileCenterY, tileSize, tileSize, 0, shader);
                }
            }
        }
        shader.setUniform3f("u_tintColor", 1.0f, 1.0f, 1.0f); // Reset tint
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
        float margin = GameMapData.DEFAULT_TILE_SIZE * 2; // Don't spawn too close to edges
        float spawnX = margin + random.nextFloat() * (width * GameMapData.DEFAULT_TILE_SIZE - 2 * margin);
        float spawnY = margin + random.nextFloat() * (height * GameMapData.DEFAULT_TILE_SIZE - 2 * margin);
        return new Vector2f(spawnX, spawnY);
    }

    // Basic check if a position is outside map bounds
    public boolean isOutOfBounds(float x, float y, float objectRadius) {
        return x - objectRadius < 0 || x + objectRadius > width * tileSize ||
                y - objectRadius < 0 || y + objectRadius > height * tileSize;
    }
}