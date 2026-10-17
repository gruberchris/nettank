package org.chrisgruber.nettank.client.util;

import org.chrisgruber.nettank.client.game.PowerUpStyle;
import org.chrisgruber.nettank.common.entities.PowerUpType;
import org.joml.Vector3f;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

/**
 * Generates the power-up art: one 64x64 token per power-up type and the white effect
 * sprites (glow, shadow, ring, sparkle, rays, particle, HUD tick) that are tinted at
 * runtime. Arcade-military hybrid: a bright token per category with a dark outline that
 * pops off the terrain, rivets and a panel line, a bold glyph, and a tier badge
 * (gold rim and badge for x3). Each category has its own silhouette so the types read
 * without relying on colour. Run from the repository root:
 *
 *   java -cp nettank-client/target/classes:nettank-common/target/classes \
 *       org.chrisgruber.nettank.client.util.PowerUpArtSynth
 */
public final class PowerUpArtSynth {

    private static final String RESOURCES_DIR = "nettank-client/src/main/resources/";
    public static final String FX_DIR = "textures/powerups/fx/";
    static final int ICON_SIZE = 64;

    private static final Color OUTLINE = new Color(16, 20, 24);
    private static final Color GOLD = new Color(255, 206, 64);
    private static final float C = ICON_SIZE / 2.0f;

    private PowerUpArtSynth() {}

    public static void main(String[] args) throws IOException {
        for (PowerUpType type : PowerUpType.values()) {
            write(icon(type), PowerUpStyle.iconPath(type));
        }
        write(glow(128), FX_DIR + "glow.png");
        write(shadow(64, 32), FX_DIR + "shadow.png");
        write(ring(128), FX_DIR + "ring.png");
        write(sparkle(32), FX_DIR + "sparkle.png");
        write(rays(128), FX_DIR + "rays.png");
        write(particle(32), FX_DIR + "particle.png");
        write(tick(8, 16), FX_DIR + "tick.png");
        System.out.println("Power-up art done.");
    }

    // --- Tokens -----------------------------------------------------------------------------

