# Server-Client Terrain Protocol

## Overview

The terrain system uses a **server-authoritative** approach where the server generates the terrain and communicates the necessary parameters to clients for reproduction.

## Message Flow

### 1. Server Initialization
When a new game session starts:
1. Server generates a **unique random seed** based on `System.currentTimeMillis()` and map dimensions
2. Server selects a **terrain profile** (e.g., GRASSLAND, DESERT, DIRT_PLAINS, MUDLANDS)
3. Server generates the full terrain using `ProceduralTerrainGenerator` with the seed and profile
4. Server stores seed and profile name in `ServerContext`

### 2. Client Connection
When a client connects, the server sends initialization messages in this order:
1. **ASSIGN_ID** - Assigns player ID and color
2. **MAP_INFO** - Map dimensions and tile size
3. **TERRAIN_INIT** - Terrain seed and profile name (NEW)
4. **GAME_STATE** - Current game state
5. Other game state data (tanks, lives, etc.)

### 3. Client Terrain Generation
When the client receives both `MAP_INFO` and `TERRAIN_INIT`:
1. Client creates `ClientGameMap` with received parameters
2. Client uses the **same seed** and **same profile** as server
3. Client runs `ProceduralTerrainGenerator` to reproduce identical terrain
4. Because the noise algorithm is deterministic, identical seeds produce identical terrain

## Network Protocol

### TERRAIN_INIT Message Format
```
TER;<seed>;<profileName>
```

**Example:**
```
TER;1731140642993;GRASSLAND
```

**Parameters:**
- `seed` (long): Random seed for procedural generation
- `profileName` (String): Name of BaseTerrainProfile enum (e.g., "GRASSLAND", "DESERT")

## Code Locations

### Common (Shared)
- `NetworkProtocol.java` - Protocol constants
- `BaseTerrainProfile.java` - Terrain profile definitions
- `FastNoiseLite.java` - Deterministic noise algorithm

### Server
- `ServerContext.java` - Stores `terrainSeed` and `terrainProfileName`
- `GameServer.java` - Generates seed and sends TERRAIN_INIT message
- `ProceduralTerrainGenerator.java` - Server-side terrain generation

### Client
- `NetworkMessage.TerrainInit` - Type-safe message record
- `GameClient.java` - Receives and parses TERRAIN_INIT
- `TankBattleGame.java` - Stores terrain parameters
- `ClientGameMap.java` - Uses parameters to generate matching terrain
- `ProceduralTerrainGenerator.java` - Client-side terrain generation (mirrors server)

## Benefits of This Approach

### Advantages
1. **Bandwidth Efficient** - Only 2 parameters sent instead of full terrain grid
2. **Deterministic** - Identical seeds always produce identical terrain
3. **Scalable** - Works for any map size without increased network traffic
4. **Server Authority** - Server controls what terrain is generated
5. **Per-Session Randomness** - Each game session has unique terrain

### Current Limitations
1. **Redundant Code** - `ProceduralTerrainGenerator` duplicated in client and server modules
2. **Sync Assumption** - Assumes client/server have identical noise implementation
3. **No Mid-Game Updates** - Terrain changes (destruction, fire) not yet synchronized

## Future Improvements

### Phase 2: Remove Client-Side Generation (Recommended)
Instead of procedural generation on client:
1. Server generates terrain once
2. Server sends **compressed terrain data** to clients
3. Client receives and renders without generation logic
4. Benefits: Eliminates code duplication, allows custom/hand-crafted maps

### Phase 3: Dynamic Terrain Sync
For destructible terrain and fire propagation:
1. Define terrain update messages (e.g., `TERRAIN_UPDATE`)
2. Server sends delta updates when terrain changes
3. Client applies updates to local terrain grid
4. Use techniques like run-length encoding for efficiency

## Seed Generation

The server generates a unique seed for each game session:
```java
this.terrainSeed = System.currentTimeMillis() ^ (mapWidth * 31L + mapHeight * 17L);
```

This ensures:
- Different terrain for each game
- Incorporates map dimensions (prevents same seed for different map sizes)
- Simple and fast to compute

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

## Determinism Guarantee

The terrain generation is **deterministic** because:
1. `FastNoiseLite` produces consistent output for same seed
2. Flood-fill algorithms process tiles in consistent order
3. No random number generators after seed initialization
4. No time-dependent or system-dependent operations

This allows server and client to independently generate identical terrain from the same seed and profile.
