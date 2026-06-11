package org.chrisgruber.nettank.common.entities;

// Selectable tank chassis types. Stats are first-pass values (HEAVY vs LIGHT
// time-to-kill roughly even) — tune in playtests. Game modes may override via
// GameMode.getTankStats(TankType).
public enum TankType {
    STANDARD(TankStats.STANDARD),
    HEAVY(new TankStats(6, 70.0f, 40.0f, 0.7f, 350.0f, 2000L, 2, 2800L, 60.0f)),
    LIGHT(new TankStats(3, 140.0f, 70.0f, 0.7f, 350.0f, 2000L, 1, 1400L, 120.0f)),
    STEALTH(new TankStats(3, 110.0f, 50.0f, 0.7f, 350.0f, 2000L, 1, 2200L, 100.0f));

    private final TankStats defaultStats;

    TankType(TankStats defaultStats) {
        this.defaultStats = defaultStats;
    }

    public TankStats getDefaultStats() {
        return defaultStats;
    }

    public boolean hasCloak() {
        return this == STEALTH;
    }

    // Lenient parse for network/config input; unknown values fall back to STANDARD
    public static TankType fromString(String name) {
        if (name == null) return STANDARD;
        try {
            return TankType.valueOf(name.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return STANDARD;
        }
    }
}
