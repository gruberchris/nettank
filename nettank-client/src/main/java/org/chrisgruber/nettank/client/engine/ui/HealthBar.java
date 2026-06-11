package org.chrisgruber.nettank.client.engine.ui;

import org.chrisgruber.nettank.client.engine.graphics.Shader;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.system.MemoryUtil;

import java.io.IOException;
import java.nio.FloatBuffer;

import static org.lwjgl.opengl.GL30.*;

public class HealthBar {

    private final Shader shaderProgram;
    private final int vaoId;
    private final int vboId;

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
        // NOTE: You will need to adapt this to your shader loading mechanism.
        // This assumes you have a ShaderProgram class that takes shader file paths.
        try {
            shaderProgram = new Shader("/shaders/ui.vert", "/shaders/ui.frag");
        } catch (IOException e) {
            throw new RuntimeException("Failed to load HealthBar shader", e);
        }

        // Create VAO and VBO
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

    public void draw(Matrix4f projectionMatrix, float currentHealth, float maxHealth, float x, float y, float width, float height, UIManager uiManager) {
        shaderProgram.bind();
        glBindVertexArray(vaoId);

        // --- 1. Draw the White Border ---
        Matrix4f borderModel = new Matrix4f().translate(x, y, 0).scale(width, height, 1);
        shaderProgram.setUniformMat4f("projection", projectionMatrix);
        shaderProgram.setUniformMat4f("model", borderModel);
        shaderProgram.setUniform3f("color", new Vector3f(1.0f, 1.0f, 1.0f)); // White
        glDrawArrays(GL_TRIANGLES, 0, 6);

        // --- 2. Draw the Health Bar (color interpolated by percentage) ---
        float healthPercentage = currentHealth / (float) maxHealth;
        float borderSize = Math.min(2, Math.min(width, height) / 4); // Clamp border for small bars

        float barX = x + borderSize;
        float barY = y + borderSize;
        float barWidth = (width - 2 * borderSize) * healthPercentage;
        float barHeight = height - 2 * borderSize;

        if (barWidth > 0) {
            Matrix4f healthModel = new Matrix4f().translate(barX, barY, 0).scale(barWidth, barHeight, 1);
            shaderProgram.setUniformMat4f("model", healthModel);
            // Interpolate color: green (full health) -> yellow (50%) -> red (0%)
            Vector3f healthColor = interpolateHealthColor(healthPercentage);
            shaderProgram.setUniform3f("color", healthColor);
            glDrawArrays(GL_TRIANGLES, 0, 6);
        }

        glBindVertexArray(0);
        shaderProgram.unbind();

        // --- 3. Draw the Text ---
        String healthText = String.format("%d / %d", (int)currentHealth, (int)maxHealth);
        float textScale = 0.35f; // A smaller scale suitable for the bar
        float textWidth = uiManager.getTextWidth(healthText, textScale);
        float textHeight = uiManager.getTextHeight(textScale);

        // Center the text on the bar
        float textX = x + (width - textWidth) / 2;
        float textY = y + (height - textHeight) / 2;

        uiManager.drawText(healthText, textX, textY, textScale, new Vector3f(1.0f, 1.0f, 1.0f));
    }

    /**
     * Interpolates a color from green (full health) through yellow to red (no health).
     */
    private Vector3f interpolateHealthColor(float healthPercentage) {
        if (healthPercentage >= 0.5f) {
            // Green to Yellow: t goes from 0 (full health) to 1 (half health)
            float t = (healthPercentage - 0.5f) / 0.5f;
            float r = 1.0f - t; // 0 at full health, 1 at half health
            float g = 1.0f;
            float b = 0.0f;
            return new Vector3f(r, g, b);
        } else {
            // Yellow to Red: t goes from 0 to 1
            float t = healthPercentage / 0.5f;
            float r = 1.0f;
            float g = t; // 1 -> 0
            float b = 0.0f;
            return new Vector3f(r, g, b);
        }
    }

    public void cleanup() {
        glDeleteVertexArrays(vaoId);
        glDeleteBuffers(vboId);
        shaderProgram.delete();
    }
}
