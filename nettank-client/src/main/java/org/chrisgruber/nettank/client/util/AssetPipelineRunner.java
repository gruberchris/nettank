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
            BufferedImage enhanced = enhanceTurretSilhouette(cropped, "standard");
            ImageIO.write(enhanced, "PNG", new File(TEXTURES_DIR + "turret.png"));

            BufferedImage mask = generateTurretTeamMask(enhanced);
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
            BufferedImage enhanced = enhanceTurretSilhouette(cropped, "heavy");
            ImageIO.write(enhanced, "PNG", new File(TEXTURES_DIR + "turret_heavy.png"));

            BufferedImage mask = generateTurretTeamMask(enhanced);
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
            BufferedImage enhanced = enhanceTurretSilhouette(cropped, "light");
            ImageIO.write(enhanced, "PNG", new File(TEXTURES_DIR + "turret_light.png"));

            BufferedImage mask = generateTurretTeamMask(enhanced);
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
            BufferedImage enhanced = enhanceTurretSilhouette(cropped, "stealth");
            ImageIO.write(enhanced, "PNG", new File(TEXTURES_DIR + "turret_stealth.png"));

            BufferedImage mask = generateTurretTeamMask(enhanced);
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

        // 4. Modern Military Tank Engine Loops (Idle, Forward Surge, Reverse Gear)
        byte[] idleWav = synthesizeEngineIdle();
        writeWavToOggOrWav(idleWav, SOUNDS_DIR + "engine_idle.ogg");

        byte[] forwardWav = synthesizeEngineForward();
        writeWavToOggOrWav(forwardWav, SOUNDS_DIR + "engine_forward.ogg");
        writeWavToOggOrWav(forwardWav, SOUNDS_DIR + "engine_loop.ogg"); // compatibility

        byte[] reverseWav = synthesizeEngineReverse();
        writeWavToOggOrWav(reverseWav, SOUNDS_DIR + "engine_reverse.ogg");

        // 5. Motorized Armored Turret Traverse Sound (Planetary gear motor + bearing race rumble)
        byte[] turretWav = synthesizeTurretRotate();
        writeWavToOggOrWav(turretWav, SOUNDS_DIR + "turret_rotate.ogg");

        // 6. Tactical Supply Crate Pickup (Crisp high-tech energy chime)
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

    private static BufferedImage enhanceTurretSilhouette(BufferedImage src, String type) {
        int w = src.getWidth(), h = src.getHeight();
        BufferedImage dest = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = dest.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.drawImage(src, 0, 0, null);

        int cx = w / 2; // 64

        if ("standard".equalsIgnoreCase(type)) {
            // M1A2 Abrams 120mm M256 Smoothbore Main Gun Barrel & Bore Evacuator
            // 1. Dark outline backing for max terrain contrast
            g2.setColor(new Color(15, 18, 15, 240));
            g2.fillRect(cx - 5, 4, 10, 56);

            // 2. Gunmetal barrel body
            GradientPaint barrelGrad = new GradientPaint(cx - 3, 0, new Color(55, 62, 58), cx + 3, 0, new Color(90, 100, 92));
            g2.setPaint(barrelGrad);
            g2.fillRect(cx - 3, 6, 6, 52);

            // 3. Fume Extractor / Bore Evacuator (High-contrast military identification collar)
            g2.setColor(new Color(20, 22, 20, 255));
            g2.fillRoundRect(cx - 8, 26, 16, 18, 4, 4);
            GradientPaint fumeGrad = new GradientPaint(cx - 7, 0, new Color(225, 215, 190), cx + 7, 0, new Color(145, 135, 115));
            g2.setPaint(fumeGrad);
            g2.fillRoundRect(cx - 7, 27, 14, 16, 3, 3);
            // Black tactical identifier chevron rings
            g2.setColor(new Color(30, 30, 30, 240));
            g2.fillRect(cx - 7, 31, 14, 2);
            g2.fillRect(cx - 7, 37, 14, 2);

            // 4. Muzzle Brake & Bore Tip
            g2.setColor(new Color(25, 28, 25));
            g2.fillRoundRect(cx - 5, 4, 10, 8, 2, 2);
            g2.setColor(new Color(210, 210, 200));
            g2.fillRect(cx - 4, 8, 8, 2); // Highlight ring
            g2.setColor(new Color(5, 5, 5));
            g2.fillOval(cx - 2, 4, 4, 3); // Muzzle opening
        } else if ("heavy".equalsIgnoreCase(type)) {
            // M1A2 TUSK II Twin Heavy Cannons
            int[] barrelX = {cx - 11, cx + 11};
            for (int bx : barrelX) {
                g2.setColor(new Color(15, 18, 15, 240));
                g2.fillRect(bx - 4, 2, 8, 56);

                GradientPaint bGrad = new GradientPaint(bx - 3, 0, new Color(60, 65, 60), bx + 3, 0, new Color(95, 105, 95));
                g2.setPaint(bGrad);
                g2.fillRect(bx - 2, 4, 5, 52);

                // Fume extractor
                g2.setColor(new Color(20, 20, 20, 255));
                g2.fillRoundRect(bx - 6, 22, 12, 16, 3, 3);
                g2.setColor(new Color(225, 200, 140)); // Heavy brass / desert camo band
                g2.fillRect(bx - 5, 23, 10, 14);
                g2.setColor(new Color(30, 30, 30));
                g2.fillRect(bx - 5, 28, 10, 2);

                // Heavy slotted muzzle brake
                g2.setColor(new Color(25, 28, 25));
                g2.fillRoundRect(bx - 5, 2, 11, 8, 2, 2);
                g2.setColor(new Color(210, 200, 180));
                g2.fillRect(bx - 4, 6, 9, 2);
                g2.setColor(new Color(5, 5, 5));
                g2.fillOval(bx - 2, 2, 4, 3);
            }
        } else if ("light".equalsIgnoreCase(type)) {
            // LAV-25 Stryker 25mm Bushmaster Rapid Autocannon
            g2.setColor(new Color(15, 18, 15, 240));
            g2.fillRect(cx - 3, 4, 6, 58);

            GradientPaint bGrad = new GradientPaint(cx - 2, 0, new Color(70, 78, 72), cx + 2, 0, new Color(115, 125, 118));
            g2.setPaint(bGrad);
            g2.fillRect(cx - 2, 6, 4, 54);

            // Fluted flash suppressor & bore tip
            g2.setColor(new Color(25, 28, 25));
            g2.fillRoundRect(cx - 4, 4, 8, 8, 2, 2);
            g2.setColor(new Color(230, 230, 220)); // Bright high-viz flash ring
            g2.fillRect(cx - 3, 8, 6, 2);
            g2.setColor(new Color(5, 5, 5));
            g2.fillOval(cx - 2, 4, 4, 3);
        } else if ("stealth".equalsIgnoreCase(type)) {
            // Shadow Stalker Faceted Radar-Absorbent Cannon Shroud
            int[] xPoints = {cx - 5, cx + 5, cx + 8, cx - 8};
            int[] yPoints = {6, 6, 58, 58};
            g2.setColor(new Color(12, 14, 12, 245));
            g2.fillPolygon(xPoints, yPoints, 4);

            GradientPaint sGrad = new GradientPaint(cx - 4, 0, new Color(38, 44, 40), cx + 4, 0, new Color(75, 85, 78));
            g2.setPaint(sGrad);
            int[] xIn = {cx - 4, cx + 4, cx + 6, cx - 6};
            int[] yIn = {8, 8, 56, 56};
            g2.fillPolygon(xIn, yIn, 4);

            // High-contrast tactical night-vision green alignment chevron
            g2.setColor(new Color(80, 230, 140, 255));
            g2.fillRect(cx - 4, 24, 8, 3);
            g2.fillRect(cx - 3, 30, 6, 2);
            g2.setColor(new Color(5, 5, 5));
            g2.fillOval(cx - 2, 6, 4, 3);
        }

        g2.dispose();
        return dest;
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

    // --- High-Fidelity Military Audio Synthesizer ---

    private static byte[] synthesizeCannonShot() {
        int sampleRate = 44100;
        float duration = 0.95f;
        int numSamples = (int) (sampleRate * duration);
        short[] samples = new short[numSamples];
        Random rand = new Random(345);

        for (int i = 0; i < numSamples; i++) {
            float t = i / (float) sampleRate;

            // 1. Initial High-Pressure Concussion Wave (0..25ms) - Heavy distorted saturation punch
            float shockwave = 0.0f;
            if (t < 0.04f) {
                float env = (float) Math.exp(-t * 80.0);
                float noise = (rand.nextFloat() * 2.0f - 1.0f);
                shockwave = (float) Math.tanh((noise * 3.5f + (float) Math.sin(2.0 * Math.PI * 180.0 * t) * 2.0f)) * env;
            }

            // 2. 120mm Smoothbore Barrel Resonant Body (45Hz sub-bass thump + 88Hz harmonic)
            float subBass = (float) (Math.sin(2.0 * Math.PI * 45.0 * t) * 0.75 + Math.sin(2.0 * Math.PI * 88.0 * t) * 0.4)
                    * (float) Math.exp(-t * 5.5);
            // Non-linear soft saturation on low-end
            subBass = (float) Math.tanh(subBass * 1.8f);

            // 3. Supersonic Projectile Crack & Muzzle Gas Expansion (Broadband filtered rumble)
            float gasRumble = (rand.nextFloat() * 2.0f - 1.0f) * (float) Math.exp(-t * 6.5) * 0.55f;

            // 4. Mechanical Breech Autoloader Slam (Dual transients at 20ms and 85ms)
            float breech = 0.0f;
            if (t >= 0.02f && t < 0.06f) {
                float bt = t - 0.02f;
                breech += (float) Math.sin(2.0 * Math.PI * 520.0 * bt) * (float) Math.exp(-bt * 60.0) * 0.45f;
            }
            if (t >= 0.08f && t < 0.14f) {
                float bt = t - 0.08f;
                breech += (float) Math.sin(2.0 * Math.PI * 740.0 * bt) * (float) Math.exp(-bt * 50.0) * 0.35f;
            }

            // 5. Open-Range Acoustic Reverberation Tail (Deep rolling exterior tail)
            float reverb = (rand.nextFloat() * 2.0f - 1.0f) * (float) Math.exp(-t * 2.8) * 0.25f;

            float mix = (shockwave * 0.6f + subBass * 0.75f + gasRumble * 0.4f + breech * 0.3f + reverb * 0.2f);
            mix = Math.max(-1.0f, Math.min(1.0f, mix * 1.25f));
            samples[i] = (short) (mix * 32767);
        }
        return createWavBytes(samples, sampleRate);
    }

    private static byte[] synthesizeExplosion() {
        int sampleRate = 44100;
        float duration = 1.35f;
        int numSamples = (int) (sampleRate * duration);
        short[] samples = new short[numSamples];
        Random rand = new Random(876);

        for (int i = 0; i < numSamples; i++) {
            float t = i / (float) sampleRate;

            // Multi-stage catastrophic ammo detonation:
            // Blast 1 (t=0s, initial hull rupture)
            float b1 = (rand.nextFloat() * 2.0f - 1.0f) * (float) Math.exp(-t * 5.0) * 0.7f;
            float sub1 = (float) Math.sin(2.0 * Math.PI * 38.0 * t) * (float) Math.exp(-t * 4.0) * 0.8f;

            // Blast 2 (t=0.08s, internal ammo carousel cookoff)
            float b2 = 0.0f;
            if (t >= 0.08f) {
                float t2 = t - 0.08f;
                b2 = (rand.nextFloat() * 2.0f - 1.0f) * (float) Math.exp(-t2 * 6.0) * 0.75f;
            }

            // Blast 3 (t=0.22s, tertiary turret blowout)
            float b3 = 0.0f;
            if (t >= 0.22f) {
                float t3 = t - 0.22f;
                b3 = (rand.nextFloat() * 2.0f - 1.0f) * (float) Math.exp(-t3 * 4.5) * 0.6f;
            }

            // Low frequency ground shockwave rumble
            float rumble = (float) Math.sin(2.0 * Math.PI * 28.0 * t) * (float) Math.exp(-t * 2.2) * 0.6f;

            // Metal tearing & shrapnel debris
            float debris = (rand.nextFloat() * 2.0f - 1.0f) * (float) Math.exp(-t * 2.5) * 0.35f;

            float mix = (b1 + sub1 + b2 + b3 + rumble + debris) * 0.42f;
            mix = (float) Math.tanh(mix * 1.5f);
            samples[i] = (short) (mix * 32767);
        }
        return createWavBytes(samples, sampleRate);
    }

    private static byte[] synthesizeHitClang(boolean crit) {
        int sampleRate = 44100;
        float duration = crit ? 0.55f : 0.35f;
        int numSamples = (int) (sampleRate * duration);
        short[] samples = new short[numSamples];
        Random rand = new Random(982);

        for (int i = 0; i < numSamples; i++) {
            float t = i / (float) sampleRate;

            // 1. High-Velocity Tungsten Penetrator Impact Thud (180Hz dense kinetic transient)
            float thud = (float) Math.sin(2.0 * Math.PI * (crit ? 140.0 : 190.0) * t) * (float) Math.exp(-t * 18.0) * 0.8f;
            thud = (float) Math.tanh(thud * 2.0f);

            // 2. Explosive Reactive Armor (ERA) Detonation Crack (Broadband high-energy crack)
            float eraCrack = (rand.nextFloat() * 2.0f - 1.0f) * (float) Math.exp(-t * 45.0) * 0.7f;

            // 3. Steel Spall & Armor Plate Groan (Low-mid resonance)
            float steelRes = (float) Math.sin(2.0 * Math.PI * (crit ? 320.0 : 480.0) * t) * (float) Math.exp(-t * 12.0) * 0.35f;

            float mix = (thud * 0.55f + eraCrack * 0.55f + steelRes * 0.3f);
            mix = Math.max(-1.0f, Math.min(1.0f, mix * 1.15f));
            samples[i] = (short) (mix * 32767);
        }
        return createWavBytes(samples, sampleRate);
    }

    private static void makeSeamlessLoop(short[] samples, int sampleRate, float crossfadeDuration) {
        int crossfadeSamples = (int) (sampleRate * crossfadeDuration);
        int n = samples.length;
        for (int i = 0; i < crossfadeSamples; i++) {
            float t = (float) i / crossfadeSamples;
            float smoothT = (float) (0.5 - 0.5 * Math.cos(t * Math.PI)); // Cosine crossfade
            float blended = samples[i] * smoothT + samples[n - crossfadeSamples + i] * (1.0f - smoothT);
            samples[i] = (short) blended;
            samples[n - crossfadeSamples + i] = (short) blended;
        }
    }

    private static byte[] synthesizeEngineIdle() {
        int sampleRate = 44100;
        float duration = 2.0f;
        int numSamples = (int) (sampleRate * duration);
        short[] samples = new short[numSamples];
        Random rand = new Random(711);

        for (int i = 0; i < numSamples; i++) {
            float t = i / (float) sampleRate;

            // 1. Deep Asymmetrical Diesel Combustion Pulse Train (22 firings/sec)
            float firingFreq = 22.0f;
            float pulsePhase = (t * firingFreq) % 1.0f;
            float combustion = (float) (Math.exp(-pulsePhase * 5.5) * Math.sin(pulsePhase * 2.0 * Math.PI * 2.0));

            // 2. Sub-bass chassis vibration (26Hz fundamental + 52Hz harmonic)
            float subBass = (float) (0.45 * Math.sin(2.0 * Math.PI * 26.0 * t) + 0.28 * Math.sin(2.0 * Math.PI * 52.0 * t));

            // 3. Gentle air induction breathing hiss
            float airBreathe = (rand.nextFloat() * 2.0f - 1.0f) * 0.16f;

            // 4. Soft gas turbine whisper (980Hz)
            float turbineWhisper = (float) (0.06 * Math.sin(2.0 * Math.PI * 980.0 * t));

            float raw = (combustion * 0.50f + subBass * 0.45f + airBreathe + turbineWhisper);
            float saturated = (float) Math.tanh(raw * 1.10f);
            samples[i] = (short) (Math.max(-1.0f, Math.min(1.0f, saturated)) * 32767);
        }
        makeSeamlessLoop(samples, sampleRate, 0.08f);
        return createWavBytes(samples, sampleRate);
    }

    private static byte[] synthesizeEngineForward() {
        int sampleRate = 44100;
        float duration = 2.0f;
        int numSamples = (int) (sampleRate * duration);
        short[] samples = new short[numSamples];
        Random rand = new Random(822);

        for (int i = 0; i < numSamples; i++) {
            float t = i / (float) sampleRate;

            // 1. Asymmetrical High-RPM Combustion Pulse Train (48 pulses/sec)
            float firingFreq = 48.0f;
            float pulsePhase = (t * firingFreq) % 1.0f;
            float combustion = (float) (Math.exp(-pulsePhase * 6.5) * Math.sin(pulsePhase * 2.0 * Math.PI * 2.2));

            // 2. Sub-bass chassis vibration (32Hz fundamental + 64Hz harmonic)
            float subBass = (float) (0.42 * Math.sin(2.0 * Math.PI * 32.0 * t) + 0.28 * Math.sin(2.0 * Math.PI * 64.0 * t));

            // 3. Steel Track Pin Squeak (Iconic "Driving Tank" squeal: resonant metallic track pin friction)
            float trackLinkRate = 16.0f; // 16 links per second passing sprocket
            float pinEnvelope = (float) Math.pow(Math.max(0.0, Math.sin(2.0 * Math.PI * trackLinkRate * t)), 4.0);
            float pinPitchMod = (float) (3000.0 + 450.0 * Math.sin(2.0 * Math.PI * 8.0 * t));
            float trackSqueak = (float) Math.sin(2.0 * Math.PI * pinPitchMod * t) * pinEnvelope * 0.26f;

            // 4. Steel Track Link Impact Thuds (Guide horns striking drive sprocket & road wheels)
            float linkImpactEnv = (float) Math.pow(Math.max(0.0, Math.sin(2.0 * Math.PI * trackLinkRate * t)), 8.0);
            float linkImpact = (float) Math.sin(2.0 * Math.PI * 185.0 * t) * linkImpactEnv * 0.32f;

            // 5. Sand & Gravel Ground Displacement Crunch (Filtered tread noise modulated with track rate)
            float groundNoise = (rand.nextFloat() * 2.0f - 1.0f);
            float sandCrunch = groundNoise * (float) (0.24 * (0.6 + 0.4 * Math.sin(2.0 * Math.PI * trackLinkRate * t)));

            // 6. Turbocharger & Gas Turbine Induction Whine
            float turbine = (float) (0.16 * Math.sin(2.0 * Math.PI * 1580.0 * t) + 0.10 * Math.sin(2.0 * Math.PI * 2370.0 * t));

            float raw = (combustion * 0.45f + subBass * 0.35f + trackSqueak + linkImpact + sandCrunch + turbine);
            float saturated = (float) Math.tanh(raw * 1.35f);
            samples[i] = (short) (Math.max(-1.0f, Math.min(1.0f, saturated)) * 32767);
        }
        makeSeamlessLoop(samples, sampleRate, 0.08f);
        return createWavBytes(samples, sampleRate);
    }

    private static byte[] synthesizeEngineReverse() {
        int sampleRate = 44100;
        float duration = 2.0f;
        int numSamples = (int) (sampleRate * duration);
        short[] samples = new short[numSamples];
        Random rand = new Random(933);

        for (int i = 0; i < numSamples; i++) {
            float t = i / (float) sampleRate;

            // 1. Asymmetrical Low-Gear High-Torque Combustion Pulse Train (32 pulses/sec)
            float firingFreq = 32.0f;
            float pulsePhase = (t * firingFreq) % 1.0f;
            float combustion = (float) (Math.exp(-pulsePhase * 6.0) * Math.sin(pulsePhase * 2.0 * Math.PI * 2.0));

            // 2. Sub-bass heavy load vibration (30Hz + 60Hz)
            float subBass = (float) (0.44 * Math.sin(2.0 * Math.PI * 30.0 * t) + 0.26 * Math.sin(2.0 * Math.PI * 60.0 * t));

            // 3. Heavy Transmission Planetary Reverse Gearbox Whine (640Hz & 960Hz mechanical meshing teeth)
            float gearWhine = (float) (0.28 * Math.sin(2.0 * Math.PI * 640.0 * t) + 0.16 * Math.sin(2.0 * Math.PI * 960.0 * t));

            // 4. Low-Speed Steel Track Clatter & Slower Pin Impact (10 links/sec)
            float trackLinkRate = 10.0f;
            float trackClank = (float) Math.sin(2.0 * Math.PI * 150.0 * t) * (float) Math.pow(Math.max(0.0, Math.sin(2.0 * Math.PI * trackLinkRate * t)), 6.0) * 0.28f;

            // 5. Sand & Gravel Ground Displacement
            float groundNoise = (rand.nextFloat() * 2.0f - 1.0f);
            float sandCrunch = groundNoise * (float) (0.20 * (0.6 + 0.4 * Math.sin(2.0 * Math.PI * trackLinkRate * t)));

            float raw = (combustion * 0.40f + subBass * 0.40f + gearWhine + trackClank + sandCrunch);
            float saturated = (float) Math.tanh(raw * 1.25f);
            samples[i] = (short) (Math.max(-1.0f, Math.min(1.0f, saturated)) * 32767);
        }
        makeSeamlessLoop(samples, sampleRate, 0.08f);
        return createWavBytes(samples, sampleRate);
    }

    private static byte[] synthesizeTurretRotate() {
        int sampleRate = 44100;
        float duration = 2.0f;
        int numSamples = (int) (sampleRate * duration);
        short[] samples = new short[numSamples];
        Random rand = new Random(456);

        for (int i = 0; i < numSamples; i++) {
            float t = i / (float) sampleRate;

            // 1. High-Torque Electro-Hydraulic Planetary Drive Motor Whine (520Hz + 1040Hz + 1560Hz)
            float motorRipple = (float) (1.0 + 0.12 * Math.sin(2.0 * Math.PI * 18.0 * t));
            float motorWhine = (float) (0.30 * Math.sin(2.0 * Math.PI * 520.0 * t) +
                                       0.18 * Math.sin(2.0 * Math.PI * 1040.0 * t) +
                                       0.08 * Math.sin(2.0 * Math.PI * 1560.0 * t)) * motorRipple;

            // 2. Turret Ring Bearing Race Rumble (Deep hull mechanical groan: 95Hz & 190Hz)
            float bearingRace = (float) (0.32 * Math.sin(2.0 * Math.PI * 95.0 * t) + 0.18 * Math.sin(2.0 * Math.PI * 190.0 * t));

            // 3. Metallic Turret Ring Friction & Churning Grease (Filtered noise pulsed with race revolution)
            float ringFriction = (rand.nextFloat() * 2.0f - 1.0f) * (float) (0.18 * (0.7 + 0.3 * Math.sin(2.0 * Math.PI * 36.0 * t)));

            // 4. Ring Gear Tooth Meshing Ticks (380Hz mechanical engagement ticks pulsed at 36Hz)
            float toothTick = (float) Math.sin(2.0 * Math.PI * 380.0 * t) * (float) Math.pow(Math.max(0.0, Math.sin(2.0 * Math.PI * 36.0 * t)), 6.0) * 0.16f;

            float raw = (motorWhine + bearingRace + ringFriction + toothTick);
            float saturated = (float) Math.tanh(raw * 1.25f);
            samples[i] = (short) (Math.max(-1.0f, Math.min(1.0f, saturated)) * 32767);
        }
        makeSeamlessLoop(samples, sampleRate, 0.08f);
        return createWavBytes(samples, sampleRate);
    }

    private static byte[] synthesizePowerupChime() {
        int sampleRate = 44100;
        float duration = 0.45f;
        int numSamples = (int) (sampleRate * duration);
        short[] samples = new short[numSamples];

        for (int i = 0; i < numSamples; i++) {
            float t = i / (float) sampleRate;
            // Tactical radio comms target lock chirp: 1100Hz -> 1760Hz rapid military chirp
            float freq = t < 0.08f ? 1100.0f : (t < 0.18f ? 1480.0f : 1760.0f);
            float tone = (float) Math.sin(2.0 * Math.PI * freq * t) * (float) Math.exp(-t * 6.0);
            float harmonic = (float) Math.sin(4.0 * Math.PI * freq * t) * 0.3f * (float) Math.exp(-t * 8.0);
            float squelch = (new Random((long)(t * 10000)).nextFloat() * 2.0f - 1.0f) * (float) Math.exp(-t * 25.0) * 0.15f;

            float mix = (tone * 0.65f + harmonic * 0.25f + squelch);
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
            // Rugged mil-spec toggle switch clack
            float tone = (float) Math.sin(2.0 * Math.PI * frequency * t) * (float) Math.exp(-t * (1.0f / duration * 5.0f));
            float clickImpulse = (new Random((long)(t * 20000)).nextFloat() * 2.0f - 1.0f) * (float) Math.exp(-t * 60.0) * 0.4f;
            float mix = (tone * 0.65f + clickImpulse * 0.4f);
            samples[i] = (short) (mix * 32767);
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
            writeIntLittleEndian(baos, 16);
            writeShortLittleEndian(baos, (short) 1);
            writeShortLittleEndian(baos, (short) 1);
            writeIntLittleEndian(baos, sampleRate);
            writeIntLittleEndian(baos, byteRate);
            writeShortLittleEndian(baos, (short) 2);
            writeShortLittleEndian(baos, (short) 16);

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

    // --- High-Contrast Tactical Power-Up Crate Icons ---

    private static BufferedImage generatePowerupCrate(String type, int size) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        // Heavy Military Ammo Crate Colors based on type
        Color primaryColor;
        Color accentColor;
        String badgeText;
        String subText;

        if (type.equals("damage_2x")) {
            primaryColor = new Color(140, 25, 20); // Crimson Ordnance
            accentColor = new Color(255, 180, 40); // Hazard Yellow
            badgeText = "2X";
            subText = "AP";
        } else if (type.equals("damage_3x")) {
            primaryColor = new Color(180, 20, 20);
            accentColor = new Color(255, 220, 60);
            badgeText = "3X";
            subText = "AP";
        } else if (type.equals("speed_2x")) {
            primaryColor = new Color(20, 65, 120); // Tactical Cyan/Blue
            accentColor = new Color(50, 210, 255);
            badgeText = "2X";
            subText = "SPD";
        } else if (type.equals("speed_3x")) {
            primaryColor = new Color(15, 85, 155);
            accentColor = new Color(80, 240, 255);
            badgeText = "3X";
            subText = "SPD";
        } else if (type.equals("reload_2x")) {
            primaryColor = new Color(145, 80, 15); // Tactical Amber/Orange
            accentColor = new Color(255, 190, 40);
            badgeText = "2X";
            subText = "LOAD";
        } else if (type.equals("reload_3x")) {
            primaryColor = new Color(180, 95, 15);
            accentColor = new Color(255, 215, 60);
            badgeText = "3X";
            subText = "LOAD";
        } else if (type.equals("repair_hp")) {
            primaryColor = new Color(25, 85, 40); // Army Medic Green
            accentColor = new Color(75, 235, 100);
            badgeText = "+HP";
            subText = "MED";
        } else if (type.equals("repair_armor")) {
            primaryColor = new Color(40, 55, 75); // Ballistic Kevlar Slate
            accentColor = new Color(100, 190, 255);
            badgeText = "+ARM";
            subText = "DEF";
        } else { // unlimited_ammo
            primaryColor = new Color(85, 30, 110); // Special Munitions Purple
            accentColor = new Color(245, 200, 50);
            badgeText = "MAX";
            subText = "AMMO";
        }

        // Crate Drop Shadow
        g.setColor(new Color(0, 0, 0, 140));
        g.fillRoundRect(6, 8, size - 12, size - 12, 10, 10);

        // Mil-Spec Crate Body
        g.setPaint(new LinearGradientPaint(0, 4, 0, size - 4,
                new float[]{0.0f, 0.5f, 1.0f},
                new Color[]{primaryColor.brighter(), primaryColor, primaryColor.darker()}));
        g.fillRoundRect(4, 4, size - 8, size - 8, 8, 8);

        // Crate Corner Protective Brackets
        g.setColor(new Color(30, 35, 40));
        g.fillRect(4, 4, 12, 4);
        g.fillRect(4, 4, 4, 12);
        g.fillRect(size - 16, 4, 12, 4);
        g.fillRect(size - 8, 4, 4, 12);
        g.fillRect(4, size - 8, 12, 4);
        g.fillRect(4, size - 16, 4, 12);
        g.fillRect(size - 16, size - 8, 12, 4);
        g.fillRect(size - 8, size - 16, 4, 12);

        // High-Contrast Center Tactical Placard
        int pSize = size - 18;
        g.setColor(new Color(15, 18, 22, 240));
        g.fillRoundRect(9, 9, pSize, pSize, 6, 6);
        g.setColor(accentColor);
        g.setStroke(new BasicStroke(2));
        g.drawRoundRect(9, 9, pSize, pSize, 6, 6);

        // Specific Tactical Iconography
        if (type.equals("repair_hp")) {
            // Bright Red/White Medic Cross
            g.setColor(Color.WHITE);
            g.fillRect(size / 2 - 12, size / 2 - 12, 24, 24);
            g.setColor(new Color(220, 30, 30));
            g.fillRect(size / 2 - 3, size / 2 - 10, 6, 20);
            g.fillRect(size / 2 - 10, size / 2 - 3, 20, 6);
        } else if (type.equals("repair_armor")) {
            // Ballistic Shield
            Polygon shield = new Polygon(
                    new int[]{size / 2 - 12, size / 2 + 12, size / 2 + 12, size / 2, size / 2 - 12},
                    new int[]{size / 2 - 12, size / 2 - 12, size / 2 + 2, size / 2 + 12, size / 2 + 2},
                    5
            );
            g.setColor(new Color(60, 160, 255));
            g.fillPolygon(shield);
            g.setColor(Color.WHITE);
            g.setStroke(new BasicStroke(2));
            g.drawPolygon(shield);
        } else if (type.startsWith("speed")) {
            // Dual High-Velocity Propulsion Chevrons >>
            g.setColor(accentColor);
            g.setStroke(new BasicStroke(3, BasicStroke.CAP_ROUND, BasicStroke.JOIN_MITER));
            g.drawLine(size / 2 - 8, size / 2 - 10, size / 2 - 1, size / 2 - 3);
            g.drawLine(size / 2 - 1, size / 2 - 3, size / 2 - 8, size / 2 + 4);
            g.drawLine(size / 2, size / 2 - 10, size / 2 + 7, size / 2 - 3);
            g.drawLine(size / 2 + 7, size / 2 - 3, size / 2, size / 2 + 4);
        } else if (type.startsWith("damage")) {
            // APFSDS Sabot Dart
            g.setColor(accentColor);
            Polygon dart = new Polygon(
                    new int[]{size / 2, size / 2 + 6, size / 2 + 2, size / 2 + 2, size / 2 - 2, size / 2 - 2, size / 2 - 6},
                    new int[]{size / 2 - 13, size / 2 - 3, size / 2 - 3, size / 2 + 5, size / 2 + 5, size / 2 - 3, size / 2 - 3},
                    7
            );
            g.fillPolygon(dart);
        } else if (type.startsWith("reload")) {
            // Autoloader Twin Shells
            g.setColor(accentColor);
            g.fillRoundRect(size / 2 - 8, size / 2 - 12, 6, 16, 3, 3);
            g.fillRoundRect(size / 2 + 2, size / 2 - 12, 6, 16, 3, 3);
        } else {
            // Infinity Ammo Symbol
            g.setColor(accentColor);
            g.setFont(new Font("SansSerif", Font.BOLD, 18));
            FontMetrics fm = g.getFontMetrics();
            int iw = fm.stringWidth("∞");
            g.drawString("∞", (size - iw) / 2, size / 2 + 1);
        }

        // Bold Stencil Subtext at bottom of crate
        g.setFont(new Font("SansSerif", Font.BOLD, 11));
        FontMetrics fm = g.getFontMetrics();
        int bw = fm.stringWidth(badgeText + " " + subText);
        g.setColor(new Color(0, 0, 0, 220));
        g.drawString(badgeText + " " + subText, (size - bw) / 2 + 1, size - 8 + 1);
        g.setColor(accentColor);
        g.drawString(badgeText + " " + subText, (size - bw) / 2, size - 8);

        g.dispose();
        return img;
    }

    // --- US Army / USMC Tactical Military UI & HUD Assets ---

    private static void processUIAssets() throws Exception {
        System.out.println("Processing US Military Tactical UI and HUD Assets...");
        String uiDir = TEXTURES_DIR + "ui/";

        // 1. Generate Tactical Vehicle Briefing Placards
        generateUnitPortrait("standard", TEXTURES_DIR + "tank.png", TEXTURES_DIR + "turret.png", "M1A2 ABRAMS MBT", new Color(200, 160, 40), uiDir + "portrait_standard.png");
        generateUnitPortrait("heavy", TEXTURES_DIR + "tank_heavy.png", TEXTURES_DIR + "turret_heavy.png", "M1A2 TUSK HEAVY", new Color(190, 70, 50), uiDir + "portrait_heavy.png");
        generateUnitPortrait("light", TEXTURES_DIR + "tank_light.png", TEXTURES_DIR + "turret_light.png", "LAV-25 STRYKER", new Color(60, 140, 220), uiDir + "portrait_light.png");
        generateUnitPortrait("stealth", TEXTURES_DIR + "tank_stealth.png", TEXTURES_DIR + "turret_stealth.png", "SHADOW STALKER", new Color(130, 70, 190), uiDir + "portrait_stealth.png");

        // 2. Generate Modern Composite Armor Schematic & Masks
        generateArmorSchematics(uiDir);

        // 3. Generate Mil-Spec HUD Frames & Energy Bars
        generateHudPanelFrame(uiDir + "hud_panel_frame.png");
        generateStatBarFrame(uiDir + "stat_bar_frame.png");
        generateStatBarFill(uiDir + "stat_bar_fill.png");

        // 4. Generate 120mm APFSDS Sabot Projectile Icon
        generateAmmoShell(uiDir + "ammo_shell.png");

        // 5. Generate Military Status Chevron Badges
        generateMilBadge(true, uiDir + "ready_seal_ready.png");
        generateMilBadge(false, uiDir + "ready_seal_unready.png");

        // 6. Generate Tactical Mission Status Placards
        generateMilBanner(true, uiDir + "victory_banner.png");
        generateMilBanner(false, uiDir + "defeat_banner.png");

        // 7. Generate Tactical Cockpit Turret Azimuth Compass Dial & Needle
        generateAzimuthDial(uiDir + "azimuth_dial.png");
        generateAzimuthChassis(uiDir + "azimuth_chassis.png");
        generateAzimuthNeedle(uiDir + "azimuth_needle.png");
        System.out.println("US Military Tactical UI Assets Generated.");
    }

    private static void generateAzimuthDial(String outputPath) throws Exception {
        int size = 96;
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = img.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        int cx = size / 2, cy = size / 2;
        int r = 44;

        // 1. Carbon dial background
        g2.setColor(new Color(16, 20, 24, 235));
        g2.fillOval(cx - r, cy - r, r * 2, r * 2);

        // 2. Beveled border ring
        g2.setColor(new Color(55, 75, 60, 240));
        g2.setStroke(new BasicStroke(2.0f));
        g2.drawOval(cx - r, cy - r, r * 2, r * 2);

        // 3. Inner radar range ring
        g2.setColor(new Color(35, 60, 45, 160));
        g2.setStroke(new BasicStroke(1.0f));
        g2.drawOval(cx - 28, cy - 28, 56, 56);
        g2.drawOval(cx - 14, cy - 14, 28, 28);

        // 4. Azimuth tick marks (every 10 deg small, every 30 deg large)
        for (int deg = 0; deg < 360; deg += 10) {
            double rad = Math.toRadians(deg - 90);
            boolean isMajor = (deg % 30 == 0);
            int len = isMajor ? 6 : 3;
            int x1 = (int) (cx + Math.cos(rad) * (r - 2));
            int y1 = (int) (cy + Math.sin(rad) * (r - 2));
            int x2 = (int) (cx + Math.cos(rad) * (r - 2 - len));
            int y2 = (int) (cy + Math.sin(rad) * (r - 2 - len));

            g2.setColor(isMajor ? new Color(120, 220, 150, 230) : new Color(60, 120, 80, 170));
            g2.setStroke(new BasicStroke(isMajor ? 1.5f : 1.0f));
            g2.drawLine(x1, y1, x2, y2);
        }

        // 5. Cardinal Labels (N, E, S, W)
        g2.setFont(new Font("Monospaced", Font.BOLD, 10));
        g2.setColor(new Color(160, 245, 180));
        g2.drawString("N", cx - 3, cy - r + 13);
        g2.setColor(new Color(110, 170, 130));
        g2.drawString("S", cx - 3, cy + r - 5);
        g2.drawString("E", cx + r - 12, cy + 4);
        g2.drawString("W", cx - r + 4, cy + 4);

        g2.dispose();
        ImageIO.write(img, "PNG", new File(outputPath));
    }

    private static void generateAzimuthChassis(String outputPath) throws Exception {
        int size = 48;
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = img.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int cx = size / 2, cy = size / 2;

        // Armored Hull Rectangle (Facing Up / North)
        // Left & Right Track Treads
        g2.setColor(new Color(30, 36, 32, 220));
        g2.fillRoundRect(cx - 15, cy - 16, 6, 32, 3, 3);
        g2.fillRoundRect(cx + 9, cy - 16, 6, 32, 3, 3);

        // Main Hull Body
        g2.setColor(new Color(60, 75, 65, 230));
        g2.fillRect(cx - 9, cy - 14, 18, 28);

        // Glacis Front Chevron (pointed forward)
        g2.setColor(new Color(130, 165, 140, 240));
        int[] xP = {cx - 7, cx, cx + 7};
        int[] yP = {cy - 8, cy - 14, cy - 8};
        g2.drawPolyline(xP, yP, 3);

        g2.dispose();
        ImageIO.write(img, "PNG", new File(outputPath));
    }

    private static void generateAzimuthNeedle(String outputPath) throws Exception {
        int size = 48;
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = img.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int cx = size / 2, cy = size / 2;

        // Turret Center Hub
        g2.setColor(new Color(15, 20, 15, 240));
        g2.fillOval(cx - 5, cy - 5, 10, 10);

        // Long Bright Bore Needle (extends to tip y=2)
        g2.setColor(new Color(50, 240, 120, 255));
        g2.setStroke(new BasicStroke(2.0f));
        g2.drawLine(cx, cy - 3, cx, 4);

        // Crossbar & Muzzle Reticle Pip
        g2.setColor(new Color(255, 255, 255, 255));
        g2.fillOval(cx - 3, 2, 6, 6);
        g2.setColor(new Color(20, 255, 100, 255));
        g2.drawOval(cx - 5, 0, 10, 10);

        g2.dispose();
        ImageIO.write(img, "PNG", new File(outputPath));
    }

    private static void generateUnitPortrait(String typeName, String hullPath, String turretPath,
                                            String label, Color accentColor, String outputPath) throws Exception {
        int size = 256;
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        // Background: Tactical Carbon Slate with FLIR Grid
        g.setColor(new Color(18, 22, 26));
        g.fillRect(0, 0, size, size);

        // Green FLIR Rangefinder Grid
        g.setColor(new Color(40, 90, 60, 45));
        for (int i = 16; i < size; i += 16) {
            g.drawLine(i, 0, i, size);
            g.drawLine(0, i, size, i);
        }

        // Circular Tactical Scope Reticle
        g.setColor(new Color(60, 140, 90, 60));
        g.drawOval(size / 2 - 80, size / 2 - 80, 160, 160);
        g.drawOval(size / 2 - 50, size / 2 - 50, 100, 100);
        g.drawLine(size / 2, size / 2 - 90, size / 2, size / 2 + 90);
        g.drawLine(size / 2 - 90, size / 2, size / 2 + 90, size / 2);

        // Load tank hull & turret
        File hullFile = new File(hullPath);
        File turretFile = new File(turretPath);
        if (hullFile.exists()) {
            BufferedImage hull = ImageIO.read(hullFile);
            BufferedImage turret = turretFile.exists() ? ImageIO.read(turretFile) : null;

            Graphics2D gVehicle = (Graphics2D) g.create();
            gVehicle.translate(size / 2.0, size * 0.46);
            gVehicle.rotate(Math.toRadians(-22));

            int vSize = 150;
            // Shadow
            gVehicle.setColor(new Color(0, 0, 0, 160));
            gVehicle.fillOval(-vSize / 2 + 10, -vSize / 2 + 14, vSize, vSize);

            // Hull
            gVehicle.drawImage(hull, -vSize / 2, -vSize / 2, vSize, vSize, null);

            // Turret
            if (turret != null) {
                Graphics2D gTurret = (Graphics2D) gVehicle.create();
                gTurret.rotate(Math.toRadians(12));
                gTurret.drawImage(turret, -vSize / 2, -vSize / 2, vSize, vSize, null);
                gTurret.dispose();
            }
            gVehicle.dispose();
        }

        // Tactical Coyote Tan / Olive Drab Mil-Spec Beveled Border
        int b = 10;
        g.setColor(new Color(55, 62, 50));
        g.fillRect(0, 0, size, b);
        g.fillRect(0, size - b, size, b);
        g.fillRect(0, 0, b, size);
        g.fillRect(size - b, 0, b, size);

        // Corner Tactical Reticle Brackets
        g.setColor(new Color(210, 175, 75));
        g.setStroke(new BasicStroke(3));
        // Top-left
        g.drawLine(b + 2, b + 2, b + 20, b + 2);
        g.drawLine(b + 2, b + 2, b + 2, b + 20);
        // Top-right
        g.drawLine(size - b - 20, b + 2, size - b - 2, b + 2);
        g.drawLine(size - b - 2, b + 2, size - b - 2, b + 20);
        // Bottom-left
        g.drawLine(b + 2, size - b - 2, b + 20, size - b - 2);
        g.drawLine(b + 2, size - b - 20, b + 2, size - b - 2);
        // Bottom-right
        g.drawLine(size - b - 20, size - b - 2, size - b - 2, size - b - 2);
        g.drawLine(size - b - 2, size - b - 20, size - b - 2, size - b - 2);

        // MIL-STD Header Plaque
        g.setColor(new Color(10, 14, 18, 240));
        g.fillRect(b + 2, b + 4, size - (b + 2) * 2, 22);
        g.setColor(new Color(75, 180, 115));
        g.setFont(new Font("Monospaced", Font.BOLD, 11));
        g.drawString("US ARMED FORCES // ARMOR PLACARD", b + 8, b + 19);

        // Bottom Designation Plaque
        int plaqueH = 34;
        int plaqueY = size - b - plaqueH - 2;
        g.setColor(new Color(12, 16, 20, 245));
        g.fillRect(b + 2, plaqueY, size - (b + 2) * 2, plaqueH);
        g.setColor(new Color(210, 175, 75));
        g.drawRect(b + 2, plaqueY, size - (b + 2) * 2, plaqueH);

        g.setFont(new Font("SansSerif", Font.BOLD, 15));
        FontMetrics fm = g.getFontMetrics();
        int textW = fm.stringWidth(label);
        g.setColor(new Color(255, 220, 110));
        g.drawString(label, (size - textW) / 2, plaqueY + 22);

        g.dispose();
        ImageIO.write(img, "PNG", new File(outputPath));
    }

    private static void generateArmorSchematics(String uiDir) throws Exception {
        int size = 128;

        // 1. Tank Silhouette
        BufferedImage sil = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D gSil = sil.createGraphics();
        gSil.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Modern Chobham Composite Hull
        gSil.setColor(new Color(26, 32, 38));
        gSil.fillRoundRect(34, 20, 60, 88, 12, 12);

        // Treads & Mud Flaps
        gSil.setColor(new Color(16, 20, 24));
        gSil.fillRoundRect(20, 14, 16, 100, 6, 6);
        gSil.fillRoundRect(92, 14, 16, 100, 6, 6);

        // Tread Rungs
        gSil.setColor(new Color(40, 48, 56));
        for (int y = 18; y < 112; y += 7) {
            gSil.drawLine(22, y, 34, y);
            gSil.drawLine(94, y, 106, y);
        }

        // Modern Abrams Turret Silhouette
        gSil.setColor(new Color(42, 50, 60));
        Polygon turretPoly = new Polygon(
                new int[]{46, 64, 82, 80, 48},
                new int[]{44, 38, 44, 82, 82},
                5
        );
        gSil.fillPolygon(turretPoly);
        gSil.setColor(new Color(60, 72, 85));
        gSil.drawPolygon(turretPoly);

        // 120mm Gun Barrel & Muzzle Brake
        gSil.fillRect(62, 18, 5, 24);
        gSil.fillRect(60, 16, 9, 4);

        gSil.dispose();
        ImageIO.write(sil, "PNG", new File(uiDir + "tank_silhouette.png"));

        // 2. Front Armor Plate (Upper & Lower Glacis)
        BufferedImage front = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D gF = front.createGraphics();
        gF.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        gF.setColor(Color.WHITE);
        Polygon frontPoly = new Polygon(
                new int[]{36, 64, 92, 86, 42},
                new int[]{26, 16, 26, 38, 38},
                5
        );
        gF.fillPolygon(frontPoly);
        gF.dispose();
        ImageIO.write(front, "PNG", new File(uiDir + "armor_front.png"));

        // 3. Left Armor Plate (Left Heavy Skirts & Hull ERA)
        BufferedImage left = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D gL = left.createGraphics();
        gL.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        gL.setColor(Color.WHITE);
        gL.fillRoundRect(18, 22, 14, 84, 4, 4);
        gL.dispose();
        ImageIO.write(left, "PNG", new File(uiDir + "armor_left.png"));

        // 4. Right Armor Plate (Right Heavy Skirts & Hull ERA)
        BufferedImage right = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D gR = right.createGraphics();
        gR.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        gR.setColor(Color.WHITE);
        gR.fillRoundRect(96, 22, 14, 84, 4, 4);
        gR.dispose();
        ImageIO.write(right, "PNG", new File(uiDir + "armor_right.png"));

        // 5. Rear Armor Plate (Engine Deck & Bustle)
        BufferedImage rear = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D gB = rear.createGraphics();
        gB.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        gB.setColor(Color.WHITE);
        gB.fillRoundRect(36, 92, 56, 16, 4, 4);
        gB.dispose();
        ImageIO.write(rear, "PNG", new File(uiDir + "armor_rear.png"));
    }

    private static void generateHudPanelFrame(String outputPath) throws Exception {
        int w = 256, h = 256;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Translucent Tactical Mil-Spec Carbon Slate
        g.setColor(new Color(12, 16, 20, 230));
        g.fillRoundRect(4, 4, w - 8, h - 8, 8, 8);

        // Tactical Olive/Tan Beveled Border
        g.setColor(new Color(60, 72, 55));
        g.setStroke(new BasicStroke(3));
        g.drawRoundRect(4, 4, w - 8, h - 8, 8, 8);

        // Mil-Spec Tactical Corner Reticles
        g.setColor(new Color(220, 180, 60));
        g.setStroke(new BasicStroke(2));
        int c = 18;
        // Top-left
        g.drawLine(6, 6, 6 + c, 6);
        g.drawLine(6, 6, 6, 6 + c);
        // Top-right
        g.drawLine(w - 6 - c, 6, w - 6, 6);
        g.drawLine(w - 6, 6, w - 6, 6 + c);
        // Bottom-left
        g.drawLine(6, h - 6, 6 + c, h - 6);
        g.drawLine(6, h - 6 - c, 6, h - 6);
        // Bottom-right
        g.drawLine(w - 6 - c, h - 6, w - 6, h - 6);
        g.drawLine(w - 6, h - 6 - c, w - 6, h - 6);

        // Subtle Night-Vision Green telemetry tick marks
        g.setColor(new Color(65, 180, 100, 120));
        for (int x = 30; x < w - 30; x += 15) {
            g.drawLine(x, 6, x, 9);
            g.drawLine(x, h - 9, x, h - 6);
        }

        g.dispose();
        ImageIO.write(img, "PNG", new File(outputPath));
    }

    private static void generateStatBarFrame(String outputPath) throws Exception {
        int w = 128, h = 32;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Dark background slot
        g.setColor(new Color(8, 11, 14, 245));
        g.fillRoundRect(2, 2, w - 4, h - 4, 4, 4);

        // Tactical HUD Bracket Frame
        g.setColor(new Color(80, 95, 80));
        g.setStroke(new BasicStroke(2));
        g.drawRoundRect(2, 2, w - 4, h - 4, 4, 4);

        // Segmented Tick Markers
        g.setColor(new Color(40, 50, 45));
        for (int x = 16; x < w - 8; x += 12) {
            g.drawLine(x, 4, x, h - 4);
        }

        g.dispose();
        ImageIO.write(img, "PNG", new File(outputPath));
    }

    private static void generateStatBarFill(String outputPath) throws Exception {
        int w = 128, h = 32;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Modern High-Contrast Solid Gradient Fill with Segment Divisions
        g.setPaint(new LinearGradientPaint(0, 0, 0, h,
                new float[]{0.0f, 0.5f, 1.0f},
                new Color[]{new Color(255, 255, 255, 255), new Color(225, 225, 225, 255), new Color(175, 175, 175, 255)}));
        g.fillRect(3, 3, w - 6, h - 6);

        // Segment grooves
        g.setColor(new Color(0, 0, 0, 140));
        for (int x = 16; x < w - 6; x += 12) {
            g.fillRect(x, 3, 2, h - 6);
        }

        g.dispose();
        ImageIO.write(img, "PNG", new File(outputPath));
    }

    private static void generateAmmoShell(String outputPath) throws Exception {
        int size = 64;
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // 120mm APFSDS (Armor-Piercing Fin-Stabilized Discarding Sabot) Modern Tank Round
        // Brass Cartridge Base (bottom 50%)
        g.setPaint(new LinearGradientPaint(20, 0, 44, 0,
                new float[]{0.0f, 0.3f, 0.7f, 1.0f},
                new Color[]{new Color(230, 190, 75), new Color(255, 240, 160), new Color(190, 145, 40), new Color(130, 90, 20)}));
        g.fillRoundRect(22, 28, 20, 30, 2, 2);
        g.fillRect(20, 55, 24, 4); // Cartridge Rim

        // Black/Carbon Sabot Shoe Petals (middle 25%)
        g.setColor(new Color(35, 40, 48));
        Polygon sabot = new Polygon(
                new int[]{22, 32, 42, 38, 26},
                new int[]{28, 20, 28, 32, 32},
                5
        );
        g.fillPolygon(sabot);

        // Needle-Sharp Tungsten Penetrator Dart (top)
        g.setPaint(new LinearGradientPaint(29, 0, 35, 0,
                new float[]{0.0f, 0.5f, 1.0f},
                new Color[]{new Color(220, 230, 240), new Color(255, 255, 255), new Color(160, 175, 190)}));
        Polygon dart = new Polygon(
                new int[]{30, 32, 34},
                new int[]{20, 4, 20},
                3
        );
        g.fillPolygon(dart);

        g.dispose();
        ImageIO.write(img, "PNG", new File(outputPath));
    }

    private static void generateMilBadge(boolean ready, String outputPath) throws Exception {
        int size = 128;
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        int cx = size / 2, cy = size / 2;

        // Tactical Military Rank / Status Patch (Hexagonal Shield)
        Polygon patch = new Polygon(
                new int[]{cx - 44, cx + 44, cx + 52, cx, cx - 52},
                new int[]{cy - 40, cy - 40, cy + 20, cy + 46, cy + 20},
                5
        );

        // Shadow
        g.setColor(new Color(0, 0, 0, 120));
        g.translate(3, 4);
        g.fillPolygon(patch);
        g.translate(-3, -4);

        // Patch Background
        if (ready) {
            g.setColor(new Color(20, 60, 32)); // Tactical Army Green
        } else {
            g.setColor(new Color(55, 45, 25)); // Standby Amber/Brown
        }
        g.fillPolygon(patch);

        // Stitched Border
        g.setColor(ready ? new Color(75, 225, 110) : new Color(240, 175, 50));
        g.setStroke(new BasicStroke(3));
        g.drawPolygon(patch);

        if (ready) {
            // Military Rank Triple Chevrons (US Army Sergeant/Staff Chevrons)
            g.setColor(new Color(255, 225, 90));
            g.setStroke(new BasicStroke(5, BasicStroke.CAP_ROUND, BasicStroke.JOIN_MITER));
            for (int dy = -16; dy <= 8; dy += 12) {
                g.drawLine(cx - 22, cy + dy, cx, cy + dy + 10);
                g.drawLine(cx, cy + dy + 10, cx + 22, cy + dy);
            }
        } else {
            // Standby Radar Reticle
            g.setColor(new Color(240, 180, 60));
            g.setStroke(new BasicStroke(3));
            g.drawOval(cx - 18, cy - 18, 36, 36);
            g.drawLine(cx, cy - 24, cx, cy + 24);
            g.drawLine(cx - 24, cy, cx + 24, cy);
        }

        g.dispose();
        ImageIO.write(img, "PNG", new File(outputPath));
    }

    private static void generateMilBanner(boolean victory, String outputPath) throws Exception {
        int w = 512, h = 128;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        // Mil-Spec Mission Status Placard
        g.setColor(new Color(0, 0, 0, 160));
        g.fillRect(14, 18, w - 28, h - 36);

        // Background Slate
        g.setColor(new Color(16, 20, 25, 245));
        g.fillRect(10, 14, w - 20, h - 28);

        // Tactical Hazard / Victory Accent Header & Footer
        Color accentColor = victory ? new Color(60, 200, 100) : new Color(230, 45, 45);
        g.setColor(accentColor);
        g.fillRect(10, 14, w - 20, 6);
        g.fillRect(10, h - 20, w - 20, 6);

        // Tactical Border
        g.setStroke(new BasicStroke(2));
        g.drawRect(10, 14, w - 20, h - 28);

        // Main Status Headline
        String headline = victory ? "MISSION ACCOMPLISHED" : "MISSION FAILED";
        g.setFont(new Font("SansSerif", Font.BOLD, 32));
        FontMetrics fm = g.getFontMetrics();
        int tw = fm.stringWidth(headline);

        g.setColor(new Color(0, 0, 0, 220));
        g.drawString(headline, (w - tw) / 2 + 2, 58 + 2);
        g.setColor(victory ? new Color(255, 235, 120) : new Color(255, 90, 80));
        g.drawString(headline, (w - tw) / 2, 58);

        // Subtext / Sector Classification
        String sub = victory ? "// ALL HOSTILE TARGETS NEUTRALIZED -- SECTOR SECURED //"
                             : "// CHASSIS COMBAT CASUALTY -- EVACUATE COMBAT ZONE //";
        g.setFont(new Font("Monospaced", Font.BOLD, 13));
        FontMetrics fmSub = g.getFontMetrics();
        int stw = fmSub.stringWidth(sub);
        g.setColor(new Color(200, 210, 220));
        g.drawString(sub, (w - stw) / 2, 84);

        g.dispose();
        ImageIO.write(img, "PNG", new File(outputPath));
    }
}
