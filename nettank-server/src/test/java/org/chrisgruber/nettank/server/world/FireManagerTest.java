package org.chrisgruber.nettank.server.world;

import org.chrisgruber.nettank.common.world.GameMapData;
import org.chrisgruber.nettank.common.world.TerrainState;
import org.chrisgruber.nettank.common.world.TerrainType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class FireManagerTest {

    private GameMapData mapData;

    // Deterministic randoms: 0.0 always passes chance rolls, 0.99 always fails them
    private final Random alwaysPass = new Random() {
        @Override public float nextFloat() { return 0.0f; }
    };
    private final Random alwaysFail = new Random() {
        @Override public float nextFloat() { return 0.99f; }
    };

    @BeforeEach
    void setUp() {
        mapData = new GameMapData(10, 10, 32.0f); // all GRASS by default
    }

    @Test
    void testIgnitionSetsIgnitingStateAndRecordsChange() {
        FireManager fireManager = new FireManager(mapData, alwaysPass);

        assertTrue(fireManager.attemptIgnition(5, 5, 1.0f, 1000L));
        assertEquals(TerrainState.IGNITING, mapData.getTile(5, 5).getCurrentState());

        var changes = fireManager.drainStateChanges();
        assertEquals(1, changes.size());
        assertEquals(TerrainState.IGNITING, changes.get(0).state);
        assertTrue(fireManager.drainStateChanges().isEmpty()); // drained
    }

    @Test
    void testIgnitionFailsWhenChanceRollFails() {
        FireManager fireManager = new FireManager(mapData, alwaysFail);

        assertFalse(fireManager.attemptIgnition(5, 5, 1.0f, 1000L));
        assertEquals(TerrainState.NORMAL, mapData.getTile(5, 5).getCurrentState());
    }

    @Test
    void testNonFlammableTerrainNeverIgnites() {
        mapData.getTile(5, 5).setBaseType(TerrainType.MUD);
        FireManager fireManager = new FireManager(mapData, alwaysPass);

        assertFalse(fireManager.attemptIgnition(5, 5, 1.0f, 1000L));
    }

    @Test
    void testFireProgressionFollowsScheduleWithInjectedTime() {
        // FOREST burns for 15000 ms: IGNITING < 2000, BURNING < 12000, SMOLDERING < 15000, then SCORCHED
        mapData.getTile(5, 5).setOverlayType(TerrainType.FOREST);
        FireManager fireManager = new FireManager(mapData, alwaysPass);

        assertTrue(fireManager.attemptIgnition(5, 5, 1.0f, 0L));

        fireManager.update(1999L);
        assertEquals(TerrainState.IGNITING, mapData.getTile(5, 5).getCurrentState());

        fireManager.update(2000L);
        assertEquals(TerrainState.BURNING, mapData.getTile(5, 5).getCurrentState());

        fireManager.update(11999L);
        assertEquals(TerrainState.BURNING, mapData.getTile(5, 5).getCurrentState());

        fireManager.update(12000L);
        assertEquals(TerrainState.SMOLDERING, mapData.getTile(5, 5).getCurrentState());

        fireManager.update(15000L);
        assertEquals(TerrainState.SCORCHED, mapData.getTile(5, 5).getCurrentState());
        assertTrue(fireManager.getBurningTiles().isEmpty());
    }

    @Test
    void testBurningTileSpreadsToFlammableNeighbors() {
        mapData.getTile(5, 5).setOverlayType(TerrainType.FOREST);
        FireManager fireManager = new FireManager(mapData, alwaysPass);

        fireManager.attemptIgnition(5, 5, 1.0f, 0L);
        fireManager.update(2000L); // transitions to BURNING and attempts spread

        assertEquals(TerrainState.IGNITING, mapData.getTile(5, 6).getCurrentState());
        assertEquals(TerrainState.IGNITING, mapData.getTile(5, 4).getCurrentState());
        assertEquals(TerrainState.IGNITING, mapData.getTile(6, 5).getCurrentState());
        assertEquals(TerrainState.IGNITING, mapData.getTile(4, 5).getCurrentState());
    }

    @Test
    void testNoSpreadWhenChanceRollFails() {
        mapData.getTile(5, 5).setOverlayType(TerrainType.FOREST);

        // Pass the initial ignition roll, then fail every subsequent roll
        Random passOnceThenFail = new Random() {
            private boolean first = true;
            @Override public float nextFloat() {
                if (first) { first = false; return 0.0f; }
                return 0.99f;
            }
        };
        FireManager fireManager = new FireManager(mapData, passOnceThenFail);

        fireManager.attemptIgnition(5, 5, 1.0f, 0L);
        fireManager.update(2000L);

        assertEquals(TerrainState.BURNING, mapData.getTile(5, 5).getCurrentState());
        assertEquals(TerrainState.NORMAL, mapData.getTile(5, 6).getCurrentState());
    }

    @Test
    void testScorchedTileCannotReignite() {
        FireManager fireManager = new FireManager(mapData, alwaysPass);

        fireManager.attemptIgnition(5, 5, 1.0f, 0L);
        fireManager.update(10000L); // GRASS burns 5000 ms, well past burnout
        assertEquals(TerrainState.SCORCHED, mapData.getTile(5, 5).getCurrentState());

        assertFalse(fireManager.attemptIgnition(5, 5, 1.0f, 11000L));
        assertEquals(TerrainState.SCORCHED, mapData.getTile(5, 5).getCurrentState());
    }
}
