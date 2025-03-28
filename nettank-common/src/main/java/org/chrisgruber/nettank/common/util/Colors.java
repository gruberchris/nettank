package org.chrisgruber.nettank.common.util;

import org.joml.Vector3f;
import java.util.List;

public class Colors {
    // Basic Colors
    public static final Vector3f WHITE = new Vector3f(1.0f, 1.0f, 1.0f);
    public static final Vector3f BLACK = new Vector3f(0.0f, 0.0f, 0.0f);
    public static final Vector3f RED = new Vector3f(1.0f, 0.0f, 0.0f);
    public static final Vector3f GREEN = new Vector3f(0.0f, 1.0f, 0.0f);
    public static final Vector3f BLUE = new Vector3f(0.0f, 0.0f, 1.0f);
    public static final Vector3f YELLOW = new Vector3f(1.0f, 1.0f, 0.0f);
    public static final Vector3f CYAN = new Vector3f(0.0f, 1.0f, 1.0f);
    public static final Vector3f MAGENTA = new Vector3f(1.0f, 0.0f, 1.0f);
    public static final Vector3f ORANGE = new Vector3f(1.0f, 0.5f, 0.0f);
    public static final Vector3f GRAY = new Vector3f(0.5f, 0.5f, 0.5f);
    public static final Vector3f DARK_GRAY = new Vector3f(0.2f, 0.2f, 0.2f);

    // Tank Colors (Used by server for assignment)
    public static final List<Vector3f> TANK_COLORS = List.of(
            new Vector3f(1.0f, 0.2f, 0.2f), // Red-ish
            new Vector3f(0.2f, 0.5f, 1.0f), // Blue-ish
            new Vector3f(0.2f, 1.0f, 0.2f), // Green-ish
            new Vector3f(1.0f, 1.0f, 0.2f), // Yellow-ish
            new Vector3f(1.0f, 0.2f, 1.0f), // Magenta-ish
            new Vector3f(0.2f, 1.0f, 1.0f)  // Cyan-ish
            // Add more distinct colors if MAX_PLAYERS > 6
    );
}