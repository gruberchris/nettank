package org.chrisgruber.nettank.server.world;

import org.chrisgruber.nettank.common.world.GameMapData;
import org.chrisgruber.nettank.common.world.TerrainType;
import org.joml.Vector2f;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LineOfSightCalculatorTest {

    private static final float TILE = 32.0f;
    private GameMapData mapData;

    // Both points on the same row, at tile centers
    private final Vector2f viewer = tileCenter(5, 15);

    private static Vector2f tileCenter(int tileX, int tileY) {
        return new Vector2f((tileX + 0.5f) * TILE, (tileY + 0.5f) * TILE);
    }

    @BeforeEach
    void setUp() {
        mapData = new GameMapData(30, 30, TILE); // all GRASS
    }

    @Test
    void testClearTerrainWithinRadiusIsVisible() {
        assertTrue(LineOfSightCalculator.canSee(mapData, viewer, tileCenter(20, 15), 500.0f));
    }

    @Test
    void testBeyondSightRadiusIsInvisible() {
        // 20 tiles away = 640 px > 500 px radius
        assertFalse(LineOfSightCalculator.canSee(mapData, viewer, tileCenter(25, 15), 500.0f));
    }

    @Test
    void testMountainBlocksSightCompletely() {
        mapData.getTile(10, 15).setBaseType(TerrainType.MOUNTAIN);
        assertFalse(LineOfSightCalculator.canSee(mapData, viewer, tileCenter(20, 15), 500.0f));
    }

    @Test
    void testRocksBlockSightCompletely() {
        mapData.getTile(10, 15).setOverlayType(TerrainType.ROCKS);
        assertFalse(LineOfSightCalculator.canSee(mapData, viewer, tileCenter(20, 15), 500.0f));
    }

    @Test
    void testForestHalvesViewDistance() {
        // Target at 13 tiles (416 px), 12 intermediate tiles: clear path is visible
        Vector2f target = tileCenter(18, 15);
        assertTrue(LineOfSightCalculator.canSee(mapData, viewer, target, 500.0f));

        // 5 FOREST tiles double their traversal cost: 7*32 + 5*64 = 544 px > 500 -> hidden
        for (int x = 8; x <= 12; x++) {
            mapData.getTile(x, 15).setOverlayType(TerrainType.FOREST);
        }
        assertFalse(LineOfSightCalculator.canSee(mapData, viewer, target, 500.0f));
    }

    @Test
    void testHillPartiallyObscuresLikeForest() {
        Vector2f target = tileCenter(18, 15);

        // 2 HILL tiles: 10*32 + 2*64 = 448 px <= 500 -> still visible
        mapData.getTile(8, 15).setOverlayType(TerrainType.HILL);
        mapData.getTile(9, 15).setOverlayType(TerrainType.HILL);
        assertTrue(LineOfSightCalculator.canSee(mapData, viewer, target, 500.0f));

        // 5 HILL tiles push the effective distance past the radius
        for (int x = 10; x <= 12; x++) {
            mapData.getTile(x, 15).setOverlayType(TerrainType.HILL);
        }
        assertFalse(LineOfSightCalculator.canSee(mapData, viewer, target, 500.0f));
    }

    @Test
    void testTargetTileDoesNotBlockItself() {
        // The target stands at the edge of a forest tile; the tile itself never blocks
        mapData.getTile(20, 15).setOverlayType(TerrainType.FOREST);
        assertTrue(LineOfSightCalculator.canSee(mapData, viewer, tileCenter(20, 15), 500.0f));
    }
}
