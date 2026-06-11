package org.chrisgruber.nettank.server.world;

import org.chrisgruber.nettank.common.entities.PowerUpType;
import org.chrisgruber.nettank.common.entities.TankData;
import org.chrisgruber.nettank.server.state.ServerContext;
import org.joml.Vector2f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Server-authoritative power-up lifecycle: spawning pickups on the map, pickup
 * collision, timed buff activation/expiry, and the category stacking rule
 * (one active buff per category; same-category pickups replace).
 *
 * Like FireManager, takes the current time in update(...) so tests can drive it
 * deterministically, and reports protocol-worthy changes as drainable events.
 */
public class PowerUpManager {
    private static final Logger logger = LoggerFactory.getLogger(PowerUpManager.class);

    static final long SPAWN_INTERVAL_MS = 10000;
    static final long SPAWN_JITTER_MS = 2500;
    static final int MAX_CONCURRENT = 4;
    static final long UNCLAIMED_DESPAWN_MS = 30000;
    static final float PICKUP_RADIUS = 16.0f;

    public static final class SpawnedPowerUp {
        public final int id;
        public final PowerUpType type;
        public final Vector2f position;
        public final long spawnTime;

        SpawnedPowerUp(int id, PowerUpType type, Vector2f position, long spawnTime) {
            this.id = id;
            this.type = type;
            this.position = position;
            this.spawnTime = spawnTime;
        }
    }

    private static final class ActiveEffect {
        final PowerUpType type;
        final long expiresAtMillis;

        ActiveEffect(PowerUpType type, long expiresAtMillis) {
            this.type = type;
            this.expiresAtMillis = expiresAtMillis;
        }
    }

    // Protocol-worthy changes, drained by GameServer each tick
    public sealed interface Event {
        record Spawned(int powerUpId, PowerUpType type, float x, float y) implements Event {}
        record Removed(int powerUpId, String reason) implements Event {}
        record Activated(int playerId, PowerUpType type, long durationMs) implements Event {}
        record Ended(int playerId, PowerUpType type) implements Event {}
        record InstantApplied(int playerId, PowerUpType type) implements Event {}
    }

    private final Map<Integer, SpawnedPowerUp> spawnedById = new LinkedHashMap<>();
    private final Map<Integer, EnumMap<PowerUpType.Category, ActiveEffect>> activeEffectsByPlayerId = new HashMap<>();
    private final List<Event> pendingEvents = new ArrayList<>();
    private final Random random;
    private int nextPowerUpId = 0;
    private long nextSpawnTime = 0;

    public PowerUpManager() {
        this(new Random());
    }

    public PowerUpManager(Random random) {
        this.random = random;
    }

    public void update(ServerContext serverContext, long currentTime) {
        spawnIfDue(serverContext, currentTime);
        despawnUnclaimed(currentTime);
        checkPickups(serverContext, currentTime);
        expireEffects(currentTime);
    }

    private void spawnIfDue(ServerContext serverContext, long currentTime) {
        if (nextSpawnTime == 0) {
            nextSpawnTime = currentTime + nextSpawnDelay();
            return;
        }
        if (currentTime < nextSpawnTime || spawnedById.size() >= MAX_CONCURRENT) return;

        PowerUpType type = pickSpawnType(serverContext);
        Vector2f position = serverContext.gameMapData.getRandomSpawnPoint();
        int id = nextPowerUpId++;

        spawnedById.put(id, new SpawnedPowerUp(id, type, position, currentTime));
        pendingEvents.add(new Event.Spawned(id, type, position.x, position.y));
        nextSpawnTime = currentTime + nextSpawnDelay();

        logger.debug("Spawned power-up {} ({}) at ({}, {})", id, type, position.x, position.y);
    }

    private long nextSpawnDelay() {
        return SPAWN_INTERVAL_MS - SPAWN_JITTER_MS + (long) (random.nextFloat() * 2 * SPAWN_JITTER_MS);
    }

