# Line of Sight (LOS) System Design

## Overview
Extend the current fog of war system to support vision blocking by terrain features, obstacles, and tall buildings. This creates tactical gameplay where players must navigate around obstacles to see enemies.

## Current Fog of War System

Your current implementation:
```java
// In TankBattleGame.java
public static final float VIEW_RANGE = 400.0f;

// In render()
float range = isSpectating ? Float.MAX_VALUE : VIEW_RANGE;
Vector2f fogCenter = (localTank != null) ? localTank.getPosition() : null;
gameMap.render(renderer, shader, grassTexture, dirtTexture, camera, range, fogCenter);
```

**Current behavior**: Simple radial fog - everything within VIEW_RANGE is visible, everything beyond is dark.

## Enhanced System: Radial + Line of Sight

### Vision Blocking Types

```java
public enum VisionBlockingType {
    NONE(false, 0.0f),              // Normal terrain - doesn't block
    PARTIAL(true, 0.3f),            // Trees/bushes - partial blocking
    FULL(true, 1.0f),               // Mountains, tall buildings - complete blocking
    DESTROYABLE(true, 1.0f);        // Buildings that can be destroyed (opens LOS)
    
    private final boolean blocksVision;
    private final float blockingStrength;  // 0.0 = transparent, 1.0 = opaque
    
    VisionBlockingType(boolean blocksVision, float blockingStrength) {
        this.blocksVision = blocksVision;
        this.blockingStrength = blockingStrength;
    }
    
    public boolean blocksVision() { return blocksVision; }
    public float getBlockingStrength() { return blockingStrength; }
}
```

### Updated TerrainType

```java
public enum TerrainType {
    GRASS(1.0f, true, VisionBlockingType.NONE),
    DIRT(0.95f, true, VisionBlockingType.NONE),
    MUD(0.6f, true, VisionBlockingType.NONE),
    SHALLOW_WATER(0.4f, true, VisionBlockingType.NONE),
    DEEP_WATER(0.0f, false, VisionBlockingType.NONE),
    SAND(0.85f, true, VisionBlockingType.NONE),
    STONE(1.0f, true, VisionBlockingType.NONE),
    FOREST(0.7f, true, VisionBlockingType.PARTIAL),      // New: Trees block vision
    MOUNTAIN(0.0f, false, VisionBlockingType.FULL);      // Blocks vision and movement
    
    private final float speedModifier;
    private final boolean passable;
    private final VisionBlockingType visionBlocking;
    
    TerrainType(float speedModifier, boolean passable, VisionBlockingType visionBlocking) {
        this.speedModifier = speedModifier;
        this.passable = passable;
        this.visionBlocking = visionBlocking;
    }
    
    public VisionBlockingType getVisionBlocking() { return visionBlocking; }
}
```

### Obstacle Vision Blocking

```java
public abstract class Obstacle {
    protected Vector2f position;
    protected float width, height;
    protected Collider collider;
    protected ObstacleType type;
    protected VisionBlockingType visionBlocking;  // How much vision it blocks
    
    public enum ObstacleType {
        ROCK(VisionBlockingType.FULL, 2.0f),          // Large boulder, blocks completely
        TREE(VisionBlockingType.PARTIAL, 1.5f),       // Tree, partial blocking
        BUSH(VisionBlockingType.NONE, 1.0f),          // Bush, no blocking
        HILL(VisionBlockingType.FULL, 3.0f),          // Hill, blocks completely
        TALL_GRASS(VisionBlockingType.NONE, 0.5f);    // Tall grass, no blocking
        
        private final VisionBlockingType visionBlocking;
        private final float height;  // Used for shadow casting
        
        ObstacleType(VisionBlockingType visionBlocking, float height) {
            this.visionBlocking = visionBlocking;
            this.height = height;
        }
    }
    
    public boolean blocksLineOfSight(Vector2f from, Vector2f to) {
        if (!visionBlocking.blocksVision()) return false;
        // Check if line from->to intersects this obstacle's collider
        return collider.intersectsLine(from, to);
    }
}
```

