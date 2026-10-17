package org.chrisgruber.nettank.client.util;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

/**
 * Utility for processing game assets: alpha keying, team mask generation,
 * sprite sheet slicing, and resizing with high-quality bicubic filtering.
 */
public class AssetProcessor {

    public static BufferedImage loadImage(File file) throws IOException {
        return ImageIO.read(file);
    }

    public static void saveImage(BufferedImage image, File file) throws IOException {
        file.getParentFile().mkdirs();
        ImageIO.write(image, "PNG", file);
    }

    public static BufferedImage resize(BufferedImage src, int targetWidth, int targetHeight) {
        BufferedImage resized = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = resized.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.drawImage(src, 0, 0, targetWidth, targetHeight, null);
        g2.dispose();
        return resized;
    }

    /**
     * Removes background by flood-filling or chroma-keying from border corners.
     */
    public static BufferedImage removeBackground(BufferedImage src, Color keyColor, int tolerance) {
        int width = src.getWidth();
        int height = src.getHeight();
        BufferedImage dest = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);

        int keyR = keyColor.getRed();
        int keyG = keyColor.getGreen();
        int keyB = keyColor.getBlue();

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int rgb = src.getRGB(x, y);
                int a = (rgb >> 24) & 0xFF;
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;

                int dist = Math.abs(r - keyR) + Math.abs(g - keyG) + Math.abs(b - keyB);
                if (dist < tolerance) {
                    dest.setRGB(x, y, 0); // Transparent
                } else if (dist < tolerance + 30) {
                    // Soft alpha edge feathering
                    float factor = (float) (dist - tolerance) / 30.0f;
                    int newA = (int) (a * factor);
                    dest.setRGB(x, y, (newA << 24) | (r << 16) | (g << 8) | b);
                } else {
                    dest.setRGB(x, y, rgb);
                }
            }
        }
        return dest;
    }

    /**
     * Slices a grid sprite sheet (rows x cols) into individual frames.
     */
    public static BufferedImage[] sliceSpriteSheet(BufferedImage sheet, int rows, int cols, int frameWidth, int frameHeight) {
        BufferedImage[] frames = new BufferedImage[rows * cols];
        int count = 0;
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                BufferedImage frame = new BufferedImage(frameWidth, frameHeight, BufferedImage.TYPE_INT_ARGB);
                Graphics2D g2 = frame.createGraphics();
                g2.drawImage(sheet, 0, 0, frameWidth, frameHeight,
                        c * frameWidth, r * frameHeight, (c + 1) * frameWidth, (r + 1) * frameHeight, null);
                g2.dispose();
                frames[count++] = frame;
            }
        }
        return frames;
    }

    /**
     * Generates a team color mask where designated primary/accent regions are white (255)
     * and non-team regions (treads, metal, vents, shadows) are black (0).
     */
    public static BufferedImage generateTeamMask(BufferedImage hull, Color primaryAccent, int tolerance) {
        int width = hull.getWidth();
        int height = hull.getHeight();
        BufferedImage mask = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);

        int targetR = primaryAccent.getRed();
        int targetG = primaryAccent.getGreen();
        int targetB = primaryAccent.getBlue();

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int rgb = hull.getRGB(x, y);
                int a = (rgb >> 24) & 0xFF;
                if (a < 10) {
                    mask.setRGB(x, y, 0);
                    continue;
                }
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;

                int dist = Math.abs(r - targetR) + Math.abs(g - targetG) + Math.abs(b - targetB);
                if (dist < tolerance) {
                    mask.setRGB(x, y, 0xFFFFFFFF); // White = Team Colored
                } else {
                    mask.setRGB(x, y, 0xFF000000); // Black = Base Metal/Tread
                }
            }
        }
        return mask;
    }
}