    // UNLIMITED_AMMO only enters the spawn table when the mode has finite ammo,
    // so modes like FreeForAll (already unlimited) keep their balance
    private PowerUpType pickSpawnType(ServerContext serverContext) {
        List<PowerUpType> table = new ArrayList<>(List.of(PowerUpType.values()));
        if (serverContext.gameMode.getStartingMainWeaponAmmoCount() < 0) {
            table.remove(PowerUpType.UNLIMITED_AMMO);
        }
        return table.get(random.nextInt(table.size()));
    }

    private void despawnUnclaimed(long currentTime) {
        Iterator<SpawnedPowerUp> iterator = spawnedById.values().iterator();
        while (iterator.hasNext()) {
            SpawnedPowerUp powerUp = iterator.next();
            if (currentTime - powerUp.spawnTime >= UNCLAIMED_DESPAWN_MS) {
                iterator.remove();
                pendingEvents.add(new Event.Removed(powerUp.id, "EXPIRED"));
            }
        }
    }

    private void checkPickups(ServerContext serverContext, long currentTime) {
        if (spawnedById.isEmpty()) return;

        Iterator<SpawnedPowerUp> iterator = spawnedById.values().iterator();
        while (iterator.hasNext()) {
            SpawnedPowerUp powerUp = iterator.next();

            for (TankData tankData : serverContext.tanks.values()) {
                if (tankData.isDestroyed()) continue;

                float pickupDistance = PICKUP_RADIUS + TankData.COLLISION_RADIUS;
                if (tankData.getPosition().distance(powerUp.position) > pickupDistance) continue;

                iterator.remove();
                pendingEvents.add(new Event.Removed(powerUp.id, "TAKEN"));
                applyPowerUp(tankData.getPlayerId(), powerUp.type, currentTime);
                break;
            }
        }
    }

    private void applyPowerUp(int playerId, PowerUpType type, long currentTime) {
        if (type.isInstant()) {
            // Instant repairs don't occupy a category slot; GameServer applies the effect
            pendingEvents.add(new Event.InstantApplied(playerId, type));
            return;
        }

        var effects = activeEffectsByPlayerId.computeIfAbsent(playerId, k -> new EnumMap<>(PowerUpType.Category.class));
        ActiveEffect replaced = effects.put(type.getCategory(), new ActiveEffect(type, currentTime + type.getDurationMs()));

        if (replaced != null && replaced.type != type) {
            pendingEvents.add(new Event.Ended(playerId, replaced.type));
        }
        pendingEvents.add(new Event.Activated(playerId, type, type.getDurationMs()));

        logger.debug("PlayerId {} activated {} for {} ms", playerId, type, type.getDurationMs());
    }

    private void expireEffects(long currentTime) {
        for (var entry : activeEffectsByPlayerId.entrySet()) {
            entry.getValue().values().removeIf(effect -> {
                if (currentTime >= effect.expiresAtMillis) {
                    pendingEvents.add(new Event.Ended(entry.getKey(), effect.type));
                    return true;
                }
                return false;
            });
        }
    }

    // Stat multiplier consumed by GameServer.getEffectiveStats
    public float getMultiplier(int playerId, PowerUpType.Category category) {
        var effects = activeEffectsByPlayerId.get(playerId);
        if (effects == null) return 1.0f;

        ActiveEffect effect = effects.get(category);
        return effect != null ? effect.type.getMultiplier() : 1.0f;
    }

    public boolean hasUnlimitedAmmo(int playerId) {
        var effects = activeEffectsByPlayerId.get(playerId);
        return effects != null && effects.containsKey(PowerUpType.Category.AMMO);
    }

    // Buffs are lost on death
    public void clearEffectsForPlayer(int playerId) {
        var effects = activeEffectsByPlayerId.remove(playerId);
        if (effects == null) return;
        for (ActiveEffect effect : effects.values()) {
            pendingEvents.add(new Event.Ended(playerId, effect.type));
        }
    }

    public Collection<SpawnedPowerUp> getSpawnedPowerUps() {
        return Collections.unmodifiableCollection(spawnedById.values());
    }

    public List<Event> drainEvents() {
        if (pendingEvents.isEmpty()) return Collections.emptyList();
        List<Event> drained = new ArrayList<>(pendingEvents);
        pendingEvents.clear();
        return drained;
    }
}