### Building Vision Blocking

```java
public class Building extends Entity {
    private int maxHealth;
    private int currentHealth;
    private BuildingType type;
    private boolean destroyed;
    
    public enum BuildingType {
        HOUSE(100, 2.5f, VisionBlockingType.FULL),
        TOWER(200, 4.0f, VisionBlockingType.FULL),      // Tall, always blocks
        WALL(150, 2.0f, VisionBlockingType.FULL),       // Walls block vision
        BARRACKS(300, 3.0f, VisionBlockingType.FULL),
        FENCE(50, 1.0f, VisionBlockingType.PARTIAL),    // Low, partial blocking
        RUINS(0, 0.5f, VisionBlockingType.NONE);        // Destroyed, no blocking
        
        private final int health;
        private final float height;
        private final VisionBlockingType visionBlocking;
        
        BuildingType(int health, float height, VisionBlockingType visionBlocking) {
            this.health = health;
            this.height = height;
            this.visionBlocking = visionBlocking;
        }
    }
    
    public VisionBlockingType getVisionBlocking() {
        // Destroyed buildings don't block vision (become ruins)
        return destroyed ? VisionBlockingType.NONE : type.visionBlocking;
    }
    
    public boolean blocksLineOfSight(Vector2f from, Vector2f to) {
        if (destroyed) return false;
        if (!type.visionBlocking.blocksVision()) return false;
        return collider.intersectsLine(from, to);
    }
}
```

## Line of Sight Algorithm Options

### Option 1: Ray Casting (Recommended for Start)

Simple and works well for tile-based games.

```java
public class LineOfSightCalculator {
    
    private final GameMapData mapData;
    private final TerrainType[][] terrainGrid;
    private final List<Obstacle> obstacles;
    private final List<Building> buildings;
    
    /**
     * Check if point 'to' is visible from point 'from'
     */
    public boolean hasLineOfSight(Vector2f from, Vector2f to, float maxRange) {
        float distance = from.distance(to);
        if (distance > maxRange) return false;
        if (distance < 0.1f) return true;  // Same position
        
        // Cast ray from 'from' to 'to'
        Vector2f direction = new Vector2f(to).sub(from).normalize();
        float stepSize = mapData.getTileSize() / 2.0f;  // Half tile for accuracy
        int steps = (int) Math.ceil(distance / stepSize);
        
        Vector2f currentPoint = new Vector2f(from);
        
        for (int i = 0; i < steps; i++) {
            currentPoint.add(direction.x * stepSize, direction.y * stepSize);
            
            // Check terrain blocking
            TerrainType terrain = getTerrainAt(currentPoint.x, currentPoint.y);
            if (terrain != null && terrain.getVisionBlocking() == VisionBlockingType.FULL) {
                return false;  // Blocked by terrain
            }
            
            // Check obstacle blocking
            for (Obstacle obstacle : obstacles) {
                if (obstacle.blocksLineOfSight(from, currentPoint)) {
                    return false;  // Blocked by obstacle
                }
            }
            
            // Check building blocking
            for (Building building : buildings) {
                if (building.blocksLineOfSight(from, currentPoint)) {
                    return false;  // Blocked by building
                }
            }
        }
        
        return true;  // Clear line of sight
    }
    
    /**
     * Get fog/shadow strength at a position (0.0 = fully visible, 1.0 = fully dark)
     */
    public float getFogStrengthAt(Vector2f viewerPos, Vector2f targetPos, float viewRange) {
        float distance = viewerPos.distance(targetPos);
        
        // Beyond range = full fog
        if (distance > viewRange) {
            return 1.0f;
        }
        
        // Check line of sight
        if (!hasLineOfSight(viewerPos, targetPos, viewRange)) {
            return 0.85f;  // Blocked = mostly dark (not fully black for aesthetics)
        }
        
        // Within range and visible = apply distance fade
        float fadeStart = viewRange - (3.5f * mapData.getTileSize());
        if (distance > fadeStart) {
            float fadeProgress = (distance - fadeStart) / (3.5f * mapData.getTileSize());
            return fadeProgress * 0.85f;  // Fade from visible to fog
        }
        
        return 0.0f;  // Fully visible
    }
}
```

