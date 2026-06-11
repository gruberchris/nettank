package org.chrisgruber.nettank.client.engine.ui;

import org.chrisgruber.nettank.client.engine.graphics.Shader;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.system.MemoryUtil;

import java.io.IOException;
import java.nio.FloatBuffer;

import static org.lwjgl.opengl.GL30.*;

/**
 * HUD top-down tank diagram showing the player's directional armor: four segments
 * (front/left/right/rear) around a hull block, each tinted green -> yellow -> red
 * by remaining fraction and going dark when destroyed. A struck segment flashes
 * white briefly (driven by lastHitTimes).
 *
 * PLACEHOLDER ART: drawn from colored quads until silhouette/segment textures exist
 * (textures/ui/tank_silhouette.png + armor_front/left/right/rear.png).
 */
public class ArmorIndicator {

    private static final long FLASH_DURATION_MS = 500;
    private static final Vector3f DESTROYED_COLOR = new Vector3f(0.18f, 0.18f, 0.18f);
    private static final Vector3f HULL_COLOR = new Vector3f(0.35f, 0.35f, 0.35f);

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

    public ArmorIndicator() {
        try {
            shaderProgram = new Shader("/shaders/ui.vert", "/shaders/ui.frag");
        } catch (IOException e) {
            throw new RuntimeException("Failed to load ArmorIndicator shader", e);
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

    /**
     * Draws the diagram with its top-left corner at (x, y) within a size x size box.
     * Arrays are indexed by ArmorSide.ordinal(): FRONT, LEFT, RIGHT, REAR.
     */
    public void draw(Matrix4f projectionMatrix, int[] armor, int[] maxArmor, long[] lastHitTimes,
                     float x, float y, float size, UIManager uiManager) {
        shaderProgram.bind();
        glBindVertexArray(vaoId);
        shaderProgram.setUniformMat4f("projection", projectionMatrix);

        float segmentThickness = size * 0.18f;
        float hullWidth = size * 0.44f;
        float hullHeight = size * 0.56f;
        float hullX = x + (size - hullWidth) / 2.0f;
        float hullY = y + (size - hullHeight) / 2.0f;

        long now = System.currentTimeMillis();

        // Hull block
        drawRect(hullX, hullY, hullWidth, hullHeight, HULL_COLOR);

        // FRONT (top), REAR (bottom), LEFT, RIGHT segments around the hull
        drawSegment(hullX, hullY - segmentThickness - 2, hullWidth, segmentThickness, 0, armor, maxArmor, lastHitTimes, now);
        drawSegment(hullX - segmentThickness - 2, hullY, segmentThickness, hullHeight, 1, armor, maxArmor, lastHitTimes, now);
        drawSegment(hullX + hullWidth + 2, hullY, segmentThickness, hullHeight, 2, armor, maxArmor, lastHitTimes, now);
        drawSegment(hullX, hullY + hullHeight + 2, hullWidth, segmentThickness, 3, armor, maxArmor, lastHitTimes, now);

        glBindVertexArray(0);
        shaderProgram.unbind();

        // Numeric labels beside the diagram: F/L/R/B current values
        String label = String.format("F%d L%d R%d B%d",
                Math.max(armor[0], 0), Math.max(armor[1], 0), Math.max(armor[2], 0), Math.max(armor[3], 0));
        float textScale = 0.3f;
        uiManager.drawText(label, x, y + size + 4, textScale, new Vector3f(1.0f, 1.0f, 1.0f));
    }

    private void drawSegment(float x, float y, float width, float height, int sideIndex,
                             int[] armor, int[] maxArmor, long[] lastHitTimes, long now) {
        int current = Math.max(armor[sideIndex], 0);
        int max = Math.max(maxArmor[sideIndex], 1);

        Vector3f color;
        if (maxArmor[sideIndex] <= 0) {
            color = DESTROYED_COLOR; // side has no armor by design (e.g. STEALTH rear)
        } else if (current <= 0) {
            color = DESTROYED_COLOR;
        } else {
            color = interpolateArmorColor(current / (float) max);
        }

        // Flash bright white right after the segment was struck
        long sinceHit = now - lastHitTimes[sideIndex];
        if (sinceHit >= 0 && sinceHit < FLASH_DURATION_MS) {
            float blend = 1.0f - (sinceHit / (float) FLASH_DURATION_MS);
            color = new Vector3f(
                    color.x + (1.0f - color.x) * blend,
                    color.y + (1.0f - color.y) * blend,
                    color.z + (1.0f - color.z) * blend);
        }

        drawRect(x, y, width, height, color);
    }

    private void drawRect(float x, float y, float width, float height, Vector3f color) {
        Matrix4f model = new Matrix4f().translate(x, y, 0).scale(width, height, 1);
        shaderProgram.setUniformMat4f("model", model);
        shaderProgram.setUniform3f("color", color);
        glDrawArrays(GL_TRIANGLES, 0, 6);
    }

    private Vector3f interpolateArmorColor(float fraction) {
        if (fraction >= 0.5f) {
            float t = (fraction - 0.5f) / 0.5f;
            return new Vector3f(1.0f - t, 1.0f, 0.0f);
        }
        float t = fraction / 0.5f;
        return new Vector3f(1.0f, t, 0.0f);
    }

    public void cleanup() {
        glDeleteVertexArrays(vaoId);
        glDeleteBuffers(vboId);
        shaderProgram.delete();
    }
}
