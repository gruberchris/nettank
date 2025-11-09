# Dynamic Terrain System - Fire, Burning, and State Changes

## Overview
Extend the terrain system to support **dynamic state changes** like fire spreading through grass/forests, burning effects, and terrain transformation (grass → scorched earth). This builds on your existing ExplosionEffect and FlameEffect systems.

## Current Effects System

You already have:
- **ExplosionEffect**: Animated explosions with duration and frame-based rendering
- **FlameEffect**: Animated flames (currently tied to tank engine fires)
- **Position-based effects**: Visual effects at specific world coordinates

**Gap**: Effects are purely visual and temporary - they don't modify the underlying terrain.

## Enhanced System: Dynamic Terrain States

### Terrain State Architecture

Instead of just a terrain **type**, each tile has both a **base type** and a **state**:

```java
public class TerrainTile {
    private TerrainType baseType;        // Original terrain: GRASS, FOREST, etc.
    private TerrainState currentState;   // Current state: NORMAL, BURNING, SCORCHED
    private long stateChangeTime;        // When state changed (for animations/spread)
    private float fireDuration;          // How long it burns
    
    public TerrainTile(TerrainType baseType) {
        this.baseType = baseType;
        this.currentState = TerrainState.NORMAL;
        this.stateChangeTime = 0;
        this.fireDuration = 0;
    }
}
```

### Terrain States

```java
public enum TerrainState {
    NORMAL(1.0f, false),           // Default state
    IGNITING(1.0f, true),          // Just caught fire (0-2 seconds)
    BURNING(0.7f, true),           // Actively burning (affects movement)
    SMOLDERING(0.8f, true),        // Dying out (2-5 seconds left)
    SCORCHED(0.9f, false),         // Burned out, permanent change
    FLOODED(0.5f, false),          // Wet, can't catch fire
    FROZEN(0.8f, false);           // Future: ice/snow mechanics
    
    private final float speedModifier;    // How it affects movement
    private final boolean hasVisualEffect;  // Show fire/smoke particles
    
    TerrainState(float speedModifier, boolean hasVisualEffect) {
        this.speedModifier = speedModifier;
        this.hasVisualEffect = hasVisualEffect;
    }
    
    public float getSpeedModifier() { return speedModifier; }
    public boolean hasVisualEffect() { return hasVisualEffect; }
}
```

### Flammability System

```java
public enum TerrainType {
    // Add flammability properties
    GRASS(1.0f, true, VisionBlockingType.NONE, 
          Flammability.MEDIUM, 5000L),     // Burns for 5 seconds
    
    FOREST(0.7f, true, VisionBlockingType.PARTIAL, 
           Flammability.HIGH, 15000L),     // Burns for 15 seconds
    
    DIRT(0.95f, true, VisionBlockingType.NONE, 
         Flammability.NONE, 0L),           // Can't burn
    
    MUD(0.6f, true, VisionBlockingType.NONE, 
        Flammability.NONE, 0L),            // Can't burn (wet)
    
    STONE(1.0f, true, VisionBlockingType.NONE, 
          Flammability.NONE, 0L),          // Can't burn
    
    SAND(0.85f, true, VisionBlockingType.NONE, 
         Flammability.NONE, 0L);           // Can't burn
    
    private final float speedModifier;
    private final boolean passable;
    private final VisionBlockingType visionBlocking;
    private final Flammability flammability;
    private final long burnDuration;  // Milliseconds it burns when ignited
    
    // Constructor and getters...
}

public enum Flammability {
    NONE(0.0f, 0.0f),          // Won't catch fire
    LOW(0.1f, 0.05f),          // Hard to ignite, spreads slowly
    MEDIUM(0.4f, 0.15f),       // Normal grass
    HIGH(0.8f, 0.35f),         // Dry grass, forests
    EXTREME(1.0f, 0.5f);       // Future: oil spills, etc.
    
    private final float ignitionChance;     // Chance to catch fire from nearby fire
    private final float spreadChance;       // Chance to spread to adjacent tiles
    
    Flammability(float ignitionChance, float spreadChance) {
        this.ignitionChance = ignitionChance;
        this.spreadChance = spreadChance;
    }
}
```

## Fire Propagation System

### Server-Side Fire Manager

