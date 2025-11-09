package org.chrisgruber.nettank.client.game.world;

import org.chrisgruber.nettank.common.world.BaseTerrainProfile;
import org.chrisgruber.nettank.common.world.GameMapData;
import org.chrisgruber.nettank.common.world.TerrainTile;
import org.chrisgruber.nettank.common.world.TerrainType;
import org.chrisgruber.nettank.common.world.noise.FastNoiseLite;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class ProceduralTerrainGenerator {
    private static final Logger logger = LoggerFactory.getLogger(ProceduralTerrainGenerator.class);
    
    private final long seed;
    private final FastNoiseLite noise;
    
    public ProceduralTerrainGenerator(long seed) {
        this.seed = seed;
        this.noise = new FastNoiseLite((int) seed);
        
        // Configure noise for natural terrain
        noise.SetNoiseType(FastNoiseLite.NoiseType.Perlin);
        noise.SetFrequency(0.05f);
        noise.SetFractalOctaves(3);
        noise.SetFractalType(FastNoiseLite.FractalType.FBm);
    }
    
    public void generateProceduralTerrain(GameMapData mapData, BaseTerrainProfile profile) {
        logger.info("Client: Generating procedural {} terrain (seed: {}) for {}x{} map",
                profile.getName(), seed, mapData.getWidthTiles(), mapData.getHeightTiles());
        
        int width = mapData.getWidthTiles();
        int height = mapData.getHeightTiles();
        
        // Step 1: Fill the entire map with base terrain
        fillAllWithBaseTerrain(mapData, profile.getBaseType());
        
        // Step 2: Generate noise-based overlays (will have scattered regions)
        float[][] noiseMap = generateNoiseMap(width, height);
        assignOverlaysFromNoise(mapData, noiseMap, profile);
        
        // Step 3: Post-process to keep only the largest contiguous regions
        keepLargestContiguousOverlayRegion(mapData, profile.getLowType());
        keepLargestContiguousOverlayRegion(mapData, profile.getHighType());
        
        logger.info("Client: Procedural terrain generation complete");
    }
    
    private void fillAllWithBaseTerrain(GameMapData mapData, TerrainType baseType) {
        for (int y = 0; y < mapData.getHeightTiles(); y++) {
            for (int x = 0; x < mapData.getWidthTiles(); x++) {
                mapData.getTile(x, y).setBaseType(baseType);
            }
        }
    }
    
    private float[][] generateNoiseMap(int width, int height) {
        float[][] noiseMap = new float[width][height];
        
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                float value = noise.GetNoise(x, y);
                noiseMap[x][y] = (value + 1.0f) / 2.0f;
            }
        }
        
        return noiseMap;
    }
    
    private void assignOverlaysFromNoise(GameMapData mapData, float[][] noiseMap, BaseTerrainProfile profile) {
        int width = mapData.getWidthTiles();
        int height = mapData.getHeightTiles();
        
        // Calculate thresholds based on actual noise distribution (percentiles)
        // Collect all noise values and sort them
        float[] allValues = new float[width * height];
        int idx = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                allValues[idx++] = noiseMap[x][y];
            }
        }
        Arrays.sort(allValues);
        
        // Calculate percentile thresholds (generate extra for region filtering)
        float lowPercentile = profile.getLowPercent() * 1.5f;
        float highPercentile = 1.0f - (profile.getHighPercent() * 1.5f);
        
        int lowIndex = (int) (allValues.length * lowPercentile);
        int highIndex = (int) (allValues.length * highPercentile);
        
        float lowThreshold = allValues[lowIndex];
        float highThreshold = allValues[highIndex];
        
        logger.info("Client: Overlay thresholds: low <= {}, high >= {} (percentiles: {}%, {}%)",
                String.format("%.3f", lowThreshold), String.format("%.3f", highThreshold),
                String.format("%.1f", lowPercentile * 100), String.format("%.1f", highPercentile * 100));
        
        int lowCount = 0, highCount = 0;
        
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                float value = noiseMap[x][y];
                TerrainType overlayType = null;
                
                if (value <= lowThreshold) {
                    overlayType = profile.getLowType();
                    lowCount++;
                } else if (value >= highThreshold) {
                    overlayType = profile.getHighType();
                    highCount++;
                }
                
                mapData.getTile(x, y).setOverlayType(overlayType);
            }
        }
        
        logger.info("Client: Assigned {} low overlays ({}) and {} high overlays ({})", 
                lowCount, profile.getLowType(), highCount, profile.getHighType());
    }
    
    private void keepLargestContiguousOverlayRegion(GameMapData mapData, TerrainType targetType) {
        List<Region> regions = findAllOverlayRegions(mapData, targetType);
        
        if (regions.isEmpty()) {
            logger.info("Client: No {} overlay regions found", targetType);
            return;
        }
        
        regions.sort((a, b) -> Integer.compare(b.size(), a.size()));
        
        logger.info("Client: Found {} regions of type {}. Largest has {} tiles, keeping it and removing {} smaller regions",
                regions.size(), targetType, regions.get(0).size(), regions.size() - 1);
        
        for (int i = 1; i < regions.size(); i++) {
            Region smallRegion = regions.get(i);
            for (Point tile : smallRegion.tiles) {
                mapData.getTile(tile.x, tile.y).setOverlayType(null);
            }
        }
    }
    
    private List<Region> findAllOverlayRegions(GameMapData mapData, TerrainType type) {
        List<Region> regions = new ArrayList<>();
        boolean[][] visited = new boolean[mapData.getWidthTiles()][mapData.getHeightTiles()];
        
        for (int y = 0; y < mapData.getHeightTiles(); y++) {
            for (int x = 0; x < mapData.getWidthTiles(); x++) {
                TerrainTile tile = mapData.getTile(x, y);
                if (!visited[x][y] && tile.hasOverlay() && tile.getOverlayType() == type) {
                    Region region = floodFillOverlay(mapData, x, y, type, visited);
                    regions.add(region);
                }
            }
        }
        
        return regions;
    }
    
    private Region floodFillOverlay(GameMapData mapData, int startX, int startY, TerrainType type, boolean[][] visited) {
        Region region = new Region();
        Queue<Point> queue = new LinkedList<>();
        
        queue.add(new Point(startX, startY));
        visited[startX][startY] = true;
        
        while (!queue.isEmpty()) {
            Point p = queue.poll();
            region.addTile(p.x, p.y);
            
            int[][] neighbors = {{0, 1}, {0, -1}, {1, 0}, {-1, 0}};
            for (int[] n : neighbors) {
                int nx = p.x + n[0];
                int ny = p.y + n[1];
                
                if (isValidTile(mapData, nx, ny) && !visited[nx][ny]) {
                    TerrainTile tile = mapData.getTile(nx, ny);
                    if (tile.hasOverlay() && tile.getOverlayType() == type) {
                        visited[nx][ny] = true;
                        queue.add(new Point(nx, ny));
                    }
                }
            }
        }
        
        return region;
    }
    
    private boolean isValidTile(GameMapData mapData, int x, int y) {
        return x >= 0 && x < mapData.getWidthTiles() && 
               y >= 0 && y < mapData.getHeightTiles();
    }
    
    private static class Region {
        private final List<Point> tiles = new ArrayList<>();
        
        public void addTile(int x, int y) {
            tiles.add(new Point(x, y));
        }
        
        public int size() {
            return tiles.size();
        }
    }
    
    private static class Point {
        final int x;
        final int y;
        
        Point(int x, int y) {
            this.x = x;
            this.y = y;
        }
    }
}