### Option 2: Shadow Casting (More Advanced)

Creates realistic shadows from obstacles. More CPU intensive but looks better.

```java
public class ShadowCastingLOS {
    
    /**
     * Calculate shadow regions cast by obstacles
     * Returns a set of "shadow tiles" that are blocked from view
     */
    public Set<Vector2i> calculateShadowTiles(Vector2f viewerPos, 
                                               float viewRange,
                                               List<Obstacle> obstacles,
                                               List<Building> buildings) {
        Set<Vector2i> shadowTiles = new HashSet<>();
        
        // For each blocking object
        for (Obstacle obstacle : obstacles) {
            if (!obstacle.getVisionBlocking().blocksVision()) continue;
            
            // Calculate shadow polygon behind obstacle
            List<Vector2f> shadowPolygon = calculateShadowPolygon(
                viewerPos, obstacle.getPosition(), obstacle.getWidth(), 
                obstacle.getHeight(), viewRange
            );
            
            // Rasterize shadow polygon to tiles
            shadowTiles.addAll(rasterizePolygon(shadowPolygon));
        }
        
        for (Building building : buildings) {
            if (!building.blocksLineOfSight(viewerPos, building.getPosition())) continue;
            
            List<Vector2f> shadowPolygon = calculateShadowPolygon(
                viewerPos, building.getPosition(), building.getWidth(),
                building.getHeight(), viewRange
            );
            
            shadowTiles.addAll(rasterizePolygon(shadowPolygon));
        }
        
        return shadowTiles;
    }
    
    private List<Vector2f> calculateShadowPolygon(Vector2f light, Vector2f objectPos,
                                                    float objectWidth, float objectHeight,
                                                    float maxDistance) {
        // Calculate the 4 corners of the object
        float halfW = objectWidth / 2.0f;
        float halfH = objectHeight / 2.0f;
        
        Vector2f[] corners = {
            new Vector2f(objectPos.x - halfW, objectPos.y - halfH),
            new Vector2f(objectPos.x + halfW, objectPos.y - halfH),
            new Vector2f(objectPos.x + halfW, objectPos.y + halfH),
            new Vector2f(objectPos.x - halfW, objectPos.y + halfH)
        };
        
        // Project each corner away from light source
        List<Vector2f> shadowPolygon = new ArrayList<>();
        for (Vector2f corner : corners) {
            Vector2f direction = new Vector2f(corner).sub(light).normalize();
            Vector2f farPoint = new Vector2f(corner).add(
                direction.x * maxDistance, 
                direction.y * maxDistance
            );
            shadowPolygon.add(corner);
            shadowPolygon.add(farPoint);
        }
        
        return shadowPolygon;
    }
}
```

### Option 3: Visibility Graph (Most Efficient for Many Objects)

Pre-compute visibility relationships, used in RTS games.

```java
public class VisibilityGraph {
    // Pre-computed for static obstacles
    private Map<Vector2i, Set<Vector2i>> visibilityMap;
    
    /**
     * Build visibility graph for static obstacles
     * Call once during map initialization
     */
    public void buildGraph(TerrainType[][] terrain, List<Obstacle> staticObstacles) {
        // Pre-compute which tiles can see which other tiles
        // Expensive one-time operation, but fast lookups during gameplay
    }
    
    /**
     * Fast lookup: is tile A visible from tile B?
     */
    public boolean isVisible(Vector2i tileA, Vector2i tileB) {
        return visibilityMap.get(tileA).contains(tileB);
    }
    
    /**
     * Update graph when buildings are destroyed (opens new LOS)
     */
    public void onBuildingDestroyed(Building building) {
        // Recalculate visibility for affected tiles
    }
}
```

