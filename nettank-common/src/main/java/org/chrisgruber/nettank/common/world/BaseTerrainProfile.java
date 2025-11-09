package org.chrisgruber.nettank.common.world;

public enum BaseTerrainProfile {
    
    GRASSLAND(
        "Grassland",
        "Large grassy plains with a lake and forest",
        TerrainType.GRASS,              // Base (85-90%)
        TerrainType.SHALLOW_WATER,      // LOW (5-10%)
        TerrainType.FOREST,             // HIGH (5-10%)
        0.08f,                          // LOW percent
        0.07f                           // HIGH percent
    ),
    
    DESERT(
        "Desert",
        "Vast sandy desert with oasis and mountains",
        TerrainType.SAND,               // Base (85-90%)
        TerrainType.GRASS,              // LOW (oasis) (5-10%)
        TerrainType.FOREST,             // HIGH (5-10%)
        0.05f,                          // LOW percent (rare oasis)
        0.10f                           // HIGH percent
    ),
    
    DIRT_PLAINS(
        "Dirt Plains",
        "Dirt terrain with muddy areas and rocky outcrops",
        TerrainType.DIRT,               // Base (85-90%)
        TerrainType.MUD,                // LOW (5-10%)
        TerrainType.FOREST,             // HIGH (5-10%)
        0.10f,                          // LOW percent
        0.05f                           // HIGH percent
    ),
    
    MUDLANDS(
        "Swamp",
        "Muddy swampland with deep water and trees",
        TerrainType.MUD,                // Base (85-90%)
        TerrainType.SHALLOW_WATER,      // LOW (5-10%)
        TerrainType.FOREST,             // HIGH (5-10%)
        0.12f,                          // LOW percent (more water)
        0.05f                           // HIGH percent
    );
    
    private final String name;
    private final String description;
    private final TerrainType baseType;
    private final TerrainType lowType;
    private final TerrainType highType;
    private final float lowPercent;
    private final float highPercent;
    
    BaseTerrainProfile(String name, String description,
                       TerrainType base, TerrainType low, TerrainType high, 
                       float lowPct, float highPct) {
        this.name = name;
        this.description = description;
        this.baseType = base;
        this.lowType = low;
        this.highType = high;
        this.lowPercent = lowPct;
        this.highPercent = highPct;
    }
    
    public String getName() {
        return name;
    }
    
    public String getDescription() {
        return description;
    }
    
    public TerrainType getBaseType() {
        return baseType;
    }
    
    public TerrainType getLowType() {
        return lowType;
    }
    
    public TerrainType getHighType() {
        return highType;
    }
    
    public float getLowPercent() {
        return lowPercent;
    }
    
    public float getHighPercent() {
        return highPercent;
    }
    
    public float getBasePercent() {
        return 1.0f - lowPercent - highPercent;
    }
}
