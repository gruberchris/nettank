package org.chrisgruber.nettank.client.engine.ui;

import org.chrisgruber.nettank.client.engine.graphics.Shader;
import org.chrisgruber.nettank.client.engine.graphics.Texture;
import org.chrisgruber.nettank.common.util.Colors;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.BufferUtils;
// Removed unused GL11 import
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

// Static imports for OpenGL constants
import static org.lwjgl.opengl.GL11.GL_NO_ERROR;
import static org.lwjgl.opengl.GL11.glGetError; // For error checking
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;


/**
 * Manages rendering of UI elements, particularly text using a bitmap font atlas.
 * Uses its own dedicated Shader and VAO/VBO for rendering UI components
 * in screen space coordinates (pixels, origin top-left).
 */
public class UIManager {
    private static final Logger logger = LoggerFactory.getLogger(UIManager.class);

    // Configuration for the font atlas texture grid
    private static final int FONT_COLS = 19;
    private static final int FONT_ROWS = 5;

    // Resources for UI rendering
    private Texture fontTexture;
    private final Shader uiShader;
    private int uiVaoId;
    private int uiVboId;
    private int uiEboId;

    // Calculated texture coordinate dimensions for a single character in the atlas
    private float charTexWidth;
    private float charTexHeight;

    // Matrices for UI rendering transformations
    private final Matrix4f uiProjectionMatrix; // Orthographic projection for screen space
    private final Matrix4f identityMatrix;     // Simple identity matrix for view (no camera)
    private final Matrix4f uiModelMatrix;      // Reusable matrix for positioning individual UI elements

    // Vertex data for a standard quad, using top-left UV origin (0,0).
    // This matches the Renderer's setup and works correctly with textures
    // that have been flipped vertically on load via STB.
    private static final float[] VERTICES_UI = {
            // Positions(X,Y)  // Texture Coords(U,V) (0,0 = Top-Left)
            -0.5f,  0.5f,    0.0f, 0.0f, // Top-left
            -0.5f, -0.5f,    0.0f, 1.0f, // Bottom-left
            0.5f, -0.5f,    1.0f, 1.0f, // Bottom-right
            0.5f,  0.5f,    1.0f, 0.0f  // Top-right
    };

    // Indices for drawing the quad using two triangles.
    private static final int[] INDICES_UI = { 0, 1, 2,  2, 3, 0 };

    /**
     * Initializes the UIManager, loading its shader and creating OpenGL resources (VAO/VBO/EBO).
     */
    public UIManager() {
        // Load the dedicated UI shader (usually simple quad shader)
        try {
            this.uiShader = new Shader("/shaders/quad.vert", "/shaders/quad.frag");
        } catch (IOException e) {
            logger.error("Failed to load UI shader", e);
            throw new RuntimeException("Failed to load UI shader", e);
        }

        // Initialize matrices
        this.uiProjectionMatrix = new Matrix4f();
        this.identityMatrix = new Matrix4f().identity(); // Constant identity matrix
        this.uiModelMatrix = new Matrix4f();      // For character positioning

        // --- Create VAO/VBO/EBO specifically for UI quads ---
        uiVaoId = glGenVertexArrays();
        glBindVertexArray(uiVaoId);

        // Setup VBO (Vertex Buffer Object)
        uiVboId = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, uiVboId);
        FloatBuffer vertexBuffer = BufferUtils.createFloatBuffer(VERTICES_UI.length);
        vertexBuffer.put(VERTICES_UI).flip();
        glBufferData(GL_ARRAY_BUFFER, vertexBuffer, GL_STATIC_DRAW); // Data is static

