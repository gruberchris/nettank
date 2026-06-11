package org.chrisgruber.nettank.client.engine.ui;

import org.chrisgruber.nettank.client.engine.graphics.Shader;
import org.chrisgruber.nettank.common.entities.ArmorSide;
import org.chrisgruber.nettank.common.entities.TankStats;
import org.chrisgruber.nettank.common.entities.TankType;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.system.MemoryUtil;

import java.io.IOException;
import java.nio.FloatBuffer;

import static org.lwjgl.opengl.GL30.*;

/**
 * In-lobby tank selection screen (WAITING/COUNTDOWN): framed panel with the
 * selected type's name, role blurb, and stat bars (HP, speed, damage, reload,
 * per-side armor). Navigation hints and the lobby status line render below.
 *
 * PLACEHOLDER ART: panel and bars are colored quads, and the battlefield tank
 * sprite stands in for per-type portraits (textures/ui/portrait_<type>.png).
 */
public class TankSelectionScreen {

    private static final Vector3f PANEL_COLOR = new Vector3f(0.07f, 0.09f, 0.12f);
    private static final Vector3f PANEL_BORDER_COLOR = new Vector3f(0.85f, 0.75f, 0.3f);
    private static final Vector3f BAR_BACKGROUND_COLOR = new Vector3f(0.2f, 0.22f, 0.25f);