    static BufferedImage icon(PowerUpType type) {
        BufferedImage img = new BufferedImage(ICON_SIZE, ICON_SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = graphics(img);
        Color base = color(PowerUpStyle.color(type));
        boolean gold = PowerUpStyle.tier(type) == 3;
        Shape body = bodyShape(type.getCategory());

        // Thick dark outline so the token separates from grass, sand and forest
        g.setColor(OUTLINE);
        g.setStroke(new BasicStroke(6.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(body);

        g.setPaint(new GradientPaint(0, 6, mix(base, Color.WHITE, 0.18f), 0, ICON_SIZE - 6, mix(base, Color.BLACK, 0.28f)));
        g.fill(body);

        // Glossy highlight across the top half
        g.setClip(body);
        g.setPaint(new GradientPaint(0, 4, new Color(255, 255, 255, 85), 0, C, new Color(255, 255, 255, 0)));
        g.fill(new Ellipse2D.Float(-ICON_SIZE * 0.1f, -ICON_SIZE * 0.4f, ICON_SIZE * 1.2f, ICON_SIZE * 0.85f));
        g.setClip(null);

        // Stamped panel line and rivets: the military half of the look
        g.setStroke(new BasicStroke(1.5f));
        g.setColor(new Color(0, 0, 0, 70));
        g.draw(scaled(body, 0.74f));
        drawRivets(g, type.getCategory());

        g.setStroke(new BasicStroke(gold ? 3.0f : 2.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(gold ? GOLD : new Color(255, 255, 255, 140));
        g.draw(scaled(body, 0.89f));

        drawGlyph(g, glyph(type));

        if (type != PowerUpType.UNLIMITED_AMMO && !type.isInstant()) {
            drawBadge(g, gold ? "3" : "2", gold);
        }
        g.dispose();
        return img;
    }

    private static Shape bodyShape(PowerUpType.Category category) {
        return switch (category) {
            case DAMAGE -> polygon(6, 27.0f, -90.0f);              // hexagon, point up
            case RELOAD -> polygon(8, 28.0f, -67.5f);              // octagon, flat top
            case SPEED -> new Ellipse2D.Float(C - 26, C - 26, 52, 52);
            case AMMO -> roundedDiamond();
            case ARMOR -> shield();
            case HEALTH -> new RoundRectangle2D.Float(6, 6, 52, 52, 16, 16);
        };
    }

    private static Shape glyph(PowerUpType type) {
        return switch (type.getCategory()) {
            case DAMAGE -> shell();
            case RELOAD -> reloadArrows();
            case SPEED -> chevrons();
            case AMMO -> infinity();
            case ARMOR -> wrench();
            case HEALTH -> cross();
        };
    }

    // Drop shadow, dark outline, then white fill: readable at 32 px on any background
    private static void drawGlyph(Graphics2D g, Shape glyph) {
        g.setColor(new Color(0, 0, 0, 90));
        g.fill(AffineTransform.getTranslateInstance(1.5, 2.0).createTransformedShape(glyph));
        g.setColor(new Color(16, 20, 24, 230));
        g.setStroke(new BasicStroke(4.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(glyph);
        g.setColor(Color.WHITE);
        g.fill(glyph);
    }

    private static void drawBadge(Graphics2D g, String text, boolean gold) {
        float r = 9.0f;
        float cx = ICON_SIZE - 12.0f;
        float cy = ICON_SIZE - 12.0f;
        Shape badge = new Ellipse2D.Float(cx - r, cy - r, r * 2, r * 2);
        g.setColor(OUTLINE);
        g.setStroke(new BasicStroke(3.0f));
        g.draw(badge);
        g.setColor(gold ? GOLD : new Color(40, 46, 52));
        g.fill(badge);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
        FontMetrics fm = g.getFontMetrics();
        g.setColor(gold ? OUTLINE : Color.WHITE);
        g.drawString(text, cx - fm.stringWidth(text) / 2.0f, cy + fm.getAscent() / 2.0f - 1.5f);
    }

    private static void drawRivets(Graphics2D g, PowerUpType.Category category) {
        float d = category == PowerUpType.Category.AMMO ? 11.0f : 15.0f; // diamonds have less room
        float[][] spots = category == PowerUpType.Category.AMMO
                ? new float[][] {{C, C - 2 * d}, {C + 2 * d, C}, {C, C + 2 * d}, {C - 2 * d, C}}
                : new float[][] {{C - d, C - d - 4}, {C + d, C - d - 4}, {C - d, C + d + 4}, {C + d, C + d + 4}};
        if (category == PowerUpType.Category.SPEED || category == PowerUpType.Category.ARMOR) {
            spots = new float[][] {{C - 19, C - 12}, {C + 19, C - 12}};
        }
        for (float[] spot : spots) {
            g.setColor(new Color(0, 0, 0, 110));
            g.fill(new Ellipse2D.Float(spot[0] - 2.2f, spot[1] - 2.2f, 4.4f, 4.4f));
            g.setColor(new Color(255, 255, 255, 120));
            g.fill(new Ellipse2D.Float(spot[0] - 1.6f, spot[1] - 1.8f, 1.8f, 1.8f));
        }
    }

    private static Shape shell() {
        Path2D.Float tip = new Path2D.Float();
        tip.moveTo(25, 28);
        tip.curveTo(25, 19, 29, 13, 32, 11);
        tip.curveTo(35, 13, 39, 19, 39, 28);
        tip.closePath();
        Area shell = new Area(tip);
        shell.add(new Area(new Rectangle2D.Float(25, 27, 14, 17)));
        shell.add(new Area(new RoundRectangle2D.Float(22.5f, 43, 19, 6, 3, 3)));
        shell.subtract(new Area(new Rectangle2D.Float(25, 34, 14, 2.2f))); // driving band
        return shell;
    }

    private static Shape reloadArrows() {
        BasicStroke stroke = new BasicStroke(5.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND);
        Area area = new Area();
        for (int half = 0; half < 2; half++) {
            double start = 30 + half * 180;
            Arc2D.Float arc = new Arc2D.Float(C - 13, C - 13, 26, 26, (float) start, 125, Arc2D.OPEN);
            area.add(new Area(stroke.createStrokedShape(arc)));
            // Arrowhead at the counter-clockwise end of each arc
            double end = Math.toRadians(start + 125);
            float ex = C + 13 * (float) Math.cos(end);
            float ey = C - 13 * (float) Math.sin(end);
            double tangent = end + Math.PI / 2; // direction of travel (counter-clockwise)
            float tx = (float) Math.cos(tangent);
            float ty = (float) -Math.sin(tangent);
            float nx = (float) Math.cos(end);
            float ny = (float) -Math.sin(end);
            Path2D.Float head = new Path2D.Float();
            head.moveTo(ex + tx * 7, ey + ty * 7);
            head.lineTo(ex + nx * 7, ey + ny * 7);
            head.lineTo(ex - nx * 7, ey - ny * 7);
            head.closePath();
            area.add(new Area(head));
        }
        return area;
    }

    private static Shape chevrons() {
        Area area = new Area();
        for (int i = 0; i < 2; i++) {
            float x = 19 + i * 13;
            Path2D.Float chevron = new Path2D.Float();
            chevron.moveTo(x, 19);
            chevron.lineTo(x + 8, 19);
            chevron.lineTo(x + 18, 32);
            chevron.lineTo(x + 8, 45);
            chevron.lineTo(x, 45);
            chevron.lineTo(x + 10, 32);
            chevron.closePath();
            area.add(new Area(chevron));
        }
        return area;
    }

    private static Shape infinity() {
        Path2D.Float path = new Path2D.Float();
        for (int i = 0; i <= 96; i++) {
            double t = i / 96.0 * 2.0 * Math.PI;
            double denom = 1.0 + Math.sin(t) * Math.sin(t);
            float x = C + (float) (15.0 * Math.cos(t) / denom);
            float y = C + (float) (15.0 * Math.sin(t) * Math.cos(t) / denom);
            if (i == 0) path.moveTo(x, y);
            else path.lineTo(x, y);
        }
        return new BasicStroke(5.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND).createStrokedShape(path);
    }

    private static Shape wrench() {
        Area area = new Area(new RoundRectangle2D.Float(C - 4, C - 4, 8, 26, 6, 6));
        Area head = new Area(new Ellipse2D.Float(C - 10, C - 22, 20, 20));
        head.subtract(new Area(new Rectangle2D.Float(C - 3.5f, C - 25, 7, 12))); // open jaw
        area.add(head);
        return AffineTransform.getRotateInstance(Math.toRadians(45), C, C).createTransformedShape(area);
    }

    private static Shape cross() {
        Area area = new Area(new RoundRectangle2D.Float(C - 5, C - 15, 10, 30, 3, 3));
        area.add(new Area(new RoundRectangle2D.Float(C - 15, C - 5, 30, 10, 3, 3)));
        return area;
    }

    private static Shape polygon(int sides, float radius, float startDegrees) {
        Path2D.Float path = new Path2D.Float();
        for (int i = 0; i < sides; i++) {
            double a = Math.toRadians(startDegrees + i * 360.0 / sides);
            float x = C + radius * (float) Math.cos(a);
            float y = C + radius * (float) Math.sin(a);
            if (i == 0) path.moveTo(x, y);
            else path.lineTo(x, y);
        }
        path.closePath();
        return path;
    }

    private static Shape roundedDiamond() {
        Shape square = new RoundRectangle2D.Float(C - 20, C - 20, 40, 40, 10, 10);
        return AffineTransform.getRotateInstance(Math.toRadians(45), C, C).createTransformedShape(square);
    }

    private static Shape shield() {
        Path2D.Float path = new Path2D.Float();
        path.moveTo(10, 12);
        path.quadTo(32, 4, 54, 12);
        path.lineTo(54, 30);
        path.curveTo(54, 45, 42, 54, 32, 59);
        path.curveTo(22, 54, 10, 45, 10, 30);
        path.closePath();
        return path;
    }

    private static Shape scaled(Shape shape, float k) {
        AffineTransform t = new AffineTransform();
        t.translate(C, C);
        t.scale(k, k);
        t.translate(-C, -C);
        return t.createTransformedShape(shape);
    }

    // --- Effect sprites (white, tinted at runtime) ------------------------------------------

    static BufferedImage glow(int size) {
        return radial(size, size, r -> Math.pow(Math.max(0.0, 1.0 - r), 1.6), Color.WHITE);
    }

    static BufferedImage shadow(int width, int height) {
        return radial(width, height, r -> 0.9 * Math.pow(Math.max(0.0, 1.0 - r), 1.2), Color.BLACK);
    }

    static BufferedImage particle(int size) {
        return radial(size, size, r -> Math.pow(Math.max(0.0, 1.0 - r), 1.6), Color.WHITE);
    }

    static BufferedImage ring(int size) {
        double radius = size / 2.0 - 6.0;
        return pixels(size, size, (dx, dy) -> {
            double d = Math.abs(Math.hypot(dx, dy) - radius);
            return Math.pow(Math.max(0.0, 1.0 - d / 3.5), 1.5);
        }, Color.WHITE);
    }

    // Four-point twinkle star with a soft core
    static BufferedImage sparkle(int size) {
        double half = size / 2.0 - 1.0;
        return pixels(size, size, (dx, dy) -> {
            double armX = Math.max(0.0, 1.0 - Math.abs(dx) / half) * Math.exp(-dy * dy / (2 * 1.1 * 1.1));
            double armY = Math.max(0.0, 1.0 - Math.abs(dy) / half) * Math.exp(-dx * dx / (2 * 1.1 * 1.1));
            double core = Math.exp(-(dx * dx + dy * dy) / (2 * 2.5 * 2.5));
            return Math.min(1.0, armX + armY + core);
        }, Color.WHITE);
    }

    // Ten soft light rays fading outward, rotated behind x3 power-ups
    static BufferedImage rays(int size) {
        double half = size / 2.0;
        return pixels(size, size, (dx, dy) -> {
            double r = Math.hypot(dx, dy) / half;
            double theta = Math.atan2(dy, dx);
            double beams = Math.pow(Math.abs(Math.cos(5.0 * theta)), 8.0);
            double inner = Math.min(1.0, Math.max(0.0, (r - 0.12) / 0.15));
            return 0.9 * beams * inner * Math.pow(Math.max(0.0, 1.0 - r), 1.3);
        }, Color.WHITE);
    }

    static BufferedImage tick(int width, int height) {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = graphics(img);
        g.setColor(Color.WHITE);
        g.fill(new RoundRectangle2D.Float(0.5f, 0.5f, width - 1, height - 1, width - 1, width - 1));
        g.dispose();
        return img;
    }

    private interface Falloff { double alpha(double r); }
    private interface Field { double alpha(double dx, double dy); }

    // Elliptical radial falloff: r = 0 at the centre, 1 at the edge
    private static BufferedImage radial(int width, int height, Falloff falloff, Color color) {
        double hx = width / 2.0;
        double hy = height / 2.0;
        return pixels(width, height, (dx, dy) -> falloff.alpha(Math.hypot(dx / hx, dy / hy)), color);
    }

    private static BufferedImage pixels(int width, int height, Field field, Color color) {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        int rgb = color.getRGB() & 0xFFFFFF;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                double dx = x + 0.5 - width / 2.0;
                double dy = y + 0.5 - height / 2.0;
                int a = (int) Math.round(255.0 * Math.max(0.0, Math.min(1.0, field.alpha(dx, dy))));
                img.setRGB(x, y, (a << 24) | rgb);
            }
        }
        return img;
    }

    // --- Helpers ----------------------------------------------------------------------------

    private static Graphics2D graphics(BufferedImage img) {
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        return g;
    }

    private static Color color(Vector3f c) {
        return new Color(clamp255(c.x), clamp255(c.y), clamp255(c.z));
    }

    private static Color mix(Color a, Color b, float t) {
        return new Color(
                Math.round(a.getRed() + (b.getRed() - a.getRed()) * t),
                Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * t),
                Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * t));
    }

    private static int clamp255(float v) {
        return Math.max(0, Math.min(255, Math.round(v * 255.0f)));
    }

    private static void write(BufferedImage img, String path) throws IOException {
        File file = new File(RESOURCES_DIR + path);
        file.getParentFile().mkdirs();
        ImageIO.write(img, "PNG", file);
        System.out.println("Wrote " + file);
    }
}
