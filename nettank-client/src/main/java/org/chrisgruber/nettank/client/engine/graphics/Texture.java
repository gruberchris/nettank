package org.chrisgruber.nettank.client.engine.graphics;

import org.lwjgl.system.MemoryStack;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE;
import static org.lwjgl.opengl.GL30.GL_INVALID_FRAMEBUFFER_OPERATION;
import static org.lwjgl.stb.STBImage.*;
import static org.lwjgl.system.MemoryStack.stackPush;

public class Texture {

    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(Texture.class);

    private int textureId = 0;
    private int width;
    private int height;

    public Texture(String filepath) throws IOException {
        logger.debug("Loading texture {}", filepath);

        ByteBuffer imageBuffer;
        Path path = Paths.get(filepath);

        if (Files.isReadable(path)) {
            try (SeekableByteChannel sbc = Files.newByteChannel(path)) {
                imageBuffer = org.lwjgl.BufferUtils.createByteBuffer((int) sbc.size() + 1);
                while (sbc.read(imageBuffer) != -1) { ; }
            }
        } else {
            URL url = Texture.class.getClassLoader().getResource(filepath);
            if (url == null) {
                throw new IOException("Failed to find texture file: " + filepath);
            }
            try (InputStream source = url.openStream();
                 ReadableByteChannel rbc = Channels.newChannel(source)) {
                imageBuffer = org.lwjgl.BufferUtils.createByteBuffer(1024 * 8); // 8KB initial buffer

                while (true) {
                    int bytes = rbc.read(imageBuffer);
                    if (bytes == -1) {
                        break;
                    }
                    if (imageBuffer.remaining() == 0) {
                        // Resize buffer
                        ByteBuffer newBuffer = org.lwjgl.BufferUtils.createByteBuffer(imageBuffer.capacity() * 2);
                        imageBuffer.flip();
                        newBuffer.put(imageBuffer);
                        imageBuffer = newBuffer;
                    }
                }
            }
        }

        imageBuffer.flip();

        try (MemoryStack stack = stackPush()) {
            IntBuffer w = stack.mallocInt(1);
            IntBuffer h = stack.mallocInt(1);
            IntBuffer channels = stack.mallocInt(1);

            // Load image using STB
            stbi_set_flip_vertically_on_load(true);

            logger.debug("Decoding image data for: {}", filepath);

            ByteBuffer decodedImage = stbi_load_from_memory(imageBuffer, w, h, channels, 4); // Request RGBA
            if (decodedImage == null) {
                String failureReason = stbi_failure_reason();
                logger.error("STB failed to load texture image: {} - Reason: {}", filepath, failureReason);
                throw new IOException("Failed to load texture image: " + filepath + " - " + stbi_failure_reason());
            }

            this.width = w.get(0);
            this.height = h.get(0);

            logger.debug("Decoded image '{}': {}x{} pixels", filepath, width, height);

            if (width <= 0 || height <= 0) {
                stbi_image_free(decodedImage); // Free memory before throwing
                logger.error("Invalid texture dimensions {}x{} for {}", width, height, filepath);
                throw new IOException("Invalid texture dimensions for " + filepath);
            }

            // --- Create and configure OpenGL texture ---
            textureId = glGenTextures();
            checkGLError("glGenTextures for " + filepath); // ADDED Check

            glBindTexture(GL_TEXTURE_2D, textureId);
            checkGLError("glBindTexture for " + filepath); // ADDED Check

            // Set texture parameters suitable for 2D pixel art
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
            checkGLError("glTexParameteri for " + filepath); // ADDED Check

            // Upload texture data to the GPU
            logger.debug("Uploading texture data to GPU for ID: {}", textureId);
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, decodedImage);
            checkGLError("glTexImage2D for " + filepath); // ADDED Check

            logger.info("Successfully loaded and uploaded texture: {} (ID: {})", filepath, textureId);

            // Free the memory allocated by STB
            stbi_image_free(decodedImage);
        } catch (Exception e) { // Catch broader exceptions during GL calls
            logger.error("Exception during texture creation for {}: {}", filepath, e.getMessage(), e);
            // Clean up partial texture if needed before re-throwing
            if (textureId != 0) {
                glDeleteTextures(textureId);
            }
            throw new IOException("Exception during texture creation for " + filepath, e);
        } finally {
            // Unbind texture (always good practice, especially after potential errors)
            glBindTexture(GL_TEXTURE_2D, 0);
        }

        // Unbind texture
        glBindTexture(GL_TEXTURE_2D, 0);
    }

    // Helper method to check for GL errors
    private void checkGLError(String operation) {
        int errorCode;
        while ((errorCode = glGetError()) != GL_NO_ERROR) {
            String errorStr;
            switch (errorCode) {
                case GL_INVALID_ENUM:                  errorStr = "INVALID_ENUM"; break;
                case GL_INVALID_VALUE:                 errorStr = "INVALID_VALUE"; break;
                case GL_INVALID_OPERATION:             errorStr = "INVALID_OPERATION"; break;
                case GL_STACK_OVERFLOW:                errorStr = "STACK_OVERFLOW"; break;
                case GL_STACK_UNDERFLOW:               errorStr = "STACK_UNDERFLOW"; break;
                case GL_OUT_OF_MEMORY:                 errorStr = "OUT_OF_MEMORY"; break;
                case GL_INVALID_FRAMEBUFFER_OPERATION: errorStr = "INVALID_FRAMEBUFFER_OPERATION"; break;
                default:                               errorStr = "UNKNOWN(" + String.format("0x%X", errorCode) + ")"; break;
            }
            // Use logger instead of System.err
            logger.error("OpenGL Error after [{}]: {} ({})", operation, errorStr, errorCode);
            // Optionally throw an exception here if you want to halt on any GL error
            // throw new RuntimeException("OpenGL Error after [" + operation + "]: " + errorStr);
        }
    }

    public void bind() {
        glBindTexture(GL_TEXTURE_2D, textureId);
    }

    public void unbind() {
        glBindTexture(GL_TEXTURE_2D, 0);
    }

    public void delete() {
        glDeleteTextures(textureId);
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }
}
