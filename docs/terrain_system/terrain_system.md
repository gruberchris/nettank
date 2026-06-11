# Terrain System - Complete Reference

## Overview

NetTank uses a **three-layer terrain system** for flexible, dynamic gameplay:

1. **Base Visual Layer**: Opaque terrain textures covering the entire map
2. **Visual Overlay Layer**: Transparent terrain/entities placed on top
3. **Data Overlay Layer**: Collision and gameplay properties

This architecture enables procedurally generated maps with natural-looking features, destructible terrain, and dynamic effects like fire.

## Architecture

### TerrainTile Structure (3D Array)

```java
TerrainTile[][][] terrainGrid = new TerrainTile[width][height][3];

// Layer 0: Base Visual - Always present, opaque texture
// Layer 1: Visual Overlay - Optional, transparent texture (trees, water effects)
// Layer 2: Data Overlay - Collision and gameplay properties
```

### TerrainTile Class

```java
public class TerrainTile {
    private TerrainType baseType;      // Base terrain (GRASS, DIRT, etc.)
    private TerrainType overlayType;   // Optional overlay (FOREST, WATER, etc.)
    private TerrainState currentState; // Dynamic state (NORMAL, BURNING, SCORCHED)

    // Properties derived from effective type (overlay if present, else base)
    public float getEffectiveSpeedModifier();
    public boolean isPassable();
    public boolean blocksVision();
    public boolean blocksBullets();
}
```

## Terrain Types ✅

### TerrainType Properties (Implemented)

| Type | Speed | Passable | Blocks Bullets | Blocks Vision | Destructible | Flammability | Burn Duration |
|------|-------|----------|----------------|---------------|--------------|--------------|---------------|
| GRASS | 100% | ✅ | ❌ | NONE | ❌ | MEDIUM (40%) | 5s |
| DIRT | 95% | ✅ | ❌ | NONE | ❌ | NONE | 0s |
| MUD | 60% | ✅ | ❌ | NONE | ❌ | NONE | 0s |
| SAND | 85% | ✅ | ❌ | NONE | ❌ | NONE | 0s |
| STONE | 100% | ✅ | ❌ | NONE | ❌ | NONE | 0s |
| SHALLOW_WATER | 40% | ❌ | ❌ | NONE | ❌ | NONE | 0s |
| DEEP_WATER | 0% | ❌ | ❌ | NONE | ❌ | NONE | 0s |
| FOREST | 70% | ❌ | ✅ | PARTIAL | ✅ | HIGH (80%) | 15s |
| MOUNTAIN | 0% | ❌ | ❌ | FULL | ❌ | NONE | 0s |

### Terrain States ✅

Dynamic states that affect tiles (fully implemented):

- **NORMAL**: Default state (100% speed modifier)
- **IGNITING**: Just caught fire (0-2s, 100% speed modifier)
- **BURNING**: Actively burning (70% speed modifier, visual fire effects)
- **SMOLDERING**: Dying out (last 3s, 80% speed modifier)
- **SCORCHED**: Permanently burned (90% speed, darkened texture overlay)
- **FLOODED**: Wet terrain, cannot be ignited (prevents fire)

## Procedural Generation

### Algorithm Overview

The terrain system uses **Perlin noise with flood fill post-processing** to create natural-looking maps with single contiguous regions:

1. Generate 2D Perlin noise (values 0.0 to 1.0)
2. Assign terrain types based on value thresholds
3. Find all contiguous regions using flood fill
4. Keep only the largest region of each overlay type
5. Convert scattered patches to base terrain

### Base Terrain Profiles

Profiles define terrain composition:

```java
public enum BaseTerrainProfile {
    GRASSLAND(
        TerrainType.GRASS,         // Base: 85% of map
        TerrainType.SHALLOW_WATER, // Low: 8% (single lake)
        TerrainType.FOREST         // High: 7% (single forest)
    ),

    DESERT(
        TerrainType.SAND,          // Base: 87% of map
        TerrainType.GRASS,         // Low: 5% (oasis)
        TerrainType.FOREST         // High: 8% (mountains)
    ),

    DIRT_PLAINS(
        TerrainType.DIRT,          // Base: 85% of map
        TerrainType.MUD,           // Low: 10% (muddy region)
        TerrainType.FOREST         // High: 5% (rocky area)
    ),

    MUDLANDS(
        TerrainType.MUD,           // Base: 83% of map
        TerrainType.SHALLOW_WATER, // Low: 12% (bog/swamp)
        TerrainType.FOREST         // High: 5% (trees)
    )
}
```

### Noise Parameters

```java
FastNoiseLite noise = new FastNoiseLite();
noise.SetNoiseType(FastNoiseLite.NoiseType.Perlin);
noise.SetFrequency(0.05f);        // Large, smooth features
noise.SetFractalOctaves(3);       // Natural detail
noise.SetFractalType(FastNoiseLite.FractalType.FBm);
```

## Layering System

### Why Three Layers?

**Base Visual Layer**: Opaque terrain everywhere (grass, dirt, sand)
- Always present, covers entire map
- Provides consistent visual foundation

