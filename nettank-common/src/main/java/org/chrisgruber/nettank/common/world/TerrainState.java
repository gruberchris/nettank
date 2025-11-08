package org.chrisgruber.nettank.common.world;

public enum TerrainState {
    NORMAL(1.0f, false),
    IGNITING(1.0f, true),
    BURNING(0.7f, true),
    SMOLDERING(0.8f, true),
    SCORCHED(0.9f, false),
    FLOODED(0.5f, false),
    FROZEN(0.8f, false);

    private final float speedModifier;
    private final boolean hasVisualEffect;

    TerrainState(float speedModifier, boolean hasVisualEffect) {
        this.speedModifier = speedModifier;
        this.hasVisualEffect = hasVisualEffect;
    }

    public float getSpeedModifier() {
        return speedModifier;
    }

    public boolean hasVisualEffect() {
        return hasVisualEffect;
    }
}
