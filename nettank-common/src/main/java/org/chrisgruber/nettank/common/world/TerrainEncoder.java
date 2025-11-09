package org.chrisgruber.nettank.common.world;

/**
 * Encodes and decodes terrain data for network transmission.
 * Uses a compact format: each tile is encoded as "B" or "B:O" where B is base type ordinal and O is overlay type ordinal.
 * Tiles are separated by commas.
 */
public class TerrainEncoder {
    
    /**
     * Encodes terrain grid data into a compact string format.
     * Format: "base" or "base:overlay" separated by commas
     * Example: "2,2,2:4,2,2:4,2" = GRASSLAND, GRASSLAND, GRASSLAND+FOREST, GRASSLAND, GRASSLAND+FOREST, GRASSLAND
     */
    public static String encode(GameMapData mapData) {
        StringBuilder sb = new StringBuilder();
        int width = mapData.getWidthTiles();
        int height = mapData.getHeightTiles();
        
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (x > 0 || y > 0) {
                    sb.append(',');
                }
                
                TerrainTile tile = mapData.getTile(x, y);
                sb.append(tile.getBaseType().ordinal());
                
                if (tile.hasOverlay()) {
                    sb.append(':').append(tile.getOverlayType().ordinal());
                }
            }
        }
        
        return sb.toString();
    }
    
    /**
     * Decodes terrain grid data from a compact string format.
     */
    public static void decode(GameMapData mapData, String encodedData) {
        String[] tiles = encodedData.split(",");
        int width = mapData.getWidthTiles();
        int height = mapData.getHeightTiles();
        int expectedTiles = width * height;
        
        if (tiles.length != expectedTiles) {
            throw new IllegalArgumentException(
                String.format("Invalid terrain data: expected %d tiles but got %d", expectedTiles, tiles.length)
            );
        }
        
        int idx = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                String tileData = tiles[idx++];
                TerrainTile tile = mapData.getTile(x, y);
                
                if (tileData.contains(":")) {
                    // Has overlay
                    String[] parts = tileData.split(":");
                    int baseOrdinal = Integer.parseInt(parts[0]);
                    int overlayOrdinal = Integer.parseInt(parts[1]);
                    
                    tile.setBaseType(TerrainType.values()[baseOrdinal]);
                    tile.setOverlayType(TerrainType.values()[overlayOrdinal]);
                } else {
                    // Base only
                    int baseOrdinal = Integer.parseInt(tileData);
                    tile.setBaseType(TerrainType.values()[baseOrdinal]);
                    tile.setOverlayType(null);
                }
            }
        }
    }
}
