package org.chrisgruber.nettank.client.engine.ui;

import org.chrisgruber.nettank.client.engine.graphics.Shader;
import org.chrisgruber.nettank.client.engine.graphics.Texture;
import org.chrisgruber.nettank.common.entities.ArmorSide;
import org.chrisgruber.nettank.common.entities.TankStats;
import org.chrisgruber.nettank.common.entities.TankType;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.system.MemoryUtil;

import java.io.IOException;
import java.nio.FloatBuffer;
import java.util.EnumMap;
import java.util.Map;

import static org.lwjgl.opengl.GL30.*;

/**
 * In-lobby tank selection screen: framed AoE2-style command panel with high-res unit
 * portraits, role blurbs, embossed stat bars, roster entries with wax seals, and control hints.
 */
public class TankSelectionScreen {

    private static final Vector3f PANEL_COLOR = new Vector3f(0.07f, 0.09f, 0.12f);
    private static final Vector3f PANEL_BORDER_COLOR = new Vector3f(0.85f, 0.75f, 0.3f);
    private static final Vector3f BAR_BACKGROUND_COLOR = new Vector3f(0.18f, 0.2f, 0.24f);

    private final Shader shaderProgram;
    private final int vaoId;
    private final int vboId;

    private Texture hudFrame;
    private Texture statBarFrame;
    private Texture statBarFill;
    private Texture readySealReady;
    private Texture readySealUnready;
    private final Map<TankType, Texture> portraits = new EnumMap<>(TankType.class);

    private static final float[] QUAD_VERTICES = {
            0.0f, 1.0f,
            1.0f, 0.0f,
            0.0f, 0.0f,

            0.0f, 1.0f,
            1.0f, 1.0f,
            1.0f, 0.0f
    };

    public TankSelectionScreen() {
        try {
            shaderProgram = new Shader("/shaders/ui.vert", "/shaders/ui.frag");
        } catch (IOException e) {
            throw new RuntimeException("Failed to load TankSelectionScreen shader", e);
        }

        vaoId = glGenVertexArrays();
        glBindVertexArray(vaoId);

        vboId = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, vboId);

        FloatBuffer verticesBuffer = MemoryUtil.memAllocFloat(QUAD_VERTICES.length);
        verticesBuffer.put(QUAD_VERTICES).flip();
        try {
            glBufferData(GL_ARRAY_BUFFER, verticesBuffer, GL_STATIC_DRAW);
            glEnableVertexAttribArray(0);
            glVertexAttribPointer(0, 2, GL_FLOAT, false, 0, 0);
        } finally {
            MemoryUtil.memFree(verticesBuffer);
        }

        glBindBuffer(GL_ARRAY_BUFFER, 0);
        glBindVertexArray(0);

