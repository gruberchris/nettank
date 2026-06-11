package org.chrisgruber.nettank.common.entities;

// Per-tank combat stats. Game modes may override the base stats to rebalance.
public record TankStats(
        int maxHitPoints,
        float moveSpeed,
        float turnSpeed,
        float backwardSpeedFactor,
        float bulletSpeed,
        long bulletLifetimeMs,
        int bulletDamage,
        long shootCooldownMs,
        float turretTurnSpeed,
        int frontArmor,
        int leftArmor,
        int rightArmor,
        int rearArmor,
        float sightRadius
) {
    // Matches the legacy flat combat constants so introducing stats changes no behavior.
    public static final TankStats STANDARD = new TankStats(
            4,      // maxHitPoints
            100.0f, // moveSpeed (px/s)
            50.0f,  // turnSpeed (deg/s)
            0.7f,   // backwardSpeedFactor
            350.0f, // bulletSpeed (px/s)
            2000L,  // bulletLifetimeMs
            1,      // bulletDamage
            2000L,  // shootCooldownMs
            90.0f,  // turretTurnSpeed (deg/s)
            2, 1, 1, 1, // front/left/right/rear armor
            500.0f  // sightRadius (px)
    );

    public int armorFor(ArmorSide side) {
        return switch (side) {
            case FRONT -> frontArmor;
            case LEFT -> leftArmor;
            case RIGHT -> rightArmor;
            case REAR -> rearArmor;
        };
    }
}
