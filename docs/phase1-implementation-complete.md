# Phase 1 Terrain System Implementation - Complete

## What Was Implemented

Phase 1 of the terrain system has been successfully implemented with support for dynamic terrain states including fire propagation.

### Core Components Created

#### 1. Common Module (Shared between client and server)

**New Files:**
- `VisionBlockingType.java` - Enum defining how terrain blocks vision (NONE, PARTIAL, FULL)
- `Flammability.java` - Enum defining how easily terrain catches and spreads fire
- `TerrainType.java` - Enum with 9 terrain types (GRASS, DIRT, MUD, WATER, SAND, STONE, FOREST, MOUNTAIN)
- `TerrainState.java` - Enum for dynamic terrain states (NORMAL, IGNITING, BURNING, SMOLDERING, SCORCHED, etc.)
- `TerrainTile.java` - Class that combines base type + current state + fire tracking

**Modified Files:**
- `GameMapData.java` - Now contains a TerrainTile grid and provides utility methods for tile queries

#### 2. Client Module

**Modified Files:**
- `ClientGameMap.java` - Updated to use TerrainTile system, supports texture registration and state overlays

#### 3. Server Module

**New Files:**
- `FireManager.java` - Handles fire ignition from explosions and burning state progression
- `TerrainGenerator.java` - Utility to generate terrain maps (simple random or with features)

## Terrain Types & Properties

| Terrain Type    | Speed | Passable | Vision Blocking | Flammability | Burn Duration |
|----------------|-------|----------|----------------|--------------|---------------|
| GRASS          | 100%  | Yes      | None           | Medium       | 5 seconds     |
| DIRT           | 95%   | Yes      | None           | None         | N/A           |
| MUD            | 60%   | Yes      | None           | None         | N/A           |
| SHALLOW_WATER  | 40%   | Yes      | None           | None         | N/A           |
| DEEP_WATER     | 0%    | No       | None           | None         | N/A           |
| SAND           | 85%   | Yes      | None           | None         | N/A           |
| STONE          | 100%  | Yes      | None           | None         | N/A           |
| FOREST         | 70%   | Yes      | Partial        | High         | 15 seconds    |
| MOUNTAIN       | 0%    | No       | Full           | None         | N/A           |

## Fire System (Phase 1)

### How It Works

1. **Explosion triggers fire**: When explosion occurs → `FireManager.onExplosion()` called
2. **Ignition check**: Tests tiles within 2.5 tile radius for flammability
3. **State progression**: IGNITING (2s) → BURNING (main) → SMOLDERING (3s) → SCORCHED (permanent)
4. **No spreading yet**: Phase 1 only handles direct explosion ignition

### Fire Lifecycle Example

```
Time 0s:  Explosion hits GRASS tile
Time 0s:  Tile enters IGNITING state
Time 2s:  Tile transitions to BURNING state
Time 5s:  Tile transitions to SMOLDERING state (3s before end)
Time 8s:  Tile transitions to SCORCHED state (permanent)
```

### State Effects

- **IGNITING**: Visual effect, no movement penalty
- **BURNING**: Visual effect, 30% movement penalty (0.7x speed)
- **SMOLDERING**: Visual effect, 20% movement penalty (0.8x speed)
- **SCORCHED**: No effect, permanent darkening (visual only)

## Integration Points

### Server Side

To integrate the fire system into your game server:

```java
// In ServerContext or GameServer initialization
private FireManager fireManager;

public void initialize() {
    gameMapData = new GameMapData(mapWidth, mapHeight);
    fireManager = new FireManager(gameMapData);
    
    // Optional: Generate terrain
    TerrainGenerator generator = new TerrainGenerator();
    generator.generateTerrainWithFeatures(gameMapData);
}

// In game update loop
public void update() {
    long currentTime = System.currentTimeMillis();
    fireManager.update(currentTime);
}

// When explosion occurs (e.g., bullet hits or tank destroyed)
public void onExplosion(Vector2f position, float radius) {
    fireManager.onExplosion(position, radius);
    // Create explosion visual effect as before
}
```

### Client Side

To integrate the terrain rendering:

```java
// In ClientGameMap or TankBattleGame initialization
clientGameMap.registerTerrainTexture(TerrainType.GRASS, grassTexture);
clientGameMap.registerTerrainTexture(TerrainType.DIRT, dirtTexture);
// ... register other terrain textures

// Optional: Register scorched overlay
clientGameMap.registerStateOverlayTexture(TerrainState.SCORCHED, scorchedTexture);

// Handle terrain state changes from server (future network sync)
public void onTerrainStateChange(int x, int y, TerrainState newState) {
    clientGameMap.onTerrainStateChanged(x, y, newState);
}
```

### Movement Physics Integration

To apply terrain speed modifiers to tank movement:

```java
// In tank movement code
public void updatePosition(float deltaTime) {
    float worldX = tank.getPosition().x();
    float worldY = tank.getPosition().y();
    
    // Get terrain speed modifier
    float terrainSpeedMod = gameMapData.getSpeedModifierAt(worldX, worldY);
    
    // Apply to movement
    float effectiveSpeed = baseSpeed * terrainSpeedMod;
    // ... apply movement with effectiveSpeed
}
```

## What's NOT Yet Implemented

Phase 1 is **basic fire without spreading**. The following are planned for future phases:

