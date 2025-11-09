# Round-Based Terrain Regeneration

## Overview
The terrain system supports automatic regeneration when starting new rounds, ensuring each round has a unique, procedurally generated map. This creates variety and prevents players from memorizing optimal positions.

## How It Works

### 1. Round Lifecycle
The game follows this state flow:
```
WAITING → COUNTDOWN (terrain regenerates here) → PLAYING → ROUND_OVER → WAITING → ...
```

### 2. Terrain Regeneration Trigger ✅
When the game transitions from `WAITING` to `COUNTDOWN` state (when first player joins or new round starts), the server:
- Generates a new unique seed based on current timestamp and map dimensions
- Regenerates the entire terrain grid using the new seed
- Encodes the terrain data using `TerrainEncoder`
- Broadcasts the complete terrain data to all connected clients via `TERRAIN_DATA` message

### 3. Game Mode Behavior
**FreeForAll Mode:**
- No victory condition - game continues indefinitely
- No round duration limit
- Terrain regenerates when:
  - Server starts and first player joins (WAITING → COUNTDOWN)
  - Players can continue playing on same terrain until server restart

### 4. Seed Generation ✅
```java
serverContext.terrainSeed = System.currentTimeMillis() ^ (mapWidth * 31L + mapHeight * 17L)
```
Each seed is unique based on:
- Current system time in milliseconds
- Map dimensions XORed for additional entropy
- Ensures different terrain each time regeneration occurs

## Implementation Details

### Server-Side Implementation ✅

**GameServer.java:**
```java
// In changeState method
if (previousState == GameState.WAITING && newState == GameState.COUNTDOWN) {
    regenerateTerrainForNewRound();
}

// Regeneration method
private void regenerateTerrainForNewRound() {
    logger.info("Regenerating terrain for new round.");
    
    // Generate new unique seed
    serverContext.terrainSeed = System.currentTimeMillis() ^ (mapWidth * 31L + mapHeight * 17L);
    
    // Create terrain generator with new seed
    TerrainGenerator terrainGenerator = new TerrainGenerator(serverContext.terrainSeed);
    
    // Regenerate terrain (delegates to ProceduralTerrainGenerator)
    terrainGenerator.generateProceduralTerrain(
        serverContext.gameMapData, 
        BaseTerrainProfile.GRASSLAND,
        serverContext.terrainSeed
    );
    
    // Encode terrain data
    String encodedTerrain = TerrainEncoder.encode(serverContext.gameMapData);
    
    // Broadcast to all connected clients
    broadcast(String.format("%s;%d;%d;%s",
        NetworkProtocol.TERRAIN_DATA,
        serverContext.gameMapData.getWidthTiles(),
        serverContext.gameMapData.getHeightTiles(),
        encodedTerrain), -1);
    
    logger.info("Broadcasted new terrain data to all clients ({} bytes)", encodedTerrain.length());
}
```

**TerrainGenerator.java:**
- Wrapper class that delegates to `ProceduralTerrainGenerator`
- Maintains backward compatibility with existing terrain generation methods
- `generateProceduralTerrain()` creates `ProceduralTerrainGenerator` with seed

**FreeForAll.java:**
- `checkIsVictoryConditionMet()` - Always returns `false` (no victory condition)
- `shouldTransitionFromRoundOver()` - Returns `PLAYING` (should never reach ROUND_OVER)
- No round duration limits in FreeForAll mode

### Client Handling ✅
Clients receive `TERRAIN_DATA` message when terrain regenerates:
```java
// In GameClient message handling
case NetworkProtocol.TERRAIN_DATA -> {
    var msg = NetworkMessage.TerrainData.parse(parts);
    networkCallbackHandler.receiveTerrainData(msg.width(), msg.height(), msg.encodedData());
}

// In TankBattleGame
public void receiveTerrainData(int width, int height, String encodedData) {
    this.receivedTerrainData = encodedData;
    this.terrainInfoReceivedForProcessing = true;
}

// Later in main thread
private void initializeMapAndTextures() {
    GameMapData mapData = new GameMapData(mapWidthTiles, mapHeightTiles, mapTileSize);
    TerrainEncoder.decode(mapData, receivedTerrainData);
    clientGameMap = new ClientGameMap(mapData);
}
```

