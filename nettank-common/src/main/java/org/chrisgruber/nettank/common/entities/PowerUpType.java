package org.chrisgruber.nettank.common.entities;

// Battlefield pickups. Timed buffs hold one slot per category (same-category
// pickups replace the active buff; cross-category buffs stack). Instant types
// (duration 0) apply immediately and never occupy a category slot.
public enum PowerUpType {
    DAMAGE_2X(Category.DAMAGE, 2.0f, 15000L),
    DAMAGE_3X(Category.DAMAGE, 3.0f, 8000L),
    RELOAD_2X(Category.RELOAD, 2.0f, 15000L),
    RELOAD_3X(Category.RELOAD, 3.0f, 8000L),
    SPEED_2X(Category.SPEED, 2.0f, 15000L),
    SPEED_3X(Category.SPEED, 3.0f, 8000L),
    UNLIMITED_AMMO(Category.AMMO, 1.0f, 15000L),
    REPAIR_ARMOR(Category.ARMOR, 0.0f, 0L),
    REPAIR_HP(Category.HEALTH, 0.0f, 0L);

    public enum Category { DAMAGE, RELOAD, SPEED, AMMO, ARMOR, HEALTH }

    private final Category category;
    private final float multiplier;
    private final long durationMs;

    PowerUpType(Category category, float multiplier, long durationMs) {
        this.category = category;
        this.multiplier = multiplier;
        this.durationMs = durationMs;
    }

    public Category getCategory() { return category; }
    public float getMultiplier() { return multiplier; }
    public long getDurationMs() { return durationMs; }
    public boolean isInstant() { return durationMs == 0L; }

    public static PowerUpType fromString(String name) {
        try {
            return PowerUpType.valueOf(name.trim().toUpperCase());
        } catch (IllegalArgumentException | NullPointerException e) {
            return null;
        }
    }
}