## Integration with Current Rendering

### Enhanced ClientGameMap.render()

```java
public void render(Renderer renderer, Shader shader, 
                   Texture grassTexture, Texture dirtTexture,
                   Camera camera, float viewRange, Vector2f fogCenter,
                   LineOfSightCalculator losCalculator) {  // New parameter
    
    shader.bind();
    
    final float tileSize = GameMapData.DEFAULT_TILE_SIZE;
    boolean isSpectating = (viewRange == Float.MAX_VALUE);
    
    // ... camera bounds calculation ...
    
    for (int y = startY; y < endY; y++) {
        for (int x = startX; x < endX; x++) {
            float tileCenterX = (x + 0.5f) * tileSize;
            float tileCenterY = (y + 0.5f) * tileSize;
            Vector2f tileCenter = new Vector2f(tileCenterX, tileCenterY);
            
            float tint = 1.0f;
            
            if (!isSpectating && fogCenter != null) {
                if (losCalculator != null) {
                    // NEW: Use LOS-aware fog calculation
                    tint = 1.0f - losCalculator.getFogStrengthAt(fogCenter, tileCenter, viewRange);
                } else {
                    // OLD: Simple radial fog (fallback)
                    float dist = fogCenter.distance(tileCenterX, tileCenterY);
                    if (dist > viewRange) {
                        tint = FOG_DARKNESS;
                    } else if (dist > viewRange - (FOG_FADE_DISTANCE * tileSize)) {
                        float fadeProgress = (dist - (viewRange - FOG_FADE_DISTANCE * tileSize)) 
                                           / (FOG_FADE_DISTANCE * tileSize);
                        tint = 1.0f - (fadeProgress * (1.0f - FOG_DARKNESS));
                    }
                }
            }
            
            // Render tile with calculated tint
            TileType type = tiles[x][y];
            Texture texture = (type == TileType.GRASS) ? grassTexture : dirtTexture;
            if (texture != null && tint > 0.01f) {
                texture.bind();
                shader.setUniform3f("u_tintColor", tint, tint, tint);
                renderer.drawQuad(tileCenterX, tileCenterY, tileSize, tileSize, 0, shader);
            }
        }
    }
}
```

### Rendering Entities with LOS

```java
// In TankBattleGame.render()
for (ClientTank tank : tanks.values()) {
    if (tank == localTank) {
        tank.render(renderer, shader, tankTexture);  // Always render local player
    } else {
        Vector2f tankPos = tank.getPosition();
        
        // Check if tank is visible
        boolean visible = isSpectating || 
            (localTank != null && 
             losCalculator.hasLineOfSight(localTank.getPosition(), tankPos, VIEW_RANGE));
        
        if (visible) {
            tank.render(renderer, shader, tankTexture);
        }
    }
}
```

## Performance Considerations

### Optimization Strategies

1. **Spatial Partitioning**: Use QuadTree for obstacles/buildings
   - Only check nearby objects for LOS blocking
   - ~O(log n) instead of O(n) for large maps

2. **Tile-Based Caching**: Cache LOS results per tile
   - Recalculate only when buildings destroyed or camera moves significantly
   - Update cache incrementally

3. **Level of Detail**: Reduce ray-casting accuracy at distance
   - Close range: Check every 16 pixels
   - Far range: Check every 32 pixels

4. **Frame Skipping**: Update LOS every 2-3 frames
   - Players won't notice 33ms lag in fog updates
   - Halves CPU cost

5. **Separate Thread**: Calculate LOS on background thread
   - Main thread uses last frame's results
   - Update thread recalculates asynchronously

### Performance Targets

```
Map Size: 100x100 tiles
Obstacles: 200
Buildings: 50
Target: < 2ms per frame for LOS calculations

Recommended: Ray casting with QuadTree spatial partitioning
```

