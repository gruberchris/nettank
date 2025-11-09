package org.chrisgruber.nettank.server.world;

import org.chrisgruber.nettank.common.world.GameMapData;
import org.chrisgruber.nettank.common.world.TerrainTile;
import org.chrisgruber.nettank.common.world.TerrainType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Random;

public class TerrainGenerator {
    private static final Logger logger = LoggerFactory.getLogger(TerrainGenerator.class);
    private final Random random;

    public TerrainGenerator() {
        this.random = new Random();
    }

    public TerrainGenerator(long seed) {
        this.random = new Random(seed);
    }

    public void generateAllGrassMap(GameMapData mapData) {
        logger.info("Generating all-grass map for {}x{} map",
                mapData.getWidthTiles(), mapData.getHeightTiles());

        for (int y = 0; y < mapData.getHeightTiles(); y++) {
            for (int x = 0; x < mapData.getWidthTiles(); x++) {
                TerrainTile tile = mapData.getTile(x, y);
                tile.setBaseType(TerrainType.GRASS);
            }
        }

        logger.info("All-grass terrain generation complete");
    }

    public void generateAllDesertMap(GameMapData mapData) {
        logger.info("Generating all-desert map for {}x{} map",
                mapData.getWidthTiles(), mapData.getHeightTiles());

        for (int y = 0; y < mapData.getHeightTiles(); y++) {
            for (int x = 0; x < mapData.getWidthTiles(); x++) {
                TerrainTile tile = mapData.getTile(x, y);
                tile.setBaseType(TerrainType.SAND);
            }
        }

        logger.info("All-desert terrain generation complete");
    }
    
    public void generateProceduralTerrain(GameMapData mapData, org.chrisgruber.nettank.common.world.BaseTerrainProfile profile) {
        // TEMPORARY: Using fixed seed so client/server generate identical maps
        // TODO: Generate random seed and send to clients via network
        long fixedSeed = 12345L;
        ProceduralTerrainGenerator procGen = new ProceduralTerrainGenerator(fixedSeed);
        procGen.generateProceduralTerrain(mapData, profile);
    }

    public void generateGrassDirtMudMap(GameMapData mapData) {
        logger.info("Generating grass/dirt/mud map for {}x{} map",
                mapData.getWidthTiles(), mapData.getHeightTiles());

        int width = mapData.getWidthTiles();
        int height = mapData.getHeightTiles();

        // Divide map into thirds horizontally: Grass (left) | Mud (center) | Dirt (right)
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

        logger.info("Grass/Dirt/Mud terrain generation complete - 1/3 each in consecutive sections");
    }

    public void generateTerrainWithFeatures(GameMapData mapData) {
        logger.info("Generating terrain with features for {}x{} map",
                mapData.getWidthTiles(), mapData.getHeightTiles());

        for (int y = 0; y < mapData.getHeightTiles(); y++) {
            for (int x = 0; x < mapData.getWidthTiles(); x++) {
                TerrainTile tile = mapData.getTile(x, y);
                tile.setBaseType(TerrainType.GRASS);
            }
        }

        int numPatches = random.nextInt(5) + 3;
        for (int i = 0; i < numPatches; i++) {
            int centerX = random.nextInt(mapData.getWidthTiles());
            int centerY = random.nextInt(mapData.getHeightTiles());
            int radius = random.nextInt(5) + 3;

            TerrainType patchType = switch(random.nextInt(4)) {
                case 0 -> TerrainType.DIRT;
                case 1 -> TerrainType.MUD;
                case 2 -> TerrainType.SAND;
                default -> TerrainType.FOREST;
            };

            createTerrainPatch(mapData, centerX, centerY, radius, patchType);
        }

        logger.info("Terrain generation with features complete");
    }

    private void createTerrainPatch(GameMapData mapData, int centerX, int centerY,
                                   int radius, TerrainType type) {
        for (int y = centerY - radius; y <= centerY + radius; y++) {
            for (int x = centerX - radius; x <= centerX + radius; x++) {
                if (!mapData.isValidTile(x, y)) continue;

                int dx = x - centerX;
                int dy = y - centerY;
                double distance = Math.sqrt(dx * dx + dy * dy);

                if (distance <= radius) {
                    float edgeFade = (float) (1.0 - (distance / radius));
                    if (random.nextFloat() < edgeFade) {
                        TerrainTile tile = mapData.getTile(x, y);
                        tile.setBaseType(type);
                    }
                }
            }
        }
    }
}