    private final Shader shaderProgram;
    private final int vaoId;
    private final int vboId;

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
    }

    public float panelX(float screenWidth) { return screenWidth / 2.0f - 320.0f; }
    public float panelY(float screenHeight) { return screenHeight * 0.18f; }
    public float panelWidth() { return 640.0f; }
    public float panelHeight() { return 420.0f; }

    // Portrait slot inside the panel (the caller draws the texture there)
    public float portraitCenterX(float screenWidth) { return panelX(screenWidth) + 150.0f; }
    public float portraitCenterY(float screenHeight) { return panelY(screenHeight) + 200.0f; }
    public float portraitSize() { return 180.0f; }

    public static String roleBlurb(TankType type) {
        return switch (type) {
            case STANDARD -> "BALANCED ALL-ROUNDER";
            case HEAVY -> "FRONTLINE BRUISER - WEAK REAR";
            case LIGHT -> "FAST SCOUT - FRAGILE BUT QUICK";
            case STEALTH -> "AMBUSHER - CLOAKS WHEN STILL";
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
        shaderProgram.setUniform1f("alpha", 0.92f);
        drawRect(x, y, panelWidth(), panelHeight());

        shaderProgram.setUniform1f("alpha", 1.0f);
        glBindVertexArray(0);
        shaderProgram.unbind();
    }

    /** Draws type name, blurb, stat bars, and control hints. Call after the portrait. */
    public void drawInfo(Matrix4f projectionMatrix, UIManager uiManager, float screenWidth, float screenHeight,
                         TankType selectedType, boolean confirmed, String statusLine) {
        float x = panelX(screenWidth);
        float y = panelY(screenHeight);
        TankStats stats = selectedType.getDefaultStats();

        String title = "SELECT YOUR TANK";
        uiManager.drawText(title, x + (panelWidth() - uiManager.getTextWidth(title, 0.8f)) / 2.0f, y + 16, 0.8f,
                new Vector3f(1.0f, 1.0f, 1.0f));

        String typeName = "<  " + selectedType.name() + "  >";
        uiManager.drawText(typeName, x + (panelWidth() - uiManager.getTextWidth(typeName, 1.1f)) / 2.0f, y + 52, 1.1f,
                new Vector3f(1.0f, 0.85f, 0.3f));

        String blurb = roleBlurb(selectedType);
        uiManager.drawText(blurb, x + (panelWidth() - uiManager.getTextWidth(blurb, 0.45f)) / 2.0f, y + 92, 0.45f,
                new Vector3f(0.8f, 0.8f, 0.8f));

        // Stat bars on the right half of the panel (normalized to cross-type maxima)
        float barX = x + 300;
        float barY = y + 130;
        float barWidth = 300;
        float barHeight = 14;
        float barSpacing = 34;

        drawStatBar(projectionMatrix, uiManager, "HP", stats.maxHitPoints() / 6.0f,
                String.valueOf(stats.maxHitPoints()), barX, barY, barWidth, barHeight, new Vector3f(0.3f, 0.9f, 0.3f));
        drawStatBar(projectionMatrix, uiManager, "SPEED", stats.moveSpeed() / 140.0f,
                String.format("%.0f", stats.moveSpeed()), barX, barY + barSpacing, barWidth, barHeight, new Vector3f(0.25f, 0.55f, 1.0f));
        drawStatBar(projectionMatrix, uiManager, "DAMAGE", stats.bulletDamage() / 2.0f,
                String.valueOf(stats.bulletDamage()), barX, barY + barSpacing * 2, barWidth, barHeight, new Vector3f(1.0f, 0.25f, 0.2f));
        drawStatBar(projectionMatrix, uiManager, "RELOAD", 1400.0f / stats.shootCooldownMs(),
                String.format("%.1fS", stats.shootCooldownMs() / 1000.0f), barX, barY + barSpacing * 3, barWidth, barHeight, new Vector3f(1.0f, 0.6f, 0.1f));

        // Per-side armor distribution
        uiManager.drawText("ARMOR", barX, barY + barSpacing * 4, 0.45f, new Vector3f(0.9f, 0.9f, 0.9f));
        String[] sideLabels = {"F", "L", "R", "B"};
        ArmorSide[] sides = {ArmorSide.FRONT, ArmorSide.LEFT, ArmorSide.RIGHT, ArmorSide.REAR};
        for (int i = 0; i < sides.length; i++) {
            float sideBarX = barX + 70 + i * 60;
            int armor = stats.armorFor(sides[i]);
            drawBar(projectionMatrix, sideBarX, barY + barSpacing * 4, 40, barHeight, armor / 3.0f,
                    armor > 0 ? new Vector3f(0.75f, 0.75f, 0.8f) : new Vector3f(0.25f, 0.25f, 0.25f));
            uiManager.drawText(sideLabels[i] + armor, sideBarX + 8, barY + barSpacing * 4 + 18, 0.35f,
                    new Vector3f(0.8f, 0.8f, 0.8f));
        }

        // Confirmation + controls
        String readyLine = confirmed ? "READY!" : "SPACE / (A) TO CONFIRM";
        uiManager.drawText(readyLine, x + (panelWidth() - uiManager.getTextWidth(readyLine, 0.6f)) / 2.0f,
                y + panelHeight() - 70, 0.6f,
                confirmed ? new Vector3f(0.3f, 1.0f, 0.3f) : new Vector3f(1.0f, 1.0f, 1.0f));

        String controls = "A/D - DPAD - BUMPERS TO CHANGE";
        uiManager.drawText(controls, x + (panelWidth() - uiManager.getTextWidth(controls, 0.4f)) / 2.0f,
                y + panelHeight() - 40, 0.4f, new Vector3f(0.65f, 0.65f, 0.65f));

        if (statusLine != null && !statusLine.isEmpty()) {
            uiManager.drawText(statusLine, x + (panelWidth() - uiManager.getTextWidth(statusLine, 0.6f)) / 2.0f,
                    y + panelHeight() + 14, 0.6f, new Vector3f(1.0f, 0.4f, 0.4f));
        }
    }

    private void drawStatBar(Matrix4f projectionMatrix, UIManager uiManager, String label, float fraction,
                             String valueText, float x, float y, float width, float height, Vector3f color) {
        uiManager.drawText(label, x - 110, y, 0.45f, new Vector3f(0.9f, 0.9f, 0.9f));
        drawBar(projectionMatrix, x, y, width - 60, height, fraction, color);
        uiManager.drawText(valueText, x + width - 50, y, 0.45f, new Vector3f(1.0f, 1.0f, 1.0f));
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
    }
}
