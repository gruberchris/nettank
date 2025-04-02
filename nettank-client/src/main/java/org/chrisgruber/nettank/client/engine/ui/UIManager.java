package org.chrisgruber.nettank.client.engine.ui;

import org.chrisgruber.nettank.client.engine.graphics.Renderer;
import org.chrisgruber.nettank.client.engine.graphics.Shader;
import org.chrisgruber.nettank.client.engine.graphics.Texture;
import org.chrisgruber.nettank.common.util.Colors;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class UIManager {
    private static final Logger logger = LoggerFactory.getLogger(UIManager.class);
    private static final int FONT_COLS = 18;
    private static final int FONT_ROWS = 6;

    private Texture fontTexture;

    private final Renderer uiRenderer;
    private final Shader uiShader;
    private float charTexWidth;
    private float charTexHeight;
    private final Matrix4f uiProjectionMatrix;
    private final Matrix4f identityMatrix;

    public UIManager() {
        this.uiRenderer = new Renderer();
        this.uiProjectionMatrix = new Matrix4f();
        this.identityMatrix = new Matrix4f().identity();

        try {
            this.uiShader = new Shader("/shaders/quad.vert", "/shaders/quad.frag");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void loadFontTexture(String filepath) throws IOException {
        if (FONT_COLS <= 0 || FONT_ROWS <= 0) {
            throw new IllegalArgumentException("FONT_COLS and FONT_ROWS must be positive.");
        }

        try {
            this.fontTexture = new Texture(filepath);
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
        uiProjectionMatrix.setOrtho(0.0f, screenWidth, screenHeight, 0.0f, -1.0f, 1.0f);
        uiShader.bind();
        uiShader.setUniformMat4f("u_projection", uiProjectionMatrix);
        uiShader.setUniformMat4f("u_view", identityMatrix);
        uiShader.setUniform1i("u_texture", 0);
    }

    public void drawText(String text, float screenX, float screenY, float scale) {
        drawText(text, screenX, screenY, scale, Colors.WHITE);
    }

    public void drawText(String text, float screenX, float screenY, float scale, Vector3f color) {
        if (fontTexture == null || uiRenderer == null || uiShader == null) return;

        fontTexture.bind();
        uiShader.bind();
        uiShader.setUniform3f("u_tintColor", color);

        // Use float division here for character screen size calculation
        float charScreenWidth = ((float)fontTexture.getWidth() / FONT_COLS) * scale;
        float charScreenHeight = ((float)fontTexture.getHeight() / FONT_ROWS) * scale;
        float currentX = screenX;

        for (char c : text.toCharArray()) {
            if (c == ' ') {
                currentX += charScreenWidth;
                continue;
            }

            if (c < 32 || c > 126) c = '?'; // Basic ASCII printable range

            // c is an ASCII character value between 32 and 126
            // charIndex value range is 0 to 94
            int charIndex = c - 32;

            int col = charIndex % FONT_COLS;
            int row = charIndex / FONT_COLS;

            // Calculate NORMALIZED texture coords for the top-left corner and the size
            float texX = col * charTexWidth;
            float texY = row * charTexHeight;

            // Set the uniform telling the shader which sub-rectangle to use
            // u_texRect = vec4(offsetX, offsetY, widthScale, heightScale)
            uiShader.setUniform4f("u_texRect", texX, texY, charTexWidth, charTexHeight);

            // Calculate the CENTER position for the standard quad renderer
            float drawX = currentX + charScreenWidth / 2.0f;
            float drawY = screenY + charScreenHeight / 2.0f;

            // Use the standard Renderer drawQuad
            uiRenderer.drawQuad(drawX, drawY, charScreenWidth, charScreenHeight, 0, uiShader);

            currentX += charScreenWidth;
        }

        // Reset the texture rectangle uniform to default after drawing text
        uiShader.setUniform4f("u_texRect", 0.0f, 0.0f, 1.0f, 1.0f);
        // Reset tint? Optional, depends on if other UI elements need default white
        // uiShader.setUniform3f("u_tintColor", 1.0f, 1.0f, 1.0f);
    }

    public float getTextWidth(String text, float scale) {
        if (fontTexture == null || FONT_COLS <= 0) return 0;

        float charScreenWidth = ((float)fontTexture.getWidth() / FONT_COLS) * scale;

        return text.length() * charScreenWidth;
    }

    public void endUIRendering() {
        // No state changes needed usually unless depth test was disabled
    }

    public void cleanup() {
        if (fontTexture != null) fontTexture.delete();
        if (uiRenderer != null) uiRenderer.cleanup();
        if (uiShader != null) uiShader.delete();
    }
}