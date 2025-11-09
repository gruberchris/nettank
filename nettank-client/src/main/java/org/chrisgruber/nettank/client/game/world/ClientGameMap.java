package org.chrisgruber.nettank.client.game.world;

import org.chrisgruber.nettank.client.engine.graphics.Camera;
import org.chrisgruber.nettank.client.engine.graphics.Renderer;
import org.chrisgruber.nettank.client.engine.graphics.Shader;
import org.chrisgruber.nettank.client.engine.graphics.Texture;
import org.chrisgruber.nettank.client.game.entities.ClientEntity;
import org.chrisgruber.nettank.common.world.GameMapData;
import org.chrisgruber.nettank.common.world.TerrainTile;
import org.chrisgruber.nettank.common.world.TerrainType;
import org.chrisgruber.nettank.common.world.TerrainState;
import org.joml.Vector2f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class ClientGameMap {
    private static final Logger logger = LoggerFactory.getLogger(ClientGameMap.class);

    private final GameMapData mapData;

    private static final float FOG_DARKNESS = 0.15f;
    private static final float FOG_FADE_DISTANCE = 3.5f;
    private static final Random random = new Random();

    private final Map<TerrainType, Texture> terrainTextures = new HashMap<>();
    private final Map<TerrainState, Texture> stateOverlayTextures = new HashMap<>();
    private final Map<String, Texture> visualOverlayTextures = new HashMap<>();

    public ClientGameMap(int width, int height) {
        logger.debug("Creating ClientGameMap ({}x{})", width, height);
        this.mapData = new GameMapData(width, height);

        // IMPORTANT: Must use same seed as server! For now, using hardcoded seed.
        // TODO: Server should send seed to client via network
        generateProceduralTerrain(org.chrisgruber.nettank.common.world.BaseTerrainProfile.GRASSLAND);
    }

    private void generateProceduralTerrain(org.chrisgruber.nettank.common.world.BaseTerrainProfile profile) {
        // TEMPORARY: Using fixed seed for now so client/server match
        // TODO: Server should send seed to client
        long fixedSeed = 12345L; // Must match server seed!
        ProceduralTerrainGenerator procGen = new ProceduralTerrainGenerator(fixedSeed);
        procGen.generateProceduralTerrain(mapData, profile);
    }

    private void generateAllDesertMap() {
        logger.debug("Generating all-desert map...");
        
        for (int y = 0; y < mapData.getHeightTiles(); y++) {
            for (int x = 0; x < mapData.getWidthTiles(); x++) {
                TerrainTile tile = mapData.getTile(x, y);
                tile.setBaseType(TerrainType.SAND);
            }
        }
        
        logger.info("Client terrain generated: All desert (100% sand)");
    }

    private void generateGrassDirtMudMap() {
        logger.debug("Generating Grass/Dirt/Mud thirds layout...");
        
        int width = mapData.getWidthTiles();
        int height = mapData.getHeightTiles();
        int thirdWidth = width / 3;
        
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                TerrainTile tile = mapData.getTile(x, y);
                
                if (x < thirdWidth) {
                    // Left third: Grass
                    tile.setBaseType(TerrainType.GRASS);
                } else if (x < thirdWidth * 2) {
                    // Middle third: Mud
                    tile.setBaseType(TerrainType.MUD);
                } else {
                    // Right third: Dirt
                    tile.setBaseType(TerrainType.DIRT);
                }
            }
        }
        
        logger.info("Client terrain generated: Grass (left 1/3) | Mud (center 1/3) | Dirt (right 1/3)");
    }

    public void registerTerrainTexture(TerrainType type, Texture texture) {
        terrainTextures.put(type, texture);
    }

    public void registerStateOverlayTexture(TerrainState state, Texture texture) {
        stateOverlayTextures.put(state, texture);
    }

    public void registerVisualOverlayTexture(String name, Texture texture) {
        visualOverlayTextures.put(name, texture);
    }

    public void onTerrainStateChanged(int x, int y, TerrainState newState) {
        if (!mapData.isValidTile(x, y)) return;

        TerrainTile tile = mapData.getTile(x, y);
        tile.setCurrentState(newState);
        tile.setStateChangeTime(System.currentTimeMillis());
        
        logger.debug("Terrain state changed at ({}, {}) to {}", x, y, newState);
    }
    
    // Smoothstep function for smoother interpolation (eases in and out)
    private float smoothstep(float t) {
        t = Math.max(0.0f, Math.min(1.0f, t)); // Clamp to [0, 1]
        return t * t * (3.0f - 2.0f * t);
    }

    public void render(Renderer renderer, Shader shader, Texture grassTexture, Texture dirtTexture,
                       Camera camera, float viewRange, Vector2f fogCenter) {

        shader.bind();

        final float tileSize = GameMapData.DEFAULT_TILE_SIZE;
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
                    float tintSum = 0.0f;
                    int sampleCount = 0;
                    
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
                                fadeProgress = smoothstep(fadeProgress);
                                sampleTint = 1.0f - (fadeProgress * (1.0f - FOG_DARKNESS));
                            }
                            
                            tintSum += sampleTint;
                            sampleCount++;
                        }
                    }
                    
                    tint = tintSum / sampleCount;
                }

                if (tint > FOG_DARKNESS - 0.01f) {
                    TerrainTile tile = mapData.getTile(x, y);
                    
                    // Draw base terrain first
                    TerrainType baseType = tile.getBaseType();
                    Texture baseTexture = terrainTextures.get(baseType);
                    if (baseTexture == null) {
                        baseTexture = (baseType == TerrainType.GRASS) ? grassTexture : dirtTexture;
                    }
                    
                    if (baseTexture != null) {
                        baseTexture.bind();
                        shader.setUniform3f("u_tintColor", tint, tint, tint);
                        renderer.drawQuad(tileCenterX, tileCenterY, tileSize, tileSize, 0, shader);
                    }
                    
                    // Draw overlay terrain on top (if exists) - affects gameplay
                    if (tile.hasOverlay()) {
                        TerrainType overlayType = tile.getOverlayType();
                        Texture overlayTexture = terrainTextures.get(overlayType);
                        
                        if (overlayTexture != null) {
                            overlayTexture.bind();
                            shader.setUniform3f("u_tintColor", tint, tint, tint);
                            renderer.drawQuad(tileCenterX, tileCenterY, tileSize, tileSize, 0, shader);
                        }
                    }
                    
                    // Draw visual overlay (tank tracks, roads, etc.) - purely cosmetic, no collision
                    if (tile.hasVisualOverlay()) {
                        String visualOverlayName = tile.getVisualOverlay();
                        Texture visualTexture = visualOverlayTextures.get(visualOverlayName);
                        
                        if (visualTexture != null) {
                            visualTexture.bind();
                            shader.setUniform3f("u_tintColor", tint, tint, tint);
                            renderer.drawQuad(tileCenterX, tileCenterY, tileSize, tileSize, 0, shader);
                        }
                    }
                    
                    // Draw state overlay (scorched, etc.) on top of everything
                    if (tile.getCurrentState() == TerrainState.SCORCHED) {
                        Texture scorchedTexture = stateOverlayTextures.get(TerrainState.SCORCHED);
                        if (scorchedTexture != null) {
                            scorchedTexture.bind();
                            shader.setUniform3f("u_tintColor", tint * 0.7f, tint * 0.7f, tint * 0.7f);
                            renderer.drawQuad(tileCenterX, tileCenterY, tileSize, tileSize, 0, shader);
                        }
                    }
                }
            }
        }

        shader.setUniform3f("u_tintColor", 1.0f, 1.0f, 1.0f);
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

    public void setVisualOverlay(int x, int y, String visualOverlayName) {
        if (!mapData.isValidTile(x, y)) return;
        TerrainTile tile = mapData.getTile(x, y);
        tile.setVisualOverlay(visualOverlayName);
    }

    public void clearVisualOverlay(int x, int y) {
        if (!mapData.isValidTile(x, y)) return;
        TerrainTile tile = mapData.getTile(x, y);
        tile.setVisualOverlay(null);
    }

    public int getWidthTiles() { return mapData.widthTiles; }
    public int getHeightTiles() { return mapData.heightTiles;
   }

    public float getWorldWidth() { return mapData.getWorldWidth(); }
    public float getWorldHeight() { return mapData.getWorldHeight(); }
    
    public boolean blocksBulletsAt(float worldX, float worldY) {
        return mapData.blocksBulletsAt(worldX, worldY);
    }
}