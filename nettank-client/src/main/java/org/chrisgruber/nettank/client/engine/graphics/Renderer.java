package org.chrisgruber.nettank.client.engine.graphics;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.BufferUtils;
// Removed unused GL11, GL15, GL20, GL30 imports as static imports are used

import java.nio.FloatBuffer;
import java.nio.IntBuffer;

import static org.lwjgl.opengl.GL15.*; // OpenGL 1.5 for buffer objects
import static org.lwjgl.opengl.GL20.*; // OpenGL 2.0 for vertex attributes
import static org.lwjgl.opengl.GL30.*; // OpenGL 3.0 for Vertex Array Objects (VAOs)

/**
 * Handles rendering of simple textured quads using a dedicated VAO/VBO.
 * Assumes textures are loaded with their origin at the bottom-left (e.g., flipped on load by STB).
 */
public class Renderer {

    private int vaoId; // Vertex Array Object ID
    private int vboId; // Vertex Buffer Object ID (stores vertex data)
    private int eboId; // Element Buffer Object ID (stores indices for drawing triangles)

    // Vertex data for a quad centered at (0,0) with size 1x1.
    // Each vertex: Position (x, y), Texture Coordinate (s, t)
    // Texture Coordinates (UVs) are flipped vertically (V=1 at top, V=0 at bottom).
    // This compensates for textures being loaded with stbi_set_flip_vertically_on_load(true),
    // ensuring the final rendered image is oriented correctly.
    private static final float[] VERTICES = {
            // Positions      // Texture Coords (V Flipped: 0,1 = Top-Left)
            -0.5f,  0.5f,   0.0f, 1.0f, // Top-left vertex     -> TexCoord V=1 (Top edge of flipped texture)
            -0.5f, -0.5f,   0.0f, 0.0f, // Bottom-left vertex  -> TexCoord V=0 (Bottom edge of flipped texture)
            0.5f, -0.5f,   1.0f, 0.0f, // Bottom-right vertex -> TexCoord V=0
            0.5f,  0.5f,   1.0f, 1.0f  // Top-right vertex    -> TexCoord V=1
    };


    // Indices to draw the quad using two triangles (0,1,2 and 2,3,0)
    private static final int[] INDICES = {
            0, 1, 2, // First triangle (top-left, bottom-left, bottom-right)
            2, 3, 0  // Second triangle (bottom-right, top-right, top-left)
    };

    // Reusable matrix for calculating model transformations
    private final Matrix4f modelMatrix = new Matrix4f();

    /**
     * Initializes the Renderer by creating and configuring OpenGL objects (VAO, VBO, EBO).
     */
    public Renderer() {
        // --- VAO Setup: Stores all the state needed to supply vertex data ---
        vaoId = glGenVertexArrays();
        glBindVertexArray(vaoId); // Bind the VAO to make it active

        // --- VBO Setup: Stores the actual vertex data (positions, tex coords) ---
        vboId = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, vboId); // Bind the VBO to the GL_ARRAY_BUFFER target
        // Create a FloatBuffer from the vertex data array
        FloatBuffer vertexBuffer = BufferUtils.createFloatBuffer(VERTICES.length);
        vertexBuffer.put(VERTICES).flip(); // Put data into buffer and flip it for reading
        // Upload the vertex data to the GPU's VBO. GL_STATIC_DRAW means the data won't change often.
        glBufferData(GL_ARRAY_BUFFER, vertexBuffer, GL_STATIC_DRAW);