**Visual Overlay Layer**: Transparent entities on top (trees, water effects)
- Optional, can be null
- Shows base terrain underneath
- Can be destroyed/removed
- Used for non-interactable visuals (tank tracks, roads, trails)

**Data Overlay Layer**: Collision and gameplay properties
- Determines actual terrain behavior
- Separate from visuals for flexibility
- Can have collision without blocking vision

### Rendering Order

```text
Bottom to Top:
1. Base terrain texture (opaque)
2. Visual overlay texture (transparent, non-interactable)
3. Data overlay entities (trees with collision)
4. State overlay (scorched marks, fire effects)
5. Entities (tanks, bullets)
6. Visual effects (explosions, smoke)
```

### Example: Forest on Grass

```text
Base Layer:    GRASS texture (green)
Visual Overlay: None (or tank tracks)
Data Overlay:  FOREST (tree entity with collision)
Result:        Tree appears on grass, blocks movement & bullets
```

## Collision System

### Layer Interactions

**Tank Movement**:
- Checks data overlay layer for passability
- Trees (FOREST) block movement
- Water (SHALLOW_WATER) blocks movement
- Base grass allows movement

**Bullet Collisions**:
- LOW range overlays (water): Bullets pass over
- HIGH range overlays (trees): Bullets collide and despawn
- Base terrain: Bullets always pass through

**Spawn Points**:
- Tanks never spawn on overlay terrain
- Only spawn on clear base terrain tiles

### Implementation

```java
// Check if tile is passable for tank
public boolean canTankPass(int tileX, int tileY) {
    TerrainTile tile = getTile(tileX, tileY);
    if (tile.hasOverlay()) {
        TerrainType overlay = tile.getOverlayType();
        return overlay.isPassable();
    }
    return tile.getBaseType().isPassable();
}

// Check if bullet should collide
public boolean bulletCollides(int tileX, int tileY) {
    TerrainTile tile = getTile(tileX, tileY);
    if (tile.hasOverlay()) {
        TerrainType overlay = tile.getOverlayType();
        // High range overlays (trees) block bullets
        return overlay == TerrainType.FOREST;
    }
    return false; // Base terrain never blocks bullets
}
```

## Dynamic Terrain (Fire System) ✅

### Fire Propagation - Currently Implemented

When explosions occur:

1. **Ignition**: Flammable terrain within 2.5 tile radius catches fire
2. **State Progression**: Fire advances through timed states
   - IGNITING (0-2s): Just caught fire
   - BURNING (2s - burn duration - 3s): Active burning phase
   - SMOLDERING (last 3s): Fire dying out
   - SCORCHED (permanent): Burned terrain with darkened texture
3. **Probabilistic Ignition**: Based on terrain Flammability enum
   - GRASS: 40% chance (5s burn duration)
   - FOREST: 80% chance (15s burn duration)
4. **Speed Penalties**: Burning and smoldering states apply movement speed reductions
5. **Water Protection**: FLOODED state prevents ignition

### Fire Manager (Server) - Implemented

```java
public class FireManager {
    // Trigger fire on explosion
    public void onExplosion(Vector2f position, float radius);

    // Update all burning tiles, handle state transitions
    public void update(long currentTime);

    // Try to ignite a specific tile
    public boolean attemptIgnition(int tileX, int tileY, float chanceMultiplier);

    // Get all currently burning tiles for network sync
    public List<TileStateChange> getBurningTiles();
}
```

### Usage in Server Game Loop

```java
// Initialize with game map
FireManager fireManager = new FireManager(gameMapData);

// Update each frame
fireManager.update(System.currentTimeMillis());

// Trigger on explosions
fireManager.onExplosion(explosionPos, explosionRadius);
```

### Not Yet Implemented
- ❌ Fire spreading to adjacent flammable tiles
- ❌ Wind direction affecting spread
- ❌ Rain/weather extinguishing fire

## Texture Requirements

### Required Textures (32x32 pixels)

**Base Terrain (Opaque)**:
- `Summer_Grass.png` - Green grass
- `Dirt_Field.png` - Brown dirt
- `Mud_Field.png` - Dark muddy ground
- `Desert.png` - Sandy terrain

**Overlay Terrain (Transparent)**:
- `Shallow_Water.png` - Blue water (with alpha channel)
- `Summer_Tree.png` - Tree/forest (transparent background)

**State Overlays**:
- `Scorched.png` - Burned/charred effect

### Transparency Guidelines

Overlay textures MUST have:
- Transparent backgrounds (alpha channel)
- Semi-transparent edges for blending
- PNG format with proper alpha

## Server-Client Synchronization

### Current Implementation ✅

**Full Terrain Data Sync**: Server generates terrain and sends complete data to clients
- Server generates terrain with ProceduralTerrainGenerator using a seed
- Terrain is encoded using TerrainEncoder (compact binary format)
- Full terrain data sent to clients on join via TERRAIN_DATA message
- Terrain regenerated and broadcast to all clients between rounds
- Clients decode terrain data using TerrainDecoder

