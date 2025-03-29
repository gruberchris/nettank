package org.chrisgruber.nettank.common.util;

import org.joml.Vector3f;

import java.util.ArrayList;
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

    /**
     * Generates a list of distinct colors with good contrast for tank assignment.
     *
     * @param count Number of distinct colors to generate
     * @return List of Vector3f colors in RGB format
     */
    public static List<Vector3f> generateDistinctColors(int count) {
        List<Vector3f> colors = new ArrayList<>(count);

        // Use golden ratio to distribute hues evenly
        float goldenRatioConjugate = 0.618033988749895f;
        float hue = 0.1f; // Starting hue offset

        for (int i = 0; i < count; i++) {
            // Advance to next hue using golden ratio conjugate
            hue = (hue + goldenRatioConjugate) % 1.0f;

            // Use high saturation and value for visibility
            float saturation = (float) (0.85f + (Math.random() * 0.1f)); // 0.85-0.95
            float value = (float) (0.85f + (Math.random() * 0.1f));      // 0.85-0.95

            // Convert HSV to RGB
            Vector3f rgb = hsvToRgb(hue, saturation, value);
            colors.add(rgb);
        }

        return colors;
    }

    /**
     * Converts HSV (hue, saturation, value) color values to RGB Vector3f
     */
    private static Vector3f hsvToRgb(float hue, float saturation, float value) {
        final int HUE_SEGMENTS = 6;

        int hueSection = (int)(hue * HUE_SEGMENTS);
        float hueDecimal = hue * HUE_SEGMENTS - hueSection;
        float minValue = value * (1 - saturation);
        float decreasing = value * (1 - hueDecimal * saturation);
        float increasing = value * (1 - (1 - hueDecimal) * saturation);

        float red;
        float green;
        float blue;

        switch (hueSection) {
            case 0: red = value;      green = increasing; blue = minValue;   break;
            case 1: red = decreasing; green = value;      blue = minValue;   break;
            case 2: red = minValue;   green = value;      blue = increasing; break;
            case 3: red = minValue;   green = decreasing; blue = value;      break;
            case 4: red = increasing; green = minValue;   blue = value;      break;
            case 5: red = value;      green = minValue;   blue = decreasing; break;
            default:
                // If we somehow get an invalid hueSection, default to case 0
                red = value;          green = increasing; blue = minValue;   break;
        }

        return new Vector3f(red, green, blue);
    }
}