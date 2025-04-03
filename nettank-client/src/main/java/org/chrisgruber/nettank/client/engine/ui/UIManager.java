package org.chrisgruber.nettank.client.engine.ui;

import org.chrisgruber.nettank.client.engine.graphics.Shader;
import org.chrisgruber.nettank.client.engine.graphics.Texture;
import org.chrisgruber.nettank.common.util.Colors;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

// Static imports for OpenGL constants
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;


public class UIManager {
    private static final Logger logger = LoggerFactory.getLogger(UIManager.class);
    private static final int FONT_COLS = 12;
    private static final int FONT_ROWS = 8;

    private Texture fontTexture;
    private final Shader uiShader;
    private float charTexWidth;
    private float charTexHeight;
    private final Matrix4f uiProjectionMatrix;
    private final Matrix4f identityMatrix;
    private final Matrix4f uiModelMatrix; // Added for drawing

    // --- UI Specific VAO/VBO/EBO ---
    private int uiVaoId;
    private int uiVboId;
    private int uiEboId;

    // Quad vertices specifically for UI (Top-Left texture origin)
    private static final float[] VERTICES_UI = {
            // Positions      // Texture Coords (Original - Top-Left origin matching image files)
            -0.5f,  0.5f,   0.0f, 0.0f, // Top-left vertex
            -0.5f, -0.5f,   0.0f, 1.0f, // Bottom-left vertex
            0.5f, -0.5f,   1.0f, 1.0f, // Bottom-right vertex
            0.5f,  0.5f,   1.0f, 0.0f  // Top-right vertex
    };
    private static final int[] INDICES_UI = { 0, 1, 2, 2, 3, 0 }; // Standard quad indices

    public UIManager() {
        // Keep its own shader
        try {
            this.uiShader = new Shader("/shaders/quad.vert", "/shaders/quad.frag");
        } catch (IOException e) {
            logger.error("Failed to load UI shader", e);
            throw new RuntimeException(e);
        }

        this.uiProjectionMatrix = new Matrix4f();
        this.identityMatrix = new Matrix4f().identity();
        this.uiModelMatrix = new Matrix4f(); // Initialize model matrix

        // --- Create UI Specific VAO/VBO/EBO ---
        uiVaoId = glGenVertexArrays();
        glBindVertexArray(uiVaoId);

        // VBO
        uiVboId = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, uiVboId);
        FloatBuffer vertexBuffer = BufferUtils.createFloatBuffer(VERTICES_UI.length);
        vertexBuffer.put(VERTICES_UI).flip();
        glBufferData(GL_ARRAY_BUFFER, vertexBuffer, GL_STATIC_DRAW);

