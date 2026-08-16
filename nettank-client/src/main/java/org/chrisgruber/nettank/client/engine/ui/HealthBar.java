package org.chrisgruber.nettank.client.engine.ui;

import org.chrisgruber.nettank.client.engine.graphics.Shader;
import org.chrisgruber.nettank.client.engine.graphics.Texture;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.system.MemoryUtil;

import java.io.IOException;
import java.nio.FloatBuffer;

import static org.lwjgl.opengl.GL30.*;

/**
 * Embossed AoE2-style health bar with brass bracket frame, gradient fill, and clear numeric HUD readout.
 */
public class HealthBar {

    private final Shader shaderProgram;
    private final int vaoId;
    private final int vboId;

    private Texture frameTexture;
    private Texture fillTexture;

    // A simple quad (2 triangles)
    private static final float[] QUAD_VERTICES = {
            0.0f, 1.0f,
            1.0f, 0.0f,
            0.0f, 0.0f,

            0.0f, 1.0f,
            1.0f, 1.0f,
            1.0f, 0.0f
    };

    public HealthBar() {
        try {
            shaderProgram = new Shader("/shaders/ui.vert", "/shaders/ui.frag");
        } catch (IOException e) {
            throw new RuntimeException("Failed to load HealthBar shader", e);
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
            frameTexture = new Texture("textures/ui/stat_bar_frame.png");
            fillTexture = new Texture("textures/ui/stat_bar_fill.png");
        } catch (Exception ignored) {}
    }

    public void draw(Matrix4f projectionMatrix, float currentHealth, float maxHealth, float x, float y, float width, float height, UIManager uiManager) {
        float healthPercentage = Math.max(0.0f, Math.min(1.0f, currentHealth / (float) maxHealth));
        Vector3f healthColor = interpolateHealthColor(healthPercentage);

        if (frameTexture != null && fillTexture != null && uiManager != null) {
            // 1. Draw outer frame
            uiManager.drawTexture(frameTexture, x, y, width, height);

            // 2. Draw fill
            float insetX = 3.0f;
            float insetY = 3.0f;
            float fillW = (width - insetX * 2.0f) * healthPercentage;
            float fillH = height - insetY * 2.0f;
            if (fillW > 1.0f) {
                uiManager.drawTexture(fillTexture, x + insetX, y + insetY, fillW, fillH, healthColor, 1.0f);
            }
        } else {
            // Fallback quad rendering
            shaderProgram.bind();
            glBindVertexArray(vaoId);

            Matrix4f borderModel = new Matrix4f().translate(x, y, 0).scale(width, height, 1);
            shaderProgram.setUniformMat4f("projection", projectionMatrix);
            shaderProgram.setUniformMat4f("model", borderModel);
            shaderProgram.setUniform3f("color", new Vector3f(0.85f, 0.72f, 0.3f));
            glDrawArrays(GL_TRIANGLES, 0, 6);

            float borderSize = 2.0f;
            float barX = x + borderSize;
            float barY = y + borderSize;
            float barWidth = (width - 2 * borderSize) * healthPercentage;
            float barHeight = height - 2 * borderSize;

            if (barWidth > 0) {
                Matrix4f healthModel = new Matrix4f().translate(barX, barY, 0).scale(barWidth, barHeight, 1);
                shaderProgram.setUniformMat4f("model", healthModel);
                shaderProgram.setUniform3f("color", healthColor);
                glDrawArrays(GL_TRIANGLES, 0, 6);
            }

            glBindVertexArray(0);
            shaderProgram.unbind();
        }

        // 3. Draw the Health Text
        String healthText = String.format("HP: %d / %d", (int) currentHealth, (int) maxHealth);
        float textScale = Math.max(0.38f, height * 0.024f);
        float textWidth = uiManager.getTextWidth(healthText, textScale);
        float textHeight = uiManager.getTextHeight(textScale);

        float textX = x + (width - textWidth) / 2;
        float textY = y + (height - textHeight) / 2 + 1;

        // Shadow & bright text
        uiManager.drawText(healthText, textX + 1, textY + 1, textScale, new Vector3f(0.0f, 0.0f, 0.0f));
        uiManager.drawText(healthText, textX, textY, textScale, new Vector3f(1.0f, 1.0f, 1.0f));
    }

    private Vector3f interpolateHealthColor(float healthPercentage) {
        if (healthPercentage >= 0.5f) {
            float t = (healthPercentage - 0.5f) / 0.5f;
            return new Vector3f(1.0f - t, 1.0f, 0.0f);
        } else {
            float t = healthPercentage / 0.5f;
            return new Vector3f(1.0f, t, 0.0f);
        }
    }

    public void cleanup() {
        glDeleteVertexArrays(vaoId);
        glDeleteBuffers(vboId);
        shaderProgram.delete();
        try {
            if (frameTexture != null) frameTexture.delete();
            if (fillTexture != null) fillTexture.delete();
        } catch (Exception ignored) {}
    }
}
