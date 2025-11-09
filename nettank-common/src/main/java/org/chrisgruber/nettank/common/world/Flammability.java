package org.chrisgruber.nettank.common.world;

public enum Flammability {
    NONE(0.0f, 0.0f),
    LOW(0.1f, 0.05f),
    MEDIUM(0.4f, 0.15f),
    HIGH(0.8f, 0.35f),
    EXTREME(1.0f, 0.5f);

    private final float ignitionChance;
    private final float spreadChance;

    Flammability(float ignitionChance, float spreadChance) {
        this.ignitionChance = ignitionChance;
        this.spreadChance = spreadChance;
    }

    public float getIgnitionChance() {
        return ignitionChance;
    }

    public float getSpreadChance() {
        return spreadChance;
    }
}
