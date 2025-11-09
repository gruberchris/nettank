# Server-Client Terrain Protocol

## Overview

The terrain system uses a **server-authoritative** approach where the server generates the terrain and sends the **complete terrain data** to clients. This eliminates synchronization issues and allows for custom or procedurally generated maps without client-side duplication of generation logic.

## Message Flow

### 1. Server Initialization
When a new game session starts:
1. Server generates a **unique random seed** based on `System.currentTimeMillis()`
2. Server selects a **terrain profile** (e.g., GRASSLAND, DESERT, DIRT_PLAINS, MUDLANDS)
3. Server generates the full terrain using `ProceduralTerrainGenerator` with the seed and profile
4. Server encodes the terrain using `TerrainEncoder` into a compact string format
5. Server stores terrain data and metadata in `ServerContext`

### 2. Client Connection
When a client connects, the server sends initialization messages in this order:
1. **ASSIGN_ID** - Assigns player ID and color
2. **MAP_INFO** - Map dimensions and tile size
3. **TERRAIN_DATA** - Complete encoded terrain data ✅
4. **GAME_STATE** - Current game state
5. Other game state data (tanks, lives, etc.)

### 3. Client Terrain Reception
When the client receives `TERRAIN_DATA`:
1. Client receives encoded terrain string from server
2. Client creates `GameMapData` with received dimensions
3. Client calls `TerrainEncoder.decode()` to populate the terrain grid
4. Client creates `ClientGameMap` wrapper for rendering
5. **No procedural generation on client side** - terrain is already complete

## Network Protocol

### TERRAIN_DATA Message Format (Current Implementation ✅)
```
TRD;<width>;<height>;<encodedData>
```

**Example:**
```
TRD;100;100;0,0,0:7,0,0,0:7,0,0,0,1,1,1,...
```

**Parameters:**
- `width` (int): Map width in tiles
- `height` (int): Map height in tiles
- `encodedData` (String): Comma-separated terrain tile data

**Encoding Format:**
Each tile is encoded as either:
- `B` - Base terrain only (e.g., `0` = GRASS)
- `B:O` - Base terrain with overlay (e.g., `0:7` = GRASS with FOREST overlay)

Where `B` and `O` are the ordinal values of `TerrainType` enum.

**Example Decoding:**
- `0` = GRASS (base only)
- `1` = DIRT (base only)
- `0:7` = GRASS base + FOREST overlay
- `0:5` = GRASS base + SHALLOW_WATER overlay

### Legacy TERRAIN_INIT Message (Deprecated)
```
TER;<seed>;<profileName>
```
**Status:** This message format is **no longer used**. The protocol was changed to send full terrain data instead of generation parameters.

## Code Locations

### Common (Shared)
- `NetworkProtocol.java` - Protocol constants including `TERRAIN_DATA`
- `TerrainEncoder.java` - Encodes/decodes terrain data for network transmission
- `BaseTerrainProfile.java` - Terrain profile definitions
- `TerrainType.java` - Terrain type enum with ordinal values
- `TerrainTile.java` - Tile data structure

### Server
- `ServerContext.java` - Stores `terrainSeed` and `terrainProfileName`
- `GameServer.java` - Generates terrain and sends `TERRAIN_DATA` message
- `ProceduralTerrainGenerator.java` - Server-side terrain generation

### Client
- `NetworkMessage.TerrainData` - Type-safe message record for parsing
- `GameClient.java` - Receives and parses `TERRAIN_DATA` message
- `TankBattleGame.java` - Processes received terrain data
- `ClientGameMap.java` - Renders terrain from decoded data
- **No ProceduralTerrainGenerator on client** - not needed anymore

## Benefits of This Approach

### Advantages ✅
1. **Server Authority** - Server has full control over terrain generation
2. **No Sync Issues** - Clients receive exact terrain, no generation mismatch possible
3. **Flexible** - Supports procedural generation OR hand-crafted maps
4. **No Code Duplication** - Client doesn't need terrain generation logic
5. **Compact Encoding** - TerrainEncoder produces efficient string format
6. **Per-Session Randomness** - Each game session has unique terrain
7. **Round Regeneration** - New terrain broadcasted to all clients between rounds

### Current Characteristics
1. **Bandwidth Usage** - Full terrain data sent (~2 bytes per tile on average)
   - 100x100 map = ~20KB encoded terrain data
   - Acceptable for small to medium maps
2. **One-Time Transfer** - Terrain sent once on join, then only state changes
3. **Dynamic Updates** - Fire and terrain state changes synced separately

## Implementation Details

### Server Side - Sending Terrain

