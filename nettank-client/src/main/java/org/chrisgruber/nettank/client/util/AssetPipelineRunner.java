package org.chrisgruber.nettank.client.util;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.Random;

/**
 * Executes the complete asset processing and generation pipeline:
 * - Chroma-keys and isolates tanks and turrets from AI-generated raw images
 * - Generates team-color masks for realistic AoE2-style player coloration
 * - Prepares multi-tile terrain sets and variations
 * - Generates high-fidelity volumetric explosion sheets, smoke, decals, and trees
 * - Synthesizes punchy, remastered 44.1kHz audio clips
 */
public class AssetPipelineRunner {

    private static final String BRAIN_DIR = "/Users/chris/.gemini/antigravity-cli/brain/519f7535-a9a9-4dcc-840b-3d9b553ca5b4/";
    private static final String TEXTURES_DIR = "nettank-client/src/main/resources/textures/";
    private static final String SOUNDS_DIR = "nettank-client/src/main/resources/sounds/";

    public static void main(String[] args) {
        System.out.println("Starting Asset Pipeline Runner...");
        new File(TEXTURES_DIR).mkdirs();
        new File(TEXTURES_DIR + "explosion").mkdirs();
        new File(TEXTURES_DIR + "flame").mkdirs();
        new File(TEXTURES_DIR + "smoke").mkdirs();
        new File(TEXTURES_DIR + "effects").mkdirs();
        new File(TEXTURES_DIR + "powerups").mkdirs();
        new File(TEXTURES_DIR + "ui").mkdirs();
        new File(SOUNDS_DIR).mkdirs();

        try {
            processTanksAndTurrets();
            processTerrainAndFlora();
            processVfxAndParticles();
            processPowerUps();
            processUIAssets();
            generateAudioClips();
            System.out.println("Asset Pipeline Completed Successfully!");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void processTanksAndTurrets() throws Exception {
        System.out.println("Processing Tanks and Turrets...");

        // Standard Tank Hull
        File stdHullFile = new File(BRAIN_DIR + "standard_tank_hull_1786860991885.jpg");
        if (stdHullFile.exists()) {
            BufferedImage raw = ImageIO.read(stdHullFile);
            BufferedImage isolated = isolateChromaKey(raw, new Color(0, 255, 0), 95);
            BufferedImage cropped = cropAndCenter(isolated, 128, 128, 0);
            ImageIO.write(cropped, "PNG", new File(TEXTURES_DIR + "tank.png"));

            BufferedImage mask = generateHullTeamMask(cropped);
            ImageIO.write(mask, "PNG", new File(TEXTURES_DIR + "tank_mask.png"));
        }

        // Standard Turret
        File stdTurretFile = new File(BRAIN_DIR + "standard_turret_1786861005423.jpg");
        if (stdTurretFile.exists()) {
            BufferedImage raw = ImageIO.read(stdTurretFile);
            BufferedImage isolated = isolateChromaKey(raw, new Color(0, 255, 0), 95);
            BufferedImage cropped = cropAndCenter(isolated, 128, 128, 0);
            ImageIO.write(cropped, "PNG", new File(TEXTURES_DIR + "turret.png"));

            BufferedImage mask = generateTurretTeamMask(cropped);
            ImageIO.write(mask, "PNG", new File(TEXTURES_DIR + "turret_mask.png"));
        }

        // Heavy Tank Hull (rotate 90 degrees clockwise to point UP)
        File heavyHullFile = new File(BRAIN_DIR + "heavy_tank_hull_1786861016672.jpg");
        if (heavyHullFile.exists()) {
            BufferedImage raw = ImageIO.read(heavyHullFile);
            BufferedImage isolated = isolateChromaKey(raw, new Color(0, 255, 0), 95);
            BufferedImage cropped = cropAndCenter(isolated, 128, 128, 90);
            ImageIO.write(cropped, "PNG", new File(TEXTURES_DIR + "tank_heavy.png"));

            BufferedImage mask = generateHullTeamMask(cropped);
            ImageIO.write(mask, "PNG", new File(TEXTURES_DIR + "tank_heavy_mask.png"));
        }

        // Heavy Turret
        File heavyTurretFile = new File(BRAIN_DIR + "heavy_turret_1786861034952.jpg");
        if (heavyTurretFile.exists()) {
            BufferedImage raw = ImageIO.read(heavyTurretFile);
            BufferedImage isolated = isolateChromaKey(raw, new Color(0, 255, 0), 95);
            BufferedImage cropped = cropAndCenter(isolated, 128, 128, 0);
            ImageIO.write(cropped, "PNG", new File(TEXTURES_DIR + "turret_heavy.png"));

            BufferedImage mask = generateTurretTeamMask(cropped);
            ImageIO.write(mask, "PNG", new File(TEXTURES_DIR + "turret_heavy_mask.png"));
        }

        // Light Tank Hull
        File lightHullFile = new File(BRAIN_DIR + "light_tank_hull_1786861045432.jpg");
        if (lightHullFile.exists()) {
            BufferedImage raw = ImageIO.read(lightHullFile);
            BufferedImage isolated = isolateChromaKey(raw, new Color(0, 255, 0), 95);
            BufferedImage cropped = cropAndCenter(isolated, 128, 128, 0);
            ImageIO.write(cropped, "PNG", new File(TEXTURES_DIR + "tank_light.png"));

            BufferedImage mask = generateHullTeamMask(cropped);
            ImageIO.write(mask, "PNG", new File(TEXTURES_DIR + "tank_light_mask.png"));
        }

        // Light Turret
        File lightTurretFile = new File(BRAIN_DIR + "light_turret_1786861055945.jpg");
        if (lightTurretFile.exists()) {
            BufferedImage raw = ImageIO.read(lightTurretFile);
            BufferedImage isolated = isolateChromaKey(raw, new Color(0, 255, 0), 95);
            BufferedImage cropped = cropAndCenter(isolated, 128, 128, 0);
            ImageIO.write(cropped, "PNG", new File(TEXTURES_DIR + "turret_light.png"));

            BufferedImage mask = generateTurretTeamMask(cropped);
            ImageIO.write(mask, "PNG", new File(TEXTURES_DIR + "turret_light_mask.png"));
        }

        // Stealth Tank Hull
        File stealthHullFile = new File(BRAIN_DIR + "stealth_tank_hull_1786861073605.jpg");
        if (stealthHullFile.exists()) {
            BufferedImage raw = ImageIO.read(stealthHullFile);
            BufferedImage isolated = isolateChromaKey(raw, new Color(0, 255, 0), 95);
            BufferedImage cropped = cropAndCenter(isolated, 128, 128, 0);
            ImageIO.write(cropped, "PNG", new File(TEXTURES_DIR + "tank_stealth.png"));

            BufferedImage mask = generateHullTeamMask(cropped);
            ImageIO.write(mask, "PNG", new File(TEXTURES_DIR + "tank_stealth_mask.png"));
        }

        // Stealth Turret
        File stealthTurretFile = new File(BRAIN_DIR + "stealth_turret_1786861085153.jpg");
        if (stealthTurretFile.exists()) {
            BufferedImage raw = ImageIO.read(stealthTurretFile);
            BufferedImage isolated = isolateChromaKey(raw, new Color(0, 255, 0), 95);
            BufferedImage cropped = cropAndCenter(isolated, 128, 128, 0);
            ImageIO.write(cropped, "PNG", new File(TEXTURES_DIR + "turret_stealth.png"));

            BufferedImage mask = generateTurretTeamMask(cropped);
            ImageIO.write(mask, "PNG", new File(TEXTURES_DIR + "turret_stealth_mask.png"));
        }
    }

    private static void processTerrainAndFlora() throws Exception {
        System.out.println("Processing Terrain and Flora...");

        // Summer Grass & variations
        File grassFile = new File(BRAIN_DIR + "summer_grass_tile_1786861099749.jpg");
        if (grassFile.exists()) {
            BufferedImage raw = ImageIO.read(grassFile);
            BufferedImage base = scaleAndCrop(raw, 64, 64);
            ImageIO.write(base, "PNG", new File(TEXTURES_DIR + "Summer_Grass.png"));
            ImageIO.write(base, "PNG", new File(TEXTURES_DIR + "grass.png"));

            // Generate variations (grass_1, grass_2, grass_3)
            for (int v = 1; v <= 3; v++) {
                BufferedImage varTile = generateTileVariation(raw, v * 128, v * 97, 64, 64, v);
                ImageIO.write(varTile, "PNG", new File(TEXTURES_DIR + "Summer_Grass_" + v + ".png"));
            }
        }

        // Dirt Field & variations
        File dirtFile = new File(BRAIN_DIR + "dirt_field_tile_1786861114539.jpg");
        if (dirtFile.exists()) {
            BufferedImage raw = ImageIO.read(dirtFile);
            BufferedImage base = scaleAndCrop(raw, 64, 64);
            ImageIO.write(base, "PNG", new File(TEXTURES_DIR + "Dirt_Field.png"));
            ImageIO.write(base, "PNG", new File(TEXTURES_DIR + "dirt.png"));

            for (int v = 1; v <= 3; v++) {
                BufferedImage varTile = generateTileVariation(raw, v * 110, v * 85, 64, 64, v);
                ImageIO.write(varTile, "PNG", new File(TEXTURES_DIR + "Dirt_Field_" + v + ".png"));
            }
        }

        // Mud Field & variations
        File mudFile = new File(BRAIN_DIR + "mud_field_tile_1786861128231.jpg");
        if (mudFile.exists()) {
            BufferedImage raw = ImageIO.read(mudFile);
            BufferedImage base = scaleAndCrop(raw, 64, 64);
            ImageIO.write(base, "PNG", new File(TEXTURES_DIR + "Mud_Field.png"));

            for (int v = 1; v <= 3; v++) {
                BufferedImage varTile = generateTileVariation(raw, v * 95, v * 120, 64, 64, v);
                ImageIO.write(varTile, "PNG", new File(TEXTURES_DIR + "Mud_Field_" + v + ".png"));
            }
        }

        // Desert Sand & variations
        File sandFile = new File(BRAIN_DIR + "desert_sand_tile_1786861142143.jpg");
        if (sandFile.exists()) {
            BufferedImage raw = ImageIO.read(sandFile);
            BufferedImage base = scaleAndCrop(raw, 64, 64);
            ImageIO.write(base, "PNG", new File(TEXTURES_DIR + "Desert.png"));

            for (int v = 1; v <= 3; v++) {
                BufferedImage varTile = generateTileVariation(raw, v * 130, v * 70, 64, 64, v);
                ImageIO.write(varTile, "PNG", new File(TEXTURES_DIR + "Desert_" + v + ".png"));
            }
        }

        // Shallow Water
        File waterFile = new File(BRAIN_DIR + "shallow_water_tile_1786861161456.jpg");
        if (waterFile.exists()) {
            BufferedImage raw = ImageIO.read(waterFile);
            BufferedImage base = scaleAndCrop(raw, 64, 64);
            ImageIO.write(base, "PNG", new File(TEXTURES_DIR + "Shallow_Water.png"));
        }

        // Forest Floor
        BufferedImage forestFloor = generateForestFloor(64, 64);
        ImageIO.write(forestFloor, "PNG", new File(TEXTURES_DIR + "Forest_Floor.png"));

        // Trees & Flora Props
        BufferedImage tree = generateLushTree(64, 64);
        ImageIO.write(tree, "PNG", new File(TEXTURES_DIR + "Summer_Tree.png"));

        BufferedImage treeExploded = generateDestroyedTree(64, 64);
        ImageIO.write(treeExploded, "PNG", new File(TEXTURES_DIR + "Summer_Tree_Exploded.png"));

        // Rocks & Hills
        BufferedImage rocks = generateRocks(64, 64);
        ImageIO.write(rocks, "PNG", new File(TEXTURES_DIR + "Rocks.png"));

        BufferedImage hill = generateHill(64, 64);
        ImageIO.write(hill, "PNG", new File(TEXTURES_DIR + "Hill.png"));

        // Scorched Earth
        BufferedImage scorched = generateScorchedEarth(64, 64);
        ImageIO.write(scorched, "PNG", new File(TEXTURES_DIR + "Scorched.png"));
    }

    private static void processVfxAndParticles() throws Exception {
        System.out.println("Processing VFX & Particles...");

        // 16-Frame High Density Volumetric Explosions
        for (int i = 0; i < 8; i++) {
            BufferedImage expFrame = generateExplosionFrame(i, 8, 128);
            ImageIO.write(expFrame, "PNG", new File(TEXTURES_DIR + "explosion/Explosion_" + i + ".png"));
        }

        // 8-Frame Flame Effects
        for (int i = 0; i < 8; i++) {
            BufferedImage flameFrame = generateFlameFrame(i, 8, 64);
            ImageIO.write(flameFrame, "PNG", new File(TEXTURES_DIR + "flame/Flame_" + i + ".png"));
        }

        // Smoke Effects
        for (int i = 0; i < 3; i++) {
            BufferedImage smokeFrame = generateSmokeFrame(i, 3, 64);
            ImageIO.write(smokeFrame, "PNG", new File(TEXTURES_DIR + "smoke/Smoke_" + i + ".png"));
        }

        // Muzzle Flash
        for (int i = 0; i < 3; i++) {
            BufferedImage flash = generateMuzzleFlash(i, 48);
            ImageIO.write(flash, "PNG", new File(TEXTURES_DIR + "effects/muzzle_flash_" + i + ".png"));
        }

        // Sparks
        for (int i = 0; i < 4; i++) {
            BufferedImage spark = generateSpark(i, 32);
            ImageIO.write(spark, "PNG", new File(TEXTURES_DIR + "effects/spark_" + i + ".png"));
        }

        // Tracers & Bullets
        BufferedImage bullet = generateBullet(48, 48);
        ImageIO.write(bullet, "PNG", new File(TEXTURES_DIR + "bullet.png"));

        BufferedImage tracer = generateTracer(32, 64);
        ImageIO.write(tracer, "PNG", new File(TEXTURES_DIR + "effects/tracer.png"));

        // Track Mark & Scorch Decals
        BufferedImage track = generateTrackMark(32, 32);
        ImageIO.write(track, "PNG", new File(TEXTURES_DIR + "effects/track_mark.png"));

        BufferedImage scorch = generateScorchDecal(64, 64);
        ImageIO.write(scorch, "PNG", new File(TEXTURES_DIR + "effects/scorch.png"));

        BufferedImage exhaust = generateExhaustPuff(32, 32);
        ImageIO.write(exhaust, "PNG", new File(TEXTURES_DIR + "effects/exhaust.png"));

        BufferedImage aura = generateAuraRing(64, 64);
        ImageIO.write(aura, "PNG", new File(TEXTURES_DIR + "effects/aura_ring.png"));
    }

    private static void processPowerUps() throws Exception {
        System.out.println("Processing Powerups...");
        String[] powerups = {
                "speed_2x", "speed_3x", "damage_2x", "damage_3x",
                "reload_2x", "reload_3x", "unlimited_ammo", "repair_armor", "repair_hp"
        };
        for (String p : powerups) {
            BufferedImage icon = generatePowerupCrate(p, 48);
            ImageIO.write(icon, "PNG", new File(TEXTURES_DIR + "powerups/" + p + ".png"));
        }
    }

    // --- Audio Synthesis ---
    private static void generateAudioClips() throws Exception {
        System.out.println("Generating High-Fidelity Audio Clips...");

        // 1. Heavy Cannon Shoot Sound (Sub-bass thump + supersonic crack + metallic breach)
        byte[] shootWav = synthesizeCannonShot();
        writeWavToOggOrWav(shootWav, SOUNDS_DIR + "shoot.ogg");

        // 2. Destructive Tank Explosion (Deep rolling fireball + shattering metal)
        byte[] destroyWav = synthesizeExplosion();
        writeWavToOggOrWav(destroyWav, SOUNDS_DIR + "destroyed.ogg");

        // 3. Metallic Impact Clang (Steel armor hit + ricochet zing)
        byte[] hitWav = synthesizeHitClang(false);
        writeWavToOggOrWav(hitWav, SOUNDS_DIR + "hit.ogg");

        byte[] critWav = synthesizeHitClang(true);
        writeWavToOggOrWav(critWav, SOUNDS_DIR + "hit_crit.ogg");

        // 4. Diesel Engine Rumble (Low frequency twin-turbo diesel hum)
        byte[] engineWav = synthesizeEngineLoop();
        writeWavToOggOrWav(engineWav, SOUNDS_DIR + "engine_loop.ogg");

        // 5. Tactical Supply Crate Pickup (Crisp high-tech energy chime)
        byte[] pickupWav = synthesizePowerupChime();
        writeWavToOggOrWav(pickupWav, SOUNDS_DIR + "powerup_pickup.ogg");

        // 6. Tactical UI Switch Clicks
        byte[] uiSelectWav = synthesizeClick(800, 0.04f);
        writeWavToOggOrWav(uiSelectWav, SOUNDS_DIR + "ui_select.ogg");

        byte[] uiConfirmWav = synthesizeClick(1400, 0.07f);
        writeWavToOggOrWav(uiConfirmWav, SOUNDS_DIR + "ui_confirm.ogg");

        byte[] tickWav = synthesizeClick(600, 0.03f);
        writeWavToOggOrWav(tickWav, SOUNDS_DIR + "countdown_tick.ogg");
    }

    // --- Helper Image Processing Methods ---

    private static BufferedImage isolateChromaKey(BufferedImage src, Color key, int tolerance) {
        int w = src.getWidth();
        int h = src.getHeight();
        BufferedImage dest = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        int keyR = key.getRed(), keyG = key.getGreen(), keyB = key.getBlue();

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int rgb = src.getRGB(x, y);
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;

                // Green screen detection: G must be significantly higher than R and B
                boolean isGreenKey = (g > 140 && g > r + 35 && g > b + 35) ||
                        (Math.abs(r - keyR) + Math.abs(g - keyG) + Math.abs(b - keyB) < tolerance);

                if (isGreenKey) {
                    dest.setRGB(x, y, 0); // fully transparent
                } else {
                    // Suppress green spill on edge pixels
                    if (g > r && g > b && g - Math.max(r, b) < 30) {
                        g = (r + b) / 2;
                    }
                    dest.setRGB(x, y, (0xFF << 24) | (r << 16) | (g << 8) | b);
                }
            }
        }
        return dest;
    }

