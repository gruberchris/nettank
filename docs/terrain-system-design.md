# Terrain System Design for NetTank

## Overview
This document outlines the design for implementing a textured terrain system with geographic obstacles and destructible buildings for NetTank, a 2D top-down multiplayer tank game.

## Current Architecture Analysis

### Existing Components
- **GameMapData** (common): Holds map dimensions, tile size, boundary checks, and spawn points
- **ClientGameMap** (client): Handles rendering with simple GRASS/DIRT tiles and fog of war
- **Entity** (common): Base class for all game objects with position, collision, and physics

### Current Limitations
- Only 2 tile types (GRASS, DIRT)
- No terrain movement modifiers
- No obstacles or destructible buildings
- No terrain-based gameplay mechanics

## Proposed Architecture: Multi-Layer Terrain System

### 1. Data Layer (Server & Common)

The data layer defines terrain properties that affect gameplay. This is the **authoritative** source of truth.

#### TerrainType Enum (common)
```java
public enum TerrainType {
    GRASS(1.0f, true),           // Normal speed, passable
    DIRT(0.95f, true),           // Slightly slower
    MUD(0.6f, true),             // Significantly slower
    SHALLOW_WATER(0.4f, true),   // Very slow, splash effects
    DEEP_WATER(0.0f, false),     // Impassable
    SAND(0.85f, true),           // Slightly slower
    STONE(1.0f, true),           // Normal speed
    MOUNTAIN(0.0f, false);       // Impassable
    
    private final float speedModifier;  // Movement speed multiplier
    private final boolean passable;     // Can entities move through?
    
    TerrainType(float speedModifier, boolean passable) {
        this.speedModifier = speedModifier;
        this.passable = passable;
    }
    
    public float getSpeedModifier() { return speedModifier; }
    public boolean isPassable() { return passable; }
}
```

#### ServerGameMap (server)
```java
public class ServerGameMap {
    private final GameMapData mapData;
    private final TerrainType[][] terrainGrid;  // Data layer: terrain types
    private final List<Obstacle> obstacles;      // Hills, rocks, trees
    private final List<Building> buildings;      // Destructible structures
    
    // Query methods
    public TerrainType getTerrainAt(float worldX, float worldY);
    public float getSpeedModifierAt(float worldX, float worldY);
    public boolean isPassableAt(float worldX, float worldY);
    public List<Building> getBuildingsInArea(float x, float y, float radius);
}
```

### 2. Visual Layer (Client)

The visual layer renders the terrain with textures and blending. It queries the data layer for what to draw.

#### ClientGameMap Enhancement
```java
public class ClientGameMap {
    private final GameMapData mapData;
    private final TerrainType[][] terrainGrid;   // Synced from server
    private final Map<TerrainType, Texture> terrainTextures;
    private final List<ClientObstacle> obstacles;
    private final List<ClientBuilding> buildings;
    
    // Render different layers
    public void renderTerrain(Renderer renderer, Camera camera);
    public void renderObstacles(Renderer renderer, Camera camera);
    public void renderBuildings(Renderer renderer, Camera camera);
}
```

#### Texture Strategy
Similar to Age of Empires, use:
- **Base tiles**: 32x32 or 64x64 textures for each terrain type
- **Transition tiles**: Blended edges between different terrain types
- **Tile variants**: 3-4 variations of each type to avoid repetition
- **Decorations**: Grass tufts, pebbles, etc. for visual variety

### 3. Obstacle System

#### Obstacle Types

**Geographic Obstacles** (static, affect movement):
- **Hills/Mountains**: Impassable elevation
- **Rocks**: Large boulders that block movement
- **Trees/Forests**: Slow movement or provide cover
- **Water Bodies**: Rivers (linear), Lakes (area)

**Implementation**:
```java
public abstract class Obstacle {
    protected Vector2f position;
    protected float width, height;
    protected Collider collider;
    protected ObstacleType type;
    
    public enum ObstacleType {
        ROCK, TREE, HILL, WATER_FEATURE
    }
    
    // For rendering and collision
    public abstract boolean blocksMovement();
    public abstract float getSpeedModifier(); // If partially passable
}
```

### 4. Building System (Destructible)

Buildings are dynamic entities that can be damaged and destroyed.

```java
public class Building extends Entity {
    private int maxHealth;
    private int currentHealth;
    private BuildingType type;
    private boolean destroyed;
    
    public enum BuildingType {
        HOUSE(100),
        TOWER(200),
        WALL(150),
        BARRACKS(300);
        
        private final int health;
        BuildingType(int health) { this.health = health; }
    }
    
    public void takeDamage(int damage);
    public boolean isDestroyed();
    public float getHealthPercentage();
}
```

#### Building Features
- **Health system**: Track damage, send updates to clients
- **Destruction states**: Show visual damage (intact → damaged → rubble)
- **Collision**: Buildings block movement and projectiles
- **Rubble**: Destroyed buildings leave passable rubble

## Implementation Strategy

### Phase 1: Enhanced Data Layer
1. Create `TerrainType` enum in common module
2. Replace `TileType` with `TerrainType` in both client and server
3. Add speed modifier queries to movement code
4. Implement terrain generation algorithms (Perlin noise, cellular automata)

### Phase 2: Visual Enhancement
1. Create terrain texture atlas with multiple variants
2. Implement tile blending for smooth transitions
3. Add texture variation system to reduce repetition
4. Enhance rendering with decorative overlays

