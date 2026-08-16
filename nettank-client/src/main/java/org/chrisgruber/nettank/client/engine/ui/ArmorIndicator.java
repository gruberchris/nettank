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
 * HUD top-down tank diagram showing the player's directional armor: four segments
 * (front/left/right/rear) around a detailed chassis silhouette, each tinted green -> yellow -> red
 * by remaining fraction and going dark when destroyed. A struck segment flashes
 * white briefly.
 */
public class ArmorIndicator {

    private static final long FLASH_DURATION_MS = 500;
    private static final Vector3f DESTROYED_COLOR = new Vector3f(0.2f, 0.22f, 0.25f);

    private final Shader shaderProgram;
    private final int vaoId;
    private final int vboId;

    private Texture silhouetteTexture;
    private final Texture[] armorPlateTextures = new Texture[4];

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

        try {
            silhouetteTexture = new Texture("textures/ui/tank_silhouette.png");
            armorPlateTextures[0] = new Texture("textures/ui/armor_front.png");
            armorPlateTextures[1] = new Texture("textures/ui/armor_left.png");
            armorPlateTextures[2] = new Texture("textures/ui/armor_right.png");
            armorPlateTextures[3] = new Texture("textures/ui/armor_rear.png");
        } catch (Exception ignored) {}
    }

    /**
     * Draws the diagram with its top-left corner at (x, y) within a size x size box.
     * Arrays are indexed by ArmorSide.ordinal(): FRONT, LEFT, RIGHT, REAR.
     */
    public void draw(Matrix4f projectionMatrix, int[] armor, int[] maxArmor, long[] lastHitTimes,
                     float x, float y, float size, UIManager uiManager) {
        long now = System.currentTimeMillis();

        if (silhouetteTexture != null && uiManager != null) {
            // Draw silhouette
            uiManager.drawTexture(silhouetteTexture, x, y, size, size);

            // Draw each armor plate with its calculated health color
            for (int sideIndex = 0; sideIndex < 4; sideIndex++) {
                int current = Math.max(armor[sideIndex], 0);
                int max = Math.max(maxArmor[sideIndex], 1);

                Vector3f color;
                if (maxArmor[sideIndex] <= 0 || current <= 0) {
                    color = DESTROYED_COLOR;
                } else {
                    color = interpolateArmorColor(current / (float) max);
                }

                // Hit flash
                long sinceHit = now - lastHitTimes[sideIndex];
                if (sinceHit >= 0 && sinceHit < FLASH_DURATION_MS) {
                    float blend = 1.0f - (sinceHit / (float) FLASH_DURATION_MS);
                    color = new Vector3f(
                            color.x + (1.0f - color.x) * blend,
                            color.y + (1.0f - color.y) * blend,
                            color.z + (1.0f - color.z) * blend);
                }

                Texture plate = armorPlateTextures[sideIndex];
                if (plate != null) {
                    uiManager.drawTexture(plate, x, y, size, size, color, 1.0f);
                }
            }
        }

        // Numeric labels beside the diagram: F/L/R/B current values
        String label = String.format("F:%d L:%d R:%d R:%d",
                Math.max(armor[0], 0), Math.max(armor[1], 0), Math.max(armor[2], 0), Math.max(armor[3], 0));
        float textScale = 0.35f;
        uiManager.drawText(label, x - 4, y + size + 4, textScale, new Vector3f(1.0f, 0.85f, 0.4f));
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
        try {
            if (silhouetteTexture != null) silhouetteTexture.delete();
            for (Texture t : armorPlateTextures) {
                if (t != null) t.delete();
            }
        } catch (Exception ignored) {}
    }
}
