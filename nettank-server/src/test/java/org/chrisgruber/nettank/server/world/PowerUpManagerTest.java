package org.chrisgruber.nettank.server.world;

import org.chrisgruber.nettank.common.entities.PowerUpType;
import org.chrisgruber.nettank.common.entities.TankData;
import org.chrisgruber.nettank.common.world.GameMapData;
import org.chrisgruber.nettank.server.gamemode.FreeForAll;
import org.chrisgruber.nettank.server.state.ServerContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.joml.Vector2f;
import org.joml.Vector3f;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class PowerUpManagerTest {

    // Deterministic Random: fixed float (controls spawn jitter) and int (controls type pick)
    private static class FakeRandom extends Random {
        float floatValue = 0.5f; // -> jitter-free 10s spawn interval
        int intValue = 0;

        @Override public float nextFloat() { return floatValue; }
        @Override public int nextInt(int bound) { return Math.min(intValue, bound - 1); }
    }

    private ServerContext context;
    private FakeRandom random;
    private PowerUpManager manager;

    @BeforeEach
    void setUp() {
        context = new ServerContext();
        context.gameMapData = new GameMapData(20, 20, 32.0f);
        context.gameMode = new FreeForAll();
        random = new FakeRandom();
        manager = new PowerUpManager(random);
    }

    private TankData addTank(int playerId) {
        TankData tank = new TankData(playerId, new Vector2f(10000, 10000), new Vector2f(), 0f,
                new Vector3f(1, 1, 1), "P" + playerId);
        context.tanks.put(playerId, tank);
        return tank;
    }

    private void moveTankOntoPowerUp(TankData tank) {
        var powerUp = manager.getSpawnedPowerUps().iterator().next();
        tank.setPosition(new Vector2f(powerUp.position));
    }

    @Test
    void testSpawnCadenceWithInjectedTime() {
        manager.update(context, 0); // primes the spawn timer
        assertEquals(0, manager.getSpawnedPowerUps().size());

        manager.update(context, 9999);
        assertEquals(0, manager.getSpawnedPowerUps().size());

        manager.update(context, 10000);
        assertEquals(1, manager.getSpawnedPowerUps().size());

        var events = manager.drainEvents();
        assertTrue(events.stream().anyMatch(e -> e instanceof PowerUpManager.Event.Spawned));
    }

    @Test
    void testConcurrencyCapHolds() {
        // Simulate 3 minutes of ticks with no tanks: spawns accumulate but despawn
        // after 30 s, so the live count must never exceed the cap
        for (long t = 0; t <= 180000; t += 500) {
            manager.update(context, t);
            assertTrue(manager.getSpawnedPowerUps().size() <= PowerUpManager.MAX_CONCURRENT);
        }
    }

    @Test
    void testUnclaimedPowerUpDespawnsAfterTimeout() {
        manager.update(context, 0);
        manager.update(context, 10000); // spawn
        assertEquals(1, manager.getSpawnedPowerUps().size());
        manager.drainEvents();

        manager.update(context, 10000 + PowerUpManager.UNCLAIMED_DESPAWN_MS);
        assertEquals(1, manager.getSpawnedPowerUps().size()); // a new one spawned at 20000/30000... check events instead

        var events = manager.drainEvents();
        assertTrue(events.stream().anyMatch(e -> e instanceof PowerUpManager.Event.Removed removed
                && "EXPIRED".equals(removed.reason())));
    }

    @Test
    void testPickupActivatesBuffAndSameCategoryReplaces() {
        TankData tank = addTank(0);

        random.intValue = 0; // DAMAGE_2X
        manager.update(context, 0);
        manager.update(context, 10000);
        moveTankOntoPowerUp(tank);
        manager.update(context, 10001);

        assertEquals(2.0f, manager.getMultiplier(0, PowerUpType.Category.DAMAGE));
        var events = manager.drainEvents();
        assertTrue(events.stream().anyMatch(e -> e instanceof PowerUpManager.Event.Removed removed
                && "TAKEN".equals(removed.reason())));
        assertTrue(events.stream().anyMatch(e -> e instanceof PowerUpManager.Event.Activated activated
                && activated.type() == PowerUpType.DAMAGE_2X));

        // Next pickup: DAMAGE_3X replaces the 2X buff (same category)
        random.intValue = 1;
        tank.setPosition(new Vector2f(10000, 10000)); // move away first
        manager.update(context, 20000);
        moveTankOntoPowerUp(tank);
        manager.update(context, 20001);

        assertEquals(3.0f, manager.getMultiplier(0, PowerUpType.Category.DAMAGE));
        events = manager.drainEvents();
        assertTrue(events.stream().anyMatch(e -> e instanceof PowerUpManager.Event.Ended ended
                && ended.type() == PowerUpType.DAMAGE_2X));
        assertTrue(events.stream().anyMatch(e -> e instanceof PowerUpManager.Event.Activated activated
                && activated.type() == PowerUpType.DAMAGE_3X));
    }

    @Test
    void testBuffExpiresAndRestoresMultiplier() {
        TankData tank = addTank(0);

        random.intValue = 0; // DAMAGE_2X, 15 s duration
        manager.update(context, 0);
        manager.update(context, 10000);
        moveTankOntoPowerUp(tank);
        manager.update(context, 10001);
        assertEquals(2.0f, manager.getMultiplier(0, PowerUpType.Category.DAMAGE));
        manager.drainEvents();

        tank.setPosition(new Vector2f(10000, 10000));
        manager.update(context, 10001 + PowerUpType.DAMAGE_2X.getDurationMs());

        assertEquals(1.0f, manager.getMultiplier(0, PowerUpType.Category.DAMAGE));
        assertTrue(manager.drainEvents().stream().anyMatch(e -> e instanceof PowerUpManager.Event.Ended ended
                && ended.type() == PowerUpType.DAMAGE_2X));
    }

    @Test
    void testDeathClearsBuffs() {
        TankData tank = addTank(0);

        random.intValue = 4; // SPEED_2X
        manager.update(context, 0);
        manager.update(context, 10000);
        moveTankOntoPowerUp(tank);
        manager.update(context, 10001);
        assertEquals(2.0f, manager.getMultiplier(0, PowerUpType.Category.SPEED));
        manager.drainEvents();

        manager.clearEffectsForPlayer(0);

        assertEquals(1.0f, manager.getMultiplier(0, PowerUpType.Category.SPEED));
        assertTrue(manager.drainEvents().stream().anyMatch(e -> e instanceof PowerUpManager.Event.Ended ended
                && ended.type() == PowerUpType.SPEED_2X));
    }

    @Test
    void testInstantRepairDoesNotOccupyCategorySlot() {
        TankData tank = addTank(0);

        random.intValue = 8; // REPAIR_HP in the FFA table (UNLIMITED_AMMO removed -> index 7 max); clamped to last
        manager.update(context, 0);
        manager.update(context, 10000);
        moveTankOntoPowerUp(tank);
        manager.update(context, 10001);

        var events = manager.drainEvents();
        assertTrue(events.stream().anyMatch(e -> e instanceof PowerUpManager.Event.InstantApplied instant
                && instant.type().isInstant()));
        assertEquals(1.0f, manager.getMultiplier(0, PowerUpType.Category.HEALTH));
    }

    @Test
    void testUnlimitedAmmoNeverSpawnsInUnlimitedAmmoModes() {
        // FreeForAll has unlimited ammo (-1), so UNLIMITED_AMMO is excluded from the table
        for (int index = 0; index < PowerUpType.values().length; index++) {
            PowerUpManager freshManager = new PowerUpManager(random);
            random.intValue = index;
            freshManager.update(context, 0);
            freshManager.update(context, 10000);

            assertEquals(1, freshManager.getSpawnedPowerUps().size());
            var type = freshManager.getSpawnedPowerUps().iterator().next().type;
            assertNotEquals(PowerUpType.UNLIMITED_AMMO, type);
        }
    }

    @Test
    void testUnlimitedAmmoSpawnsInFiniteAmmoModes() {
        context.gameMode = new FreeForAll() {
            { this.startingMainWeaponAmmoCount = 10; }
        };

        int unlimitedAmmoIndex = java.util.List.of(PowerUpType.values()).indexOf(PowerUpType.UNLIMITED_AMMO);
        random.intValue = unlimitedAmmoIndex;

        manager.update(context, 0);
        manager.update(context, 10000);

        assertEquals(PowerUpType.UNLIMITED_AMMO, manager.getSpawnedPowerUps().iterator().next().type);
    }
}
