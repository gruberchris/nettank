# Terrain System Quick Reference

## Quick Start Integration

### 1. Server Setup (3 lines of code)

```java
// In your GameServer or ServerContext initialization
FireManager fireManager = new FireManager(gameMapData);

// In your game update loop (60 Hz)
fireManager.update(System.currentTimeMillis());

// When bullet explodes or tank destroyed
fireManager.onExplosion(explosionPosition, explosionRadius);
```

### 2. Client Setup (Textures)

```java
// Register textures once during initialization
clientGameMap.registerTerrainTexture(TerrainType.GRASS, grassTexture);
clientGameMap.registerTerrainTexture(TerrainType.DIRT, dirtTexture);
// ... register others as you add them

// Optional: Scorched overlay
clientGameMap.registerStateOverlayTexture(TerrainState.SCORCHED, scorchedTexture);
```

### 3. Generate Terrain (Server)

```java
// Option A: Simple random terrain
TerrainGenerator generator = new TerrainGenerator();
generator.generateSimpleTerrain(gameMapData);

// Option B: Terrain with patches (forests, mud, etc.)
generator.generateTerrainWithFeatures(gameMapData);
```

## Terrain Types Cheat Sheet

| Type          | Symbol | Speed | Can Pass? | Flammable? | Burn Time |
|---------------|--------|-------|-----------|------------|-----------|
| GRASS         | 🟩     | 100%  | ✅        | Yes        | 5s        |
| DIRT          | 🟫     | 95%   | ✅        | No         | -         |
| MUD           | 🟤     | 60%   | ✅        | No         | -         |
| SHALLOW_WATER | 🔵     | 40%   | ✅        | No         | -         |
| DEEP_WATER    | 🌊     | 0%    | ❌        | No         | -         |
| SAND          | 🟨     | 85%   | ✅        | No         | -         |
| STONE         | ⬜     | 100%  | ✅        | No         | -         |
| FOREST        | 🌲     | 70%   | ✅        | Yes        | 15s       |
| MOUNTAIN      | 🗻     | 0%    | ❌        | No         | -         |

## Fire States Timeline

```
Explosion → IGNITING (2s) → BURNING (varies) → SMOLDERING (3s) → SCORCHED (forever)
              🔥               🔥🔥🔥              💨                 ⚫
```

## Common Queries

```java
// Get terrain at world position
TerrainTile tile = gameMapData.getTileAt(worldX, worldY);

// Check if passable
boolean canPass = gameMapData.isPassableAt(worldX, worldY);

// Get speed modifier (includes terrain + state)
float speedMod = gameMapData.getSpeedModifierAt(worldX, worldY);

// Check terrain type
if (tile.getBaseType() == TerrainType.GRASS) { ... }

// Check terrain state
if (tile.getCurrentState() == TerrainState.BURNING) { ... }
```

## Movement Speed Examples

```java
// Tank on normal GRASS
speedModifier = 1.0 * 1.0 = 1.0 (100%)

// Tank on MUD
speedModifier = 0.6 * 1.0 = 0.6 (60%)

// Tank on BURNING GRASS
speedModifier = 1.0 * 0.7 = 0.7 (70%)

// Tank on BURNING MUD (if somehow set on fire)
speedModifier = 0.6 * 0.7 = 0.42 (42%)
```

## Fire System Configuration

Edit these constants in `FireManager.java`:

```java
private static final float EXPLOSION_IGNITION_RADIUS_TILES = 2.5f;  // Explosion fire radius
```

Edit burn durations in `TerrainType.java`:

```java
GRASS(..., 5000L),   // Burns for 5 seconds
FOREST(..., 15000L), // Burns for 15 seconds
```

Edit ignition chances in `Flammability.java`:

```java
MEDIUM(0.4f, 0.15f),  // 40% chance to ignite, 15% spread chance
HIGH(0.8f, 0.35f),    // 80% chance to ignite, 35% spread chance
```

## Debugging Tips

### Enable Debug Logging

Add to your logback.xml or log4j2.xml:

```xml
<logger name="org.chrisgruber.nettank.server.world.FireManager" level="DEBUG"/>
```

### Fire Debug Messages

You'll see:
- "Tile (x, y) ignited, will burn for X ms"
- "Tile (x, y) transitioned to BURNING"
- "Tile (x, y) transitioned to SMOLDERING"
- "Tile (x, y) burned out, now SCORCHED"

### Manual Fire Test