        // EBO
        uiEboId = glGenBuffers();
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, uiEboId);
        IntBuffer elementBuffer = BufferUtils.createIntBuffer(INDICES_UI.length);
        elementBuffer.put(INDICES_UI).flip();
        glBufferData(GL_ELEMENT_ARRAY_BUFFER, elementBuffer, GL_STATIC_DRAW);

        // Vertex Attributes
        int stride = 4 * Float.BYTES;
        // Position attribute (location = 0)
        glVertexAttribPointer(0, 2, GL_FLOAT, false, stride, 0);
        glEnableVertexAttribArray(0);
        // Texture coordinate attribute (location = 1)
        glVertexAttribPointer(1, 2, GL_FLOAT, false, stride, 2 * Float.BYTES);
        glEnableVertexAttribArray(1);

        // Unbind
        glBindBuffer(GL_ARRAY_BUFFER, 0);
        glBindVertexArray(0);
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, 0); // Unbind EBO after VAO unbind is fine
        //--------------------------------------
    }

    public void loadFontTexture(String filepath) throws IOException {
        if (FONT_COLS <= 0 || FONT_ROWS <= 0) {
            throw new IllegalArgumentException("FONT_COLS and FONT_ROWS must be positive.");
        }
        try {
            this.fontTexture = new Texture(filepath);
            // These calculations remain the same
            this.charTexWidth = 1.0f / FONT_COLS;
            this.charTexHeight = 1.0f / FONT_ROWS;
            logger.info("Font Loaded: {} ({}x{} px, {}x{} grid -> charTexSize {}x{})",
                    filepath, fontTexture.getWidth(), fontTexture.getHeight(),
                    FONT_COLS, FONT_ROWS, charTexWidth, charTexHeight);
        } catch (IOException e) {
            logger.error("Failed to load font texture file: {}", filepath, e);
            this.fontTexture = null;
            throw e;
        }
    }

    public void startUIRendering(int screenWidth, int screenHeight) {
        // Set up projection and view for UI space
        uiProjectionMatrix.setOrtho(0.0f, screenWidth, screenHeight, 0.0f, -1.0f, 1.0f);
        uiShader.bind();
        uiShader.setUniformMat4f("u_projection", uiProjectionMatrix);
        uiShader.setUniformMat4f("u_view", identityMatrix); // UI typically doesn't use camera view
        uiShader.setUniform1i("u_texture", 0);
        // Reset tint and texRect at the start of UI rendering for safety
        uiShader.setUniform3f("u_tintColor", 1.0f, 1.0f, 1.0f);
        uiShader.setUniform4f("u_texRect", 0.0f, 0.0f, 1.0f, 1.0f);
    }

    public void drawText(String text, float screenX, float screenY, float scale) {
        drawText(text, screenX, screenY, scale, Colors.WHITE);
    }

    public void drawText(String text, float screenX, float screenY, float scale, Vector3f color) {
        if (fontTexture == null || uiShader == null || uiVaoId == 0) { // Check VAO ID
            logger.warn("UI Text rendering prerequisites not met.");
            return;
        }

        fontTexture.bind(); // Bind font texture to unit 0 (set in startUIRendering)
        // Set font texture sampling to nearest (good for pixel fonts)
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);

        uiShader.bind(); // Ensure UI shader is bound
        uiShader.setUniform3f("u_tintColor", color); // Set text color

        float charScreenWidth = ((float)fontTexture.getWidth() / FONT_COLS) * scale;
        float charScreenHeight = ((float)fontTexture.getHeight() / FONT_ROWS) * scale;
        float currentX = screenX;

        // *** Bind the UI-specific VAO ***
        glBindVertexArray(uiVaoId);

        for (char c : text.toCharArray()) {
            if (c == ' ') {
                currentX += charScreenWidth;
                continue;
            }
            if (c < 32 || c > 126) {
                c = '?'; // Use '?' for invalid characters
            }
            int charIndex = c - 32; // Index relative to space (ASCII 32)

            int col = charIndex % FONT_COLS;
            int row = charIndex / FONT_COLS;

            float texX = col * charTexWidth;    // U coordinate of left edge

            float texY_top_edge = 1.0f - (row * charTexHeight);
            float texY_top_left = 1.0f - (row * charTexHeight) - charTexHeight;
            texY_top_left = 1.0f - ((row + 1) * charTexHeight);



            // *** CRITICAL: Adjust texY calculation for flipped VBO + flipped texture ***
            // We need the V coordinate of the *bottom* edge of the character cell in the atlas,
            // mapped to the *flipped* texture's coordinate space (where V=0 is bottom).
            // Row 0 (top row in file) is near V=1 after flip.
            // Row 'max' (bottom row in file) is near V=0 after flip.
            // The V coordinate for the bottom edge of character at row 'row' is:
            // V = 1.0f - ((row + 1) * charTexHeight)
            // OR, calculate the top-left V and use that for u_texRect.y?
            // Let's try calculating the top-left V coord in the *flipped* space:
            // Top-left V for row 'row' = 1.0f - (row * charTexHeight) - charTexHeight (approx)
            // Let's rethink: the VERTICES_UI now go from V=0 (bottom) to V=1 (top).
            // The texRect needs the bottom-left UV.
            // In the flipped texture, row 0 is near V=1, row max is near V=0.
            // The bottom-left V coord for row 'row' is 1.0f - (row + 1) * charTexHeight
            //float texY_bottom_left = 1.0f - ((float)(row + 1) * charTexHeight);
            //float texY_bottom_edge = 1.0f - ((float)(row + 1) * charTexHeight);
            float texY_bottom_left = 1.0f - ((float)(row + 1) * charTexHeight);

            // Calculate sub-rectangle for the character in the texture atlas
            float inset = 0.001f; // Small inset to prevent bleeding

            uiShader.setUniform4f("u_texRect",
                    texX + inset,
                    texY_bottom_left + inset, // Bottom V coord of char in flipped texture
                    charTexWidth - (2 * inset),
                    charTexHeight - (2 * inset)); // Positive height

            // Calculate screen position for the center of the character quad
            float drawX = currentX + charScreenWidth / 2.0f;
            float drawY = screenY + charScreenHeight / 2.0f;

            // Calculate model matrix for this character quad
            uiModelMatrix.identity()
                    .translate(drawX, drawY, 0)
                    .scale(charScreenWidth, charScreenHeight, 1);
            uiShader.setUniformMat4f("u_model", uiModelMatrix);

            // *** Draw using the UI EBO ***
            glDrawElements(GL_TRIANGLES, INDICES_UI.length, GL_UNSIGNED_INT, 0);

            currentX += charScreenWidth;
        }

        // Unbind VAO after drawing all characters
        glBindVertexArray(0);

        // Reset the texture rectangle uniform for safety (important if shader is reused)
        uiShader.setUniform4f("u_texRect", 0.0f, 0.0f, 1.0f, 1.0f);
        // Optionally reset tint color
        // uiShader.setUniform3f("u_tintColor", 1.0f, 1.0f, 1.0f);
    }

    public float getTextWidth(String text, float scale) {
        if (fontTexture == null || FONT_COLS <= 0) return 0;
        float charScreenWidth = ((float)fontTexture.getWidth() / FONT_COLS) * scale;
        return text.length() * charScreenWidth;
    }

    public void endUIRendering() {
        // Maybe unbind shader here if needed, or reset GL state
        // glUseProgram(0); // Generally not needed if next draw call binds its own
    }

    public void cleanup() {
        logger.debug("Cleaning up UIManager resources...");
        if (fontTexture != null) {
            try { fontTexture.delete(); } catch (Exception e) { logger.error("Error deleting font texture", e); }
            fontTexture = null;
        }
        // Delete UI VAO, VBO, EBO
        if (uiVaoId != 0) {
            try { glDeleteVertexArrays(uiVaoId); } catch (Exception e) { logger.error("Error deleting UI VAO", e); }
            uiVaoId = 0;
        }
        if (uiVboId != 0) {
            try { glDeleteBuffers(uiVboId); } catch (Exception e) { logger.error("Error deleting UI VBO", e); }
            uiVboId = 0;
        }
        if (uiEboId != 0) {
            try { glDeleteBuffers(uiEboId); } catch (Exception e) { logger.error("Error deleting UI EBO", e); }
            uiEboId = 0;
        }
        // Delete UI shader
        if (uiShader != null) {
            try { uiShader.delete(); } catch (Exception e) { logger.error("Error deleting UI shader", e); }
            // Don't nullify uiShader here if constructor might fail before cleanup
        }
        logger.debug("UIManager cleanup finished.");
    }
}