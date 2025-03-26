package org.chrisgruber.nettank.ui;

import org.chrisgruber.nettank.rendering.Renderer;
import org.chrisgruber.nettank.rendering.Shader;
import org.chrisgruber.nettank.rendering.Texture;
import org.chrisgruber.nettank.util.Colors;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.io.IOException;

// Simple UI Manager using a bitmap font texture
public class UIManager {

    private Texture fontTexture;
    private Renderer uiRenderer; // Use the same quad renderer
    private Shader uiShader;     // Could use the same shader if it handles tinting

    private final int FONT_COLS = 16; // Columns in the font texture
    private final int FONT_ROWS = 16; // Rows in the font texture
    private float charTexWidth;
    private float charTexHeight;

    private Matrix4f uiProjectionMatrix;


    public UIManager() {
        // Assuming a basic Renderer can be reused or create a specific one
        this.uiRenderer = new Renderer(); // Need separate instance? Check Renderer's statefulness
        this.uiProjectionMatrix = new Matrix4f();

        // Load a specific UI shader or reuse the main one if suitable
        try {
            // Reuse the main shader - it has tint and texture support
            this.uiShader = new Shader("src/main/resources/shaders/quad.vert", "src/main/resources/shaders/quad.frag");
        } catch (IOException e) {
            throw new RuntimeException("Failed to load UI shader", e);
        }
    }

    public void loadFontTexture(String filepath) throws IOException {
        this.fontTexture = new Texture(filepath);
        this.charTexWidth = 1.0f / FONT_COLS;
        this.charTexHeight = 1.0f / FONT_ROWS;
    }

    public void startUIRendering(int screenWidth, int screenHeight) {
        // Set up orthographic projection for UI (0,0 is top-left)
        // Ortho: left, right, bottom, top, near, far
        uiProjectionMatrix.setOrtho(0.0f, (float)screenWidth, (float)screenHeight, 0.0f, -1.0f, 1.0f);

        uiShader.bind();
        uiShader.setUniformMat4f("u_projection", uiProjectionMatrix);
        // View matrix is identity for screen space UI
        uiShader.setUniformMat4f("u_view", new Matrix4f().identity()); // Use a static identity matrix
        uiShader.setUniform1i("u_texture", 0); // Use texture unit 0

        // Disable depth testing for UI elements to ensure they draw on top
        // glDisable(GL_DEPTH_TEST); // Be careful if mixing 3D/2D without proper setup
        // Blending should already be enabled by the main Game class
    }

    public void drawText(String text, float screenX, float screenY, float scale) {
        drawText(text, screenX, screenY, scale, Colors.WHITE); // Default white
    }

    public void drawText(String text, float screenX, float screenY, float scale, Vector3f color) {
        if (fontTexture == null || uiRenderer == null || uiShader == null) return;

        fontTexture.bind();
        uiShader.setUniform3f("u_tintColor", color);

        float charWidth = (fontTexture.getWidth() / FONT_COLS) * scale;
        float charHeight = (fontTexture.getHeight() / FONT_ROWS) * scale;

        float currentX = screenX;

        for (char c : text.toCharArray()) {
            if (c == ' ') { // Handle spaces
                currentX += charWidth;
                continue;
            }
            if (c < 32 || c > 126) { // Basic ASCII range check
                c = '?'; // Replace unsupported characters
            }

            int charIndex = c; // ASCII value can directly map if font sheet matches
            int col = charIndex % FONT_COLS;
            int row = charIndex / FONT_COLS;

            float texX = col * charTexWidth;
            float texY = row * charTexHeight;

            // Need to render a specific portion of the texture
            // Modifying the standard Renderer/Shader for this is complex.
            // Alternative: Render the full quad and use shader to clip texture coords.
            // Simpler Alternative: Draw a full textured quad for each char, adjusting its model matrix.

            // --- Using the existing drawQuad ---
            // Calculate center position for the character quad
            float charCenterX = currentX + charWidth / 2.0f;
            float charCenterY = screenY + charHeight / 2.0f;

            // Set the texture coordinates via uniforms (Requires Shader Modification)
            // OR: A more complex renderer that allows custom tex coords per draw.

            // --- Workaround: Use Model matrix to scale/translate texture coords (Less clean) ---
            // This requires modifying the shader or accepting distortion.

            // --- Simplest (but potentially inefficient): Draw full quad and let shader handle it ---
            // This is difficult without shader changes.

            // --- Let's assume Renderer::drawQuad can be extended or we use immediate mode (Bad!) ---
            // For now, we'll just draw tinted quads as placeholders. THIS WON'T SHOW TEXT.
            // uiRenderer.drawQuad(charCenterX, charCenterY, charWidth, charHeight, 0, uiShader);

            // *** Proper Bitmap Font Rendering requires ***
            // 1. A way to specify texture coordinates (s0, t0, s1, t1) for the sub-rectangle of the font texture.
            // 2. Either:
            //    a) Modifying the Renderer/VAO setup to accept custom tex coords per quad.
            //    b) Modifying the shader to calculate texture coordinates based on uniforms (e.g., uniform vec4 u_texCoords).
            //    c) Using a library specifically for font rendering (like LWJGL STB Truetype).

            // Placeholder - Draw white boxes where text should be
            // uiRenderer.drawQuad(charCenterX, charCenterY, charWidth, charHeight, color, uiShader);


            // ACTUAL IMPLEMENTATION WOULD LOOK MORE LIKE THIS (conceptual):
            // uiRenderer.drawTexturedSubQuad(
            //    currentX, screenY, charWidth, charHeight,  // Position & Size
            //    texX, texY, charTexWidth, charTexHeight, // Texture Rect
            //    color, uiShader
            // );


            currentX += charWidth; // Move to the next character position
        }
        uiShader.setUniform3f("u_tintColor", 1.0f, 1.0f, 1.0f); // Reset tint
    }

    // Estimate text width - basic implementation
    public float getTextWidth(String text, float scale) {
        if (fontTexture == null) return 0;
        float charWidth = (fontTexture.getWidth() / FONT_COLS) * scale;
        return text.length() * charWidth;
    }

    public void endUIRendering() {
        // Re-enable depth testing if it was disabled
        // glEnable(GL_DEPTH_TEST);
    }

    public void cleanup() {
        if (fontTexture != null) fontTexture.delete();
        if (uiRenderer != null) uiRenderer.cleanup();
        if (uiShader != null) uiShader.delete();
    }
}