        // --- EBO Setup: Stores indices that define the triangles ---
        eboId = glGenBuffers();
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, eboId); // Bind the EBO
        // Create an IntBuffer from the index data array
        IntBuffer elementBuffer = BufferUtils.createIntBuffer(INDICES.length);
        elementBuffer.put(INDICES).flip(); // Put data into buffer and flip it
        // Upload the index data to the GPU's EBO.
        glBufferData(GL_ELEMENT_ARRAY_BUFFER, elementBuffer, GL_STATIC_DRAW);

        // --- Vertex Attribute Pointers: Define how OpenGL should interpret the VBO data ---
        final int stride = 4 * Float.BYTES; // Bytes between consecutive vertices (2 pos + 2 tex = 4 floats)

        // Attribute 0: Vertex Position (bound to location = 0 in vertex shader)
        // Takes 2 floats, type GL_FLOAT, not normalized, stride as calculated, offset 0
        glVertexAttribPointer(0, 2, GL_FLOAT, false, stride, 0);
        glEnableVertexAttribArray(0); // Enable this attribute

        // Attribute 1: Texture Coordinate (bound to location = 1 in vertex shader)
        // Takes 2 floats, type GL_FLOAT, not normalized, stride as calculated, offset by 2 floats (position data)
        final long texCoordOffset = 2 * Float.BYTES;
        glVertexAttribPointer(1, 2, GL_FLOAT, false, stride, texCoordOffset);
        glEnableVertexAttribArray(1); // Enable this attribute

        // --- Unbind objects to prevent accidental modification ---
        glBindBuffer(GL_ARRAY_BUFFER, 0); // Unbind the VBO
        glBindVertexArray(0); // Unbind the VAO **IMPORTANT: Do this AFTER configuring attributes**
        // EBO is associated with the VAO, so it doesn't need explicit unbinding before the VAO,
        // but unbinding it afterwards is okay. Unbinding the EBO *while* the VAO is bound disconnects it.
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, 0);
    }

    /**
     * Draws a textured quad at the specified position, size, and rotation.
     * Assumes the correct shader and texture are already bound externally.
     *
     * @param x               Center X position
     * @param y               Center Y position
     * @param width           Width of the quad
     * @param height          Height of the quad
     * @param rotationDegrees Rotation in degrees around the Z axis
     * @param shader          The shader program to use (must have u_model uniform)
     */
    public void drawQuad(float x, float y, float width, float height, float rotationDegrees, Shader shader) {
        // Note: Shader binding is expected to happen *before* this call if different from previous draw.
        // shader.bind(); // Re-binding here might be redundant but safe.

        // --- Calculate Model Matrix ---
        modelMatrix.identity() // Start with identity matrix
                .translate(x, y, 0) // Apply translation
                // Apply rotation around the Z axis (for 2D)
                .rotate((float) Math.toRadians(rotationDegrees), 0, 0, 1)
                .scale(width, height, 1); // Apply scaling

        // --- Set Uniforms ---
        // Upload the calculated model matrix to the shader
        shader.setUniformMat4f("u_model", modelMatrix);
        // Texture unit uniform (e.g., u_texture) and tint color (u_tintColor)
        // should ideally be set *before* calling this method if they change.

        // --- Draw Call ---
        glBindVertexArray(vaoId); // Bind the VAO containing the quad's geometry setup
        // Draw the quad using the indices stored in the EBO
        glDrawElements(GL_TRIANGLES, INDICES.length, GL_UNSIGNED_INT, 0);
        glBindVertexArray(0); // Unbind the VAO
    }

    /**
     * Draws a quad with a solid color tint instead of a texture.
     * Useful for simple UI elements or debugging.
     * Assumes the shader has a 'u_tintColor' uniform and can handle non-textured drawing.
     *
     * @param x      Center X position
     * @param y      Center Y position
     * @param width  Width of the quad
     * @param height Height of the quad
     * @param color  The solid color tint (RGB)
     * @param shader The shader program to use (must have u_model and u_tintColor)
     */
    public void drawQuad(float x, float y, float width, float height, Vector3f color, Shader shader) {
        // shader.bind(); // Expected to be bound externally

        // Calculate simple model matrix (translation and scale only)
        modelMatrix.identity()
                .translate(x, y, 0)
                .scale(width, height, 1);

        // Set uniforms
        shader.setUniformMat4f("u_model", modelMatrix);
        shader.setUniform3f("u_tintColor", color); // Set the desired tint

        // Bind the quad VAO and draw
        glBindVertexArray(vaoId);
        glDrawElements(GL_TRIANGLES, INDICES.length, GL_UNSIGNED_INT, 0);
        glBindVertexArray(0);

        // Reset tint color to default white to avoid affecting subsequent draws
        shader.setUniform3f("u_tintColor", 1.0f, 1.0f, 1.0f);
    }


    /**
     * Cleans up OpenGL resources (VAO, VBO, EBO) used by this renderer.
     * Should be called when the renderer is no longer needed.
     */
    public void cleanup() {
        glDeleteVertexArrays(vaoId);
        glDeleteBuffers(vboId);
        glDeleteBuffers(eboId);
        // Reset IDs to 0 as good practice, though instance will likely be GC'd
        vaoId = vboId = eboId = 0;
    }
}