```java
public class FireManager {
    private final ServerGameMap gameMap;
    private final Set<Vector2i> burningTiles;  // Currently burning tile coordinates
    private final Map<Vector2i, Long> ignitionTimes;  // When each tile caught fire
    private final Random random = new Random();
    
    // Configuration
    private static final long FIRE_UPDATE_INTERVAL_MS = 500;  // Check spread every 0.5s
    private static final int FIRE_SPREAD_RADIUS = 1;          // Spreads to adjacent tiles
    private static final float EXPLOSION_IGNITION_RADIUS = 2.5f;  // Tiles affected by explosion
    
    /**
     * Called when an explosion occurs - ignite nearby flammable terrain
     */
    public void onExplosion(Vector2f position, float radius) {
        int tileX = (int) (position.x / gameMap.getTileSize());
        int tileY = (int) (position.y / gameMap.getTileSize());
        
        int radiusTiles = (int) Math.ceil(radius / gameMap.getTileSize());
        
        // Check all tiles in explosion radius
        for (int y = tileY - radiusTiles; y <= tileY + radiusTiles; y++) {
            for (int x = tileX - radiusTiles; x <= tileX + radiusTiles; x++) {
                if (!gameMap.isValidTile(x, y)) continue;
                
                float tileCenterX = (x + 0.5f) * gameMap.getTileSize();
                float tileCenterY = (y + 0.5f) * gameMap.getTileSize();
                float distToExplosion = position.distance(tileCenterX, tileCenterY);
                
                if (distToExplosion <= radius) {
                    attemptIgnition(x, y, 1.0f);  // 100% chance from direct explosion
                }
            }
        }
    }
    
    /**
     * Attempt to ignite a tile
     */
    public boolean attemptIgnition(int tileX, int tileY, float chanceMultiplier) {
        TerrainTile tile = gameMap.getTile(tileX, tileY);
        
        // Already burning or not flammable
        if (tile.getCurrentState().hasVisualEffect() || 
            tile.getBaseType().getFlammability() == Flammability.NONE) {
            return false;
        }
        
        // Wet/flooded tiles can't burn
        if (tile.getCurrentState() == TerrainState.FLOODED) {
            return false;
        }
        
        // Check ignition chance
        float ignitionChance = tile.getBaseType().getFlammability().getIgnitionChance();
        if (random.nextFloat() > ignitionChance * chanceMultiplier) {
            return false;  // Failed to ignite
        }
        
        // Ignite!
        tile.setCurrentState(TerrainState.IGNITING);
        tile.setStateChangeTime(System.currentTimeMillis());
        tile.setFireDuration(tile.getBaseType().getBurnDuration());
        
        burningTiles.add(new Vector2i(tileX, tileY));
        ignitionTimes.put(new Vector2i(tileX, tileY), System.currentTimeMillis());
        
        // Network: Notify clients
        broadcastTerrainStateChange(tileX, tileY, TerrainState.IGNITING);
        
        return true;
    }
    
    /**
     * Update fire spread and burning tiles (call every frame or on a timer)
     */
    public void update(long currentTime) {
        Iterator<Vector2i> iterator = burningTiles.iterator();
        
        while (iterator.hasNext()) {
            Vector2i tilePos = iterator.next();
            TerrainTile tile = gameMap.getTile(tilePos.x, tilePos.y);
            
            long timeBurning = currentTime - tile.getStateChangeTime();
            long burnDuration = tile.getFireDuration();
            
            // Update burning state
            if (timeBurning < 2000) {
                // First 2 seconds: IGNITING
                if (tile.getCurrentState() != TerrainState.IGNITING) {
                    tile.setCurrentState(TerrainState.IGNITING);
                    broadcastTerrainStateChange(tilePos.x, tilePos.y, TerrainState.IGNITING);
                }
            } else if (timeBurning < burnDuration - 3000) {
                // Main burning phase
                if (tile.getCurrentState() != TerrainState.BURNING) {
                    tile.setCurrentState(TerrainState.BURNING);
                    broadcastTerrainStateChange(tilePos.x, tilePos.y, TerrainState.BURNING);
                }
                
                // Attempt to spread fire
                if (timeBurning % FIRE_UPDATE_INTERVAL_MS < 50) {  // Check periodically
                    attemptFireSpread(tilePos.x, tilePos.y);
                }
            } else if (timeBurning < burnDuration) {
                // Last 3 seconds: SMOLDERING
                if (tile.getCurrentState() != TerrainState.SMOLDERING) {
                    tile.setCurrentState(TerrainState.SMOLDERING);
                    broadcastTerrainStateChange(tilePos.x, tilePos.y, TerrainState.SMOLDERING);
                }
            } else {
                // Fire burned out - leave scorched earth
                tile.setCurrentState(TerrainState.SCORCHED);
                broadcastTerrainStateChange(tilePos.x, tilePos.y, TerrainState.SCORCHED);
                
                // Optionally: Change base terrain type
                if (tile.getBaseType() == TerrainType.FOREST) {
                    tile.setBaseType(TerrainType.DIRT);  // Forest → cleared land
                }
                
                iterator.remove();  // No longer actively burning
            }
        }
    }
    
    /**
     * Attempt to spread fire to adjacent tiles
     */
    private void attemptFireSpread(int centerX, int centerY) {
        TerrainTile centerTile = gameMap.getTile(centerX, centerY);
        float spreadChance = centerTile.getBaseType().getFlammability().getSpreadChance();
        
        // Check 8 adjacent tiles (or 4 for cardinal only)
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                if (dx == 0 && dy == 0) continue;  // Skip center
                
                int targetX = centerX + dx;
                int targetY = centerY + dy;
                
                if (!gameMap.isValidTile(targetX, targetY)) continue;
                
                // Diagonal spread is slightly less likely
                float distanceMultiplier = (Math.abs(dx) + Math.abs(dy) == 2) ? 0.7f : 1.0f;
                
                attemptIgnition(targetX, targetY, spreadChance * distanceMultiplier);
            }
        }
    }
    
    /**
     * Network synchronization - send state change to all clients
     */
    private void broadcastTerrainStateChange(int x, int y, TerrainState newState) {
        // Send network message to all connected clients
        // Message format: TERRAIN_STATE_CHANGE { x, y, state }
    }
}
```

