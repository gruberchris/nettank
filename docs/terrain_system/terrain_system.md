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

## Terrain Types

### TerrainType Properties

| Type | Speed | Passable | Blocks Bullets | Blocks Vision | Destructible | Flammable |
|------|-------|----------|----------------|---------------|--------------|-----------|
| GRASS | 100% | ✅ | ❌ | ❌ | ❌ | Yes (5s) |
| DIRT | 95% | ✅ | ❌ | ❌ | ❌ | ❌ |
| MUD | 60% | ✅ | ❌ | ❌ | ❌ | ❌ |
| SAND | 85% | ✅ | ❌ | ❌ | ❌ | ❌ |
| STONE | 100% | ✅ | ❌ | ❌ | ❌ | ❌ |
| SHALLOW_WATER | 40% | ❌ | ❌ | ❌ | ❌ | ❌ |
| DEEP_WATER | 0% | ❌ | ❌ | ❌ | ❌ | ❌ |
| FOREST | 70% | ❌ | ✅ | Partial | ✅ | Yes (15s) |
| MOUNTAIN | 0% | ❌ | ❌ | Full | ❌ | ❌ |

### Terrain States

Dynamic states that affect tiles:

- **NORMAL**: Default state
- **IGNITING**: Just caught fire (2s)
- **BURNING**: Actively burning (speed penalty, visual effects)
- **SMOLDERING**: Dying out (3s)
- **SCORCHED**: Permanently burned (darkened texture overlay)

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

```
Bottom to Top:
1. Base terrain texture (opaque)
2. Visual overlay texture (transparent, non-interactable)
3. Data overlay entities (trees with collision)
4. State overlay (scorched marks, fire effects)
5. Entities (tanks, bullets)
6. Visual effects (explosions, smoke)
```

### Example: Forest on Grass

```
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

## Dynamic Terrain (Fire System)

### Fire Propagation

When explosions occur:

1. **Ignition**: Flammable terrain within explosion radius catches fire
2. **Burning**: Fire progresses through states (IGNITING → BURNING → SMOLDERING)
3. **Scorched**: Burned terrain becomes permanently darkened
4. **Optional**: Fire can spread to adjacent flammable tiles (future)

### Fire Manager (Server)

```java
public class FireManager {
    public void onExplosion(Vector2f position, float radius);
    public void update(long currentTime);
    // Handles fire state transitions and network sync
}
```

### Usage

```java
// Server game loop
fireManager.update(System.currentTimeMillis());

// When explosion occurs
fireManager.onExplosion(explosionPos, explosionRadius);
```

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

### Current Implementation

**Temporary**: Fixed seed (12345) on both server and client
- Ensures identical map generation
- No network sync needed yet

**Future**: Server sends seed to clients
```java
// Server generates seed
long seed = System.currentTimeMillis();

// Send to clients in join message
joinResponse.setMapSeed(seed);

// Client uses server's seed
terrainGenerator.generate(serverSeed, profile);
```

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

## Future Enhancements

### Phase 1: Complete ✅
- Three-layer terrain system
- Procedural generation with noise
- Single contiguous overlay regions
- Base terrain profiles
- Fire system with state transitions
- Collision detection for overlays

### Phase 2: Planned
- Network seed synchronization
- Fire spreading between tiles
- Destructible terrain (shoot trees to destroy)
- Terrain regeneration over time

### Phase 3: Planned
- Line of sight calculations
- Fog of war integration with vision blocking
- Advanced fire effects (smoke, embers)
- Weather effects (rain extinguishes fire)

### Phase 4: Planned
- Buildings as destructible entities
- Multiple overlay layers
- Roads/paths as visual overlays
- Height maps for true 3D terrain

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
