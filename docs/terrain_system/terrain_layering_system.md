# Terrain Layering System - Implementation

## Overview

The terrain system now supports **two-layer rendering**:

1. **Base Layer**: Opaque terrain (grass, dirt, sand, etc.)
2. **Overlay Layer**: Transparent terrain/entities rendered on top (water, trees, etc.)

This allows you to have a grass base with water puddles and trees placed on top, creating a more realistic and flexible terrain system.

## Architecture

### TerrainTile Structure

```java
public class TerrainTile {
    private TerrainType baseType;      // Base layer (always present)
    private TerrainType overlayType;   // Overlay layer (optional, can be null)
    private TerrainState currentState; // State effects (scorched, etc.)
    
    // Overlay management
    public boolean hasOverlay()        // Check if overlay exists
    public TerrainType getEffectiveType() // Returns overlay if exists, else base
}
```

### Rendering Order

```
Bottom → Top:
1. Base Terrain (opaque)
2. Overlay Terrain (transparent)
3. State Overlay (scorched, burning, etc.)
```

## Procedural Generation with Layers

### Algorithm

```
Step 1: Fill entire map with BASE terrain
   ┌─────────────────────────────┐
   │ GGGGGGGGGGGGGGGGGGGGGGGGGG │
   │ GGGGGGGGGGGGGGGGGGGGGGGGGG │
   │ GGGGGGGGGGGGGGGGGGGGGGGGGG │
   │ GGGGGGGGGGGGGGGGGGGGGGGGGG │
   └─────────────────────────────┘
   100% grass base

Step 2: Generate noise for OVERLAYS
   ┌─────────────────────────────┐
   │ GGWGGGFGGGGGWGGFGGGWGGFGGG │
   │ GGGWGGFGGWFGWGGGWGGFGGWGGG │
   │ GGGGFGGGWGFGGGWGGGFGGGWGGG │
   └─────────────────────────────┘
   Base: GRASS everywhere
   Overlays: Scattered W (water) and F (forest)

Step 3: Flood fill - keep largest regions
   ┌─────────────────────────────┐
   │ GGGGGGGGGGGGGGGGGGGGGGGGGG │
   │ GGGWWWWWGGGGGGGGGGGGGGGGGG │
   │ GGGWWWWWGGGGGGGGGGGGFFFFFF │
   │ GGGWWWWWGGGGGGGGGGGGFFFFFF │
   └─────────────────────────────┘
   Base: GRASS (100%)
   Overlays: Single water region + single forest region
```

### Visual Result

When rendered:
```
Player sees:
- Green grass texture everywhere (base layer)
- Blue water texture in one contiguous region (overlay)
- Tree texture in one contiguous region (overlay)
- Water and trees appear "on top" of grass
```

## Profile System

### GRASSLAND Profile (Active)

```java
BaseTerrainProfile.GRASSLAND:
  Base:    GRASS (100% of map)
  Overlay: SHALLOW_WATER (8% - single lake)
  Overlay: FOREST (7% - single tree cluster)
```

**Rendering:**
- Base: `Summer_Grass.png` (opaque, covers entire map)
- Overlay 1: `Shallow_Water.png` (transparent, shows grass underneath)
- Overlay 2: `Summer_Tree.png` (transparent, trees on grass)

**Result:** Green grassy field with a lake and forest area

### DESERT Profile

```java
BaseTerrainProfile.DESERT:
  Base:    SAND (100% of map)
  Overlay: GRASS (5% - oasis)
  Overlay: FOREST (10% - mountains/rocky area)
```

**Rendering:**
- Base: `Desert.png` (sandy texture everywhere)
- Overlay 1: `Summer_Grass.png` (grass oasis on sand)
- Overlay 2: `Summer_Tree.png` (trees/rocks on sand)

**Result:** Sandy desert with green oasis and mountain area

### DIRT_PLAINS Profile

```java
BaseTerrainProfile.DIRT_PLAINS:
  Base:    DIRT (100% of map)
  Overlay: MUD (10% - muddy region)
  Overlay: FOREST (5% - rocky outcrops)
```

**Rendering:**
- Base: `Dirt_Field.png` (dirt everywhere)
- Overlay 1: `Mud_Field.png` (muddy patch on dirt)
- Overlay 2: `Summer_Tree.png` (rocks/vegetation on dirt)

**Result:** Dirt terrain with muddy area and rocky region

### MUDLANDS Profile (Swamp)

```java
BaseTerrainProfile.MUDLANDS:
  Base:    MUD (100% of map)
  Overlay: SHALLOW_WATER (12% - bog/swamp water)
  Overlay: FOREST (5% - trees)
```

**Rendering:**
- Base: `Mud_Field.png` (muddy everywhere)
- Overlay 1: `Shallow_Water.png` (water pools on mud)
- Overlay 2: `Summer_Tree.png` (swamp trees on mud)

**Result:** Muddy swamp with water pools and tree clusters