### Client-Side Rendering

```java
public class ClientGameMap {
    private final TerrainTile[][] terrainGrid;  // Now uses TerrainTile instead of TerrainType
    private final Map<TerrainType, Texture> baseTextures;
    private final Map<TerrainState, Texture> stateOverlayTextures;
    private final List<TerrainFireEffect> fireEffects;  // Per-tile fire animations
    
    /**
     * Render terrain with state overlays
     */
    public void renderTerrain(Renderer renderer, Shader shader, Camera camera) {
        // ... culling setup ...
        
        for (int y = startY; y < endY; y++) {
            for (int x = startX; x < endX; x++) {
                TerrainTile tile = terrainGrid[x][y];
                
                float tileCenterX = (x + 0.5f) * tileSize;
                float tileCenterY = (y + 0.5f) * tileSize;
                
                // 1. Render base terrain
                Texture baseTexture = getBaseTexture(tile);
                if (baseTexture != null) {
                    baseTexture.bind();
                    renderer.drawQuad(tileCenterX, tileCenterY, tileSize, tileSize, 0, shader);
                }
                
                // 2. Render state overlay (scorched marks, etc.)
                if (tile.getCurrentState() == TerrainState.SCORCHED) {
                    Texture scorchedTexture = stateOverlayTextures.get(TerrainState.SCORCHED);
                    if (scorchedTexture != null) {
                        scorchedTexture.bind();
                        // Render with slight transparency/blend mode
                        shader.setUniform1f("u_alpha", 0.7f);
                        renderer.drawQuad(tileCenterX, tileCenterY, tileSize, tileSize, 0, shader);
                        shader.setUniform1f("u_alpha", 1.0f);
                    }
                }
                
                // 3. Fire effects rendered separately in renderFireEffects()
            }
        }
    }
    
    /**
     * Render fire effects on burning tiles
     */
    public void renderFireEffects(Renderer renderer, Shader shader, Camera camera) {
        for (int y = 0; y < mapData.getHeightTiles(); y++) {
            for (int x = 0; x < mapData.getWidthTiles(); x++) {
                TerrainTile tile = terrainGrid[x][y];
                
                if (tile.getCurrentState().hasVisualEffect()) {
                    float tileCenterX = (x + 0.5f) * tileSize;
                    float tileCenterY = (y + 0.5f) * tileSize;
                    
                    // Render fire animation
                    renderFireForTile(x, y, tileCenterX, tileCenterY, 
                                     tile.getCurrentState(), renderer, shader);
                }
            }
        }
    }
    
    /**
     * Render animated fire for a specific burning tile
     */
    private void renderFireForTile(int x, int y, float worldX, float worldY,
                                   TerrainState state, Renderer renderer, Shader shader) {
        // Reuse your existing FlameEffect system
        TerrainFireEffect fireEffect = getOrCreateFireEffect(x, y);
        
        if (fireEffect != null && !fireEffect.isFinished()) {
            Texture currentFrame = fireEffect.getCurrentFrameTexture();
            currentFrame.bind();
            
            // Vary intensity based on state
            float alpha = switch(state) {
                case IGNITING -> 0.5f;
                case BURNING -> 1.0f;
                case SMOLDERING -> 0.3f;
                default -> 0.0f;
            };
            
            shader.setUniform1f("u_alpha", alpha);
            renderer.drawQuad(worldX, worldY, tileSize, tileSize, 0, shader);
            shader.setUniform1f("u_alpha", 1.0f);
        }
    }
    
    /**
     * Handle terrain state change from server
     */
    public void onTerrainStateChanged(int x, int y, TerrainState newState) {
        if (!isValidTile(x, y)) return;
        
        TerrainTile tile = terrainGrid[x][y];
        tile.setCurrentState(newState);
        tile.setStateChangeTime(System.currentTimeMillis());
        
        // Create fire effect if starting to burn
        if (newState.hasVisualEffect() && newState == TerrainState.IGNITING) {
            createFireEffect(x, y);
        }
    }
}
```