        // Setup EBO (Element Buffer Object)
        uiEboId = glGenBuffers();
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, uiEboId);
        IntBuffer elementBuffer = BufferUtils.createIntBuffer(INDICES_UI.length);
        elementBuffer.put(INDICES_UI).flip();
        glBufferData(GL_ELEMENT_ARRAY_BUFFER, elementBuffer, GL_STATIC_DRAW);

        // Setup Vertex Attribute Pointers (how to interpret VBO data)
        final int stride = 4 * Float.BYTES; // 4 floats per vertex
        // Position Attribute (layout location = 0)
        glVertexAttribPointer(0, 2, GL_FLOAT, false, stride, 0);
        glEnableVertexAttribArray(0);
        // Texture Coordinate Attribute (layout location = 1)
        final long texCoordOffset = 2 * Float.BYTES;
        glVertexAttribPointer(1, 2, GL_FLOAT, false, stride, texCoordOffset);
        glEnableVertexAttribArray(1);

        // Unbind all buffers and the VAO
        glBindBuffer(GL_ARRAY_BUFFER, 0);
        glBindVertexArray(0);
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, 0);
        //--------------------------------------
        checkGLError("UIManager Constructor End"); // Check for errors during setup
    }

    /**
     * Loads the font atlas texture used for rendering text.
     * Calculates character dimensions based on atlas grid size.
     *
     * @param filepath Path to the font texture file.
     * @throws IOException If the texture fails to load.
     */
    public void loadFontTexture(String filepath) throws IOException {
        if (FONT_COLS <= 0 || FONT_ROWS <= 0) {
            throw new IllegalArgumentException("FONT_COLS and FONT_ROWS must be positive.");
        }
        try {
            // Load the texture using the Texture class (which handles flipping)
            this.fontTexture = new Texture(filepath);

            // Calculate the normalized width/height of a single character cell in the texture atlas
            this.charTexWidth = 1.0f / FONT_COLS;
            this.charTexHeight = 1.0f / FONT_ROWS;

            logger.info("Font Loaded: {} ({}x{} px, {}x{} grid -> charTex UV Size {}x{})",
                    filepath, fontTexture.getWidth(), fontTexture.getHeight(),
                    FONT_COLS, FONT_ROWS, charTexWidth, charTexHeight);
        } catch (IOException e) {
            logger.error("Failed to load font texture file: {}", filepath, e);
            this.fontTexture = null; // Ensure texture is null on failure
            throw e; // Re-throw the exception
        }
    }

    /**
     * Prepares for rendering UI elements for the current frame.
     * Sets up orthographic projection and binds the UI shader.
     *
     * @param screenWidth  The width of the screen/window in pixels.
     * @param screenHeight The height of the screen/window in pixels.
     */
    public void startUIRendering(int screenWidth, int screenHeight) {
        // Set up orthographic projection: 0,0 top-left, width/height bottom-right
        uiProjectionMatrix.setOrtho(0.0f, screenWidth, screenHeight, 0.0f, -1.0f, 1.0f);

        // Bind the UI shader and set common uniforms
        uiShader.bind();
        uiShader.setUniformMat4f("u_projection", uiProjectionMatrix);
        uiShader.setUniformMat4f("u_view", identityMatrix); // Use identity matrix for view (no camera)
        uiShader.setUniform1i("u_texture", 0); // Tell shader to use texture unit 0

        // Reset default uniforms at the start of UI rendering pass
        uiShader.setUniform3f("u_tintColor", 1.0f, 1.0f, 1.0f); // Default white tint
        uiShader.setUniform4f("u_texRect", 0.0f, 0.0f, 1.0f, 1.0f); // Default full texture rectangle
    }

    /**
     * Draws text using the loaded font texture with default white color.
     *
     * @param text    The string to draw.
     * @param screenX The X coordinate (in pixels, from left) of the text's starting position.
     * @param screenY The Y coordinate (in pixels, from top) of the text's starting position.
     * @param scale   The scaling factor applied to the text size.
     */
    public void drawText(String text, float screenX, float screenY, float scale) {
        drawText(text, screenX, screenY, scale, Colors.WHITE); // Delegate with white color
    }

    /**
     * Draws text using the loaded font texture with a specified color.
     * Renders each character as a separate quad, selecting the correct sub-region
     * from the font atlas texture.
     *
     * @param text    The string to draw.
     * @param screenX The X coordinate (in pixels, from left) of the text's starting position.
     * @param screenY The Y coordinate (in pixels, from top) of the text's starting position.
     * @param scale   The scaling factor applied to the text size.
     * @param color   The color tint (RGB) to apply to the text.
     */
    public void drawText(String text, float screenX, float screenY, float scale, Vector3f color) {
        drawText(text, screenX, screenY, scale, color, 0.35f); // Use 80% of full width as default
    }

    public void drawText(String text, float screenX, float screenY, float scale, Vector3f color, float spaceWidthFactor) {
        // Check if prerequisites are met
        if (fontTexture == null || fontTexture.getTextureId() == 0 || uiShader == null || uiVaoId == 0) {
            logger.warn("UI Text rendering prerequisites not met (Font Texture ID: {}, Shader valid: {}, VAO ID: {}).",
                    (fontTexture != null ? fontTexture.getTextureId() : "null"), (uiShader != null), uiVaoId);
            return;
        }

        // Bind the font texture to texture unit 0
        // Assumes texture filtering (GL_NEAREST) was set correctly during texture loading.
        fontTexture.bind();

        // Ensure the correct UI shader is active and set the text color
        uiShader.bind();
        uiShader.setUniform3f("u_tintColor", color);

        // Calculate the on-screen dimensions of a single character based on font texture size and scale
        final float charBaseWidth = (float)fontTexture.getWidth() / FONT_COLS;
        final float charBaseHeight = (float)fontTexture.getHeight() / FONT_ROWS;
        final float charScreenWidth = charBaseWidth * scale;
        final float charScreenHeight = charBaseHeight * scale;

        // Current drawing position on screen
        float currentX = screenX;

        // Bind the VAO containing the quad geometry setup ONCE before the loop
        glBindVertexArray(uiVaoId);

        // Iterate through each character in the text string
        for (char c : text.toCharArray()) {
            // Handle spaces by simply advancing the drawing position
            if (c == ' ') {
                currentX += charScreenWidth * spaceWidthFactor; // Use a factor to control space width
                continue;
            }
            // Use '?' for characters outside the expected ASCII range (32-126)
            if (c < 32 || c > 126) {
                c = '?';
            }
            // Calculate index relative to the start of the font atlas characters (ASCII 32 = space)
            int charIndex = c - 32;

            // Calculate the row and column of the character in the font atlas grid
            int col = charIndex % FONT_COLS;
            int row = charIndex / FONT_COLS; // row 0 = top row in image file

            // Calculate the texture coordinates (UV) for the character's sub-rectangle
            // U coordinate of the left edge
            float texX = col * charTexWidth;

            // V coordinate of the BOTTOM edge of the character cell in the FLIPPED texture space.
            // Since the texture is loaded flipped (V=0 bottom, V=1 top), and the VBO uses
            // V=0 top / V=1 bottom, this calculation correctly identifies the starting V
            // coordinate for the u_texRect uniform needed by the shader.
            float texY_bottom_left = 1.0f - ((row + 1) * charTexHeight);

            // Apply a small inset to prevent sampling pixels from adjacent characters (texture bleeding)
            final float inset = 0.001f;

            // Set the u_texRect uniform in the shader: (startX, startY, width, height) in UV space
            uiShader.setUniform4f("u_texRect",
                    texX + inset,
                    texY_bottom_left + inset,   // Starting V coordinate (bottom-left)
                    charTexWidth - (2 * inset),   // Width of character in UV space
                    charTexHeight - (2 * inset)); // Height of character in UV space

            // Calculate the screen position for the CENTER of this character's quad
            float drawX = currentX + charScreenWidth / 2.0f;
            float drawY = screenY + charScreenHeight / 2.0f;

            // Calculate and set the model matrix for this character quad (position and scale)
            uiModelMatrix.identity()
                    .translate(drawX, drawY, 0)
                    .scale(charScreenWidth, charScreenHeight, 1);
            uiShader.setUniformMat4f("u_model", uiModelMatrix);

            // Draw the single character quad using the bound VAO and EBO
            glDrawElements(GL_TRIANGLES, INDICES_UI.length, GL_UNSIGNED_INT, 0);

            // Advance the drawing position for the next character
            currentX += charScreenWidth;
        }

        // Unbind the VAO after drawing all characters
        glBindVertexArray(0);

        // Reset the texture rectangle uniform to cover the full texture, preventing
        // potential issues if the same shader is reused for non-atlas drawing later.
        uiShader.setUniform4f("u_texRect", 0.0f, 0.0f, 1.0f, 1.0f);
        // Optional: Reset tint color if needed, though startUIRendering usually handles this.
        // uiShader.setUniform3f("u_tintColor", 1.0f, 1.0f, 1.0f);
    }

    /**
     * Calculates the approximate screen width of a given text string when rendered
     * with the specified scale.
     *
     * @param text  The text string.
     * @param scale The scaling factor.
     * @return The width in pixels, or 0 if the font texture is not loaded.
     */
    public float getTextWidth(String text, float scale) {
        if (fontTexture == null || FONT_COLS <= 0) return 0;
        // Calculate base width from texture and apply scale
        float charBaseWidth = (float)fontTexture.getWidth() / FONT_COLS;
        float charScreenWidth = charBaseWidth * scale;
        return text.length() * charScreenWidth; // Total width is length * char width
    }

    /**
     * Placeholder method called after all UI rendering for the frame is done.
     * Can be used to reset any specific GL states if needed.
     */
    public void endUIRendering() {
        // Currently no specific actions needed here.
        // Could potentially unbind the UI shader if desired: glUseProgram(0);
    }

    /**
     * Cleans up all resources used by the UIManager (Texture, Shader, VAO, VBO, EBO).
     * Should be called when the UI is no longer needed (e.g., game shutdown).
     */
    public void cleanup() {
        logger.debug("Cleaning up UIManager resources...");
        // Delete Font Texture
        if (fontTexture != null) {
            try { fontTexture.delete(); } catch (Exception e) { logger.error("Error deleting font texture", e); }
            fontTexture = null;
        }
        // Delete OpenGL buffers and array object
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
        // Delete UI Shader
        if (uiShader != null) {
            try { uiShader.delete(); } catch (Exception e) { logger.error("Error deleting UI shader", e); }
            // Avoid nullifying shader here in case cleanup is called after constructor failure
        }
        logger.debug("UIManager cleanup finished.");
    }

    /**
     * Helper method to check for OpenGL errors after an operation within UIManager.
     * Logs any errors found.
     *
     * @param operation A description of the OpenGL operation just performed.
     */
    private void checkGLError(String operation) {
        int errorCode;
        while ((errorCode = glGetError()) != GL_NO_ERROR) {
            String errorStr;
            switch (errorCode) {
                // ... (same error code mapping as in Texture.java) ...
                case GL_INVALID_ENUM:                  errorStr = "INVALID_ENUM"; break;
                case GL_INVALID_VALUE:                 errorStr = "INVALID_VALUE"; break;
                case GL_INVALID_OPERATION:             errorStr = "INVALID_OPERATION"; break;
                case GL_STACK_OVERFLOW:                errorStr = "STACK_OVERFLOW"; break;
                case GL_STACK_UNDERFLOW:               errorStr = "STACK_UNDERFLOW"; break;
                case GL_OUT_OF_MEMORY:                 errorStr = "OUT_OF_MEMORY"; break;
                case GL_INVALID_FRAMEBUFFER_OPERATION: errorStr = "INVALID_FRAMEBUFFER_OPERATION"; break;
                default:                               errorStr = String.format("UNKNOWN(0x%X)", errorCode); break;
            }
            logger.error("OpenGL Error after [UIManager - {}]: {} ({})", operation, errorStr, errorCode);
        }
    }
}