## Texture Requirements

### Required Textures

All textures should be **32x32 pixels** (or consistent size):

**Base Terrain Textures (Opaque):**
- ✅ `Summer_Grass.png` - Green grass
- ✅ `Dirt_Field.png` - Brown dirt
- ✅ `Mud_Field.png` - Dark muddy ground
- ✅ `Desert.png` - Sandy yellow/tan

**Overlay Terrain Textures (Should have transparency):**
- ✅ `Shallow_Water.png` - Blue water (transparent edges)
- ✅ `Summer_Tree.png` - Tree/forest (transparent background)

**State Overlay Textures:**
- ✅ `Scorched.png` - Burned/charred effect

### Transparency Guidelines

**Overlay textures should:**
- Have transparent backgrounds (alpha channel)
- Allow base terrain to show through
- Use semi-transparency for natural blending

**Example: Summer_Tree.png**
```
Should look like:
┌──────────┐
│ ░▒▓█▓▒░  │  ← Tree sprite with transparent background
│   ░█░    │  ← Grass shows through around edges
└──────────┘
```

## Code Implementation

### Server Side

`ProceduralTerrainGenerator.java` (Server):

```java
public void generateProceduralTerrain(GameMapData mapData, BaseTerrainProfile profile) {
    // Step 1: Fill with base terrain
    fillAllWithBaseTerrain(mapData, profile.getBaseType());
    
    // Step 2: Generate noise-based overlays
    float[][] noiseMap = generateNoiseMap(width, height);
    assignOverlaysFromNoise(mapData, noiseMap, profile);
    
    // Step 3: Keep only largest contiguous overlay regions
    keepLargestContiguousOverlayRegion(mapData, profile.getLowType());
    keepLargestContiguousOverlayRegion(mapData, profile.getHighType());
}

private void fillAllWithBaseTerrain(GameMapData mapData, TerrainType baseType) {
    // Fill entire map with base terrain
    for (int y = 0; y < height; y++) {
        for (int x = 0; x < width; x++) {
            mapData.getTile(x, y).setBaseType(baseType);
        }
    }
}

private void assignOverlaysFromNoise(GameMapData mapData, float[][] noiseMap, 
                                      BaseTerrainProfile profile) {
    for (int y = 0; y < height; y++) {
        for (int x = 0; x < width; x++) {
            float value = noiseMap[x][y];
            TerrainType overlayType = null;
            
            if (value < lowThreshold) {
                overlayType = profile.getLowType();  // Water/oasis
            } else if (value > highThreshold) {
                overlayType = profile.getHighType(); // Forest/mountains
            }
            // else: no overlay, base terrain shows through
            
            mapData.getTile(x, y).setOverlayType(overlayType);
        }
    }
}

private void keepLargestContiguousOverlayRegion(GameMapData mapData, TerrainType targetType) {
    List<Region> regions = findAllOverlayRegions(mapData, targetType);
    
    if (regions.isEmpty()) return;
    
    // Sort by size, keep largest
    regions.sort((a, b) -> Integer.compare(b.size(), a.size()));
    
    // Remove all smaller regions
    for (int i = 1; i < regions.size(); i++) {
        for (Point tile : regions.get(i).tiles) {
            mapData.getTile(tile.x, tile.y).setOverlayType(null);
        }
    }
}
```

### Client Side

`ClientGameMap.java` render method:

```java
// Draw base terrain first (opaque)
TerrainType baseType = tile.getBaseType();
Texture baseTexture = terrainTextures.get(baseType);
if (baseTexture != null) {
    baseTexture.bind();
    renderer.drawQuad(x, y, size, size, 0, shader);
}

// Draw overlay terrain on top (transparent)
if (tile.hasOverlay()) {
    TerrainType overlayType = tile.getOverlayType();
    Texture overlayTexture = terrainTextures.get(overlayType);
    if (overlayTexture != null) {
        overlayTexture.bind();
        renderer.drawQuad(x, y, size, size, 0, shader);
    }
}

// Draw state overlay (scorched, etc.) on top of everything
if (tile.getCurrentState() == TerrainState.SCORCHED) {
    Texture scorchedTexture = stateOverlayTextures.get(TerrainState.SCORCHED);
    if (scorchedTexture != null) {
        scorchedTexture.bind();
        renderer.drawQuad(x, y, size, size, 0, shader);
    }
}
```

## Gameplay Integration

### Movement System

The `getEffectiveType()` method returns the overlay if it exists, otherwise the base:

```java
public float getEffectiveSpeedModifier() {
    TerrainType effectiveType = getEffectiveType(); // Overlay or base
    return effectiveType.getSpeedModifier() * currentState.getSpeedModifier();
}

public boolean isPassable() {
    TerrainType effectiveType = getEffectiveType();
    return effectiveType.isPassable();
}
```

