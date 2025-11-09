# Terrain System Quick Reference

## Quick Start Integration

### 1. Server Setup

```java
// Create terrain generator with seed
long seed = System.currentTimeMillis(); // or any long value
ProceduralTerrainGenerator terrainGen = new ProceduralTerrainGenerator(seed);

// Generate terrain with profile
terrainGen.generateProceduralTerrain(gameMapData, BaseTerrainProfile.GRASSLAND);

// Encode terrain for network transmission
String encodedTerrain = TerrainEncoder.encode(gameMapData);

// Send to client on join
handler.sendMessage(String.format("%s;%d;%d;%s",
    NetworkProtocol.TERRAIN_DATA,
    mapWidth, mapHeight,
    encodedTerrain));

// Create fire manager
FireManager fireManager = new FireManager(gameMapData);

// In game update loop (60 Hz)
fireManager.update(System.currentTimeMillis());

// When explosion occurs
fireManager.onExplosion(explosionPosition, explosionRadius);
```

### 2. Client Setup

```java
// Receive terrain data from server via TERRAIN_DATA message
// Client decodes the data instead of generating
TerrainEncoder.decode(gameMapData, encodedTerrainData);

// Register textures for rendering
clientGameMap.registerTerrainTexture(TerrainType.GRASS, grassTexture);
clientGameMap.registerTerrainTexture(TerrainType.DIRT, dirtTexture);
clientGameMap.registerTerrainTexture(TerrainType.SHALLOW_WATER, waterTexture);
clientGameMap.registerTerrainTexture(TerrainType.FOREST, treeTexture);
clientGameMap.registerStateOverlayTexture(TerrainState.SCORCHED, scorchedTexture);
```

## Terrain Types Cheat Sheet

| Type          | Speed | Passable | Blocks Bullets | Vision Block | Flammable | Ignite % | Burn Time |
|---------------|-------|----------|----------------|--------------|-----------|----------|-----------|
| GRASS         | 100%  | ✅       | ❌             | NONE         | HIGH      | 90%      | 5s        |
| DIRT          | 95%   | ✅       | ❌             | NONE         | No        | 0%       | -         |
| MUD           | 60%   | ✅       | ❌             | NONE         | No        | 0%       | -         |
| SHALLOW_WATER | 40%   | ❌       | ❌             | NONE         | No        | 0%       | -         |
| DEEP_WATER    | 0%    | ❌       | ❌             | NONE         | No        | 0%       | -         |
| SAND          | 85%   | ✅       | ❌             | NONE         | No        | 0%       | -         |
| STONE         | 100%  | ✅       | ❌             | NONE         | No        | 0%       | -         |
| FOREST        | 70%   | ❌       | ✅             | PARTIAL      | MEDIUM    | 70%      | 15s       |
| MOUNTAIN      | 0%    | ❌       | ❌             | FULL         | No        | 0%       | -         |

## Fire States Timeline

```
Explosion → IGNITING (2s) → BURNING (varies) → SMOLDERING (3s) → SCORCHED (forever)
              🔥               🔥🔥🔥              💨                 ⚫
            85% speed        85% speed          90% speed         100% speed
```

**State Speed Modifiers:**
- NORMAL: 100%
- IGNITING: 85%
- BURNING: 85%
- SMOLDERING: 90%
- SCORCHED: 100%
- FLOODED: 100% (but prevents ignition)

## Common Queries

```java
// Get terrain at world position
TerrainTile tile = gameMapData.getTileAt(worldX, worldY);

// Check if passable for tanks
boolean canPass = tile.isPassable();

// Check if bullets collide
boolean bulletHits = tile.getEffectiveType() == TerrainType.FOREST;

// Get speed modifier (includes terrain + state)
float speedMod = tile.getEffectiveSpeedModifier();

// Check base terrain
if (tile.getBaseType() == TerrainType.GRASS) { ... }

// Check overlay terrain
if (tile.hasOverlay() && tile.getOverlayType() == TerrainType.FOREST) { ... }

// Check terrain state
if (tile.getCurrentState() == TerrainState.BURNING) { ... }
```

## Movement Speed Examples

```java
// Tank on normal GRASS
speedModifier = 1.0 (terrain) * 1.0 (state) = 1.0 (100%)

// Tank on MUD
speedModifier = 0.6 (terrain) * 1.0 (state) = 0.6 (60%)

// Tank on BURNING GRASS
speedModifier = 1.0 (terrain) * 0.85 (burning) = 0.85 (85%)

// Tank on FOREST (overlay terrain)
speedModifier = 0.7 (terrain) * 1.0 (state) = 0.7 (70%)
// Note: Tanks can't actually enter forest - it's impassable!

// Tank on SCORCHED GRASS (after fire)
speedModifier = 1.0 (terrain) * 1.0 (scorched) = 1.0 (100%)
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
NONE(0.0f),      // Cannot ignite
LOW(0.3f),       // 30% chance to ignite
MEDIUM(0.7f),    // 70% chance to ignite (FOREST)
HIGH(0.9f),      // 90% chance to ignite (GRASS)
```