## Gameplay Implications

### Tactical Advantages

1. **Ambush Potential**: Hide behind buildings, attack when enemy approaches
2. **Map Control**: Controlling high ground or open areas = better vision
3. **Siege Mechanics**: Destroy buildings to open new sightlines
4. **Flanking**: Navigate around mountains to avoid detection
5. **Cover System**: Trees provide partial concealment

### Balancing Vision Blocking

- **Too much blocking**: Frustrating, feels random
- **Too little blocking**: No tactical depth
- **Sweet spot**: ~20-30% of map has vision blockers

### Visual Feedback

```
Fully Visible:      Tint = 1.0 (100% brightness)
Partial Shadow:     Tint = 0.7 (70% brightness) - behind trees
Blocked:            Tint = 0.15 (15% brightness) - behind mountains
Out of Range:       Tint = 0.15 (15% brightness) - standard fog
```

## Implementation Phases

### Phase 1: Basic Terrain LOS (Start Here)
1. Add `VisionBlockingType` to `TerrainType`
2. Create `LineOfSightCalculator` with simple ray casting
3. Add MOUNTAIN or FOREST terrain type that blocks vision
4. Update `ClientGameMap.render()` to use LOS calculator
5. Test with simple maps

### Phase 2: Obstacle LOS
1. Add vision blocking to `Obstacle` class
2. Implement large rocks/boulders that block vision
3. Add trees with partial vision blocking
4. Integrate obstacles into LOS calculator

### Phase 3: Building LOS
1. Add vision blocking to `Building` class
2. Make tall buildings block vision
3. Implement dynamic LOS updates when buildings destroyed
4. Add visual effects (shadows, fog gradients)

### Phase 4: Optimization
1. Add QuadTree spatial partitioning
2. Implement LOS caching
3. Profile and optimize hot paths
4. Consider shadow casting if performance allows

### Phase 5: Polish
1. Smooth fog gradients around blockers
2. Add "fog of war memory" (last known positions)
3. Minimap with fog of war overlay
4. Audio occlusion (muffled sounds behind walls)

## Testing Scenarios

```
Scenario 1: Mountain Range
GGGGGGMMMMMGGGGG
GGGGMMMMMMMGGG    Player behind mountain
GGGMMMMMMMGGGGG    = Can't see enemies on other side
GGGGMMMMMGGGGG
GGGGGGGMMMGGGGG

Scenario 2: Building Siege
SSSSSSSSSSSS
SBBBBSSGGGGG      Destroy building B
SBBBBSSGGGGG       = Opens new sightlines
SSSSSSSGGGGG      

Scenario 3: Forest Ambush
GGGGGTTTTTGG
GGGGTTTTTTTG      Trees = partial vision
GGTTTTTTTTTG       = Can barely see enemies
GTTTTTTTGGGG      
```

## Recommended Approach for Your Game

**Start Simple, Iterate:**

1. **Week 1**: Add MOUNTAIN terrain type with FULL vision blocking, implement basic ray casting
2. **Week 2**: Test with players, gather feedback on feel
3. **Week 3**: Add buildings with vision blocking
4. **Week 4**: Add destruction → LOS change mechanic
5. **Week 5**: Optimize with spatial partitioning if needed

**Performance First**: Ray casting with QuadTree should be sufficient for your game scale (multiplayer with ~10-20 players).

**Gameplay Balance**: Start with sparse vision blockers (15%), increase based on testing.

## Summary

Yes, you can absolutely implement vision blocking with this system! The key additions are:

1. **VisionBlockingType** property on terrain/obstacles/buildings
2. **LineOfSightCalculator** that ray-casts through the world
3. **Enhanced fog rendering** that respects LOS, not just distance
4. **Dynamic updates** when buildings are destroyed

This creates deep tactical gameplay where positioning and map knowledge matter, similar to games like Company of Heroes or StarCraft II.