```java
// On player join
public void sendInitialGameState(int playerId, ClientHandler handler) {
    // ... send MAP_INFO first ...
    
    // Encode and send terrain data
    String encodedTerrain = TerrainEncoder.encode(serverContext.gameMapData);
    handler.sendMessage(String.format("%s;%d;%d;%s",
        NetworkProtocol.TERRAIN_DATA,
        serverContext.gameMapData.getWidthTiles(),
        serverContext.gameMapData.getHeightTiles(),
        encodedTerrain));
    
    logger.info("Sent TERRAIN_DATA ({}x{} tiles, {} bytes) to player ID {}", 
        width, height, encodedTerrain.length(), playerId);
}

// On round regeneration
public void regenerateTerrainForNewRound() {
    // Generate new terrain
    long newSeed = System.currentTimeMillis();
    terrainGenerator = new ProceduralTerrainGenerator(newSeed);
    terrainGenerator.generateProceduralTerrain(
        serverContext.gameMapData,
        BaseTerrainProfile.GRASSLAND
    );
    
    // Broadcast to all connected clients
    String encodedTerrain = TerrainEncoder.encode(serverContext.gameMapData);
    broadcast(String.format("%s;%d;%d;%s",
        NetworkProtocol.TERRAIN_DATA,
        serverContext.gameMapData.getWidthTiles(),
        serverContext.gameMapData.getHeightTiles(),
        encodedTerrain), -1);
}
```

### Client Side - Receiving Terrain

```java
// In GameClient.java
case NetworkProtocol.TERRAIN_DATA -> {
    var msg = NetworkMessage.TerrainData.parse(parts);
    networkCallbackHandler.receiveTerrainData(msg.width(), msg.height(), msg.encodedData());
    logger.info("Received TERRAIN_DATA: {}x{} tiles, {} bytes", 
        msg.width(), msg.height(), msg.encodedData().length());
}

// In TankBattleGame.java
@Override
public void receiveTerrainData(int width, int height, String encodedData) {
    logger.info("Received terrain data from server: {}x{} tiles, {} bytes", 
        width, height, encodedData.length());
    this.receivedTerrainData = encodedData;
    this.terrainInfoReceivedForProcessing = true;
}

// Later in main thread
private void initializeMapAndTextures() {
    // Create map data structure
    GameMapData mapData = new GameMapData(mapWidthTiles, mapHeightTiles, mapTileSize);
    
    // Decode terrain from server
    TerrainEncoder.decode(mapData, receivedTerrainData);
    
    // Create client wrapper
    clientGameMap = new ClientGameMap(mapData);
    
    logger.info("Terrain decoded and initialized");
}
```

## Future Improvements

### Phase 3: Optimizations (Optional)
If bandwidth becomes an issue for larger maps:
1. **Compression** - Gzip or run-length encoding of terrain data
2. **Delta Updates** - Only send changed tiles on regeneration
3. **Chunking** - Split large maps into chunks for progressive loading

### Phase 4: Dynamic Terrain Sync (In Progress)
For destructible terrain and fire propagation:
1. ✅ Fire state changes already synced separately
2. ⏳ Define `TERRAIN_UPDATE` message for tile destruction
3. ⏳ Server sends delta updates when terrain overlay removed
4. ⏳ Client applies updates to local terrain grid

## Terrain Generation on Server

The server generates terrain using procedural generation:

```java
// In GameServer initialization
long seed = System.currentTimeMillis();
ProceduralTerrainGenerator terrainGen = new ProceduralTerrainGenerator(seed);
terrainGen.generateProceduralTerrain(
    serverContext.gameMapData, 
    BaseTerrainProfile.GRASSLAND
);
```

This ensures:
- Different terrain for each game session
- Single contiguous regions for overlays (water, forest)
- Natural-looking procedural maps using Perlin noise
- Server has full control over terrain

## Terrain Profiles

Available profiles (defined in `BaseTerrainProfile` enum):
- **GRASSLAND** - Grass base with water lake and forest
- **DESERT** - Sand base with oasis and rocky outcrops
- **DIRT_PLAINS** - Dirt base with muddy areas and rocks
- **MUDLANDS** - Mud base with water and sparse trees

Each profile defines:
- Base terrain type (covers 85-90% of map)
- Low-range overlay (5-10%, e.g., water)
- High-range overlay (5-10%, e.g., trees/rocks)

## Encoding Efficiency

The `TerrainEncoder` uses a compact format:
- Base terrain only: `1 digit + 1 comma` = ~2 bytes per tile
- With overlay: `1 digit + 1 colon + 1 digit + 1 comma` = ~4 bytes per tile

**Example encoding sizes:**
- 100x100 map (10,000 tiles) with 10% overlays: ~22 KB
- 200x200 map (40,000 tiles) with 10% overlays: ~88 KB
- 50x50 map (2,500 tiles) with 10% overlays: ~5.5 KB

This is efficient enough for small to medium multiplayer maps without compression.