## Gameplay Impact

### Movement Through Fire
```java
// In movement physics code
public float getMovementSpeedAt(float x, float y) {
    TerrainTile tile = gameMap.getTileAt(x, y);
    
    float baseSpeed = tile.getBaseType().getSpeedModifier();
    float stateSpeed = tile.getCurrentState().getSpeedModifier();
    
    return baseSpeed * stateSpeed;  // Both affect movement
}

// Example: Tank in burning grass
// Base speed: 1.0 (grass)
// State speed: 0.7 (burning)
// Total: 0.7 = 30% slower movement
```

### Damage from Fire
```java
public class ServerGameMap {
    
    /**
     * Check if entity is in fire and apply damage
     */
    public void applyFireDamage(Entity entity, long deltaTime) {
        int tileX = (int) (entity.getX() / getTileSize());
        int tileY = (int) (entity.getY() / getTileSize());
        
        TerrainTile tile = getTile(tileX, tileY);
        
        if (tile.getCurrentState() == TerrainState.BURNING) {
            // Apply damage over time
            float damagePerSecond = 5.0f;  // 5 HP per second in fire
            float damage = damagePerSecond * (deltaTime / 1000.0f);
            entity.takeDamage(damage);
        }
    }
}
```

### Strategic Fire Usage

1. **Area Denial**: Set fire to block enemy paths
2. **Smoke Cover**: Burning forests create smoke (reduce visibility)
3. **Destroy Cover**: Burn down forests to remove enemy hiding spots
4. **Environmental Hazard**: Explosions near forests = massive fires

## Network Protocol

### Messages

```java
// Server → Client: Terrain state change
public class TerrainStateChangeMessage {
    private int tileX;
    private int tileY;
    private TerrainState newState;
    private long timestamp;
}

// Optional: Batch multiple changes
public class BatchTerrainStateChangeMessage {
    private List<TileStateChange> changes;
    
    public static class TileStateChange {
        int x, y;
        TerrainState state;
    }
}
```

### Bandwidth Considerations

- **Fire spreads slowly**: ~1 tile every 0.5-1 second
- **Small messages**: ~8 bytes per tile (x, y, state enum)
- **Typical scenario**: 10-20 burning tiles = 160 bytes/sec
- **Compression**: Run-length encoding for large fires

## Visual Effects Hierarchy

```
Rendering Order (bottom to top):
1. Base terrain texture (grass, dirt, stone)
2. Scorched overlay (permanent darkening)
3. Entities (tanks, bullets)
4. Fire effects (animated flames using your FlameEffect)
5. Smoke particles (rising from burning/smoldering tiles)
6. Explosion effects (your ExplosionEffect)
7. UI/HUD
```

## Configuration & Balancing

### Fire Tuning Parameters
```java
public class FireConfig {
    // How long terrain types burn
    public static final long GRASS_BURN_DURATION = 5000L;      // 5 seconds
    public static final long FOREST_BURN_DURATION = 15000L;    // 15 seconds
    public static final long DRY_GRASS_BURN_DURATION = 3000L;  // 3 seconds
    
    // Spread rates
    public static final float GRASS_SPREAD_CHANCE = 0.15f;     // 15% per tick
    public static final float FOREST_SPREAD_CHANCE = 0.35f;    // 35% per tick
    
    // Damage
    public static final float FIRE_DAMAGE_PER_SECOND = 5.0f;
    
    // Explosion ignition
    public static final float EXPLOSION_IGNITION_RADIUS = 2.5f; // Tiles
}
```

### Balance Considerations

- **Too much fire spread**: Game becomes unplayable, entire map burns
- **Too little fire spread**: Fire feels pointless, no tactical value
- **Sweet spot**: Fire spreads 3-5 tiles from explosion center over 10-15 seconds

