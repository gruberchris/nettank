package org.chrisgruber.nettank.rendering;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;

import static org.lwjgl.opengl.GL15.*; // Buffer objects
import static org.lwjgl.opengl.GL20.*; // Vertex attribs
import static org.lwjgl.opengl.GL30.*; // VAOs

public class Renderer {

    private int vaoId;
    private int vboId; // Vertex buffer (positions, tex coords)
    private int eboId; // Element buffer (indices)

    // Quad vertices (pos x, y, tex s, t) - centered at origin
    private static final float[] VERTICES = {
            // Positions      // Texture Coords
            -0.5f,  0.5f,   0.0f, 1.0f, // Top-left
            -0.5f, -0.5f,   0.0f, 0.0f, // Bottom-left
            0.5f, -0.5f,   1.0f, 0.0f, // Bottom-right
            0.5f,  0.5f,   1.0f, 1.0f  // Top-right
    };

    private static final int[] INDICES = {
            0, 1, 2, // First triangle
            2, 3, 0  // Second triangle
    };

    private Matrix4f modelMatrix = new Matrix4f();

    public Renderer() {
        // --- VAO Setup ---
        vaoId = glGenVertexArrays();
        glBindVertexArray(vaoId);

        // --- VBO Setup ---
        vboId = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, vboId);
        // Use BufferUtils to create float buffer
        FloatBuffer vertexBuffer = BufferUtils.createFloatBuffer(VERTICES.length);
        vertexBuffer.put(VERTICES).flip();
        // Upload data, static draw since quad vertices don't change
        glBufferData(GL_ARRAY_BUFFER, vertexBuffer, GL_STATIC_DRAW);

        // --- EBO Setup ---
        eboId = glGenBuffers();
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, eboId);
        // Use BufferUtils for int buffer
        IntBuffer elementBuffer = BufferUtils.createIntBuffer(INDICES.length);
        elementBuffer.put(INDICES).flip();
        glBufferData(GL_ELEMENT_ARRAY_BUFFER, elementBuffer, GL_STATIC_DRAW);

        // --- Vertex Attribute Pointers ---
        int stride = 4 * Float.BYTES; // 4 floats per vertex (pos + tex)

        // Position attribute (location = 0 in shader)
        glVertexAttribPointer(0, 2, GL_FLOAT, false, stride, 0);
        glEnableVertexAttribArray(0);

        // Texture coordinate attribute (location = 1 in shader)
        glVertexAttribPointer(1, 2, GL_FLOAT, false, stride, 2 * Float.BYTES); // Offset by 2 floats
        glEnableVertexAttribArray(1);

        // --- Unbind ---
        glBindBuffer(GL_ARRAY_BUFFER, 0); // Unbind VBO
        glBindVertexArray(0); // Unbind VAO (IMPORTANT: Unbind VAO AFTER unbinding EBO is not required, but unbinding VBO before VAO is ok)
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, 0); // Unbind EBO LAST (or before VAO)
    }

    public void drawQuad(float x, float y, float width, float height, float rotationDegrees, Shader shader) {
        shader.bind(); // Ensure the correct shader is active

        // --- Calculate Model Matrix ---
        modelMatrix.identity()
                .translate(x, y, 0)
                // Rotate around Z axis. Rotation origin is the center (0,0) of the base quad.
                .rotate((float) Math.toRadians(rotationDegrees), 0, 0, 1)
                // Scale after rotation
                .scale(width, height, 1);

        // --- Set Uniforms ---
        shader.setUniformMat4f("u_model", modelMatrix);
        // Texture binding and other uniforms (like color tint) should be done *before* calling drawQuad

        // --- Draw Call ---
        glBindVertexArray(vaoId); // Bind the VAO for the quad
        glDrawElements(GL_TRIANGLES, INDICES.length, GL_UNSIGNED_INT, 0); // Draw using indices
        glBindVertexArray(0); // Unbind the VAO
    }

    // Overload for drawing with explicit color (useful for UI or simple shapes)
    public void drawQuad(float x, float y, float width, float height, Vector3f color, Shader shader) {
        shader.bind();
        modelMatrix.identity()
                .translate(x, y, 0)
                .scale(width, height, 1);

        shader.setUniformMat4f("u_model", modelMatrix);
        shader.setUniform3f("u_tintColor", color); // Set tint color

        // Assuming texture 0 is bound to a white pixel or similar if no texture is desired
        // Or modify shader to conditionally use color vs texture

        glBindVertexArray(vaoId);
        glDrawElements(GL_TRIANGLES, INDICES.length, GL_UNSIGNED_INT, 0);
        glBindVertexArray(0);

        shader.setUniform3f("u_tintColor", 1.0f, 1.0f, 1.0f); // Reset tint
    }


    public void cleanup() {
        glDeleteVertexArrays(vaoId);
        glDeleteBuffers(vboId);
        glDeleteBuffers(eboId);
    }
}