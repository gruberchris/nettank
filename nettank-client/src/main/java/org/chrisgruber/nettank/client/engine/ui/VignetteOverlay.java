package org.chrisgruber.nettank.client.engine.ui;

import org.chrisgruber.nettank.client.engine.graphics.Shader;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.system.MemoryUtil;

import java.io.IOException;
import java.nio.FloatBuffer;

import static org.lwjgl.opengl.GL30.*;

/**
 * Red screen-edge vignette: pulses when the player takes a hit (stronger for
 * crits) and "heartbeats" slowly while at critically low HP. Drawn as four
 * translucent edge bars; intensity in [0, 1] scales the alpha.
 */
public class VignetteOverlay {

    private static final Vector3f VIGNETTE_COLOR = new Vector3f(0.8f, 0.05f, 0.05f);
    private static final float MAX_ALPHA = 0.45f;

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

    public VignetteOverlay() {
        try {
            shaderProgram = new Shader("/shaders/ui.vert", "/shaders/ui.frag");
        } catch (IOException e) {
            throw new RuntimeException("Failed to load VignetteOverlay shader", e);
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

    public void draw(Matrix4f projectionMatrix, float screenWidth, float screenHeight, float intensity) {
        if (intensity <= 0.01f) return;

        shaderProgram.bind();
        glBindVertexArray(vaoId);
        shaderProgram.setUniformMat4f("projection", projectionMatrix);
        shaderProgram.setUniform3f("color", VIGNETTE_COLOR);
        shaderProgram.setUniform1f("alpha", MAX_ALPHA * Math.min(1.0f, intensity));

        float horizontalThickness = screenHeight * 0.14f;
        float verticalThickness = screenWidth * 0.10f;

        drawRect(0, 0, screenWidth, horizontalThickness);                                  // top
        drawRect(0, screenHeight - horizontalThickness, screenWidth, horizontalThickness); // bottom
        drawRect(0, 0, verticalThickness, screenHeight);                                   // left
        drawRect(screenWidth - verticalThickness, 0, verticalThickness, screenHeight);     // right

        shaderProgram.setUniform1f("alpha", 1.0f);
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
