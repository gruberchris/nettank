package org.chrisgruber.nettank.common.world;

public enum TerrainType {
    GRASS(1.0f, true, VisionBlockingType.NONE, Flammability.MEDIUM, 5000L),
    DIRT(0.95f, true, VisionBlockingType.NONE, Flammability.NONE, 0L),
    MUD(0.6f, true, VisionBlockingType.NONE, Flammability.NONE, 0L),
    SHALLOW_WATER(0.4f, true, VisionBlockingType.NONE, Flammability.NONE, 0L),
    DEEP_WATER(0.0f, false, VisionBlockingType.NONE, Flammability.NONE, 0L),
    SAND(0.85f, true, VisionBlockingType.NONE, Flammability.NONE, 0L),
    STONE(1.0f, true, VisionBlockingType.NONE, Flammability.NONE, 0L),
    FOREST(0.7f, true, VisionBlockingType.PARTIAL, Flammability.HIGH, 15000L),
    MOUNTAIN(0.0f, false, VisionBlockingType.FULL, Flammability.NONE, 0L);

    private final float speedModifier;
    private final boolean passable;
    private final VisionBlockingType visionBlocking;
    private final Flammability flammability;
    private final long burnDuration;

    TerrainType(float speedModifier, boolean passable, VisionBlockingType visionBlocking,
                Flammability flammability, long burnDuration) {
        this.speedModifier = speedModifier;
        this.passable = passable;
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
