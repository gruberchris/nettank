package org.chrisgruber.nettank.common.entities;

// Hull sides carrying directional armor. Quadrants are relative to hull rotation
// (the chassis, not the turret): FRONT spans +/-45 degrees around the facing.
public enum ArmorSide {
    FRONT,
    LEFT,
    RIGHT,
    REAR;

    public static ArmorSide fromString(String name) {
        try {
            return ArmorSide.valueOf(name.trim().toUpperCase());
        } catch (IllegalArgumentException | NullPointerException e) {
            return FRONT;
        }
    }
}