        try {
            hudFrame = new Texture("textures/ui/hud_panel_frame.png");
            statBarFrame = new Texture("textures/ui/stat_bar_frame.png");
            statBarFill = new Texture("textures/ui/stat_bar_fill.png");
            readySealReady = new Texture("textures/ui/ready_seal_ready.png");
            readySealUnready = new Texture("textures/ui/ready_seal_unready.png");

            portraits.put(TankType.STANDARD, new Texture("textures/ui/portrait_standard.png"));
            portraits.put(TankType.HEAVY, new Texture("textures/ui/portrait_heavy.png"));
            portraits.put(TankType.LIGHT, new Texture("textures/ui/portrait_light.png"));
            portraits.put(TankType.STEALTH, new Texture("textures/ui/portrait_stealth.png"));
        } catch (Exception ignored) {}
    }

    /** One row in the lobby roster: player name + ready state. */
    public record RosterEntry(String name, boolean ready, boolean isLocal) {}

    public float panelX(float screenWidth) { return screenWidth / 2.0f - 330.0f; }
    public float panelY(float screenHeight) { return screenHeight * 0.12f; }
    public float panelWidth() { return 660.0f; }
    public float panelHeight() { return 520.0f; }

    // Portrait slot inside the panel
    public float portraitCenterX(float screenWidth) { return panelX(screenWidth) + 145.0f; }
    public float portraitCenterY(float screenHeight) { return panelY(screenHeight) + 175.0f; }
    public float portraitSize() { return 180.0f; }

    public static String roleBlurb(TankType type) {
        return switch (type) {
            case STANDARD -> "MAIN BATTLE TANK - 120MM SMOOTHBORE // HEAVY CHOBHAM GLACIS";
            case HEAVY -> "TWIN-CANNON ASSAULT DREADNOUGHT // URBAN SURVIVAL KIT // WEAK REAR";
            case LIGHT -> "ARMORED 8X8 SCOUT // 25MM BUSHMASTER AUTOCANNON // HIGH SPEED";
            case STEALTH -> "ADVANCED CLOAKED VEHICLE // THERMAL MASKING WHEN STATIONARY";
        };
    }

    public static String militaryDesignation(TankType type) {
        return switch (type) {
            case STANDARD -> "M1A2 ABRAMS MBT";
            case HEAVY -> "M1A2 TUSK II HEAVY";
            case LIGHT -> "LAV-25 STRYKER";
            case STEALTH -> "SHADOW STALKER";
        };
    }

    /** Draws the translucent panel and border. Call before the portrait and text. */
    public void drawBackdrop(Matrix4f projectionMatrix, float screenWidth, float screenHeight) {
        float x = panelX(screenWidth);
        float y = panelY(screenHeight);

        shaderProgram.bind();
        glBindVertexArray(vaoId);
        shaderProgram.setUniformMat4f("projection", projectionMatrix);

        shaderProgram.setUniform3f("color", PANEL_BORDER_COLOR);
        shaderProgram.setUniform1f("alpha", 0.9f);
        drawRect(x - 3, y - 3, panelWidth() + 6, panelHeight() + 6);

        shaderProgram.setUniform3f("color", PANEL_COLOR);
        shaderProgram.setUniform1f("alpha", 0.94f);
        drawRect(x, y, panelWidth(), panelHeight());

        shaderProgram.setUniform1f("alpha", 1.0f);
        glBindVertexArray(0);
        shaderProgram.unbind();
    }

    /** Draws type name, blurb, stat bars, roster, and control hints. Call after the portrait. */
    public void drawInfo(Matrix4f projectionMatrix, UIManager uiManager, float screenWidth, float screenHeight,
                         TankType selectedType, boolean confirmed,
                         java.util.List<RosterEntry> roster) {
        float x = panelX(screenWidth);
        float y = panelY(screenHeight);
        TankStats stats = selectedType.getDefaultStats();

        // Draw framed backdrop if texture available
        if (hudFrame != null) {
            uiManager.drawTexture(hudFrame, x, y, panelWidth(), panelHeight());
        }

        // Draw portrait
        Texture portrait = portraits.get(selectedType);
        if (portrait != null) {
            float pSize = portraitSize();
            uiManager.drawTexture(portrait, portraitCenterX(screenWidth) - pSize / 2.0f,
                    portraitCenterY(screenHeight) - pSize / 2.0f, pSize, pSize);
        }

        String title = "US ARMED FORCES - VEHICLE DEPLOYMENT BRIEFING";
        uiManager.drawText(title, x + (panelWidth() - uiManager.getTextWidth(title, 0.68f)) / 2.0f, y + 20, 0.68f,
                new Vector3f(0.55f, 0.95f, 0.65f));

        String typeName = "<   " + militaryDesignation(selectedType) + "   >";
        uiManager.drawText(typeName, x + (panelWidth() - uiManager.getTextWidth(typeName, 1.0f)) / 2.0f, y + 54, 1.0f,
                new Vector3f(1.0f, 0.88f, 0.35f));

        String blurb = roleBlurb(selectedType);
        uiManager.drawText(blurb, x + (panelWidth() - uiManager.getTextWidth(blurb, 0.40f)) / 2.0f, y + 94, 0.40f,
                new Vector3f(0.85f, 0.88f, 0.92f));

        // Stat bars on the right half of the panel
        float labelX = x + 255;
        float barX = x + 375;
        float barVisualWidth = 170;
        float valueX = barX + barVisualWidth + 14;
        float barY = y + 118;
        float barHeight = 15;
        float barSpacing = 28;

        drawStatBar(projectionMatrix, uiManager, "HULL INTEGRITY", stats.maxHitPoints() / 6.0f,
                stats.maxHitPoints() + " HP", labelX, barX, barVisualWidth, valueX, barY, barHeight, new Vector3f(0.35f, 0.95f, 0.35f));
        drawStatBar(projectionMatrix, uiManager, "COMBAT SPEED", stats.moveSpeed() / 140.0f,
                String.format("%.0f KM/H", stats.moveSpeed() * 0.45f), labelX, barX, barVisualWidth, valueX, barY + barSpacing, barHeight, new Vector3f(0.3f, 0.7f, 1.0f));
        drawStatBar(projectionMatrix, uiManager, "TURRET SPEED", stats.turretTurnSpeed() / 140.0f,
                String.format("%.0f DEG/S", stats.turretTurnSpeed()), labelX, barX, barVisualWidth, valueX, barY + barSpacing * 2, barHeight, new Vector3f(0.4f, 0.85f, 0.95f));
        drawStatBar(projectionMatrix, uiManager, "FIREPOWER (KE)", stats.bulletDamage() / 2.0f,
                stats.bulletDamage() + " KE", labelX, barX, barVisualWidth, valueX, barY + barSpacing * 3, barHeight, new Vector3f(1.0f, 0.35f, 0.3f));
        drawStatBar(projectionMatrix, uiManager, "CYCLE TIME", 1400.0f / stats.shootCooldownMs(),
                String.format("%.1fS", stats.shootCooldownMs() / 1000.0f), labelX, barX, barVisualWidth, valueX, barY + barSpacing * 4, barHeight, new Vector3f(1.0f, 0.75f, 0.25f));

        // Per-side composite armor distribution
        float armorY = barY + barSpacing * 5;
        uiManager.drawText("COMPOSITE", labelX, armorY, 0.42f, new Vector3f(0.95f, 0.85f, 0.5f));
        String[] sideLabels = {"F:", "L:", "R:", "B:"};
        ArmorSide[] sides = {ArmorSide.FRONT, ArmorSide.LEFT, ArmorSide.RIGHT, ArmorSide.REAR};
        for (int i = 0; i < sides.length; i++) {
            float sideBarX = barX + i * 54;
            int armor = stats.armorFor(sides[i]);
            drawBar(projectionMatrix, sideBarX, armorY, 38, barHeight, armor / 3.0f,
                    armor > 0 ? new Vector3f(0.85f, 0.75f, 0.4f) : new Vector3f(0.28f, 0.28f, 0.3f));
            uiManager.drawText(sideLabels[i] + armor, sideBarX + 6, armorY + 20, 0.36f,
                    new Vector3f(0.9f, 0.9f, 0.9f));
        }

        // Player roster with military chevron badges
        float rosterX = x + 35;
        float rosterY = y + 295;
        uiManager.drawText("OPERATORS IN SQUADRON", rosterX, rosterY, 0.44f, new Vector3f(0.55f, 0.95f, 0.65f));
        rosterY += 24;

        int maxRosterRows = 5;
        int shown = 0;
        for (RosterEntry entry : roster) {
            if (shown >= maxRosterRows) {
                uiManager.drawText("+" + (roster.size() - shown) + " MORE", rosterX, rosterY, 0.35f,
                        new Vector3f(0.6f, 0.6f, 0.6f));
                break;
            }

            Texture seal = entry.ready() ? readySealReady : readySealUnready;
            if (seal != null) {
                uiManager.drawTexture(seal, rosterX, rosterY - 2, 18, 18);
            }

            String name = entry.isLocal() ? entry.name() + " [YOU]" : entry.name();
            uiManager.drawText(name, rosterX + 24, rosterY, 0.38f,
                    entry.isLocal() ? new Vector3f(1.0f, 0.88f, 0.35f) : new Vector3f(0.88f, 0.88f, 0.88f));

            String state = entry.ready() ? "DEPLOYED" : "SELECTING...";
            uiManager.drawText(state, rosterX + 165, rosterY, 0.38f,
                    entry.ready() ? new Vector3f(0.4f, 1.0f, 0.4f) : new Vector3f(0.95f, 0.6f, 0.2f));

            rosterY += 22;
            shown++;
        }

        // Confirmation + controls
        String readyLine = confirmed ? "STATUS: OPERATOR DEPLOYED // READY (SPACE TO CANCEL)" : "PRESS SPACE / (A) TO CONFIRM LOADOUT";
        uiManager.drawText(readyLine, x + (panelWidth() - uiManager.getTextWidth(readyLine, 0.54f)) / 2.0f,
                y + panelHeight() - 65, 0.54f,
                confirmed ? new Vector3f(0.4f, 1.0f, 0.4f) : new Vector3f(1.0f, 0.9f, 0.45f));

        String controls = "NAVIGATE: A/D OR CONTROLLER DPAD // CONFIRM: SPACE OR (A) // AUTO-ALIGN: F";
        uiManager.drawText(controls, x + (panelWidth() - uiManager.getTextWidth(controls, 0.36f)) / 2.0f,
                y + panelHeight() - 36, 0.36f, new Vector3f(0.65f, 0.7f, 0.75f));
    }

    private void drawStatBar(Matrix4f projectionMatrix, UIManager uiManager, String label, float fraction,
                             String valueText, float labelX, float barX, float barWidth, float valueX,
                             float y, float height, Vector3f color) {
        uiManager.drawText(label, labelX, y, 0.44f, new Vector3f(0.92f, 0.92f, 0.92f));
        drawBar(projectionMatrix, barX, y, barWidth, height, fraction, color);
        uiManager.drawText(valueText, valueX, y, 0.44f, new Vector3f(1.0f, 1.0f, 1.0f));
    }

    private void drawBar(Matrix4f projectionMatrix, float x, float y, float width, float height,
                         float fraction, Vector3f color) {
        float clamped = Math.max(0.0f, Math.min(1.0f, fraction));

        shaderProgram.bind();
        glBindVertexArray(vaoId);
        shaderProgram.setUniformMat4f("projection", projectionMatrix);

        shaderProgram.setUniform3f("color", BAR_BACKGROUND_COLOR);
        shaderProgram.setUniform1f("alpha", 1.0f);
        drawRect(x, y, width, height);

        if (clamped > 0.0f) {
            shaderProgram.setUniform3f("color", color);
            drawRect(x + 1, y + 1, (width - 2) * clamped, height - 2);
        }

        glBindVertexArray(0);
        shaderProgram.unbind();
    }

    private void drawRect(float x, float y, float width, float height) {
        Matrix4f model = new Matrix4f().translate(x, y, 0).scale(width, height, 1);
        shaderProgram.setUniformMat4f("model", model);
        glDrawArrays(GL_TRIANGLES, 0, 6);
    }

    public void cleanup() {
        glDeleteVertexArrays(vaoId);
        glDeleteBuffers(vboId);
        shaderProgram.delete();
        try {
            if (hudFrame != null) hudFrame.delete();
            if (statBarFrame != null) statBarFrame.delete();
            if (statBarFill != null) statBarFill.delete();
            if (readySealReady != null) readySealReady.delete();
            if (readySealUnready != null) readySealUnready.delete();
            for (Texture p : portraits.values()) {
                if (p != null) p.delete();
            }
        } catch (Exception ignored) {}
    }
}