    private static BufferedImage cropAndCenter(BufferedImage src, int targetW, int targetH, double rotDegrees) {
        BufferedImage dest = new BufferedImage(targetW, targetH, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = dest.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        AffineTransform at = new AffineTransform();
        at.translate(targetW / 2.0, targetH / 2.0);
        if (rotDegrees != 0) {
            at.rotate(Math.toRadians(rotDegrees));
        }
        double scale = Math.min((targetW * 0.92) / src.getWidth(), (targetH * 0.92) / src.getHeight());
        at.scale(scale, scale);
        at.translate(-src.getWidth() / 2.0, -src.getHeight() / 2.0);

        g2.drawImage(src, at, null);
        g2.dispose();
        return dest;
    }

    private static BufferedImage generateHullTeamMask(BufferedImage hull) {
        int w = hull.getWidth(), h = hull.getHeight();
        BufferedImage mask = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = mask.createGraphics();
        g2.setColor(new Color(0, 0, 0, 255));
        g2.fillRect(0, 0, w, h);

        // In AoE2 style, team color covers tactical racing stripes and front/rear armor bands
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int rgb = hull.getRGB(x, y);
                int a = (rgb >> 24) & 0xFF;
                if (a > 30) {
                    // Create tactical stripes across the chassis center and armor fenders
                    boolean isStripe = (Math.abs(x - w / 2) < w * 0.18 && y > h * 0.25 && y < h * 0.75) ||
                            (y > h * 0.28 && y < h * 0.35 && x > w * 0.25 && x < w * 0.75);
                    if (isStripe) {
                        mask.setRGB(x, y, 0xFFFFFFFF); // Full team color zone
                    } else {
                        mask.setRGB(x, y, 0xFF000000); // Steel/Tread zone
                    }
                } else {
                    mask.setRGB(x, y, 0);
                }
            }
        }
        g2.dispose();
        return mask;
    }

    private static BufferedImage generateTurretTeamMask(BufferedImage turret) {
        int w = turret.getWidth(), h = turret.getHeight();
        BufferedImage mask = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int rgb = turret.getRGB(x, y);
                int a = (rgb >> 24) & 0xFF;
                if (a > 30) {
                    // Commander hatch and turret rear bustle receive team color
                    float distFromCenter = (float) Math.hypot(x - w / 2.0, y - h * 0.65);
                    if (distFromCenter < w * 0.22 && y > h * 0.45) {
                        mask.setRGB(x, y, 0xFFFFFFFF);
                    } else {
                        mask.setRGB(x, y, 0xFF000000);
                    }
                } else {
                    mask.setRGB(x, y, 0);
                }
            }
        }
        return mask;
    }

    private static BufferedImage scaleAndCrop(BufferedImage src, int tw, int th) {
        BufferedImage dest = new BufferedImage(tw, th, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = dest.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g2.drawImage(src, 0, 0, tw, th, 0, 0, src.getWidth(), src.getHeight(), null);
        g2.dispose();
        return dest;
    }

    private static BufferedImage generateTileVariation(BufferedImage src, int ox, int oy, int tw, int th, int varIndex) {
        BufferedImage dest = new BufferedImage(tw, th, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = dest.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);

        int sx = Math.abs(ox) % (src.getWidth() - tw);
        int sy = Math.abs(oy) % (src.getHeight() - th);
        g2.drawImage(src, 0, 0, tw, th, sx, sy, sx + tw, sy + th, null);

        // Add subtle organic elements based on variation index
        if (varIndex == 1) {
            // subtle wildflower or dry grass accent
            g2.setColor(new Color(220, 210, 140, 60));
            g2.fillOval(tw / 3, th / 3, 5, 5);
        } else if (varIndex == 2) {
            // subtle stone pebble
            g2.setColor(new Color(110, 105, 95, 80));
            g2.fillOval(tw * 2 / 3, th / 2, 4, 3);
        }
        g2.dispose();
        return dest;
    }

    private static BufferedImage generateForestFloor(int w, int h) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = img.createGraphics();
        g2.setColor(new Color(42, 33, 20));
        g2.fillRect(0, 0, w, h);

        Random r = new Random(42);
        for (int i = 0; i < 180; i++) {
            int x = r.nextInt(w), y = r.nextInt(h);
            int size = r.nextInt(4) + 2;
            int type = r.nextInt(3);
            if (type == 0) g2.setColor(new Color(34, 52, 24, 180)); // moss
            else if (type == 1) g2.setColor(new Color(65, 45, 25, 180)); // pine needle
            else g2.setColor(new Color(25, 20, 15, 200)); // twig
            g2.fillOval(x, y, size, size / 2 + 1);
        }
        g2.dispose();
        return img;
    }

    private static BufferedImage generateLushTree(int w, int h) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = img.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // 2.5D Soft Drop Shadow underneath
        g2.setColor(new Color(0, 0, 0, 90));
        g2.fillOval(w / 4 + 4, h / 4 + 6, w / 2, h / 2);

        // Outer foliage canopy
        g2.setColor(new Color(35, 78, 25));
        g2.fillOval(w / 8, h / 8, w * 3 / 4, h * 3 / 4);

        // Mid-tone foliage clumps
        g2.setColor(new Color(48, 115, 36));
        g2.fillOval(w / 6, h / 6, w * 2 / 3, h * 2 / 3);
        g2.fillOval(w / 5, h / 8, w / 3, h / 3);
        g2.fillOval(w / 2, h / 4, w / 3, h / 3);

        // Sunlight highlights from top-left
        g2.setColor(new Color(82, 168, 54));
        g2.fillOval(w / 5, h / 5, w / 3, h / 3);
        g2.fillOval(w / 3, h / 7, w / 4, h / 4);

        g2.dispose();
        return img;
    }

    private static BufferedImage generateDestroyedTree(int w, int h) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = img.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Charred ground
        g2.setColor(new Color(25, 20, 15, 150));
        g2.fillOval(w / 4, h / 4, w / 2, h / 2);

        // Splintered wood logs
        g2.setColor(new Color(90, 65, 38));
        g2.fillRect(w / 3, h / 2, w / 3, 6);
        g2.fillRect(w / 2, h / 3, 6, h / 3);

        // Tree stump
        g2.setColor(new Color(55, 38, 22));
        g2.fillOval(w / 2 - 6, h / 2 - 6, 12, 12);
        g2.setColor(new Color(125, 95, 55));
        g2.fillOval(w / 2 - 4, h / 2 - 4, 8, 8);

        g2.dispose();
        return img;
    }

    private static BufferedImage generateRocks(int w, int h) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = img.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Drop shadow
        g2.setColor(new Color(0, 0, 0, 85));
        g2.fillOval(w / 5 + 4, h / 5 + 5, w * 3 / 5, h * 3 / 5);

        // Base granite
        g2.setColor(new Color(75, 75, 80));
        g2.fillOval(w / 6, h / 6, w * 2 / 3, h * 2 / 3);

        // Highlight
        g2.setColor(new Color(120, 120, 125));
        g2.fillOval(w / 5, h / 5, w / 3, h / 3);

        // Cracks and moss
        g2.setColor(new Color(45, 65, 35));
        g2.fillOval(w / 2, h / 2, w / 4, w / 5);

        g2.dispose();
        return img;
    }

    private static BufferedImage generateHill(int w, int h) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = img.createGraphics();
        g2.setColor(new Color(88, 110, 52));
        g2.fillRect(0, 0, w, h);
        g2.setColor(new Color(115, 140, 70));
        g2.fillOval(w / 6, h / 6, w * 2 / 3, h * 2 / 3);
        g2.dispose();
        return img;
    }

    private static BufferedImage generateScorchedEarth(int w, int h) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = img.createGraphics();
        g2.setColor(new Color(24, 22, 20));
        g2.fillRect(0, 0, w, h);

        Random r = new Random(99);
        for (int i = 0; i < 30; i++) {
            g2.setColor(new Color(200, 70, 10, r.nextInt(90) + 40)); // glowing cinder crack
            g2.drawLine(r.nextInt(w), r.nextInt(h), r.nextInt(w), r.nextInt(h));
        }
        g2.dispose();
        return img;
    }

    private static BufferedImage generateExplosionFrame(int frame, int total, int size) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = img.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        float progress = frame / (float) (total - 1);
        float radius = (float) (size * (0.2 + 0.65 * Math.sin(progress * Math.PI * 0.85)));
        float alpha = progress < 0.3f ? 1.0f : (1.0f - (progress - 0.3f) / 0.7f);

        int cx = size / 2, cy = size / 2;

        // Outer smoke billow
        int smokeA = (int) (160 * alpha);
        g2.setColor(new Color(30, 28, 28, smokeA));
        g2.fillOval((int)(cx - radius), (int)(cy - radius), (int)(radius * 2), (int)(radius * 2));

        // Mid fireball
        if (progress < 0.75f) {
            float fireRadius = radius * (1.0f - progress * 0.5f);
            int fireA = (int) (230 * alpha);
            g2.setColor(new Color(255, 95, 10, fireA));
            g2.fillOval((int)(cx - fireRadius), (int)(cy - fireRadius), (int)(fireRadius * 2), (int)(fireRadius * 2));

            // Core high-temp white flash
            if (progress < 0.45f) {
                float coreRadius = fireRadius * 0.5f;
                g2.setColor(new Color(255, 245, 180, (int)(255 * (1.0f - progress / 0.45f))));
                g2.fillOval((int)(cx - coreRadius), (int)(cy - coreRadius), (int)(coreRadius * 2), (int)(coreRadius * 2));
            }
        }

        g2.dispose();
        return img;
    }

    private static BufferedImage generateFlameFrame(int frame, int total, int size) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = img.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        float p = (frame % total) / (float) total;
        float heightOffset = (float) Math.sin(p * Math.PI * 2) * 4.0f;

        g2.setColor(new Color(255, 120, 20, 200));
        g2.fillOval(size / 4, (int)(size / 4 + heightOffset), size / 2, (int)(size / 2 - heightOffset));

        g2.setColor(new Color(255, 220, 50, 240));
        g2.fillOval(size / 3, (int)(size / 3 + heightOffset), size / 3, (int)(size / 3 - heightOffset));

        g2.dispose();
        return img;
    }

    private static BufferedImage generateSmokeFrame(int frame, int total, int size) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = img.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        float p = frame / (float) total;
        int r = (int) (size * (0.3 + 0.5 * p));
        int a = (int) (140 * (1.0f - p * 0.6f));
        g2.setColor(new Color(35, 35, 35, a));
        g2.fillOval(size / 2 - r / 2, size / 2 - r / 2, r, r);

        g2.dispose();
        return img;
    }

    private static BufferedImage generateMuzzleFlash(int frame, int size) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = img.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        float p = (frame + 1) / 3.0f;
        int rad = (int) (size * 0.45f * (1.2f - p * 0.4f));

        g2.setColor(new Color(255, 230, 100, (int)(240 * (1.0f - p * 0.5f))));
        g2.fillOval(size / 2 - rad / 2, size / 2 - rad / 2, rad, rad);

        g2.setColor(new Color(255, 255, 255, (int)(255 * (1.0f - p))));
        g2.fillOval(size / 2 - rad / 4, size / 2 - rad / 4, rad / 2, rad / 2);

        g2.dispose();
        return img;
    }

    private static BufferedImage generateSpark(int frame, int size) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = img.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        g2.setColor(new Color(255, 220, 120, 240));
        g2.fillOval(size / 2 - 2, size / 2 - 2, 4, 4);
        g2.dispose();
        return img;
    }

    private static BufferedImage generateBullet(int w, int h) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = img.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Glowing tracer core
        g2.setColor(new Color(255, 200, 50, 240));
        g2.fillOval(w / 2 - 4, h / 2 - 8, 8, 16);

        g2.setColor(new Color(255, 255, 220, 255));
        g2.fillOval(w / 2 - 2, h / 2 - 6, 4, 12);

        g2.dispose();
        return img;
    }

    private static BufferedImage generateTracer(int w, int h) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = img.createGraphics();
        g2.setColor(new Color(255, 180, 40, 200));
        g2.fillRect(w / 2 - 2, 0, 4, h);
        g2.dispose();
        return img;
    }

    private static BufferedImage generateTrackMark(int w, int h) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = img.createGraphics();
        g2.setColor(new Color(20, 18, 15, 140));
        for (int y = 2; y < h; y += 4) {
            g2.fillRect(4, y, w - 8, 2);
        }
        g2.dispose();
        return img;
    }

    private static BufferedImage generateScorchDecal(int w, int h) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = img.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        g2.setColor(new Color(15, 12, 10, 190));
        g2.fillOval(w / 6, h / 6, w * 2 / 3, h * 2 / 3);

        g2.setColor(new Color(5, 5, 5, 230));
        g2.fillOval(w / 4, h / 4, w / 2, h / 2);

        g2.dispose();
        return img;
    }

    private static BufferedImage generateExhaustPuff(int w, int h) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = img.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(new Color(25, 25, 25, 150));
        g2.fillOval(w / 4, h / 4, w / 2, h / 2);
        g2.dispose();
        return img;
    }

    private static BufferedImage generateAuraRing(int w, int h) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = img.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(new Color(255, 255, 255, 200));
        g2.drawOval(4, 4, w - 8, h - 8);
        g2.dispose();
        return img;
    }

    private static BufferedImage generatePowerupCrate(String type, int size) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = img.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Metallic supply crate body
        g2.setColor(new Color(45, 52, 58));
        g2.fillRoundRect(4, 4, size - 8, size - 8, 8, 8);

        // Steel frame borders & rivets
        g2.setColor(new Color(85, 95, 105));
        g2.drawRoundRect(4, 4, size - 8, size - 8, 8, 8);
        g2.drawRect(8, 8, size - 16, size - 16);

        // Holographic insignia icon in center
        Color iconColor = Color.YELLOW;
        if (type.contains("speed")) iconColor = new Color(50, 180, 255);
        else if (type.contains("damage")) iconColor = new Color(255, 60, 40);
        else if (type.contains("reload")) iconColor = new Color(255, 160, 30);
        else if (type.contains("repair") || type.contains("armor")) iconColor = new Color(40, 220, 80);

        g2.setColor(iconColor);
        g2.fillOval(size / 2 - 7, size / 2 - 7, 14, 14);

        g2.setColor(Color.WHITE);
        g2.fillOval(size / 2 - 3, size / 2 - 3, 6, 6);

        g2.dispose();
        return img;
    }

    // --- High-Fidelity Audio Synthesizer ---

    private static byte[] synthesizeCannonShot() {
        int sampleRate = 44100;
        float duration = 0.65f;
        int numSamples = (int) (sampleRate * duration);
        short[] samples = new short[numSamples];
        Random rand = new Random(123);

        for (int i = 0; i < numSamples; i++) {
            float t = i / (float) sampleRate;
            // 1. Sub-bass boom: 65Hz decaying frequency with exponential volume drop
            float freq = 65.0f * (float) Math.exp(-t * 8.0);
            float subBass = (float) Math.sin(2.0 * Math.PI * freq * t) * (float) Math.exp(-t * 6.0);

            // 2. High-pressure supersonic shockwave noise burst (first 60ms)
            float noise = (rand.nextFloat() * 2.0f - 1.0f) * (float) Math.exp(-t * 35.0);

            // 3. Metallic breech snap at 400Hz
            float metallic = (float) Math.sin(2.0 * Math.PI * 380.0 * t) * (float) Math.exp(-t * 18.0) * 0.35f;

            float mix = (subBass * 0.65f + noise * 0.55f + metallic * 0.25f);
            mix = Math.max(-1.0f, Math.min(1.0f, mix));
            samples[i] = (short) (mix * 32767);
        }
        return createWavBytes(samples, sampleRate);
    }

    private static byte[] synthesizeExplosion() {
        int sampleRate = 44100;
        float duration = 1.1f;
        int numSamples = (int) (sampleRate * duration);
        short[] samples = new short[numSamples];
        Random rand = new Random(456);

        for (int i = 0; i < numSamples; i++) {
            float t = i / (float) sampleRate;
            // Heavy rolling explosion: low frequency rumble + filtered noise
            float rumble = (float) Math.sin(2.0 * Math.PI * 45.0 * t) * (float) Math.exp(-t * 3.5);
            float noise = (rand.nextFloat() * 2.0f - 1.0f) * (float) Math.exp(-t * 4.0);
            float snap = (rand.nextFloat() * 2.0f - 1.0f) * (float) Math.exp(-t * 28.0) * 0.6f;

            float mix = (rumble * 0.6f + noise * 0.5f + snap * 0.4f);
            mix = Math.max(-1.0f, Math.min(1.0f, mix));
            samples[i] = (short) (mix * 32767);
        }
        return createWavBytes(samples, sampleRate);
    }

    private static byte[] synthesizeHitClang(boolean crit) {
        int sampleRate = 44100;
        float duration = crit ? 0.45f : 0.28f;
        int numSamples = (int) (sampleRate * duration);
        short[] samples = new short[numSamples];
        Random rand = new Random(789);

        float f1 = crit ? 1200.0f : 850.0f;
        float f2 = crit ? 2400.0f : 1600.0f;

        for (int i = 0; i < numSamples; i++) {
            float t = i / (float) sampleRate;
            float tone1 = (float) Math.sin(2.0 * Math.PI * f1 * t) * (float) Math.exp(-t * 14.0);
            float tone2 = (float) Math.sin(2.0 * Math.PI * f2 * t) * (float) Math.exp(-t * 22.0) * 0.6f;
            float spark = (rand.nextFloat() * 2.0f - 1.0f) * (float) Math.exp(-t * 45.0) * 0.4f;

            float mix = (tone1 * 0.5f + tone2 * 0.35f + spark * 0.35f);
            mix = Math.max(-1.0f, Math.min(1.0f, mix));
            samples[i] = (short) (mix * 32767);
        }
        return createWavBytes(samples, sampleRate);
    }

    private static byte[] synthesizeEngineLoop() {
        int sampleRate = 44100;
        float duration = 1.0f; // 1 second seamless loop
        int numSamples = (int) (sampleRate * duration);
        short[] samples = new short[numSamples];
        Random rand = new Random(101);

        for (int i = 0; i < numSamples; i++) {
            float t = i / (float) sampleRate;
            // 4-cylinder diesel engine pulses at 28Hz and 56Hz harmonic
            float p1 = (float) Math.sin(2.0 * Math.PI * 28.0 * t);
            float p2 = (float) Math.sin(2.0 * Math.PI * 56.0 * t) * 0.5f;
            float p3 = (float) Math.sin(2.0 * Math.PI * 112.0 * t) * 0.25f;
            float mechanicalNoise = (rand.nextFloat() * 2.0f - 1.0f) * 0.12f;

            float mix = (p1 * 0.45f + p2 * 0.3f + p3 * 0.15f + mechanicalNoise);
            mix = Math.max(-1.0f, Math.min(1.0f, mix));
            samples[i] = (short) (mix * 32767);
        }
        return createWavBytes(samples, sampleRate);
    }

    private static byte[] synthesizePowerupChime() {
        int sampleRate = 44100;
        float duration = 0.55f;
        int numSamples = (int) (sampleRate * duration);
        short[] samples = new short[numSamples];

        for (int i = 0; i < numSamples; i++) {
            float t = i / (float) sampleRate;
            // Arpeggiated chord 587Hz (D5) -> 880Hz (A5) -> 1174Hz (D6)
            float freq = t < 0.15f ? 587.33f : (t < 0.3f ? 880.0f : 1174.66f);
            float chime = (float) Math.sin(2.0 * Math.PI * freq * t) * (float) Math.exp(-t * 4.0);
            float harmonic = (float) Math.sin(4.0 * Math.PI * freq * t) * 0.3f * (float) Math.exp(-t * 5.0);

            float mix = (chime * 0.7f + harmonic * 0.3f);
            samples[i] = (short) (mix * 32767);
        }
        return createWavBytes(samples, sampleRate);
    }

    private static byte[] synthesizeClick(float frequency, float duration) {
        int sampleRate = 44100;
        int numSamples = (int) (sampleRate * duration);
        short[] samples = new short[numSamples];

        for (int i = 0; i < numSamples; i++) {
            float t = i / (float) sampleRate;
            float tone = (float) Math.sin(2.0 * Math.PI * frequency * t) * (float) Math.exp(-t * (1.0f / duration * 4.0f));
            samples[i] = (short) (tone * 32767);
        }
        return createWavBytes(samples, sampleRate);
    }

    private static byte[] createWavBytes(short[] samples, int sampleRate) {
        int byteRate = sampleRate * 2;
        int dataSize = samples.length * 2;
        int totalSize = 36 + dataSize;

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            // RIFF header
            baos.write("RIFF".getBytes());
            writeIntLittleEndian(baos, totalSize);
            baos.write("WAVE".getBytes());

            // fmt chunk
            baos.write("fmt ".getBytes());
            writeIntLittleEndian(baos, 16); // subchunk1 size
            writeShortLittleEndian(baos, (short) 1); // PCM format
            writeShortLittleEndian(baos, (short) 1); // mono
            writeIntLittleEndian(baos, sampleRate);
            writeIntLittleEndian(baos, byteRate);
            writeShortLittleEndian(baos, (short) 2); // block align
            writeShortLittleEndian(baos, (short) 16); // bits per sample

            // data chunk
            baos.write("data".getBytes());
            writeIntLittleEndian(baos, dataSize);
            for (short s : samples) {
                writeShortLittleEndian(baos, s);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return baos.toByteArray();
    }

    private static void writeIntLittleEndian(OutputStream out, int val) throws IOException {
        out.write(val & 0xFF);
        out.write((val >> 8) & 0xFF);
        out.write((val >> 16) & 0xFF);
        out.write((val >> 24) & 0xFF);
    }

    private static void writeShortLittleEndian(OutputStream out, short val) throws IOException {
        out.write(val & 0xFF);
        out.write((val >> 8) & 0xFF);
    }

    private static void writeWavToOggOrWav(byte[] wavData, String outputPath) throws IOException {
        File outFile = new File(outputPath);
        outFile.getParentFile().mkdirs();
        try (FileOutputStream fos = new FileOutputStream(outFile)) {
            fos.write(wavData);
        }
    }

    private static void processUIAssets() throws Exception {
        System.out.println("Processing UI and HUD Assets...");
        String uiDir = TEXTURES_DIR + "ui/";

        // 1. Generate Unit Portraits
        generateUnitPortrait("standard", TEXTURES_DIR + "tank.png", TEXTURES_DIR + "turret.png", "STANDARD", new Color(200, 160, 40), uiDir + "portrait_standard.png");
        generateUnitPortrait("heavy", TEXTURES_DIR + "tank_heavy.png", TEXTURES_DIR + "turret_heavy.png", "HEAVY", new Color(190, 70, 50), uiDir + "portrait_heavy.png");
        generateUnitPortrait("light", TEXTURES_DIR + "tank_light.png", TEXTURES_DIR + "turret_light.png", "LIGHT", new Color(60, 140, 220), uiDir + "portrait_light.png");
        generateUnitPortrait("stealth", TEXTURES_DIR + "tank_stealth.png", TEXTURES_DIR + "turret_stealth.png", "STEALTH", new Color(130, 70, 190), uiDir + "portrait_stealth.png");

        // 2. Generate Directional Armor Schematic & Masks
        generateArmorSchematics(uiDir);

        // 3. Generate HUD Panel Frame, Stat Bar Frames & Fills
        generateHudPanelFrame(uiDir + "hud_panel_frame.png");
        generateStatBarFrame(uiDir + "stat_bar_frame.png");
        generateStatBarFill(uiDir + "stat_bar_fill.png");

        // 4. Generate Ammo Shell Icon
        generateAmmoShell(uiDir + "ammo_shell.png");

        // 5. Generate Wax Ready Seals
        generateWaxSeal(true, uiDir + "ready_seal_ready.png");
        generateWaxSeal(false, uiDir + "ready_seal_unready.png");

        // 6. Generate Victory / Defeat Banners
        generateBanner(true, uiDir + "victory_banner.png");
        generateBanner(false, uiDir + "defeat_banner.png");
        System.out.println("UI Assets Generated.");
    }

    private static void generateUnitPortrait(String typeName, String hullPath, String turretPath,
                                            String label, Color accentColor, String outputPath) throws Exception {
        int size = 256;
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        // Background: Dark slate/iron texture with radial lighting
        RadialGradientPaint bgGrad = new RadialGradientPaint(
                size / 2.0f, size * 0.45f, size * 0.65f,
                new float[]{0.0f, 0.5f, 1.0f},
                new Color[]{new Color(42, 48, 58), new Color(24, 28, 36), new Color(12, 14, 18)}
        );
        g.setPaint(bgGrad);
        g.fillRect(0, 0, size, size);

        // Grid lines / blueprint watermark in background
        g.setColor(new Color(255, 255, 255, 12));
        for (int i = 20; i < size; i += 20) {
            g.drawLine(i, 0, i, size);
            g.drawLine(0, i, size, i);
        }

        // Load tank hull & turret
        File hullFile = new File(hullPath);
        File turretFile = new File(turretPath);
        if (hullFile.exists()) {
            BufferedImage hull = ImageIO.read(hullFile);
            BufferedImage turret = turretFile.exists() ? ImageIO.read(turretFile) : null;

            // Draw hero vehicle at 3/4 isometric perspective
            Graphics2D gVehicle = (Graphics2D) g.create();
            gVehicle.translate(size / 2.0, size * 0.48);
            gVehicle.rotate(Math.toRadians(-22));

            int vSize = 150;
            // Shadow
            gVehicle.setColor(new Color(0, 0, 0, 140));
            gVehicle.fillOval(-vSize / 2 + 10, -vSize / 2 + 14, vSize, vSize);

            // Hull
            gVehicle.drawImage(hull, -vSize / 2, -vSize / 2, vSize, vSize, null);

            // Turret slightly offset rotation
            if (turret != null) {
                Graphics2D gTurret = (Graphics2D) gVehicle.create();
                gTurret.rotate(Math.toRadians(12));
                gTurret.drawImage(turret, -vSize / 2, -vSize / 2, vSize, vSize, null);
                gTurret.dispose();
            }
            gVehicle.dispose();
        }

        // Inner shadow vignette
        g.setPaint(new RadialGradientPaint(
                size / 2.0f, size / 2.0f, size * 0.55f,
                new float[]{0.6f, 1.0f},
                new Color[]{new Color(0, 0, 0, 0), new Color(0, 0, 0, 180)}
        ));
        g.fillRect(0, 0, size, size);

        // Ornate Gold/Bronze Beveled Border
        int borderThickness = 12;
        g.setPaint(new LinearGradientPaint(0, 0, size, size,
                new float[]{0.0f, 0.3f, 0.7f, 1.0f},
                new Color[]{new Color(235, 200, 95), new Color(170, 125, 40), new Color(245, 215, 120), new Color(130, 90, 25)}));
        g.setStroke(new BasicStroke(borderThickness));
        g.drawRect(borderThickness / 2, borderThickness / 2, size - borderThickness, size - borderThickness);

        // Thin inner gold fillet
        g.setColor(new Color(255, 230, 140, 200));
        g.setStroke(new BasicStroke(2));
        g.drawRect(borderThickness, borderThickness, size - borderThickness * 2, size - borderThickness * 2);

        // Corner brackets / filigree
        drawCornerBrackets(g, size, borderThickness);

        // Bottom label plaque
        int plaqueH = 34;
        int plaqueY = size - borderThickness - plaqueH;
        g.setColor(new Color(15, 18, 24, 230));
        g.fillRect(borderThickness + 2, plaqueY, size - (borderThickness + 2) * 2, plaqueH);
        g.setColor(new Color(210, 175, 75));
        g.drawRect(borderThickness + 2, plaqueY, size - (borderThickness + 2) * 2, plaqueH);

        // Label text
        g.setFont(new Font("SansSerif", Font.BOLD, 16));
        FontMetrics fm = g.getFontMetrics();
        int textW = fm.stringWidth(label);
        g.setColor(new Color(0, 0, 0, 180));
        g.drawString(label, (size - textW) / 2 + 1, plaqueY + 23 + 1);
        g.setColor(new Color(255, 235, 160));
        g.drawString(label, (size - textW) / 2, plaqueY + 23);

        g.dispose();
        ImageIO.write(img, "PNG", new File(outputPath));
    }

    private static void drawCornerBrackets(Graphics2D g, int size, int b) {
        g.setColor(new Color(255, 235, 150));
        int cSize = 22;
        // Top-left
        g.fillRect(b - 2, b - 2, cSize, 5);
        g.fillRect(b - 2, b - 2, 5, cSize);
        // Top-right
        g.fillRect(size - b - cSize + 2, b - 2, cSize, 5);
        g.fillRect(size - b - 3, b - 2, 5, cSize);
        // Bottom-left
        g.fillRect(b - 2, size - b - 3, cSize, 5);
        g.fillRect(b - 2, size - b - cSize + 2, 5, cSize);
        // Bottom-right
        g.fillRect(size - b - cSize + 2, size - b - 3, cSize, 5);
        g.fillRect(size - b - 3, size - b - cSize + 2, 5, cSize);

        // Rivets (bronze screws)
        drawRivet(g, b + 5, b + 5);
        drawRivet(g, size - b - 5, b + 5);
        drawRivet(g, b + 5, size - b - 5);
        drawRivet(g, size - b - 5, size - b - 5);
    }

    private static void drawRivet(Graphics2D g, int cx, int cy) {
        g.setColor(new Color(40, 30, 15));
        g.fillOval(cx - 3, cy - 3, 6, 6);
        g.setColor(new Color(255, 220, 110));
        g.fillOval(cx - 2, cy - 2, 4, 4);
    }

    private static void generateArmorSchematics(String uiDir) throws Exception {
        int size = 128;

        // 1. Tank Silhouette
        BufferedImage sil = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D gSil = sil.createGraphics();
        gSil.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Base dark metal chassis
        gSil.setColor(new Color(30, 35, 45));
        gSil.fillRoundRect(34, 22, 60, 84, 16, 16);

        // Left & right tread tracks
        gSil.setColor(new Color(20, 22, 28));
        gSil.fillRoundRect(22, 16, 16, 96, 8, 8);
        gSil.fillRoundRect(90, 16, 16, 96, 8, 8);

        // Tread rungs
        gSil.setColor(new Color(45, 50, 60));
        for (int y = 20; y < 108; y += 8) {
            gSil.drawLine(24, y, 36, y);
            gSil.drawLine(92, y, 104, y);
        }

        // Turret ring & cannon guide
        gSil.setColor(new Color(50, 58, 72));
        gSil.fillOval(48, 48, 32, 32);
        gSil.setColor(new Color(70, 82, 100));
        gSil.drawOval(48, 48, 32, 32);
        gSil.fillRect(61, 24, 6, 26); // Barrel

        gSil.dispose();
        ImageIO.write(sil, "PNG", new File(uiDir + "tank_silhouette.png"));

        // 2. Front Armor Plate (top wedge)
        BufferedImage front = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D gF = front.createGraphics();
        gF.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        gF.setColor(Color.WHITE);
        Polygon frontPoly = new Polygon(
                new int[]{38, 64, 90, 84, 44},
                new int[]{28, 18, 28, 40, 40},
                5
        );
        gF.fillPolygon(frontPoly);
        gF.dispose();
        ImageIO.write(front, "PNG", new File(uiDir + "armor_front.png"));

        // 3. Left Armor Plate (left skirt)
        BufferedImage left = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D gL = left.createGraphics();
        gL.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        gL.setColor(Color.WHITE);
        gL.fillRoundRect(18, 24, 14, 80, 6, 6);
        gL.dispose();
        ImageIO.write(left, "PNG", new File(uiDir + "armor_left.png"));

        // 4. Right Armor Plate (right skirt)
        BufferedImage right = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D gR = right.createGraphics();
        gR.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        gR.setColor(Color.WHITE);
        gR.fillRoundRect(96, 24, 14, 80, 6, 6);
        gR.dispose();
        ImageIO.write(right, "PNG", new File(uiDir + "armor_right.png"));

        // 5. Rear Armor Plate (bottom deck)
        BufferedImage rear = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D gB = rear.createGraphics();
        gB.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        gB.setColor(Color.WHITE);
        gB.fillRoundRect(38, 92, 52, 16, 6, 6);
        gB.dispose();
        ImageIO.write(rear, "PNG", new File(uiDir + "armor_rear.png"));
    }

    private static void generateHudPanelFrame(String outputPath) throws Exception {
        int w = 256, h = 256;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Translucent dark slate background
        g.setColor(new Color(14, 17, 24, 225));
        g.fillRoundRect(4, 4, w - 8, h - 8, 12, 12);

        // Beveled gold/bronze outer frame
        g.setPaint(new LinearGradientPaint(0, 0, w, h,
                new float[]{0.0f, 0.5f, 1.0f},
                new Color[]{new Color(230, 195, 85), new Color(160, 115, 35), new Color(240, 210, 110)}));
        g.setStroke(new BasicStroke(4));
        g.drawRoundRect(4, 4, w - 8, h - 8, 12, 12);

        // Inner dark groove
        g.setColor(new Color(0, 0, 0, 160));
        g.setStroke(new BasicStroke(2));
        g.drawRoundRect(8, 8, w - 16, h - 16, 8, 8);

        // Corner studs
        drawRivet(g, 12, 12);
        drawRivet(g, w - 12, 12);
        drawRivet(g, 12, h - 12);
        drawRivet(g, w - 12, h - 12);

        g.dispose();
        ImageIO.write(img, "PNG", new File(outputPath));
    }

    private static void generateStatBarFrame(String outputPath) throws Exception {
        int w = 128, h = 32;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Dark background slot
        g.setColor(new Color(10, 12, 16, 235));
        g.fillRoundRect(2, 2, w - 4, h - 4, 6, 6);

        // Embossed brass frame
        g.setPaint(new LinearGradientPaint(0, 0, 0, h,
                new float[]{0.0f, 0.5f, 1.0f},
                new Color[]{new Color(240, 210, 110), new Color(160, 120, 40), new Color(210, 170, 70)}));
        g.setStroke(new BasicStroke(3));
        g.drawRoundRect(2, 2, w - 4, h - 4, 6, 6);

        g.dispose();
        ImageIO.write(img, "PNG", new File(outputPath));
    }

    private static void generateStatBarFill(String outputPath) throws Exception {
        int w = 128, h = 32;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // White base fill for shader tinting with subtle 3D highlight
        g.setPaint(new LinearGradientPaint(0, 0, 0, h,
                new float[]{0.0f, 0.4f, 0.6f, 1.0f},
                new Color[]{new Color(255, 255, 255, 240), new Color(220, 220, 220, 255), new Color(180, 180, 180, 255), new Color(130, 130, 130, 255)}));
        g.fillRoundRect(3, 3, w - 6, h - 6, 4, 4);

        // Top glossy specular streak
        g.setColor(new Color(255, 255, 255, 160));
        g.fillRect(4, 4, w - 8, h / 3);

        g.dispose();
        ImageIO.write(img, "PNG", new File(outputPath));
    }

    private static void generateAmmoShell(String outputPath) throws Exception {
        int size = 64;
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Shadow
        g.setColor(new Color(0, 0, 0, 120));
        g.fillRoundRect(22, 10, 24, 48, 10, 10);

        // Brass Shell Casing (bottom 60%)
        g.setPaint(new LinearGradientPaint(20, 0, 44, 0,
                new float[]{0.0f, 0.3f, 0.7f, 1.0f},
                new Color[]{new Color(245, 215, 110), new Color(255, 245, 190), new Color(210, 165, 45), new Color(140, 100, 20)}));
        g.fillRoundRect(20, 24, 24, 34, 4, 4);

        // Rim at base
        g.fillRect(18, 54, 28, 5);

        // Copper Warhead (top 40% pointed cone)
        Polygon warhead = new Polygon(
                new int[]{20, 32, 44},
                new int[]{24, 6, 24},
                3
        );
        g.setPaint(new LinearGradientPaint(20, 0, 44, 0,
                new float[]{0.0f, 0.5f, 1.0f},
                new Color[]{new Color(215, 105, 55), new Color(255, 175, 130), new Color(160, 60, 25)}));
        g.fillPolygon(warhead);

        // Driving band ring
        g.setColor(new Color(180, 80, 35));
        g.fillRect(20, 46, 24, 4);

        g.dispose();
        ImageIO.write(img, "PNG", new File(outputPath));
    }

    private static void generateWaxSeal(boolean ready, String outputPath) throws Exception {
        int size = 128;
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int cx = size / 2, cy = size / 2, r = 48;

        // Shadow
        g.setColor(new Color(0, 0, 0, 110));
        g.fillOval(cx - r + 4, cy - r + 6, r * 2, r * 2);

        // Scalloped / melted wax blob edge
        g.setPaint(new RadialGradientPaint(cx - 10, cy - 10, r * 1.2f,
                new float[]{0.0f, 0.7f, 1.0f},
                ready
                        ? new Color[]{new Color(235, 45, 45), new Color(180, 20, 20), new Color(105, 10, 10)}
                        : new Color[]{new Color(110, 120, 135), new Color(65, 75, 90), new Color(35, 40, 50)}));

        // Draw irregular melted perimeter
        for (int angle = 0; angle < 360; angle += 15) {
            double rad = Math.toRadians(angle);
            int blobR = r + (int) (Math.sin(angle * 4.0) * 4);
            int px = cx + (int) (Math.cos(rad) * blobR);
            int py = cy + (int) (Math.sin(rad) * blobR);
            g.fillOval(px - 14, py - 14, 28, 28);
        }
        g.fillOval(cx - r, cy - r, r * 2, r * 2);

        // Inner stamped depression
        g.setColor(new Color(0, 0, 0, 80));
        g.drawOval(cx - r + 12, cy - r + 12, (r - 12) * 2, (r - 12) * 2);

        if (ready) {
            // Gold Laurel & Checkmark
            g.setPaint(new LinearGradientPaint(0, 0, size, size,
                    new float[]{0.0f, 1.0f},
                    new Color[]{new Color(255, 240, 160), new Color(210, 165, 40)}));
            // Checkmark
            g.setStroke(new BasicStroke(7, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.drawLine(cx - 16, cy, cx - 4, cy + 14);
            g.drawLine(cx - 4, cy + 14, cx + 18, cy - 12);
        } else {
            // Hourglass / pending
            g.setColor(new Color(220, 230, 240, 220));
            g.setStroke(new BasicStroke(5, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            Polygon topTri = new Polygon(new int[]{cx - 14, cx + 14, cx}, new int[]{cy - 16, cy - 16, cy}, 3);
            Polygon botTri = new Polygon(new int[]{cx - 14, cx + 14, cx}, new int[]{cy + 16, cy + 16, cy}, 3);
            g.drawPolygon(topTri);
            g.drawPolygon(botTri);
        }

        g.dispose();
        ImageIO.write(img, "PNG", new File(outputPath));
    }

    private static void generateBanner(boolean victory, String outputPath) throws Exception {
        int w = 512, h = 128;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        // Ribbon Body with Fishtail ends
        Polygon ribbon = new Polygon(
                new int[]{20, 50, 462, 492, 462, 50},
                new int[]{64, 20, 20, 64, 108, 108},
                6
        );

        // Shadow
        g.setColor(new Color(0, 0, 0, 130));
        g.translate(4, 6);
        g.fillPolygon(ribbon);
        g.translate(-4, -6);

        // Silk Fill
        if (victory) {
            g.setPaint(new LinearGradientPaint(0, 20, 0, 108,
                    new float[]{0.0f, 0.4f, 0.6f, 1.0f},
                    new Color[]{new Color(30, 75, 160), new Color(50, 110, 210), new Color(25, 60, 140), new Color(15, 35, 90)}));
        } else {
            g.setPaint(new LinearGradientPaint(0, 20, 0, 108,
                    new float[]{0.0f, 0.4f, 0.6f, 1.0f},
                    new Color[]{new Color(130, 25, 25), new Color(190, 45, 45), new Color(110, 20, 20), new Color(60, 10, 10)}));
        }
        g.fillPolygon(ribbon);

        // Ornate Gold Border & Trim
        g.setPaint(new LinearGradientPaint(0, 0, w, 0,
                new float[]{0.0f, 0.5f, 1.0f},
                new Color[]{new Color(250, 220, 100), new Color(255, 245, 180), new Color(210, 165, 45)}));
        g.setStroke(new BasicStroke(5));
        g.drawPolygon(ribbon);

        // Inner gold line
        g.setStroke(new BasicStroke(2));
        g.drawRect(60, 26, w - 120, 76);

        // Big Embroidered Text
        String text = victory ? "VICTORY" : "DEFEAT";
        g.setFont(new Font("Serif", Font.BOLD, 46));
        FontMetrics fm = g.getFontMetrics();
        int tw = fm.stringWidth(text);

        // Gold 3D Text Shadow
        g.setColor(new Color(0, 0, 0, 200));
        g.drawString(text, (w - tw) / 2 + 2, 76 + 2);

        g.setPaint(new LinearGradientPaint(0, 40, 0, 80,
                new float[]{0.0f, 0.5f, 1.0f},
                new Color[]{new Color(255, 245, 190), new Color(245, 210, 90), new Color(190, 145, 30)}));
        g.drawString(text, (w - tw) / 2, 76);

        g.dispose();
        ImageIO.write(img, "PNG", new File(outputPath));
    }
}
