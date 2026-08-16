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
            case STANDARD -> "BALANCED MAIN BATTLE TANK";
            case HEAVY -> "TWIN-CANNON DREADNOUGHT - WEAK REAR";
            case LIGHT -> "FAST SCOUT - HIGH MOBILITY & RAPID FIRE";
            case STEALTH -> "AMBUSH STRIKER - CLOAKS WHEN STATIONARY";
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

        String title = "WAR COUNCIL - CHOOSE YOUR CHASSIS";
        uiManager.drawText(title, x + (panelWidth() - uiManager.getTextWidth(title, 0.75f)) / 2.0f, y + 20, 0.75f,
                new Vector3f(1.0f, 0.92f, 0.75f));

        String typeName = "<   " + selectedType.name() + "   >";
        uiManager.drawText(typeName, x + (panelWidth() - uiManager.getTextWidth(typeName, 1.1f)) / 2.0f, y + 54, 1.1f,
                new Vector3f(1.0f, 0.84f, 0.28f));

        String blurb = roleBlurb(selectedType);
        uiManager.drawText(blurb, x + (panelWidth() - uiManager.getTextWidth(blurb, 0.44f)) / 2.0f, y + 94, 0.44f,
                new Vector3f(0.85f, 0.85f, 0.88f));

        // Stat bars on the right half of the panel
        float labelX = x + 260;
        float barX = x + 370;
        float barVisualWidth = 175;
        float valueX = barX + barVisualWidth + 14;
        float barY = y + 130;
        float barHeight = 16;
        float barSpacing = 34;

        drawStatBar(projectionMatrix, uiManager, "HP", stats.maxHitPoints() / 6.0f,
                String.valueOf(stats.maxHitPoints()), labelX, barX, barVisualWidth, valueX, barY, barHeight, new Vector3f(0.35f, 0.95f, 0.35f));
        drawStatBar(projectionMatrix, uiManager, "SPEED", stats.moveSpeed() / 140.0f,
                String.format("%.0f", stats.moveSpeed()), labelX, barX, barVisualWidth, valueX, barY + barSpacing, barHeight, new Vector3f(0.3f, 0.65f, 1.0f));
        drawStatBar(projectionMatrix, uiManager, "DAMAGE", stats.bulletDamage() / 2.0f,
                String.valueOf(stats.bulletDamage()), labelX, barX, barVisualWidth, valueX, barY + barSpacing * 2, barHeight, new Vector3f(1.0f, 0.3f, 0.25f));
        drawStatBar(projectionMatrix, uiManager, "RELOAD", 1400.0f / stats.shootCooldownMs(),
                String.format("%.1fS", stats.shootCooldownMs() / 1000.0f), labelX, barX, barVisualWidth, valueX, barY + barSpacing * 3, barHeight, new Vector3f(1.0f, 0.7f, 0.2f));

        // Per-side armor distribution
        float armorY = barY + barSpacing * 4;
        uiManager.drawText("ARMOR", labelX, armorY, 0.45f, new Vector3f(0.95f, 0.85f, 0.5f));
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

        // Player roster with ready seals
        float rosterX = x + 35;
        float rosterY = y + 285;
        uiManager.drawText("COMMANDERS READY", rosterX, rosterY, 0.46f, new Vector3f(1.0f, 0.85f, 0.4f));
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

            String name = entry.isLocal() ? entry.name() + " (YOU)" : entry.name();
            uiManager.drawText(name, rosterX + 24, rosterY, 0.38f,
                    entry.isLocal() ? new Vector3f(1.0f, 0.88f, 0.35f) : new Vector3f(0.88f, 0.88f, 0.88f));

            String state = entry.ready() ? "READY" : "SELECTING...";
            uiManager.drawText(state, rosterX + 165, rosterY, 0.38f,
                    entry.ready() ? new Vector3f(0.4f, 1.0f, 0.4f) : new Vector3f(0.95f, 0.6f, 0.2f));

            rosterY += 22;
            shown++;
        }

        // Confirmation + controls
        String readyLine = confirmed ? "STATUS: READY FOR BATTLE! (SPACE TO CANCEL)" : "PRESS SPACE / (A) TO READY UP";
        uiManager.drawText(readyLine, x + (panelWidth() - uiManager.getTextWidth(readyLine, 0.58f)) / 2.0f,
                y + panelHeight() - 65, 0.58f,
                confirmed ? new Vector3f(0.35f, 1.0f, 0.35f) : new Vector3f(1.0f, 0.9f, 0.5f));

        String controls = "LEFT / RIGHT OR CONTROLLER DPAD TO SELECT CHASSIS";
        uiManager.drawText(controls, x + (panelWidth() - uiManager.getTextWidth(controls, 0.38f)) / 2.0f,
                y + panelHeight() - 36, 0.38f, new Vector3f(0.7f, 0.7f, 0.7f));
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