**Example:**
- Tile has: Base=GRASS, Overlay=FOREST
- `getEffectiveType()` returns FOREST
- Movement speed uses FOREST's speed modifier (slower)
- Visual: Player sees tree on grass, moves slowly through trees

### Future: Destructible Overlays

The overlay system enables future destructible terrain:

```java
// When tank fires at tree:
if (tile.hasOverlay() && tile.getOverlayType() == TerrainType.FOREST) {
    tile.setOverlayType(null);  // Remove tree overlay
    // Base grass terrain is revealed!
}
```

**Result:** Tree is destroyed, grass underneath is revealed.

### Future: Fire Spread

Fire could spread through overlays:

```java
// Forest tile catches fire
if (tile.getOverlayType() == TerrainType.FOREST) {
    tile.setCurrentState(TerrainState.BURNING);
    // After burn duration:
    tile.setOverlayType(null);        // Remove forest
    tile.setCurrentState(TerrainState.SCORCHED); // Mark as burned
}
```

**Result:** Trees burn and disappear, leaving scorched grass.

## Benefits of Layering System

### ✅ Advantages

1. **Flexible Composition**
   - Base terrain everywhere
   - Add/remove overlays dynamically
   - Multiple overlay types possible

2. **Natural Blending**
   - Transparent overlays show base terrain
   - More realistic visual appearance
   - Trees appear "on" grass, not replacing it

3. **Destructible Terrain**
   - Remove overlay, base remains
   - Enable gameplay: destroy trees, drain water, etc.

4. **Memory Efficient**
   - Base terrain: 1 type per tile
   - Overlay: nullable (no memory if not used)
   - State: separate system

5. **Easy to Extend**
   - Add new overlay types easily
   - Combine different overlays
   - Future: multiple overlay layers

### Example Scenarios

**Scenario 1: Tank drives over grass with tree**
```
Before: Base=GRASS, Overlay=FOREST → Slow movement
Tank destroys tree
After:  Base=GRASS, Overlay=null   → Normal movement
Visual: Tree disappears, grass remains
```

**Scenario 2: Explosion in water**
```
Before: Base=GRASS, Overlay=SHALLOW_WATER
Explosion hits
After:  Base=GRASS, Overlay=null, State=SCORCHED
Visual: Water evaporates, reveals scorched grass
```

**Scenario 3: Fire spreads through forest**
```
T=0: Base=GRASS, Overlay=FOREST → Green grass + trees
T=1: Base=GRASS, Overlay=FOREST, State=BURNING → Fire!
T=2: Base=GRASS, Overlay=null, State=SCORCHED → Burned field
T=3: Base=GRASS, Overlay=null, State=NORMAL → Grass regrows
```

## Current Status

### ✅ Implemented

- [x] Two-layer TerrainTile (base + overlay)
- [x] Procedural generation with layering
- [x] Flood fill for contiguous overlay regions
- [x] Client rendering of base + overlay
- [x] Texture loading for overlays
- [x] Profile system with overlay definitions

### 🔧 Configuration

**Active Profile:** GRASSLAND
- Base: GRASS (100%)
- Overlay: SHALLOW_WATER (8%)
- Overlay: FOREST (7%)

**Textures Loaded:**
- Base: `Summer_Grass.png`
- Overlay: `Shallow_Water.png`
- Overlay: `Summer_Tree.png`

### 📊 Expected Result

When you run the game, you should see:
1. **Green grass everywhere** (base layer)
2. **Blue water region** overlaid on grass (8% of map)
3. **Tree region** overlaid on grass (7% of map)
4. **Natural organic shapes** from Perlin noise
5. **Single contiguous regions** (no scattered patches)

## Next Steps

### Phase 1: Visual Verification ✅
- [x] Ensure textures have transparency
- [x] Check rendering order (base → overlay → state)
- [x] Verify grass shows through transparent areas

### Phase 2: Destructible Terrain (Future)
- [ ] Add health to overlay tiles
- [ ] Implement overlay destruction on hit
- [ ] Add destruction effects/particles
- [ ] Network sync for destroyed overlays

### Phase 3: Dynamic Terrain (Future)
- [ ] Water can spread/drain
- [ ] Trees can burn and spread fire
- [ ] Terrain can regenerate over time
- [ ] Seasonal changes (different tree sprites)

### Phase 4: Advanced Layering (Future)
- [ ] Multiple overlay layers
- [ ] Entity placement on terrain
- [ ] Buildings as overlays
- [ ] Roads/paths as overlays

## Summary

The new terrain layering system provides:

**Foundation:** Base terrain covers entire map
**Overlays:** Transparent terrain/entities on top
**Flexibility:** Add/remove overlays dynamically
**Visuals:** Natural blending of layers
**Gameplay:** Enables destructible terrain

**Current Implementation:**
- Grassland base with water and tree overlays
- Procedurally generated with single contiguous regions
- Rendered in correct order for transparency
- Ready for future destructible terrain features!

🎮 **Test it out and see your layered terrain in action!**