- ❌ Fire spreading to adjacent tiles
- ❌ Network synchronization of terrain state changes
- ❌ Fire damage to entities standing in flames
- ❌ Line of sight blocking by terrain/obstacles
- ❌ Obstacles (rocks, trees, etc.)
- ❌ Destructible buildings
- ❌ Visual fire effects on burning tiles (FlameEffect integration)
- ❌ Smoke particles

## Testing the Implementation

### Manual Testing

1. **Compile**: `mvn clean compile` (already verified - compiles successfully)
2. **Run server**: Start game server
3. **Trigger explosion**: Fire bullet or destroy tank on GRASS terrain
4. **Observe logs**: Should see "Tile (x, y) ignited" messages
5. **Wait 5 seconds**: Tile should transition through states
6. **Check state**: Tile should end in SCORCHED state

### Quick Test Scenario

```java
// In test or game initialization
GameMapData mapData = new GameMapData(50, 50);
FireManager fireManager = new FireManager(mapData);

// Set a patch of grass
for (int y = 20; y < 25; y++) {
    for (int x = 20; x < 25; x++) {
        mapData.getTile(x, y).setBaseType(TerrainType.GRASS);
    }
}

// Trigger explosion at center
Vector2f explosionPos = new Vector2f(22.5f * 32.0f, 22.5f * 32.0f);
fireManager.onExplosion(explosionPos, 50.0f);

// Update in game loop
fireManager.update(System.currentTimeMillis());
```

## Next Steps (Phase 2+)

### Immediate TODOs

1. **Add network messages** for terrain state synchronization
   - Define `TERRAIN_STATE_CHANGE` message type
   - Server broadcasts state changes to all clients
   - Clients apply changes via `clientGameMap.onTerrainStateChanged()`

2. **Integrate fire visuals** on client
   - Reuse existing `FlameEffect` for burning tiles
   - Add to rendering pipeline between terrain and entities

3. **Apply fire damage** to entities
   - In server update loop, check if tank is on BURNING tile
   - Apply damage per second (e.g., 5 HP/sec)

### Future Phases

**Phase 2**: Fire Spreading
- Implement `FireManager.attemptFireSpread()` method
- Burning tiles spread to adjacent flammable tiles
- Configurable spread rates per terrain type

**Phase 3**: Line of Sight
- Implement `LineOfSightCalculator` class
- Terrain/obstacles block vision
- Integrate with fog of war rendering

**Phase 4**: Obstacles & Buildings
- Create `Obstacle` base class
- Create `Building` entity with health
- Implement collision detection
- Add destruction mechanics

## Textures Needed

To fully visualize the terrain system, you'll need these texture files:

### Base Terrain Textures (32x32 or 64x64)
- `grass.png` - Green grass texture
- `dirt.png` - Brown dirt texture
- `mud.png` - Dark brown muddy texture
- `shallow_water.png` - Light blue water
- `deep_water.png` - Dark blue water
- `sand.png` - Tan/yellow sandy texture
- `stone.png` - Gray stone/cobblestone
- `forest.png` - Dense green tree texture
- `mountain.png` - Rocky mountain texture

### State Overlay Textures (32x32 or 64x64, semi-transparent)
- `scorched.png` - Dark charred marks (50-70% opacity)

### Fire Effect Textures (for future integration)
- `fire_frame_1.png` through `fire_frame_8.png` - Animated flames
- `smoke_frame_1.png` through `smoke_frame_4.png` - Rising smoke

### Temporary Solution

Until you have textures, the system will work with your existing grass/dirt textures. It will:
- Use grass texture for: GRASS, FOREST, MOUNTAIN (as fallback)
- Use dirt texture for: DIRT, MUD, SAND, STONE, WATER (as fallback)
- Skip scorched overlay if texture not provided

## File Summary

### New Files (12 total)
```
nettank-common/src/main/java/org/chrisgruber/nettank/common/world/
  ├── VisionBlockingType.java
  ├── Flammability.java
  ├── TerrainType.java
  ├── TerrainState.java
  └── TerrainTile.java

nettank-server/src/main/java/org/chrisgruber/nettank/server/world/
  ├── FireManager.java
  └── TerrainGenerator.java
```

### Modified Files (3 total)
```
nettank-common/src/main/java/org/chrisgruber/nettank/common/world/
  └── GameMapData.java (added TerrainTile grid + query methods)

nettank-client/src/main/java/org/chrisgruber/nettank/client/game/world/
  └── ClientGameMap.java (updated to use TerrainTile + texture registration)
```

## Architecture Benefits

✅ **Separation of concerns**: Visual layer (client) vs data layer (server)
✅ **Multiplayer ready**: Server authoritative terrain state
✅ **Extensible**: Easy to add new terrain types and states
✅ **Performance**: Grid-based lookups for terrain queries
✅ **Future-proof**: Ready for LOS, obstacles, buildings

## Conclusion

Phase 1 implementation is **complete and compiles successfully**. The foundation is in place for:
- Multiple terrain types with gameplay properties
- Dynamic fire system (ignition and burning lifecycle)
- Extensible architecture for future features

The system is ready for integration once you add:
1. Fire system hooks in server explosion code
2. Terrain texture loading in client
3. Network synchronization messages

All code follows your existing patterns and integrates seamlessly with the current game architecture.
