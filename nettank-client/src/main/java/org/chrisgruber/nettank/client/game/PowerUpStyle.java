package org.chrisgruber.nettank.client.game;

import org.chrisgruber.nettank.common.entities.PowerUpType;
import org.joml.Vector3f;

/**
 * Look and sound of each power-up: category colour, short arcade label, tier and the
 * sounds played on pickup. Shared by the in-world pickup, its effects and the HUD.
 */
public final class PowerUpStyle {

    private static final String SOUNDS_DIR = "sounds/powerups/";

    public static final String SOUND_SPAWN = SOUNDS_DIR + "spawn";
    public static final String SOUND_EXPIRE = SOUNDS_DIR + "expire";
    public static final String SOUND_BUFF_END = SOUNDS_DIR + "buff_end";
    public static final String SOUND_BUFF_WARNING = SOUNDS_DIR + "buff_warning";
    // Extra sparkle layered over the pickup sound of x3 power-ups
    public static final String SOUND_TIER3_SPARKLE = SOUNDS_DIR + "tier3_sparkle";

    private PowerUpStyle() {}

    public static Vector3f color(PowerUpType.Category category) {
        return switch (category) {
            case DAMAGE -> new Vector3f(1.0f, 0.27f, 0.22f);
            case RELOAD -> new Vector3f(1.0f, 0.62f, 0.12f);
            case SPEED -> new Vector3f(0.25f, 0.62f, 1.0f);
            case AMMO -> new Vector3f(0.78f, 0.45f, 1.0f);
            case ARMOR -> new Vector3f(0.3f, 0.92f, 1.0f);
            case HEALTH -> new Vector3f(0.35f, 1.0f, 0.45f);
        };
    }

    public static Vector3f color(PowerUpType type) {
        return color(type.getCategory());
    }

    /** Short label shown over the pickup and in the HUD (the bitmap font is ASCII-only). */
    public static String label(PowerUpType type) {
        return switch (type) {
            case DAMAGE_2X -> "DAMAGE x2";
            case DAMAGE_3X -> "DAMAGE x3";
            case RELOAD_2X -> "RELOAD x2";
            case RELOAD_3X -> "RELOAD x3";
            case SPEED_2X -> "SPEED x2";
            case SPEED_3X -> "SPEED x3";
            case UNLIMITED_AMMO -> "INFINITE AMMO";
            case REPAIR_ARMOR -> "ARMOR +1";
            case REPAIR_HP -> "HP +2";
        };
    }

    /** 3 for the rare x3 power-ups (gold rim, light rays, extra sparkle), otherwise 1. */
    public static int tier(PowerUpType type) {
        return switch (type) {
            case DAMAGE_3X, RELOAD_3X, SPEED_3X -> 3;
            default -> 1;
        };
    }

    public static String pickupSound(PowerUpType type) {
        return SOUNDS_DIR + "pickup_" + type.getCategory().name().toLowerCase();
    }

    public static String iconPath(PowerUpType type) {
        return "textures/powerups/" + type.name().toLowerCase() + ".png";
    }
}
