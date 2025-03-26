package org.chrisgruber.nettank.util;

import org.joml.Vector3f;
import java.util.List;

public class Colors {

    // Define 6 distinct colors for tanks
    public static final Vector3f RED = new Vector3f(1.0f, 0.2f, 0.2f);
    public static final Vector3f BLUE = new Vector3f(0.2f, 0.5f, 1.0f);
    public static final Vector3f GREEN = new Vector3f(0.2f, 1.0f, 0.2f);
    public static final Vector3f YELLOW = new Vector3f(1.0f, 1.0f, 0.2f);
    public static final Vector3f MAGENTA = new Vector3f(1.0f, 0.2f, 1.0f);
    public static final Vector3f CYAN = new Vector3f(0.2f, 1.0f, 1.0f);

    // List for easy access/assignment by the server
    public static final List<Vector3f> TANK_COLORS = List.of(
            RED, BLUE, GREEN, YELLOW, MAGENTA, CYAN
    );

    // Other useful colors
    public static final Vector3f WHITE = new Vector3f(1.0f, 1.0f, 1.0f);
    public static final Vector3f BLACK = new Vector3f(0.0f, 0.0f, 0.0f);
    public static final Vector3f GRAY = new Vector3f(0.5f, 0.5f, 0.5f);
}
