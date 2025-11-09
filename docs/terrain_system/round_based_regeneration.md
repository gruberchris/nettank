# Round-Based Terrain Regeneration

## Overview
The terrain system now supports automatic regeneration between rounds, ensuring each round has a unique, procedurally generated map.

## How It Works

### 1. Round Lifecycle
The game follows this state flow:
```
WAITING → COUNTDOWN → PLAYING → ROUND_OVER → WAITING (new terrain) → ...
```

### 2. Terrain Regeneration Trigger
When the game transitions from `ROUND_OVER` back to `WAITING` state, the server:
- Generates a new unique seed based on current timestamp and map dimensions
- Regenerates the entire terrain grid using the new seed
- Broadcasts the new terrain data to all connected clients

### 3. Round Duration
**FreeForAll Mode:**
- Configurable round duration (default: 5 minutes)
- Set `ROUND_DURATION_MINUTES = 0` for infinite rounds (no regeneration)
- After round ends, 10 second delay before starting new round

### 4. Seed Generation
```java
terrainSeed = System.currentTimeMillis() ^ (mapWidth * 31L + mapHeight * 17L)
```
Each seed is unique based on:
- Current system time (milliseconds)
- Map dimensions (for additional entropy)

## Implementation Details

### Server-Side Changes

**GameServer.java:**
- `regenerateTerrainForNewRound()` - Creates new seed and regenerates terrain
- Called automatically when transitioning to `WAITING` state
- Broadcasts new `TERRAIN_DATA` message to all clients

**FreeForAll.java:**
- `ROUND_DURATION_MINUTES` - Configurable round length
- `checkIsVictoryConditionMet()` - Checks if round duration reached
- `shouldTransitionFromRoundOver()` - Returns to `WAITING` after delay

### Client Handling
Clients receive `TERRAIN_DATA` message during round transitions:
- Decodes terrain data
- Updates local terrain grid
- Respawns players at valid spawn points on new terrain

## Configuration

### Adjusting Round Duration
Edit `FreeForAll.java`:
```java
private static final long ROUND_DURATION_MINUTES = 5; // Change this value
```

### Adjusting Round-Over Delay
```java
private static final long ROUND_OVER_DELAY_SECONDS = 10; // Delay before new round
```

### Changing Terrain Profile
Edit `GameServer.java` initialization:
```java
terrainGenerator.generateProceduralTerrain(serverContext.gameMapData, 
    BaseTerrainProfile.GRASSLAND); // or DESERT, WINTER, etc.
```

## Benefits
1. **Replayability** - Each round offers a fresh tactical landscape
2. **Fairness** - No single player can memorize spawn/resource locations
3. **Variety** - Different terrain layouts encourage different strategies
4. **Performance** - Regeneration happens during transition (no gameplay impact)

## Testing
To verify regeneration is working:
1. Start server and join game
2. Wait for round to end (5 minutes default)
3. Observe "Regenerating terrain for new round" in server logs
4. Verify new terrain appears after countdown
5. Check server logs for new seed value

## Notes
- Terrain regeneration only occurs when returning to `WAITING` from `ROUND_OVER`
- Server restart also generates new terrain with fresh seed
- All connected clients receive synchronized terrain data
- Players are respawned at valid locations (avoiding obstacles)