- Decodes complete terrain data from server
- Updates local terrain grid
- Players respawn at valid spawn points on new terrain

## Configuration

### Changing Terrain Profile
Edit `GameServer.java` in both initialization and `regenerateTerrainForNewRound()`:
```java
terrainGenerator.generateProceduralTerrain(
    serverContext.gameMapData, 
    BaseTerrainProfile.GRASSLAND,  // Change to DESERT, DIRT_PLAINS, or MUDLANDS
    serverContext.terrainSeed
);
```

### Available Terrain Profiles
- `BaseTerrainProfile.GRASSLAND` - Green grass with water lakes and forest
- `BaseTerrainProfile.DESERT` - Sandy terrain with oasis and rocky outcrops
- `BaseTerrainProfile.DIRT_PLAINS` - Dirt base with muddy areas and rocks
- `BaseTerrainProfile.MUDLANDS` - Mud base with water and sparse trees

### Adjusting Countdown Duration
Edit `GameMode.java` or specific mode implementation:
```java
public long getCountdownStateLengthInSeconds() {
    return 5L; // Change countdown duration before round starts
}
```

### Future: Round Duration (Not Yet Implemented)
To add timed rounds that regenerate terrain:
1. Implement victory condition based on time in game mode
2. Add `ROUND_DURATION_MINUTES` configuration
3. Have `checkIsVictoryConditionMet()` return true after duration
4. Transition to `ROUND_OVER` then back to `WAITING` (triggers regeneration)

## Benefits
1. **Replayability** - Fresh terrain for each game session
2. **Fairness** - No player advantage from memorizing map layout
3. **Variety** - Different terrain layouts encourage different strategies
4. **Performance** - Regeneration happens during countdown (no gameplay impact)
5. **Seamless Sync** - All clients receive identical terrain data automatically
6. **Bandwidth Efficient** - Only ~20KB for 100x100 map with encoding

## Testing

### Testing Terrain Regeneration
To verify regeneration is working:

1. **Start server** - New terrain generated with unique seed
2. **First player joins** - Triggers WAITING → COUNTDOWN transition
3. **Check server logs** for:
   ```
   Regenerating terrain for new round.
   Terrain regeneration complete (new seed: 1731187245123, profile: GRASSLAND)
   Broadcasted new terrain data to all clients (22458 bytes)
   ```
4. **Client receives terrain** - Check client logs:
   ```
   Received TERRAIN_DATA: 100x100 tiles, 22458 bytes
   Terrain decoded and initialized
   ```
5. **Verify visually** - Different terrain layout each server restart

### Testing Different Seeds
Force specific seed for testing:
```java
// In GameServer.regenerateTerrainForNewRound()
serverContext.terrainSeed = 12345L; // Fixed seed for reproducible testing
```

## Current Status

### Implemented ✅
- ✅ Terrain regeneration on WAITING → COUNTDOWN transition
- ✅ Unique seed generation with XOR for entropy
- ✅ Full terrain data encoding and network transmission
- ✅ Client-side terrain decoding and display
- ✅ Broadcast to all connected clients
- ✅ Works seamlessly with FreeForAll mode

### Not Yet Implemented ❌
- ❌ Round duration limits (FreeForAll has no time limit)
- ❌ ROUND_OVER → WAITING regeneration cycle
- ❌ Victory-condition-based round endings
- ❌ Manual terrain regeneration command
- ❌ Random profile selection per round

## Notes
- Terrain regeneration currently occurs when transitioning from `WAITING` to `COUNTDOWN`
- In FreeForAll mode, this happens when first player joins after server start
- Server restart always generates new terrain with fresh seed
- All connected clients receive synchronized terrain data via `TERRAIN_DATA` message
- Players spawn at valid locations (avoiding water, trees, mountains)
- Fire system state resets with new terrain (no burning tiles carry over)