```java
// Create test explosion on grass
Vector2f testPos = new Vector2f(500f, 500f);  // World coordinates
fireManager.onExplosion(testPos, 50f);

// Check tiles burned
List<FireManager.TileStateChange> burning = fireManager.getBurningTiles();
logger.info("Currently {} tiles burning", burning.size());
```

## Performance Notes

- **Fire update**: ~0.1ms per 100 burning tiles
- **Terrain lookup**: O(1) grid access
- **Memory**: ~16 bytes per tile (TerrainTile object)
- **Network**: ~8 bytes per state change message

## Common Issues & Solutions

### Issue: No fire appears after explosion
**Solution**: Check terrain type is flammable (GRASS or FOREST)

### Issue: Fire doesn't transition states
**Solution**: Make sure `fireManager.update()` is called in game loop

### Issue: Tiles stay burning forever
**Solution**: Verify system time is passed correctly to `update(currentTime)`

### Issue: Textures not showing
**Solution**: Register textures with `clientGameMap.registerTerrainTexture()`

## Texture File Checklist

Essential for visuals:
- [ ] `grass.png` - Most common terrain
- [ ] `dirt.png` - Second most common
- [ ] `scorched.png` - Shows burned areas (optional but recommended)

Optional but nice:
- [ ] `forest.png` - For forest tiles
- [ ] `mud.png` - For mud/swamp areas
- [ ] `sand.png` - For sandy terrain
- [ ] `stone.png` - For stone roads/paths
- [ ] `water.png` - For water features

Fire animations (future):
- [ ] `fire_1.png` through `fire_8.png` - Animated flames
- [ ] `smoke_1.png` through `smoke_4.png` - Smoke particles

## Example: Full Integration

```java
// ===== SERVER SIDE =====
public class GameServer {
    private FireManager fireManager;
    private TerrainGenerator terrainGenerator;
    
    public void initialize() {
        // Create map
        serverContext.gameMapData = new GameMapData(100, 100);
        
        // Generate terrain
        terrainGenerator = new TerrainGenerator(12345L);  // seed for consistency
        terrainGenerator.generateTerrainWithFeatures(serverContext.gameMapData);
        
        // Create fire manager
        fireManager = new FireManager(serverContext.gameMapData);
        
        logger.info("Terrain system initialized");
    }
    
    public void gameLoop() {
        long currentTime = System.currentTimeMillis();
        
        // Update fire states
        fireManager.update(currentTime);
        
        // ... rest of game loop
    }
    
    public void onBulletHit(Vector2f position) {
        // Create explosion visual effect
        // ...
        
        // Ignite terrain
        fireManager.onExplosion(position, 30.0f);
    }
}

// ===== CLIENT SIDE =====
public class TankBattleGame {
    private ClientGameMap clientGameMap;
    
    public void loadResources() {
        // Load terrain textures
        Texture grassTex = loadTexture("textures/grass.png");
        Texture dirtTex = loadTexture("textures/dirt.png");
        Texture scorchedTex = loadTexture("textures/scorched.png");
        
        // Register them
        clientGameMap.registerTerrainTexture(TerrainType.GRASS, grassTex);
        clientGameMap.registerTerrainTexture(TerrainType.DIRT, dirtTex);
        clientGameMap.registerStateOverlayTexture(TerrainState.SCORCHED, scorchedTex);
        
        logger.info("Terrain textures loaded");
    }
    
    public void render() {
        // Render terrain (already updated to use new system)
        clientGameMap.render(renderer, shader, grassTexture, dirtTexture, 
                           camera, viewRange, fogCenter);
        
        // ... render entities, effects, etc.
    }
}
```

## What's Ready to Use RIGHT NOW

✅ Terrain types with different movement speeds
✅ Terrain passability (impassable water/mountains)
✅ Fire ignition from explosions
✅ Fire state progression (igniting → burning → scorched)
✅ Terrain queries (speed modifier, passability)
✅ Client rendering with base terrain + scorched overlay
✅ Procedural terrain generation

## What Needs Implementation for Full Experience

⏳ Network synchronization of terrain state changes
⏳ Fire visual effects (FlameEffect integration)
⏳ Fire damage to entities
⏳ Texture loading for all terrain types
⏳ Fire spreading to adjacent tiles (Phase 2)

## Support & Documentation

- Full design: `docs/terrain-system-design.md`
- Implementation details: `docs/phase1-implementation-complete.md`
- Fire system: `docs/dynamic-terrain-system.md`
- Line of sight: `docs/line-of-sight-system.md`