## Implementation Phases

### Phase 1: Basic Fire State (Start Here)
1. Convert `TileType` to `TerrainTile` class with states
2. Add `TerrainState` enum (NORMAL, BURNING, SCORCHED)
3. Make explosions set tiles to BURNING state
4. After burn duration, transition to SCORCHED
5. Render scorched overlay texture
6. **No spread yet** - just direct explosion damage

### Phase 2: Fire Spreading
1. Implement `FireManager` class
2. Add flammability to terrain types
3. Implement fire spread algorithm
4. Add network synchronization
5. Test with controlled burns

### Phase 3: Visual Polish
1. Integrate with existing `FlameEffect` for burning tiles
2. Add smoke particles rising from burning tiles
3. Animated transition from flames to scorched earth
4. Sound effects (crackling fire, whoosh when igniting)

### Phase 4: Gameplay Integration
1. Fire slows movement (apply state speed modifier)
2. Fire damages entities standing in it
3. Optionally: Reduce vision through smoke
4. Balance fire spread rates

### Phase 5: Advanced Features
1. Wind system (fire spreads faster downwind)
2. Water extinguishes fire
3. Fire-resistant buildings
4. Fog of war interacts with smoke

## Example Scenarios

### Scenario 1: Grassland Explosion
```
Before:            After 5 sec:       After 15 sec:
GGGGGGGGG          GGGGGGGGG          GGGGGGGGG
GGGGGGGGG          GGFBFGGGG          GGBBBGGGG
GGGGGGGGG    →     GFBBBFGGG    →     GBSSBGGG
GGGXGGGGG          GGFBFGGGG          GGBBBGGGG
GGGGGGGGG          GGGGGGGGG          GGGGGGGGG

G=Grass, X=Explosion, F=Igniting, B=Burning, S=Scorched
```

### Scenario 2: Forest Fire
```
Before:            After 10 sec:       After 30 sec:
TTTTTTTTTT         TTTTTTTTTT          DDDDDDDDDD
TTTTTTTTTT         TTFBBFTTTT          DDBSSBDDDD
TTTTTTTTTT   →     TFBBBBTTT     →     DBSSSSBDDD
TTTTXTTTTT         TTFBBFTTTT          DDBSSBDDDD
TTTTTTTTTT         TTTTTTTTTT          DDDDDDDDDD

T=Trees, X=Explosion, F=Igniting, B=Burning, S=Scorched, D=Dirt (cleared land)
```

### Scenario 3: Mixed Terrain (Fire Containment)
```
GGGGGGWWWWW
GGGGGWWWWWW     Fire spreads through grass
GGGGXWWWWWW  →  but stops at water
GGGGGWWWWWW     
GGGGGGWWWWW

G=Grass, W=Water, X=Explosion
Water acts as natural firebreak!
```

## Performance Optimization

### Server-Side
- **Update frequency**: 500ms intervals (not every frame)
- **Spatial partitioning**: Only check tiles near existing fires
- **Max burning tiles**: Limit to 200-300 active fires
- **Auto-extinguish**: Old fires die out to prevent runaway spread

### Client-Side
- **Effect pooling**: Reuse FlameEffect instances
- **LOD**: Distant fires use simpler animations
- **Culling**: Only render fire effects in camera view
- **Batching**: Group fire quads into single draw call

## Testing Checklist

- [ ] Single tile ignites and burns out correctly
- [ ] Fire spreads to adjacent flammable tiles
- [ ] Fire doesn't spread to water/stone/dirt
- [ ] Explosions ignite multiple tiles
- [ ] Burned tiles show scorched texture
- [ ] Network sync: all clients see same fires
- [ ] Performance: 50+ burning tiles at 60 FPS
- [ ] Balance: Fire feels impactful but not overwhelming

## Summary

**YES!** Your terrain system can absolutely support fire propagation:

1. **TerrainTile class**: Holds base type + current state
2. **TerrainState enum**: NORMAL, BURNING, SCORCHED, etc.
3. **FireManager**: Server-side fire spread logic
4. **Network sync**: Broadcast state changes to clients
5. **Reuse effects**: Your existing FlameEffect for fire visuals
6. **Gameplay impact**: Movement speed, damage, vision

The key insight: **Separate visual effects from terrain state**. Your FlameEffect handles the pretty fire animation, while TerrainState handles the gameplay logic (spreading, damage, movement penalties).

Start with Phase 1 (explosions → burning → scorched), then add spreading in Phase 2. This gives you quick visible progress while keeping complexity manageable.
