package org.chrisgruber.nettank.common.world;

public enum VisionBlockingType {
    NONE(false, 0.0f),
    PARTIAL(true, 0.3f),
    FULL(true, 1.0f);

    private final boolean blocksVision;
    private final float blockingStrength;

    VisionBlockingType(boolean blocksVision, float blockingStrength) {
        this.blocksVision = blocksVision;
        this.blockingStrength = blockingStrength;
    }

    public boolean blocksVision() {
        return blocksVision;
    }

    public float getBlockingStrength() {
        return blockingStrength;
    }
}