### Phase 3: Obstacle System
1. Create `Obstacle` base class hierarchy
2. Implement collision detection with obstacles
3. Add obstacle rendering (static sprites)
4. Create map editor or procedural generation for obstacles

### Phase 4: Building System
1. Create `Building` entity class
2. Implement health/damage system
3. Add destruction animations and state transitions
4. Network synchronization for building state
5. Add rubble/debris rendering

### Phase 5: Gameplay Integration
1. Apply terrain speed modifiers to tank movement
2. Implement projectile collision with buildings
3. Add tactical elements (cover, line of sight)
4. Balance terrain effects with gameplay

## Map Generation Approaches

### Option A: Procedural Generation
Use algorithms like:
- **Perlin/Simplex Noise**: Organic terrain patterns (hills, water)
- **Cellular Automata**: Cave-like structures, forests
- **Voronoi Diagrams**: Natural-looking regions

### Option B: Map Editor
Create a tool to:
- Paint terrain types
- Place obstacles and buildings
- Save/load map files (JSON or binary)

### Option C: Hybrid
- Procedurally generate base terrain
- Manually place strategic buildings/obstacles
- Save final maps for multiplayer consistency

## Technical Considerations

### Network Synchronization
- **Initial sync**: Send complete terrain grid when player joins
- **Obstacles**: Send once (static)
- **Buildings**: Sync health changes, destruction events
- **Compression**: Use run-length encoding for terrain data

### Performance Optimization
- **Culling**: Only render tiles in camera view (already implemented)
- **Texture atlasing**: Combine textures to reduce draw calls
- **Instancing**: Render similar tiles in batches
- **LOD**: Use simpler textures for distant tiles (optional)

### Collision Detection
- **Grid-based**: Fast lookups for terrain passability
- **Spatial partitioning**: QuadTree for obstacles/buildings
- **Layered checks**: 
  1. Terrain type (grid lookup)
  2. Obstacles (spatial query)
  3. Buildings (entity collision)

## Example Terrain Layouts

### Open Field Battle
```
GGGGGGGGGGGGGGGG
GGGGMMMGGGGGGGG
GGMMMMMMMMGGGG
GGGMMMMMGGGGGG
GGGGGGWWWGGGGG    G=Grass, M=Mud, W=Water
GGGGGWWWWWGGGG    B=Building
GGBGGGWWGGGGGG
GGGGGGGGGBGGGGG
```

### Urban Combat
```
SSSSSSSSSSSSSSS
SBBBSSSBBBSSSS
SBBBSSSBBBSSSS    S=Stone/Road
SSSSSSSSSSSSSS    B=Building
SBBBGGGGBBBSSS    G=Grass courtyard
SBBBGGGGBBBSSS
SSSSSSSSSSSSSS
```

### Mixed Terrain
```
MMMMMGGGGGSSSSS
MMMWWWGGGSSSSS
MMWWWWGGGSSSSS    M=Mountain, W=Water
TTWWGGGGGSSSSS    T=Trees, G=Grass
TTTGGGGGGSSSBS    S=Sand, B=Building
TTGGGGGGGGSSSS
```

## Rendering Order
1. **Base terrain tiles** (with fog of war tinting)
2. **Ground decorations** (grass, pebbles)
3. **Obstacles** (rocks, trees)
4. **Buildings** (with damage states)
5. **Entities** (tanks, projectiles)
6. **Effects** (explosions, smoke)
7. **UI overlays** (health bars, indicators)

## Recommended First Steps

1. **Create TerrainType enum** with properties
2. **Modify GameMapData** to use terrain grid
3. **Update movement physics** to respect speed modifiers
4. **Add basic textures** for 4-5 terrain types
5. **Test** with simple generated map
6. **Iterate** based on gameplay feel

## Files to Create/Modify

### New Files
- `nettank-common/src/main/java/org/chrisgruber/nettank/common/world/TerrainType.java`
- `nettank-common/src/main/java/org/chrisgruber/nettank/common/world/Obstacle.java`
- `nettank-common/src/main/java/org/chrisgruber/nettank/common/entities/Building.java`
- `nettank-server/src/main/java/org/chrisgruber/nettank/server/world/ServerGameMap.java`
- `nettank-server/src/main/java/org/chrisgruber/nettank/server/world/TerrainGenerator.java`
- `nettank-client/src/main/java/org/chrisgruber/nettank/client/game/world/TextureManager.java`

### Modified Files
- `nettank-common/src/main/java/org/chrisgruber/nettank/common/world/GameMapData.java`
- `nettank-client/src/main/java/org/chrisgruber/nettank/client/game/world/ClientGameMap.java`
- Movement/physics code (to apply speed modifiers)
- Network protocol (to sync terrain and buildings)

## Conclusion

Your intuition about having both a **visual layer** (textures) and a **data layer** (terrain properties) is absolutely correct! This separation is crucial for:
- **Multiplayer consistency**: Server authoritative data
- **Performance**: Client optimizes rendering independently
- **Flexibility**: Change visuals without affecting gameplay
- **Scalability**: Easy to add new terrain types

The key is that the server owns the gameplay-affecting data (movement modifiers, passability), while the client owns the rendering details (textures, animations, effects). The Age of Empires approach is perfect for your 2D top-down game!