**Note:** Fire spreading between tiles is not yet implemented.

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
    private ProceduralTerrainGenerator terrainGenerator;
    
    public void initialize() {
        // Create map
        serverContext.gameMapData = new GameMapData(100, 100, 32);
        
        // Generate terrain with seed
        long seed = System.currentTimeMillis();
        terrainGenerator = new ProceduralTerrainGenerator(seed);
        terrainGenerator.generateProceduralTerrain(
            serverContext.gameMapData, 
            BaseTerrainProfile.GRASSLAND
        );
        
        // Create fire manager
        fireManager = new FireManager(serverContext.gameMapData);
        
        logger.info("Terrain system initialized (seed: {})", seed);
    }
    
    public void onPlayerJoin(ClientHandler handler) {
        // Send terrain data to client
        String encodedTerrain = TerrainEncoder.encode(serverContext.gameMapData);
        handler.sendMessage(String.format("%s;%d;%d;%s",
            NetworkProtocol.TERRAIN_DATA,
            serverContext.gameMapData.getWidthTiles(),
            serverContext.gameMapData.getHeightTiles(),
            encodedTerrain));
    }
    
    public void gameLoop() {
        long currentTime = System.currentTimeMillis();
        
        // Update fire states
        fireManager.update(currentTime);
        
        // ... rest of game loop
    }
    
    public void onExplosion(Vector2f position) {
        // Create explosion visual effect
        // ...
        
        // Ignite terrain in radius
        fireManager.onExplosion(position, 30.0f);
    }
    
    public void regenerateTerrainForNewRound() {
        // Generate new terrain
        long newSeed = System.currentTimeMillis();
        terrainGenerator = new ProceduralTerrainGenerator(newSeed);
        terrainGenerator.generateProceduralTerrain(
            serverContext.gameMapData,
            BaseTerrainProfile.GRASSLAND
        );
        
        // Broadcast to all clients
        String encodedTerrain = TerrainEncoder.encode(serverContext.gameMapData);
        broadcast(String.format("%s;%d;%d;%s",
            NetworkProtocol.TERRAIN_DATA,
            serverContext.gameMapData.getWidthTiles(),
            serverContext.gameMapData.getHeightTiles(),
            encodedTerrain), -1);
    }
}

// ===== CLIENT SIDE =====
public class TankBattleGame {
    private ClientGameMap clientGameMap;
    
    public void onTerrainDataReceived(int width, int height, String encodedData) {
        // Decode terrain from server
        GameMapData mapData = new GameMapData(width, height, 32);
        TerrainEncoder.decode(mapData, encodedData);
        
        // Create client map wrapper
        clientGameMap = new ClientGameMap(mapData);
        
        logger.info("Received and decoded terrain: {}x{} tiles", width, height);
    }
    
    public void loadResources() {
        // Load terrain textures
        Texture grassTex = loadTexture("textures/grass.png");
        Texture dirtTex = loadTexture("textures/dirt.png");
        Texture forestTex = loadTexture("textures/forest.png");
        Texture scorchedTex = loadTexture("textures/scorched.png");
        
        // Register them
        clientGameMap.registerTerrainTexture(TerrainType.GRASS, grassTex);
        clientGameMap.registerTerrainTexture(TerrainType.DIRT, dirtTex);
        clientGameMap.registerTerrainTexture(TerrainType.FOREST, forestTex);
        clientGameMap.registerStateOverlayTexture(TerrainState.SCORCHED, scorchedTex);
        
        logger.info("Terrain textures loaded");
    }
    
    public void render() {
        // Render terrain with current camera view
        clientGameMap.render(camera);
        
        // ... render entities, effects, etc.
    }
}
```

## Current Status

### Phase 1: Complete ✅
✅ Three-layer terrain system (base + visual overlay + data overlay)  
✅ Procedural generation with Perlin noise  
✅ Single contiguous regions (flood fill algorithm)  
✅ Four terrain profiles (Grassland, Desert, Dirt Plains, Mudlands)  
✅ Terrain collision detection for tanks and bullets  
✅ Overlay terrain blocks bullets (trees)  
✅ Fire ignition from explosions (probabilistic)  
✅ Fire state progression with timed transitions  
✅ Safe spawn points (no spawning in overlays)  
✅ TerrainTile state management  
✅ FireManager with explosion triggering  

### Phase 2: Complete ✅
✅ Network terrain synchronization (TerrainEncoder/Decoder)  
✅ Server sends full terrain data to clients  
✅ Terrain regeneration between rounds  
✅ Broadcast terrain updates to all clients  
✅ Vision blocking types defined (NONE, PARTIAL, FULL)  
✅ Destructible property on terrain types  

### Phase 3: In Progress 🔄
❌ Fire spreading between adjacent tiles  
❌ Destructible terrain implementation (shoot to destroy)  
❌ Line of sight calculations using vision blocking  
❌ Fog of war integration  
❌ Advanced fire effects (particles, smoke)  
❌ Weather effects (rain extinguishes fire)  

### Phase 4: Planned 📋
❌ Buildings as destructible entities  
❌ Multiple visual overlay layers  
❌ Roads/paths as visual overlays (tank tracks)  
❌ Height maps for elevation gameplay  
❌ Terrain deformation (explosion craters)

## Support & Documentation

- Full design: `docs/terrain-system-design.md`
- Implementation details: `docs/phase1-implementation-complete.md`
- Fire system: `docs/dynamic-terrain-system.md`
- Line of sight: `docs/line-of-sight-system.md`