```java
// Server: Generate terrain
ProceduralTerrainGenerator generator = new ProceduralTerrainGenerator(seed);
generator.generateProceduralTerrain(gameMapData, BaseTerrainProfile.GRASSLAND);

// Server: Encode and send to client
String encodedTerrain = TerrainEncoder.encode(gameMapData);
handler.sendMessage(String.format("%s;%d;%d;%s",
    NetworkProtocol.TERRAIN_DATA,
    mapWidth, mapHeight,
    encodedTerrain));

// Client: Decode received terrain
TerrainEncoder.decode(mapData, encodedTerrainData);
```

### Dynamic State Updates

Fire and terrain state changes are synced separately:
- Server FireManager tracks burning tiles
- State changes broadcast to clients as they occur
- Clients update local terrain state without regenerating entire map

## Performance

### Generation Speed
- 100x100 map: ~10-20ms
- Noise generation: ~5ms
- Flood fill: ~4ms per overlay type
- Total: <20ms (fast enough for runtime generation)

### Memory Usage
- Terrain grid: 16 bytes per tile
- Overlay data: Nullable (no memory if unused)
- 100x100 map: ~160 KB

## Integration Guide

### Server Setup

```java
// 1. Create terrain generator
ProceduralTerrainGenerator generator = new ProceduralTerrainGenerator();

// 2. Generate terrain with profile
generator.generateProceduralTerrain(
    gameMapData,
    BaseTerrainProfile.GRASSLAND
);

// 3. Create fire manager
FireManager fireManager = new FireManager(gameMapData);

// 4. Update in game loop
fireManager.update(currentTime);
```

### Client Setup

```java
// 1. Generate matching terrain
proceduralGenerator.generateProceduralTerrain(
    gameMapData,
    BaseTerrainProfile.GRASSLAND
);

// 2. Load textures
clientGameMap.registerTerrainTexture(TerrainType.GRASS, grassTex);
clientGameMap.registerTerrainTexture(TerrainType.SHALLOW_WATER, waterTex);
clientGameMap.registerTerrainTexture(TerrainType.FOREST, treeTex);

// 3. Register state overlays
clientGameMap.registerStateOverlayTexture(TerrainState.SCORCHED, scorchedTex);
```

## Implementation Status & Future Enhancements

### Phase 1: Complete ✅
- ✅ Three-layer terrain system (Base Visual, Visual Overlay, Data Overlay)
- ✅ Procedural generation with Perlin noise
- ✅ Single contiguous overlay regions (flood fill algorithm)
- ✅ Base terrain profiles (GRASSLAND, DESERT, DIRT_PLAINS, MUDLANDS)
- ✅ Fire system with state transitions (IGNITING → BURNING → SMOLDERING → SCORCHED)
- ✅ Collision detection for overlays (tanks and bullets)
- ✅ TerrainTile with state management
- ✅ FireManager with explosion-triggered ignition
- ✅ Flammable terrain types (GRASS, FOREST)
- ✅ Speed modifiers affected by terrain state

### Phase 2: Complete ✅
- ✅ Network terrain synchronization (TerrainEncoder/Decoder)
- ✅ Server sends full terrain data to clients on join
- ✅ Terrain regeneration between rounds
- ✅ Broadcast terrain updates to all clients
- ✅ Vision blocking types defined (NONE, PARTIAL, FULL)
- ✅ Destructible property on terrain types

### Phase 3: Planned 🔄
- ❌ Fire spreading between adjacent tiles
- ❌ Destructible terrain implementation (shoot trees to destroy)
- ❌ Line of sight calculations using vision blocking
- ❌ Fog of war integration with vision blocking
- ❌ Advanced fire effects (smoke particles, embers)
- ❌ Weather effects (rain extinguishes fire)

### Phase 4: Planned 📋
- ❌ Buildings as destructible entities
- ❌ Multiple visual overlay layers
- ❌ Roads/paths as visual overlays (tank tracks)
- ❌ Height maps for elevation-based gameplay
- ❌ Terrain deformation (craters from explosions)

## Troubleshooting

**Problem**: Overlay textures appear solid, not transparent
**Solution**: Ensure PNG files have alpha channel, export with transparency enabled

**Problem**: Bullets pass through trees
**Solution**: Check that FOREST type has `blocksBullets = true` in TerrainType enum

**Problem**: Tanks spawn inside trees/water
**Solution**: Spawn point selection must check for overlay terrain and avoid it

**Problem**: Multiple scattered regions instead of single contiguous
**Solution**: Verify flood fill algorithm is running in ProceduralTerrainGenerator

## Related Documentation

- `terrain_quick_reference.md` - Quick setup and usage guide
- `terrain_layering_system.md` - Detailed layering architecture
- `terrain_system_design.md` - Original design document
- `dynamic_terrain_system.md` - Fire system details
- `contiguous_terrain_generation.md` - Procedural generation algorithm
- `procedural_generation_implementation.md` - Implementation details
- `round_based_regeneration.md` - Round-based terrain regeneration
- `line_of_sight_system.md` - Vision blocking system (future)
