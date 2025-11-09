package org.chrisgruber.nettank.common.world;

public enum TerrainType {
    GRASS(1.0f, true, false, false, VisionBlockingType.NONE, Flammability.MEDIUM, 5000L),
    DIRT(0.95f, true, false, false, VisionBlockingType.NONE, Flammability.NONE, 0L),
    MUD(0.6f, true, false, false, VisionBlockingType.NONE, Flammability.NONE, 0L),
    SHALLOW_WATER(0.4f, false, false, false, VisionBlockingType.NONE, Flammability.NONE, 0L),
    DEEP_WATER(0.0f, false, false, false, VisionBlockingType.NONE, Flammability.NONE, 0L),
    SAND(0.85f, true, false, false, VisionBlockingType.NONE, Flammability.NONE, 0L),
    STONE(1.0f, true, false, false, VisionBlockingType.NONE, Flammability.NONE, 0L),
    FOREST(0.7f, false, true, true, VisionBlockingType.PARTIAL, Flammability.HIGH, 15000L),
    MOUNTAIN(0.0f, false, false, false, VisionBlockingType.FULL, Flammability.NONE, 0L);

    private final float speedModifier;
    private final boolean passable;
    private final boolean blocksBullets;
    private final boolean destructible;
    private final VisionBlockingType visionBlocking;
    private final Flammability flammability;
    private final long burnDuration;

    TerrainType(float speedModifier, boolean passable, boolean blocksBullets, boolean destructible,
                VisionBlockingType visionBlocking, Flammability flammability, long burnDuration) {
        this.speedModifier = speedModifier;
        this.passable = passable;
        this.blocksBullets = blocksBullets;
        this.destructible = destructible;
        this.visionBlocking = visionBlocking;
        this.flammability = flammability;
        this.burnDuration = burnDuration;
    }

    public float getSpeedModifier() {
        return speedModifier;
    }

    public boolean isPassable() {
        return passable;
    }

    public boolean blocksBullets() {
        return blocksBullets;
    }

    public boolean isDestructible() {
        return destructible;
    }

    public VisionBlockingType getVisionBlocking() {
        return visionBlocking;
    }

    public Flammability getFlammability() {
        return flammability;
    }

    public long getBurnDuration() {
        return burnDuration;
    }
}
